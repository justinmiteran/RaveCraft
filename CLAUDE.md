# RaveCraft — guide de l'environnement

Mod Fabric `ravecraft` pour Minecraft 26.3. Le cahier des charges et l'avancement sont dans [docs/SPEC.md](docs/SPEC.md). Ce fichier décrit **l'environnement et les règles de travail**.

> RaveCraft ajoute du matériel de sonorisation, de DJ et d'éclairage fonctionnel et réaliste (enceintes, platines, consoles, projecteurs…), avec une progression qui va de l'enceinte portable au soundsystem de festival. Package `com.ravecraft.mod`. Le projet est déjà initialisé : la section suivante ne sert plus.

## Démarrer un nouveau mod (à faire une seule fois)

```bash
cp -r ~/Desktop/Projets/templates/fabric-mod ~/Desktop/Projets/MonMod
cd ~/Desktop/Projets/MonMod
python tools/init_mod.py --modid monmod --package com.moi.monmod --name "Mon Mod"
python tools/textures/gen_textures.py
(cd tools/blockbench-mcp && npm ci)        # serveur MCP blockbench
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"
./gradlew runDatagen && ./gradlew build
git init -b main && git add -A && git commit -m "Initial commit from Fabric mod template"
```

`init_mod.py` renomme partout l'id (`ravecraft`), le package (`com.ravecraft.mod`), le préfixe de classe (`RaveCraft…`) et le nom affiché (`RaveCraft`), y compris dans ce fichier. Ensuite :
- écrire le cahier des charges dans `docs/SPEC.md` ;
- remplacer le bloc et l'objet d'exemple (`example_block`, `example_item`, `Cooldown`), ou les garder comme modèles jusqu'au premier vrai contenu ;
- ouvrir le dossier dans IntelliJ, qui importe Gradle et génère les run configs.

## Versions (figées, ne pas changer sans raison)

| Élément | Version | Où |
|---|---|---|
| Minecraft | `26.3` | `gradle.properties` |
| Fabric Loader | `0.20.1` | `gradle.properties` |
| Fabric API | `0.166.2+26.3` | `gradle.properties` |
| Loom | `1.19-SNAPSHOT` (résout en 1.19.x), plugin `net.fabricmc.fabric-loom` | `build.gradle` |
| Gradle | `9.8.2` (wrapper) | `gradle/wrapper/gradle-wrapper.properties` |
| Java | 25 (Temurin) ; daemon Gradle forcé via `gradle/gradle-daemon-jvm.properties` | |
| JUnit | `5.14.4` (BOM) | `gradle.properties` |

Pour monter de version : vérifier les combinaisons sur `https://meta.fabricmc.net/v2/versions/...`, `https://maven.fabricmc.net/.../maven-metadata.xml` et la branche de la version visée de `FabricMC/fabric-example-mod`. Mettre à jour ce tableau et `depends` dans `fabric.mod.json`.

## Machine (Windows 11)

- JDK 25 : `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`. **Le `java` du PATH est Java 8 et `JAVA_HOME` n'est pas défini.** En ligne de commande, préfixer :
  ```bash
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"
  ```
- IDE : IntelliJ IDEA + plugin *Minecraft Development*. Loom génère les run configs *Minecraft Client*, *Minecraft Server* et *Data Generation*.
- Outils : `gh` (connecté), Node 24, Python 3.14 + Pillow, `fnm` (Node 20 pour le MCP minecraft-dev).
- Ne jamais accepter l'EULA Minecraft à la place de l'utilisateur (`run/eula.txt`).

## Commandes

```bash
./gradlew build          # compile + tests JUnit + GameTests + jar (build/libs/<modid>-<ver>.jar)
./gradlew test           # tests JUnit purs (package core)
./gradlew runGameTest    # GameTests seuls (serveur headless)
./gradlew codeQuality    # règles de code Checkstyle + PMD (hors build, job CI séparé)
./gradlew runDatagen     # génère les JSON d'assets/données dans src/main/generated
./gradlew runClient      # lance le jeu (préférer la run config IntelliJ)
./gradlew genSources     # (re)décompile Minecraft pour la navigation
python tools/textures/gen_textures.py   # régénère les textures + art/textures/preview.png
```

