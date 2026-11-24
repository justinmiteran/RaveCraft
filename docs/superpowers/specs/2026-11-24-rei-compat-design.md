# Intégration REI des recettes de machines

Suite de l'intégration JEI (branche `feat/jei-compat`). Les machines de la spec des matériaux ([2026-11-22-materials-design.md](2026-11-22-materials-design.md)) ont leurs pages dans JEI. Cette spec ajoute les mêmes pages dans **Roughly Enough Items (REI)**, sans dupliquer la logique d'affichage, pour qu'EMI puisse s'y brancher plus tard.

## 1. Objectif

Un joueur qui utilise REI au lieu de JEI voit les recettes de la bobineuse, du magnétiseur, du graveur et de l'extrudeuse comme dans JEI, avec la même disposition que l'écran de la machine.

### Critères de réussite

- Avec REI seul (sans JEI), un clic sur le fil de cuivre ouvre sa recette de la bobineuse : lingot de cuivre (tout lingot du tag `c:ingots/copper`), case d'énergie qui fait défiler la redstone et le bloc de redstone avec leur nombre d'opérations, flèche animée, résultat ×4, temps en secondes.
- Une recette d'extrudeuse montre son moule à la même place que dans la machine.
- L'objet machine est listé comme poste de fabrication ; un clic sur la flèche de l'écran de la machine ouvre ses recettes.
- Le bouton `+` de REI remplit la machine avec les ingrédients de l'inventaire, s'ils y sont.
- Sans REI, le mod se charge normalement et aucune classe REI n'est chargée. Sur un serveur sans REI, un client avec REI voit quand même les recettes vanilla, sans erreur.
- Les pages JEI ne changent pas.

### Hors périmètre

- **EMI** : pas de version pour Minecraft 26.x à ce jour (dernière version : 1.1.24, Minecraft 1.21.1). Il viendra comme un adaptateur de plus sur le modèle commun du §3.1.
- Just Enough Resources n'a pas d'équivalent REI : les filons restent dans JER seulement.

## 2. Ce que REI impose

Lu dans le jar `RoughlyEnoughItems-26.3.824.jar` :

- REI a **deux plugins** : un plugin commun (`REICommonPlugin`, entrypoint `rei_common`) qui tourne sur le serveur, et un plugin client (`REIClientPlugin`, entrypoint `rei_client`).
- Le serveur construit les **pages** (`Display`) à partir des recettes, dans `registerDisplays(ServerDisplayRegistry)`, puis REI les **synchronise** vers le client. Chaque type de page déclare son format (`DisplaySerializer` : un `MapCodec` et un `StreamCodec`) dans `registerDisplaySerializer`.
- Le client enregistre les catégories (`DisplayCategory`), les postes de fabrication, les zones cliquables (`ScreenRegistry`) et les transferts (`SimpleTransferHandler`).
- Dépendances de REI à l'exécution : Architectury (≥ 22.0.0) et Cloth Config.

Conséquence : contrairement à JEI, une partie de l'intégration vit dans `src/main` (code commun). REI y est en dépendance de compilation seule, comme Trinkets.

## 3. Conception

### 3.1 Modèle commun d'affichage (client)

`client/compat/MachineRecipeLayout` : ce que toutes les intégrations partagent, sans aucune classe de viewer.

- Pour un `MachineKind` : le décalage de la page, sa taille, la position de chaque case d'entrée, du moule, de la case d'énergie, du résultat, de l'éclair, de la flèche et du texte du temps. Toutes ces valeurs viennent de `MachineMenu`, décalées vers le coin haut-gauche (valeurs actuelles de `MachineRecipeCategory`).
- `seconds(int ticks)` : le temps en secondes sans zéro final (`200` → `"10"`, `30` → `"1.5"`).

La partie calcul (positions, secondes) est placée dans `core` si elle ne dépend que d'entiers, pour être testée en JUnit. Les fonctions qui lisent le monde client (liste des objets d'énergie synchronisés, opérations d'un objet) restent dans `client/compat/MachineEnergyView`.

`MachineRecipeCategory` (JEI) est réécrit sur ce modèle, sans changement visible.

