#!/usr/bin/env bash
# Makes the three fonts of the web pages again from the font files the app ships, whose hashes are
# pinned in core/designsystem/font-checksums.sha256, so that a page reads in the same type as the app:
#
#   bricolage-grotesque-800.woff2  from bricolage_grotesque_extrabold.ttf, the 14pt cut: titles and amounts
#   be-vietnam-pro-400.woff2       from be_vietnam_pro_regular.ttf: text
#   be-vietnam-pro-600.woff2       from be_vietnam_pro_semibold.ttf: labels, names and buttons
#
# Each file keeps only Latin and Vietnamese: Basic Latin, Latin-1, Latin Extended-A and -B, the
# spacing and combining marks, Latin Extended Additional (every Vietnamese letter), general
# punctuation, the currency signs, the trade mark sign and the minus sign. A character outside them,
# such as one of a name in another script, is drawn in the browser's own font. The cut keeps the
# layout features the pages need (marks, kerning, ligatures and tabular figures for amounts), the
# hinting of the source files and every name record, the copyright (name ID 0) and the licence
# (IDs 13 and 14) among them. The cut of Bricolage Grotesque drawn for large sizes is not used on
# the web. The licences, the SIL Open Font License 1.1 of each family, lie beside the fonts.
#
# fontTools 4.60.1 and Brotli 1.1.0 give the same bytes every time. Install them into a folder and
# run this from the repository's root, for example:
#
#   pip install --target <folder> fonttools==4.60.1 brotli==1.1.0
#   PYTHONPATH=<folder> bash web/src/fonts/make-fonts.sh
#
# Then check the result in this folder: sha256sum --check --strict font-checksums.sha256
set -euo pipefail

for font in bricolage_grotesque_extrabold:bricolage-grotesque-800 \
    be_vietnam_pro_regular:be-vietnam-pro-400 \
    be_vietnam_pro_semibold:be-vietnam-pro-600; do
  python3 -m fontTools.subset "core/designsystem/src/main/res/font/${font%%:*}.ttf" \
    --output-file="web/src/fonts/${font##*:}.woff2" --flavor=woff2 \
    --unicodes=U+0000-00FF,U+0100-024F,U+0250-02FF,U+0300-036F,U+1E00-1EFF,U+2000-206F,U+20A0-20CF,U+2122,U+2212 \
    --layout-features=ccmp,locl,mark,mkmk,kern,liga,tnum --name-IDs='*'
done