Règle : **ne jamais déclarer une tâche terminée sans `./gradlew build` et `./gradlew codeQuality` verts.** Lancer aussi `runDatagen` si des blocs, objets, recettes, tags ou loot ont changé.

## ⚠️ Noms Mojang, pas Yarn

Depuis 26.1, Minecraft n'est plus obfusqué et Fabric utilise les **noms officiels Mojang**. Les noms Yarn (tutoriels ≤ 1.21.11) **n'existent pas** ici.

| Yarn (ancien) | Mojang (à utiliser) |
|---|---|
| `PersistentState` | `SavedData` (`net.minecraft.world.level.saveddata`) |
| `ServerWorld` / `World` | `ServerLevel` / `Level` |
| `Registries` | `BuiltInRegistries` (et `Registries` pour les `ResourceKey`) |
| `CustomPayload` / `PacketCodec` | `CustomPacketPayload` / `StreamCodec` |
| `Identifier.of` | `Identifier.fromNamespaceAndPath` |
| `getWeakRedstonePower` | `getSignal` |
| `DrawContext` | `GuiGraphicsExtractor` |

Datagen Fabric : `FabricModelProvider` (côté client), `FabricBlockLootSubProvider`, `FabricTagsProvider.BlockTagsProvider`, `FabricLanguageProvider`, `FabricRecipeProvider`.

**Avant d'utiliser une classe ou une méthode Minecraft/Fabric, vérifier qu'elle existe.** Trois moyens : le MCP `minecraft-dev`, les sources décompilées, ou le LSP. Les sources décompilées sont dans `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-{common,clientOnly}-*/26.3/*-sources.jar` ; les extraire dans le scratchpad pour y faire des `grep`.

Pièges déjà rencontrés :
- Chaque bloc ou objet doit recevoir son id **avant** sa construction : `properties.setId(key)` (voir `ModBlocks.register`).
- GLFW n'est pas sur le classpath de compilation. Pour les touches, utiliser `com.mojang.blaze3d.platform.InputConstants.KEY_*`.
- Les codecs de liste (`ByteBufCodecs.list(max)`) lèvent une exception au-delà de `max`, ce qui déconnecte le joueur. Il faut plafonner les données **avant** l'envoi.
- Un bloc avec `requiresCorrectToolForDrops()` ne lâche rien sans tag d'outil (`mineable/pickaxe`…).

## Serveurs MCP (`.mcp.json`)

| Serveur | Usage |
|---|---|
| `minecraft-dev` | Sources Minecraft (Mojmap), recherche de symboles, validation de mixins. **Premier réflexe** pour toute API vanilla. |
| `context7` | Doc Fabric API et Loom à jour. |
| `intellij` | IDE ouvert sur le projet (`127.0.0.1:64342/stream`) : inspections, run configs, navigation. IntelliJ doit être lancé. |
| `blockbench` | Mode headless sur `./art` : créer et éditer des `.bbmodel`, intégrer des PNG, **rendre un aperçu PNG** pour vérifier visuellement. Prérequis : `npm ci` dans `tools/blockbench-mcp`. |

## Architecture du code

Source sets séparés (Loom `splitEnvironmentSourceSets`) :

- `src/main/java` : code commun et serveur. **Jamais d'import `net.minecraft.client`** ici.
  - `<package>.core` : logique métier **en Java pur**, sans dépendance Minecraft, donc testable en JUnit. Y mettre tout ce qui peut l'être (calculs, graphes, parsers, règles).
  - `<package>.registry` : enregistrement des blocs, objets, block entities, onglets créatifs, composants. Une classe `ModX` par registre, avec `init()` appelé depuis `RaveCraft.onInitialize`.
  - `block`, `blockentity`, `item`, `network`, `world` : la couche Minecraft, qui adapte `core` au jeu.
- `src/client/java` : `client` (rendu, écrans, HUD, touches) et `datagen`. Le datagen tourne côté client : `configureDataGeneration { client = true }`.
- `src/test/java` : tests JUnit 5 de `core`.
- `src/gametest/java` : GameTests (vrai serveur, mod séparé `ravecraft-gametest`, jamais dans le jar livré). Chaque classe doit être déclarée dans `src/gametest/resources/fabric.mod.json`.

