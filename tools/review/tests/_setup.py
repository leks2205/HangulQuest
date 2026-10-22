"""Makes tools/review and tools importable for the tests (run: python3 -m unittest discover -s tools/review/tests)."""
import sys
from pathlib import Path

REVIEW = Path(__file__).resolve().parent.parent
TOOLS = REVIEW.parent
ROOT = TOOLS.parent
for path in (str(REVIEW), str(TOOLS)):
    if path not in sys.path:
        sys.path.insert(0, path)
