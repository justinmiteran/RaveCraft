# RaveCraft

Mod Fabric pour **Minecraft 26.3** : du matériel de sonorisation, de DJ et d'éclairage **fonctionnel et réaliste** (enceintes, platines, tables de mixage, projecteurs, consoles lumière…). La progression va de l'enceinte portable au soundsystem de festival.

> 🚧 En développement : la vision est écrite, le premier contenu jouable n'existe pas encore.

| Document | Contenu |
|---|---|
| [docs/SPEC.md](docs/SPEC.md) | Cahier des charges : vision, éléments, progression, idées, sous-projets, avancement |
| [docs/RELEASING.md](docs/RELEASING.md) | Numérotation des versions et procédure de publication |
| [CLAUDE.md](CLAUDE.md) | Environnement de dev, conventions, règles de travail |

---

## Développer

### Prérequis

- **JDK 25** (Temurin). Le `java` du PATH de cette machine est Java 8, donc en ligne de commande :
  ```bash
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"
  ```
- IntelliJ IDEA + plugin *Minecraft Development* (run configs *Minecraft Client*, *Minecraft Server*, *Data Generation*).
- Python 3 + Pillow (textures), Node 24 (MCP blockbench : `npm ci` dans `tools/blockbench-mcp`).

### Versions

| Minecraft | Fabric Loader | Fabric API | Loom | Gradle | Java |
|---|---|---|---|---|---|
| 26.3 | 0.20.1 | 0.166.2+26.3 | 1.19.x | 9.8.2 | 25 |

### Commandes

| Commande | Rôle |
|---|---|
| `./gradlew build` | compile, tests JUnit, GameTests, jar dans `build/libs/` |
| `./gradlew test` | tests JUnit seuls |
| `./gradlew runGameTest` | GameTests seuls (serveur headless) |
| `./gradlew runDatagen` | régénère blockstates, modèles, loot, tags, recettes (`src/main/generated`) |
| `./gradlew runClient` | lance le jeu |
| `python tools/textures/gen_textures.py` | régénère les textures et `art/textures/preview.png` |

### Organisation du code

| Dossier | Contenu |
|---|---|
| `src/main/java` | code commun et serveur (`core` = logique pure testable, `registry`, blocs, réseau…) |
| `src/client/java` | rendu, écrans, HUD, datagen |
| `src/test/java` | tests JUnit 5 |
| `src/gametest/java` | GameTests (mod séparé, jamais dans le jar livré) |
| `art/` | sources de travail : modèles Blockbench, brouillons de textures |
| `tools/` | générateur de textures, serveur MCP blockbench |

---

## CI/CD

Deux workflows GitHub Actions, visibles dans l'onglet **Actions** du dépôt.

| Workflow | Déclencheur | Rôle |
|---|---|---|
| **build** (`.github/workflows/build.yml`) | chaque pull request et chaque `push` sur `main` | compiler, tester, produire le jar |
| **release** (`.github/workflows/release.yml`) | push d'un tag `vX.Y.Z` | publier une version sur GitHub |

### CI : `build`

Sur Ubuntu 24.04 avec le JDK 25 Temurin :

1. récupère le code et vérifie que le wrapper Gradle est authentique ;
2. installe Java 25 et met Gradle en cache (les runs suivants sont plus rapides) ;
3. lance `./gradlew build` : compilation, **tests JUnit**, **GameTests**, jar ;
4. publie le check **« Test results »** et un résumé sur la page du run, même si le build échoue ;
5. en cas d'échec, joint le rapport HTML des tests (artefact `test-reports`) ;
6. joint le jar du mod (artefact `Artifacts`).

Les deux familles de tests passent par `check`. Un seul test rouge rend la CI rouge.

| Tests | Où | Rapport XML |
|---|---|---|
| JUnit 5 (logique pure, package `core`) | `src/test/java` | `build/test-results/test/` |
| GameTests (vrai serveur Minecraft headless) | `src/gametest/java` | `build/test-results/gameTest/` |

Les résultats se lisent sur la page du run (**Summary**), dans le check « Test results » d'un commit ou d'une pull request, ou en ligne de commande :

```bash
gh run list --limit 5
```

```bash
gh run view --log-failed
```

### CD : `release`

Pousser un tag `v` + `mod_version` (par exemple `v0.2.0`) :

1. le workflow refuse le tag s'il ne correspond pas à `mod_version` dans `gradle.properties` ;
2. il relance le build complet. Si un test échoue, aucune release n'est créée ;
3. il crée la **release GitHub** `RaveCraft X.Y.Z (Minecraft 26.3)` avec le jar et des notes générées à partir des commits. Les versions `0.x` et celles à suffixe (`-beta.1`) sont publiées en pré-release.

Procédure complète : [docs/RELEASING.md](docs/RELEASING.md).

### Pas encore en place

- publication sur Modrinth et CurseForge (il faudra un jeton API dans les secrets du dépôt) ;
- GameTests côté client (désactivés).

### Protection de `main`

CI `build` verte obligatoire, pull request requise, force-push et suppression interdits, squash merge uniquement, branches supprimées après fusion.

---

## Contribuer

- Toute fonctionnalité passe par une branche `<type>/<sujet>` (`feat/`, `fix/`, `docs/`, `ci/`, `refactor/`, `chore/`) et une pull request.
- Les PR suivent le modèle [.github/pull_request_template.md](.github/pull_request_template.md) et sont fusionnées en **squash merge** une fois la CI verte.
- Règles complètes : section Git de [CLAUDE.md](CLAUDE.md).

---

## Licence

| Partie | Licence | Fichier |
|---|---|---|
| Code (Java, build, outils, données générées) | **LGPL-3.0-only** | [LICENSE](LICENSE), avec le texte de la GPL-3.0 dans [COPYING](COPYING) |
| Assets (textures, icône, sons, modèles faits main, `art/`) | **CC BY-NC-SA 4.0** | [LICENSE-ASSETS.md](LICENSE-ASSETS.md) |
| Bibliothèques embarquées (décodeurs MP3 et FLAC) | **LGPL** (leurs propres licences) | [LICENSE-THIRD-PARTY.md](LICENSE-THIRD-PARTY.md) |

En bref : on peut utiliser le code et faire des addons, mais une version modifiée de RaveCraft doit rester open source sous la même licence. Les assets peuvent être réutilisés en citant RaveCraft, sans usage commercial et sous la même licence.