Règles de code : vérifiées par `./gradlew codeQuality` (Checkstyle `config/checkstyle/`, PMD `config/pmd/ruleset.xml`), détail dans [README.md](README.md). Écrire directement du code conforme : méthodes courtes (complexité cognitive ≤ 15, cyclomatique ≤ 10), au plus deux niveaux de `if` ou de boucles, des constantes nommées dans `core`, `RaveCraft.id(path)` pour les identifiants, `RaveCraft.LOGGER` plutôt que la console, une Javadoc sur chaque classe, chaque méthode publique ou protégée et chaque méthode de plus de 10 lignes (hors tests et mixins), avec `@param` et `@return` sur les méthodes publiques. Pas d'exception fichier par fichier : corriger le code ou, si une règle est fausse pour tout le projet, la changer dans la config.

Règles de performance : aucun scan de blocs ou de chunks par tick. Les données dérivées vivent en mémoire et ne sont invalidées que sur événement (placement, casse, voisin). La persistance passe par `SavedData`. Les envois réseau sont plafonnés et à fréquence limitée.

## Ajouter un bloc ou un objet (checklist)

1. Enregistrer dans `registry/ModBlocks` (bloc + `BlockItem`) ou `registry/ModItems`.
2. Texture : une fonction dans `tools/textures/gen_textures.py`, puis régénérer et **regarder `art/textures/preview.png`**.
3. Modèle et blockstate : une ligne dans `RaveCraftModelProvider` (`createTrivialCube`, `generateFlatItem`…). Pour une géométrie custom : Blockbench (`art/models`), export JSON dans `assets/<modid>/models/block/`.
4. Loot (`RaveCraftBlockLootProvider`), tags d'outil (`RaveCraftBlockTagProvider`), recette (`RaveCraftRecipeProvider`).
5. Traductions dans `assets/<modid>/lang/en_us.json` **et** `fr_fr.json`.
6. Un test : JUnit si la logique est dans `core`, GameTest si elle dépend du monde.
7. `./gradlew runDatagen && ./gradlew build`, puis vérifier en jeu.

## Assets : textures et modèles

```
art/                                  # SOURCES de travail (non packagées)
  models/*.bbmodel                    #   modèles Blockbench (MCP blockbench)
  textures/                           #   brouillons, palettes, preview.png
tools/textures/gen_textures.py        # générateur Pillow des textures finales
src/main/resources/assets/<modid>/
  textures/block/*.png, textures/item/*.png
  lang/en_us.json, lang/fr_fr.json
  icon.png                            # écrit par gen_textures.py
src/main/generated/                   # SORTIE de runDatagen : ne jamais éditer à la main
```

**Toutes les textures du mod sont des créations originales**, produites par code dans `tools/textures/gen_textures.py` (Pillow, déterministe) :

- Ajouter une texture : écrire une fonction qui retourne une `Image` 16×16, puis l'enregistrer dans `BLOCKS`, `ANIMATED` (frames + `frametime`, génère le `.png.mcmeta`) ou `ITEMS`.
- Réutiliser la palette `C` et les helpers (`casing`, `inset`, `grain`) pour garder une identité visuelle commune. Définir tôt dans la SPEC un code couleur par rôle.
- Après toute modification, régénérer puis **ouvrir `art/textures/preview.png`** (x8) pour contrôler visuellement.
- Une texture retouchée à la main (Blockbench, Aseprite…) doit être retirée du générateur, sinon elle sera écrasée.

Conventions :
- Textures **16×16 PNG RGBA**, style vanilla : palette limitée, pas de dégradés lisses, ombrage venant du haut-gauche. Animations : bande verticale de frames 16×16 + fichier `.png.mcmeta`.
- Noms en `snake_case`, égaux à l'id du bloc ou de l'objet (suffixes `_front`, `_side`, `_top`, `_on`).
- Blockstates, modèles et `assets/<modid>/items/*.json` sont **générés** par le datagen.
- Référence de style : textures vanilla dans `~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar` (`assets/minecraft/textures/block/`). Lecture locale uniquement : **ne jamais copier de texture Mojang dans le mod**.
- Vérifier visuellement chaque modèle et chaque texture (rendu `blockbench` ou `runClient`) avant de le considérer fini.

## Façon de travailler

