# RaveCraft — asset license

Copyright © 2026 the RaveCraft authors.

The following **assets** are licensed under the
**Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License (CC BY-NC-SA 4.0)**:

- textures and their animation files: `src/main/resources/assets/ravecraft/textures/**`
- the mod icon: `src/main/resources/assets/ravecraft/icon.png`
- sounds: `src/main/resources/assets/ravecraft/sounds/**`
- hand-made models: `src/main/resources/assets/ravecraft/models/**` that are not under `src/main/generated`
- art sources: `art/**` (Blockbench models, drafts, previews)

## Exceptions: Mojang textures

These files contain textures by Mojang (© Mojang Studios, used under the
[Minecraft Usage Guidelines](https://www.minecraft.net/usage-guidelines)) and are **not** covered by CC BY-NC-SA:

- `textures/block/deepslate_neodymium_ore_*.png`: the neodymium grains are drawn on Mojang's `deepslate.png`;
- `textures/item/neodymium_ingot.png`: a recolour of Mojang's `iron_ingot.png`.

`tools/textures/materials.py` reads both vanilla textures from the local Minecraft jar at generation time; the
repository holds no other Mojang texture.

Full legal text: https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode

In short, you may share and adapt these assets if you:
- **give credit** to RaveCraft, link to the license and indicate changes;
- do **not** use them for commercial purposes;
- **share** your adaptations under the same license.

Everything else (Java source, build scripts, tools such as `tools/textures/gen_textures.py`, generated JSON data) is licensed under the **GNU Lesser General Public License v3.0 only**: see [LICENSE](LICENSE) and [COPYING](COPYING).
