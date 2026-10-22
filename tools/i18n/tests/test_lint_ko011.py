"""KO011 (놓다 with a sapling, carpet, banner ...) looks at the object, not at an anchor in the same clause."""
import importlib.util
import unittest

from _setup import ROOT

_spec = importlib.util.spec_from_file_location("lint_korean_tool", ROOT / "tools" / "lint_korean.py")
lint = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(lint)


def ids(text):
    return [f["rule"] if "rule" in f else f.get("id") for f in lint.lint_text(text, "trail.t.s1.clue", 2)]


class Ko011(unittest.TestCase):
    def test_aCarpetIsStillNotPlaced(self):
        self.assertIn("KO011", " ".join(map(str, lint.lint_text("양탄자를 세 개 놓아요.", "trail.t.s1.clue", 2))))

    def test_aBannerAnchorDoesNotMakeATorchWrong(self):
        for text in ("현수막 옆에 횃불을 놓아요.", "양탄자 위에 상자를 놓아야 해요.", "깃발 근처에 침대를 놓으세요."):
            self.assertNotIn("KO011", " ".join(map(str, lint.lint_text(text, "trail.t.s1.clue", 2))), text)

    def test_aColouredBannerObjectIsStillCaught(self):
        self.assertIn("KO011", " ".join(map(str, lint.lint_text("회색 현수막을 놓아요.", "trail.t.s1.clue", 2))))


if __name__ == "__main__":
    unittest.main()