- Fonctionnalité non triviale : spec dans `docs/superpowers/specs/`, plan dans `docs/superpowers/plans/`, puis exécution tâche par tâche en TDD (test rouge, puis vert, puis build vert).
- Répondre à l'utilisateur en français. Code, commentaires et messages de commit en anglais.
- Tenir `docs/SPEC.md` à jour quand une étape est terminée.

## Git

### Règles absolues

- **Toujours demander avant de pousser.** Aucun `git push` (branche, tag, force-push) sans l'accord explicite de l'utilisateur pour ce push-là. Committer en local est permis ; pousser, non. Même règle pour ouvrir, fusionner ou fermer une pull request, et pour créer une release.
- **Toute fonctionnalité passe par une branche et une pull request.** On ne committe jamais une fonctionnalité directement sur `main`.

### Branches

- Partir d'un `main` à jour : `git switch main && git pull`, puis `git switch -c <type>/<sujet>`.
- Nom : `<type>/<sujet-en-kebab-case>`, en anglais, court. Types :

  | Type | Pour |
  |---|---|
  | `feat/` | une fonctionnalité ou un contenu de jeu (`feat/portable-speaker`) |
  | `fix/` | une correction de bug (`fix/speaker-volume-reset`) |
  | `docs/` | documentation, spec, plan (`docs/audio-core-spec`) |
  | `ci/` | workflows GitHub, build Gradle (`ci/modrinth-publish`) |
  | `refactor/` | restructuration sans changement de comportement |
  | `chore/` | outillage, dépendances, versions |

- Une branche = un sujet. Un sous-projet de la SPEC peut être découpé en plusieurs branches successives.
- Seuls les petits changements de documentation ou de config peuvent aller directement sur `main`, et toujours après accord de l'utilisateur.

### Pull requests

- Titre : en anglais, à l'impératif, comme un message de commit (`Add portable speaker`).
- Description : en français, suivant le modèle [.github/pull_request_template.md](.github/pull_request_template.md) : résumé, changements, tests, vérification en jeu, liens vers la spec et le plan, checklist.
- Avant de demander la fusion : `./gradlew build` vert en local, CI verte, `runDatagen` relancé si du contenu a changé, `docs/SPEC.md` à jour.
- Fusion : **squash merge** (un commit par PR sur `main`, titre de la PR comme message), puis suppression de la branche. La fusion est faite par l'utilisateur, ou par Claude seulement sur sa demande explicite.

### Commits et dépôt

- Messages de commit en anglais, à l'impératif.
- **Aucune attribution à Claude** : pas de ligne `Co-Authored-By: Claude …` dans les commits, pas de « Generated with Claude Code » dans les pull requests ni les releases.
- Ignorés : `build/`, `.gradle/`, `.idea/`, `run/`, `logs/`, `node_modules/`. Ne pas committer `src/main/generated` modifié à la main ; le régénérer.
- Dépôt **public** `justinmiteran/RaveCraft`. Code sous LGPL-3.0-only (`LICENSE`, `COPYING`), assets sous CC BY-NC-SA 4.0 (`LICENSE-ASSETS.md`).
- `main` est protégée sur GitHub : CI `build` verte obligatoire, pull request requise (l'admin peut pousser directement les petits changements), force-push et suppression interdits, conversations résolues avant fusion. Seul le squash merge est autorisé ; les branches fusionnées sont supprimées automatiquement.
- Le workflow `code-quality` (`.github/workflows/quality.yml`) lance `./gradlew codeQuality` dans un job séparé, sur chaque pull request et chaque push sur `main` ou `develop`.
- La CI GitHub (`.github/workflows/build.yml`) lance `./gradlew build` sur chaque pull request et chaque push sur `main` (pas sur les push des autres branches, déjà construites par leur PR) et publie le rapport des tests JUnit et des GameTests (check « Test results »). Les GameTests écrivent leur XML dans `build/test-results/gameTest/` (propriété `fabric-api.gametest.report-file` dans `build.gradle`).
- Versions : `mod_version` dans `gradle.properties`, publiées en poussant un tag `vX.Y.Z` (workflow `release.yml`). Procédure dans [docs/RELEASING.md](docs/RELEASING.md), vue d'ensemble de la CI/CD dans [README.md](README.md).