### 3.2 REI, partie commune (`compat/rei`, `src/main`)

- `MachineDisplay` : une page de recette de machine. Elle porte le type de machine, les entrées comptées, le moule (optionnel), le résultat, le temps et l'id de la recette. Elle fournit à REI ses entrées (chaque entrée comptée devient un `EntryIngredient` de piles à la bonne quantité, plus le moule) et sa sortie.
- Son format de synchronisation est construit à partir de ceux de `MachineRecipe` (`CountedIngredient`, `Ingredient`, `ItemStackTemplate`), sans nouveau format maison.
- `RaveCraftReiCommonPlugin` (`rei_common`) déclare le format et crée une `MachineDisplay` par `MachineRecipe`, quel que soit son type.
- Une catégorie par type de machine : `ravecraft:winding`, `magnetizing`, `etching`, `extruding` (mêmes ids que les types de recette).

### 3.3 REI, partie client (`client/compat/rei`)

- `MachineDisplayCategory` : une catégorie par type de machine. Titre et icône = l'objet machine, taille et positions données par `MachineRecipeLayout`. Elle contient :
  - les cases d'entrée et du moule,
  - la case d'énergie, qui fait défiler les objets des recettes `MachineEnergy` avec l'info-bulle « N opérations » (`gui.ravecraft.jei.energy`, clé réutilisée),
  - l'éclair (sprite `container/machine/energy`), la flèche animée sur la durée de la recette, le résultat et le temps aligné à droite (`gui.ravecraft.jei.time`).
- `RaveCraftReiClientPlugin` (`rei_client`) : il enregistre :
  - les catégories, avec l'objet machine comme poste de fabrication ;
  - la zone cliquable sur la flèche de `MachineScreen` ;
  - un `SimpleTransferHandler` par machine (cases d'entrée et de moule de `MachineMenu`, inventaire du joueur).

  Si le transfert simple ne sait pas placer le moule ou les quantités, il est retiré et noté comme suite, sans bloquer le reste.

### 3.4 Build et manifeste

- `build.gradle` : REI 26.3.824 (`maven.modrinth:nfn13YXA:rKsAE6bX`) en `compileOnly` (commun) et `clientCompileOnly`. Ajouter Architectury en `compileOnly` seulement si la compilation le réclame. Rien n'est livré dans le jar.
- `fabric.mod.json` : entrypoints `rei_common` et `rei_client`, `"suggests"` complété par `"roughlyenoughitems": "*"`.
- Pour essayer : déposer REI, Architectury et Cloth Config (versions 26.3) dans `run/mods`.

## 4. Tests

- **JUnit** : positions de `MachineRecipeLayout` pour une machine à une entrée, à trois entrées et à moule. Elles doivent égaler les valeurs que la catégorie JEI utilise aujourd'hui. Tester aussi `seconds`.
- **GameTest** : pour chaque recette de machine chargée, une `MachineDisplay` encodée puis décodée (format réseau et format `MapCodec`) garde la même machine, les mêmes entrées et quantités, le même moule, le même résultat et le même temps. Le GameTest ne dépend pas de REI à l'exécution : il n'utilise que les codecs construits par RaveCraft.

  Si `MachineDisplay` implémente `Display`, le chargement de la classe sans REI échouerait. Le format est donc porté par une classe sans dépendance REI (`MachineDisplayData`), testée ici, et `MachineDisplay` l'enveloppe.
- **En jeu** (manuel) : avec REI seul, chaque critère du §1. Puis avec JEI seul : pages inchangées.

## 5. Risques

- **Architectury à la compilation** : si des signatures de l'API REI le demandent, ajouter Architectury en `compileOnly`.
- **Transfert** : le transfert simple de REI suppose des cases contiguës. Nos entrées et le moule ne le sont peut-être pas. Repli : pas de bouton `+`, suite notée dans la SPEC.
- **Rendu de l'énergie** : l'info-bulle dépend de la pile affichée. Si REI ne permet pas une info-bulle par pile, on montre une info-bulle par objet d'énergie via la liste d'entrées.
