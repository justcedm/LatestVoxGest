"""Word-token phrase builder for VoxGest.

This module consumes only accepted recognition tokens. It does not classify raw
model output, and it deliberately avoids old endpoint labels such as ASK_NAME.
"""

from dataclasses import dataclass
import time


NO_OUTPUT_TOKENS = {"", "NOTHING", "NONE", "NO_WORD", "IDLE", "REST"}
CONTROL_CLEAR = {"CLEAR", "RESET"}
CONTROL_DELETE = {"DEL", "DELETE", "BACKSPACE"}
CONTROL_SPACE = {"SPACE", "_"}
ALPHABET = set("ABCDEFGHIJKLMNOPQRSTUVWXYZ")

STATIC_PATTERNS = {
    ("WHAT", "IS", "YOUR", "NAME"): "What is your name?",
    ("ARE", "YOU", "A", "STUDENT"): "Are you a student?",
    ("ARE", "YOU", "OKAY"): "Are you okay?",
    ("WHERE", "DO", "YOU", "LIVE"): "Where do you live?",
}
NAME_PREFIX = ("MY", "NAME", "IS")
KNOWN_PREFIXES = set()
for pattern in STATIC_PATTERNS:
    for idx in range(1, len(pattern) + 1):
        KNOWN_PREFIXES.add(pattern[:idx])
for idx in range(1, len(NAME_PREFIX) + 1):
    KNOWN_PREFIXES.add(NAME_PREFIX[:idx])


@dataclass
class PhraseResult:
    action: str
    accepted: bool
    tokens: tuple
    partial_text: str = ""
    finalized_text: str = ""
    reason: str = ""


class PhraseBuilder:
    """Build sentence outputs from confirmed word and letter tokens."""

    def __init__(self, timeout_seconds=6.0):
        self.timeout_seconds = float(timeout_seconds)
        self.tokens = []
        self.last_token_at = None
        self.last_finalized_text = ""

    def clear(self):
        self.tokens.clear()
        self.last_finalized_text = ""
        self.last_token_at = None
        return self._result("clear", True)

    def accept(self, token, now=None):
        now = time.time() if now is None else float(now)
        normalized = self._normalize(token)

        if normalized in NO_OUTPUT_TOKENS:
            return self._result("noop", False, reason="no_output")
        if normalized in CONTROL_SPACE:
            self.last_token_at = now
            return self._result("space", True, reason="separator")
        if normalized in CONTROL_CLEAR:
            return self.clear()
        if normalized in CONTROL_DELETE:
            removed = self.tokens.pop() if self.tokens else ""
            self.last_token_at = now
            self.last_finalized_text = ""
            return self._result("delete", True, reason=removed)

        self.tokens.append(normalized)
        self.last_token_at = now
        self.last_finalized_text = ""

        finalized = self._finalize_if_complete()
        if finalized:
            return finalized
        return self._result("append", True)

    def confirm(self):
        """Finalize on explicit user confirmation, such as Speak."""
        finalized = self._format_current_tokens()
        if finalized:
            self.tokens.clear()
            self.last_finalized_text = finalized
            self.last_token_at = None
            return self._result("finalize", True, finalized_text=finalized)
        return self._result("confirm_wait", False, reason="incomplete_pattern")

    def check_timeout(self, now=None):
        """Keep partial text visible after timeout, but do not finalize."""
        if self.last_token_at is None:
            return self._result("idle", False)
        now = time.time() if now is None else float(now)
        if now - self.last_token_at >= self.timeout_seconds:
            return self._result("timeout_partial", False, reason="partial_not_finalized")
        return self._result("active", False)

    def partial_text(self):
        if not self.tokens:
            return ""
        formatted = self._format_current_tokens()
        if formatted:
            return formatted
        return " ".join(self.tokens)

    def strip(self, max_chars=70):
        text = self.last_finalized_text or self.partial_text()
        if len(text) <= max_chars:
            return text
        return text[-max_chars:]

    def _normalize(self, token):
        return str(token or "").strip().upper().replace(" ", "")

    def _result(self, action, accepted, partial_text=None, finalized_text="", reason=""):
        return PhraseResult(
            action=action,
            accepted=accepted,
            tokens=tuple(self.tokens),
            partial_text=self.partial_text() if partial_text is None else partial_text,
            finalized_text=finalized_text,
            reason=reason,
        )

    def _finalize_if_complete(self):
        key = tuple(self.tokens)
        if key in STATIC_PATTERNS:
            text = STATIC_PATTERNS[key]
            self.tokens.clear()
            self.last_finalized_text = text
            self.last_token_at = None
            return self._result("finalize", True, partial_text="", finalized_text=text)
        return None

    def _format_current_tokens(self):
        key = tuple(self.tokens)
        if key in STATIC_PATTERNS:
            return STATIC_PATTERNS[key]
        if len(self.tokens) > len(NAME_PREFIX) and tuple(self.tokens[:3]) == NAME_PREFIX:
            letters = self.tokens[3:]
            if letters and all(token in ALPHABET for token in letters):
                return f"My name is {''.join(letters)}."
        return ""
