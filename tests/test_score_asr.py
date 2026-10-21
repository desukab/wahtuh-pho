#!/usr/bin/env python3
"""Unit tests for the dependency-free ASR scorer."""
import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "tools" / "score_asr.py"
SPEC = importlib.util.spec_from_file_location("score_asr", MODULE_PATH)
score_asr = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(score_asr)


class ScoreAsrTests(unittest.TestCase):
    def test_normalization_is_unicode_aware_and_removes_punctuation(self):
        self.assertEqual(score_asr.normalize(" Hello,  WORLD! "), "hello world")
        self.assertEqual(score_asr.normalize("తెలుగు, తెలుగు!"), "తెలుగు తెలుగు")
        self.assertEqual(score_asr.normalize("e\u0301"), score_asr.normalize("é"))

    def test_edit_distance(self):
        self.assertEqual(score_asr.edit_distance(list("kitten"), list("sitting")), 3)
        self.assertEqual(score_asr.edit_distance([], list("తెలుగు")), 6)
        self.assertEqual(score_asr.edit_distance(list("same"), list("same")), 0)

    def test_word_error_count(self):
        self.assertEqual(score_asr.errors("one two three", "one four three", "word"), (1, 3))
        self.assertEqual(score_asr.errors("తెలుగు మాట", "తెలుగు మాట", "word"), (0, 2))

    def test_character_error_count_ignores_spaces(self):
        self.assertEqual(score_asr.errors("a b", "ab", "char"), (0, 2))
        self.assertEqual(score_asr.errors("abc", "adc", "char"), (1, 3))


if __name__ == "__main__":
    unittest.main()
