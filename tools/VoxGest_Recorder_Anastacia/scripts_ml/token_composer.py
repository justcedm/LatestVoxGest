"""Token-based sentence composition for VoxGest recognition hardening.

The composer only changes output after a prediction has passed the runtime
acceptance gates. Raw or unstable predictions should never be sent here.
"""

from dataclasses import dataclass


ALPHABET_LABELS = tuple("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
CONTROL_LABELS = {"space", "del"}
NO_OUTPUT_LABELS = {"NOTHING", "nothing", "idle", "rest", "no_word", "none"}


@dataclass
class TokenResult:
    label: str
    action: str
    accepted: bool
    sentence: str
    token: str = ""
    reason: str = ""


class TokenComposer:
    """Build sentence text from accepted labels, not raw model guesses."""

    def __init__(self, word_labels=None):
        self.word_labels = {str(label).upper() for label in (word_labels or [])}
        self.tokens = []
        self.letters = []

    def set_word_labels(self, word_labels):
        self.word_labels = {str(label).upper() for label in (word_labels or [])}

    def clear(self):
        self.tokens.clear()
        self.letters.clear()

    def _flush_letters(self):
        if self.letters:
            self.tokens.append("".join(self.letters))
            self.letters.clear()

    def sentence(self):
        parts = list(self.tokens)
        if self.letters:
            parts.append("".join(self.letters))
        return " ".join(part for part in parts if part).strip()

    def strip(self, max_chars=70):
        text = self.sentence()
        if len(text) <= max_chars:
            return text
        return text[-max_chars:]

    def accept(self, label):
        raw = str(label or "").strip()
        if not raw:
            return TokenResult(raw, "ignore", False, self.sentence(), reason="empty")

        upper = raw.upper()
        lower = raw.lower()

        if upper in NO_OUTPUT_LABELS or lower in NO_OUTPUT_LABELS:
            return TokenResult(raw, "noop", False, self.sentence(), reason="no_output")

        if upper in ALPHABET_LABELS:
            self.letters.append(upper)
            return TokenResult(upper, "append_letter", True, self.sentence(), token=upper)

        if lower == "space":
            self._flush_letters()
            return TokenResult(raw, "space", True, self.sentence(), token=" ")

        if lower == "del":
            if self.letters:
                removed = self.letters.pop()
            elif self.tokens:
                removed = self.tokens.pop()
            else:
                removed = ""
            return TokenResult(raw, "delete", True, self.sentence(), token=removed)

        if upper in self.word_labels:
            self._flush_letters()
            self.tokens.append(upper)
            return TokenResult(upper, "append_word", True, self.sentence(), token=upper)

        return TokenResult(raw, "ignore", False, self.sentence(), reason="unknown_label")
