# Third-party notices

Hangul Quest is MIT licensed (see [LICENSE](LICENSE)). It bundles one third-party asset and
builds on several projects whose licences stay their own.

## Noto Sans CJK KR (font subset)

`src/main/resources/assets/hangulquest/font/hangul.ttf` is a subset of **Noto Sans CJK KR**
(weight 400), generated from `/usr/share/fonts/google-noto-sans-cjk-vf-fonts/NotoSansCJK-VF.ttc`
with `tools/make_font.py`.

- Copyright 2014–2021 Adobe (<http://www.adobe.com/>), with Reserved Font Name "Source".
- Noto is a trademark of Google Inc.
- Licensed under the **SIL Open Font License, Version 1.1**: <https://scripts.sil.org/OFL>

The OFL permits bundling and subsetting; the font is not sold on its own and the reserved name is
not used for a modified font beyond the required attribution. The subset contains Hangul syllables,
jamo, ASCII and the punctuation the mod draws.

## Minecraft and Fabric

Minecraft is a trademark of Mojang Synergies AB. This mod is not affiliated with, endorsed or
sponsored by Mojang Studios or Microsoft. It contains no Minecraft code or assets and requires a
legitimate copy of the game.

[Fabric Loader](https://github.com/FabricMC/fabric-loader) and
[Fabric API](https://github.com/FabricMC/fabric) are licensed under the Apache License 2.0. They are
not bundled; the mod declares them as dependencies and the player installs them separately.

Some questlines are themed around vanilla **advancements**: only their names and completion
conditions are referenced, and no advancement data or assets are redistributed.

## Vanilla names

`src/main/resources/assets/hangulquest/data/names.json` is generated with `tools/extract_names.py`
from the Korean and German language files of the installed game. It holds item, block, entity and
biome **names** only (the Korean and German words themselves), not models, textures, sounds or any
other asset. The quest text built on those names is original.

## Lute

The vocabulary import reads the CSV export of [Lute](https://github.com/LuteOrg/lute-v3). Lute is not
bundled and is not modified; Hangul Quest only parses a file the player exports and never writes to
it. Lute is licensed under the MIT licence.

## Build tools

Gradle (Apache-2.0), Fabric Loom (Apache-2.0) and JUnit 5 (Eclipse Public License 2.0) are used to
build and test the mod and are not distributed with it.
