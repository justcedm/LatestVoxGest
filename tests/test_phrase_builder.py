import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts_ml"))

from phrase_builder import PhraseBuilder


def feed(builder, tokens):
    result = None
    for token in tokens:
        result = builder.accept(token)
    return result


class PhraseBuilderTests(unittest.TestCase):
    def test_what_your_name(self):
        builder = PhraseBuilder()
        result = feed(builder, ["WHAT", "YOUR", "NAME"])
        self.assertEqual(result.finalized_text, "What is your name?")

    def test_your_name_what(self):
        builder = PhraseBuilder()
        result = feed(builder, ["YOUR", "NAME", "WHAT"])
        self.assertEqual(result.finalized_text, "What is your name?")

    def test_my_name_is_spelled_letters_confirmed(self):
        builder = PhraseBuilder()
        result = feed(builder, ["MY", "NAME", "J", "H", "O", "N"])
        self.assertEqual(result.finalized_text, "")
        self.assertEqual(builder.confirm().finalized_text, "My name is JHON.")

    def test_you_student(self):
        builder = PhraseBuilder()
        result = feed(builder, ["YOU", "STUDENT"])
        self.assertEqual(result.finalized_text, "Are you a student?")

    def test_student_you(self):
        builder = PhraseBuilder()
        result = feed(builder, ["STUDENT", "YOU"])
        self.assertEqual(result.finalized_text, "Are you a student?")

    def test_you_okay(self):
        builder = PhraseBuilder()
        result = feed(builder, ["YOU", "OKAY"])
        self.assertEqual(result.finalized_text, "Are you okay?")

    def test_okay_you(self):
        builder = PhraseBuilder()
        result = feed(builder, ["OKAY", "YOU"])
        self.assertEqual(result.finalized_text, "Are you okay?")

    def test_where_you_live(self):
        builder = PhraseBuilder()
        result = feed(builder, ["WHERE", "YOU", "LIVE"])
        self.assertEqual(result.finalized_text, "Where do you live?")

    def test_you_live_where(self):
        builder = PhraseBuilder()
        result = feed(builder, ["YOU", "LIVE", "WHERE"])
        self.assertEqual(result.finalized_text, "Where do you live?")

    def test_nothing_tokens_are_ignored(self):
        builder = PhraseBuilder()
        result = feed(builder, ["WHAT", "NOTHING", "YOUR", "NOTHING", "NAME"])
        self.assertEqual(result.finalized_text, "What is your name?")

    def test_partial_phrases_do_not_finalize_early(self):
        builder = PhraseBuilder()
        result = feed(builder, ["WHAT", "YOUR"])
        self.assertEqual(result.finalized_text, "")
        self.assertEqual(builder.confirm().finalized_text, "")

    def test_timeout_keeps_partial_without_finalize(self):
        builder = PhraseBuilder(timeout_seconds=1.0)
        builder.accept("WHERE", now=10.0)
        builder.accept("YOU", now=10.5)
        result = builder.check_timeout(now=12.0)
        self.assertEqual(result.action, "timeout_partial")
        self.assertEqual(result.finalized_text, "")
        self.assertEqual(result.partial_text, "WHERE YOU")

    def test_del_removes_last_token(self):
        builder = PhraseBuilder()
        feed(builder, ["WHAT", "YOUR", "del", "YOUR", "NAME"])
        self.assertEqual(builder.last_finalized_text, "What is your name?")

    def test_clear_resets_phrase_buffer(self):
        builder = PhraseBuilder()
        feed(builder, ["YOU", "clear", "OKAY"])
        self.assertEqual(builder.tokens, ["OKAY"])
        self.assertEqual(builder.confirm().finalized_text, "")


if __name__ == "__main__":
    unittest.main()
