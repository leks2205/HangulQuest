"""Makes tools/i18n importable for the tests (run: python3 -m unittest discover -s tools/i18n/tests)."""
import sys
from pathlib import Path

I18N = Path(__file__).resolve().parent.parent
ROOT = I18N.parent.parent
if str(I18N) not in sys.path:
    sys.path.insert(0, str(I18N))
