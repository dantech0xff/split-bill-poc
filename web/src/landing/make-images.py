#!/usr/bin/env python3
"""Makes the home page's screenshots: WebP copies of the app's reference images.

The home page shows the screens that the app's screenshot tests draw, the ones
docs/store-listing.md lists for Google Play: each image of app/src/test/screenshots/, light and
dark, cut to its top 824 x 1830 pixels as the store's are, and scaled to 600 pixels wide, twice
the size a phone shows it at or more. Run it from the repository's root when those images change:

    python3 web/src/landing/make-images.py

It needs Pillow with WebP support (pip install pillow).
"""

import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SOURCE = ROOT / "app" / "src" / "test" / "screenshots"
SCREEN = (824, 1830)
WIDTH = 600

# Each screenshot's name on the page, and the reference image it is made from.
IMAGES = {
    "home-owed": "01-home-owed",
    "new-split": "02-new-split",
    "result": "05-result-top",
    "assistant": "31-assistant-proposal",
    "requests": "10-requests-sent",
    "group": "23-group",
    "insights": "40-insights",
}


def main() -> None:
    height = round(SCREEN[1] * WIDTH / SCREEN[0])
    for name, source in IMAGES.items():
        for theme in ("", "-dark"):
            path = SOURCE / f"{source}{theme}.png"
            image = Image.open(path).convert("RGB")
            if image.width != SCREEN[0] or image.height < SCREEN[1]:
                sys.exit(f"{path.name} is {image.width} x {image.height} pixels; the page takes 824 wide and at least 1830 high.")
            screen = image.crop((0, 0, *SCREEN)).resize((WIDTH, height), Image.LANCZOS)
            screen.save(HERE / f"{name}{theme}.webp", "WEBP", quality=80, method=6)
    print(f"Wrote {len(IMAGES) * 2} screenshots of {WIDTH} x {height} pixels to {HERE.relative_to(ROOT)}.")


if __name__ == "__main__":
    main()
