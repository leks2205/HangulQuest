"""The adnominal colour forms count as their colour words in the coverage lexicon (run with the i18n tests)."""
import importlib.util
import unittest

from _setup import ROOT

_spec = importlib.util.spec_from_file_location("coverage_tool", ROOT / "tools" / "coverage.py")
coverage_tool = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(coverage_tool)


class AdnominalColours(unittest.TestCase):
    def lexicon(self):
        return coverage_tool.Lexicon.from_json([
            {"ko": "검은색", "id": "black"}, {"ko": "검", "id": "sword"}, {"ko": "양털", "id": "wool"},
            {"ko": "빨간색", "id": "red"}, {"ko": "파란색", "id": "blue"}, {"ko": "노란색", "id": "yellow"},
            {"ko": "하얀색", "id": "white"}, {"ko": "침대", "id": "bed"}])

    def test_blackIsNotASword(self):
        found, placed, seen = self.lexicon().tokens("검은 양털을 놓아야 해요.")
        self.assertEqual(["검은색", "양털"], found)
        self.assertNotIn("검", found)

    def test_everyAdjectiveIsItsColour(self):
        lexicon = self.lexicon()
        for adjective, noun in coverage_tool.ADNOMINAL_COLOURS.items():
            self.assertEqual([noun, "침대"], lexicon.tokens(f"{adjective} 침대")[0], adjective)

    def test_aLoneColourNounStillWorks(self):
        self.assertEqual(["검은색"], self.lexicon().tokens("검은색이에요")[0][:1])


if __name__ == "__main__":
    unittest.main()
