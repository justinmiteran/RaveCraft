# Matériaux et composants (paliers 1 et 2)

Deuxième sous-projet ([SPEC](../../SPEC.md), sections 3.7, 4 et 10), après le socle audio livré en `v0.1.0`. Il remplace les recettes provisoires des enceintes ([2026-11-21-party-speaker-design.md](2026-11-21-party-speaker-design.md)) par une vraie chaîne de fabrication.

## 1. Objectif

Fabriquer son matériel comme un vrai équipement : extraire des matières, les transformer dans un petit atelier de machines spécialisées, puis assembler les composants en enceintes. La chaîne donne envie d'explorer (trois minerais) et de construire (quatre machines), et sert de base aux paliers suivants, qui ajouteront leurs propres composants.

### Périmètre

- ✅ Le système de fabrication et les matériaux des paliers 1 (*Nomade*) et 2 (*Maison / soirée*) seulement.
- ✅ Surtout de la transformation de matériaux vanilla, plus des minerais propres au mod ; le néodyme et le silicium sont des objectifs d'exploration du palier 2.
- ✅ Des machines spécialisées qui fonctionnent comme un four, plutôt qu'un établi en grille.
- ✅ Du pétrole, traité comme un minerai solide (pas de fluides).
- ✅ Les moules de l'extrudeuse sont des objets à fabriquer et à placer dans la machine.
- ✅ Les recettes des machines apparaissent dans le livre de recettes vanilla, comme celles du four.
- 💡 Noms, répartition des recettes entre machines, énergie en redstone, bobine mobile, emplacement des minerais, valeurs chiffrées.

Hors périmètre : les composants des paliers 3 à 5, les fluides (seaux, tuyaux, réservoirs), l'électricité au sens large (question ouverte « Énergie » de la SPEC, qui reste ouverte : la redstone ne sert ici que de carburant aux machines), JEI/EMI.

### Critères de réussite

- Dans un monde survie neuf, je fabrique l'enceinte portable puis l'enceinte de soirée à partir de zéro, en ne m'aidant que du livre de recettes.
- Chaque recette (table de craft, four, machines) apparaît dans le livre dès que je ramasse **l'un** de ses ingrédients.
- Je trouve le schiste bitumineux en surface sans guide ; le néodyme et le silicium demandent de descendre.
- Je branche des entonnoirs sur une machine : elle tourne seule tant qu'elle a des entrées et de la redstone.

### Découpage

Une spec, quatre PR successives, chacune avec un build vert et testable seule :

| PR | Branche | Contenu |
|---|---|---|
| 7a | `feat/materials-ores` | 3 minerais (4 blocs), génération, matières premières, recettes de fonte |
| 7b | `feat/machine-core` | Base commune des machines (logique `core`, bloc, block entity, inventaire latéral, menu, écran, livre de recettes, type de recette), livrée avec la **bobineuse** |
| 7c | `feat/machines` | Magnétiseur, graveur, extrudeuse, moules |
| 7d | `feat/components-recipes` | Composants des paliers 1 et 2, assemblages à la table de craft, nouvelles recettes des enceintes, critères de déblocage, infobulles « Fabriqué dans », onglet créatif, mise à jour de `docs/SPEC.md` |

Chaque PR commence par ses propositions visuelles (3 à 4 rendus par modèle ou texture), que l'utilisateur choisit avant l'implémentation.

## 2. Décisions de cadrage

| # | Décision | Écarté |
|---|---|---|
| D1 | Paliers 1 et 2 seulement | Tous les paliers d'un coup |
| D2 | Transformation de vanilla + 3 minerais du mod | Vanilla seul ; minerais seuls |
| D3 | Quatre machines spécialisées style four : bobineuse, magnétiseur, graveur, extrudeuse | Établi en grille 3×3 ; liste façon tailleur de pierre ; établi à gabarit ; un seul poste de soudure |
| D4 | Chaque machine fait un seul geste (enrouler, aimanter, graver, mouler) ; l'assemblage se fait à la table de craft | Machines qui assemblent les haut-parleurs |
| D5 | Énergie : poudre de redstone (8 opérations) ou bloc de redstone (72) dans un emplacement dédié | Charbon ; électricité |
| D6 | Pétrole = schiste bitumineux, minerai solide qui donne du bitume | Fluides ; bioplastique d'algues |
| D7 | Moules en objets, non consommés, sans usure, dans un emplacement inaccessible aux entonnoirs | Boutons de sélection dans l'écran |
| D8 | Recettes des machines dans le livre vanilla, onglet par machine, état ouvert/filtré partagé avec le four (`RecipeBookType.FURNACE`) | Panneau de recettes maison ; mixin pour un nouveau `RecipeBookType` |
| D9 | Silicium en minerai classique (pierre et ardoise) | Cristal posé sur les parois des grottes (gardé pour plus tard) |
| D10 | Déblocage d'une recette par n'importe lequel de ses ingrédients | Déblocage par l'ingrédient le plus rare |

## 3. Contenu

### 3.1 Blocs

| Id | Nom (fr / en) | Rôle |
|---|---|---|
| `oil_shale` | Schiste bitumineux / Oil Shale | minerai de bitume |
| `deepslate_neodymium_ore` | Minerai de néodyme des abîmes / Deepslate Neodymium Ore | minerai de néodyme |
| `silicon_ore` | Minerai de silicium / Silicon Ore | minerai de silicium |
| `deepslate_silicon_ore` | Minerai de silicium des abîmes / Deepslate Silicon Ore | minerai de silicium |
| `coil_winder` | Bobineuse / Coil Winder | machine |
| `magnetizer` | Magnétiseur / Magnetizer | machine |
| `etcher` | Graveur / Etcher | machine |
| `extruder` | Extrudeuse / Extruder | machine |

### 3.2 Objets

| Groupe | Id | Nom (fr / en) |
|---|---|---|
| Matières | `bitumen` | Bitume / Bitumen |
| | `raw_neodymium` | Néodyme brut / Raw Neodymium |
| | `neodymium_ingot` | Lingot de néodyme / Neodymium Ingot |
| | `silicon_crystal` | Cristal de silicium / Silicon Crystal |
| | `silicon_wafer` | Plaquette de silicium / Silicon Wafer |
| Palier 1 | `copper_wire` | Fil de cuivre / Copper Wire |
| | `voice_coil` | Bobine mobile / Voice Coil |
| | `iron_magnet` | Aimant en fer / Iron Magnet |
| | `membrane` | Membrane / Membrane |
| | `plastic_sheet` | Plaque de plastique / Plastic Sheet |
| | `plastic_casing` | Boîtier en plastique / Plastic Casing |
| | `circuit_board` | Support de circuit / Circuit Board |
| | `simple_circuit` | Circuit simple / Simple Circuit |
| | `speaker_driver` | Haut-parleur simple / Speaker Driver |
| Palier 2 | `neodymium_magnet` | Aimant en néodyme / Neodymium Magnet |
| | `reinforced_membrane` | Membrane renforcée / Reinforced Membrane |
| | `chip` | Puce / Chip |
| | `amplifier_board` | Carte d'amplification / Amplifier Board |
| | `woofer` | Haut-parleur de grave / Woofer |
| Moules | `sheet_mold` | Moule à plaque / Sheet Mold |
| | `casing_mold` | Moule à boîtier / Casing Mold |
| | `membrane_mold` | Moule à membrane / Membrane Mold |
| | `board_mold` | Moule à support / Board Mold |

La plaque de plastique sert aux membranes (section 6.1) et sera la matière de base des façades et boîtiers des paliers suivants.

## 4. Minerais et génération

| Bloc | Hauteur | Biomes | Filon × par chunk | Outil minimal | Butin (sans toucher de soie) | XP au minage |
|---|---|---|---|---|---|---|
| `oil_shale` | Y 20 à 80 (uniforme) | tous les biomes de l'Overworld ; un second placement dans `#c:is_desert` et `#minecraft:is_badlands` | 17 blocs × 12, + 17 × 8 | pioche en pierre | 1 à 3 `bitumen`, Fortune comme le charbon | 0 à 2 |
| `deepslate_neodymium_ore` | Y −64 à −16 (triangle centré sur −40) | tous les biomes de l'Overworld | 6 blocs × 3, 50 % exposé à l'air retiré | pioche en fer | 1 `raw_neodymium`, Fortune comme le fer | 0 |
| `silicon_ore` / `deepslate_silicon_ore` | Y −32 à 48 (triangle centré sur 8) | tous les biomes de l'Overworld | 8 blocs × 5 | pioche en fer | 1 à 2 `silicon_crystal`, Fortune comme le lapis | 1 à 3 |

- Le filon de silicium remplace la pierre par `silicon_ore` et l'ardoise des abîmes par `deepslate_silicon_ore` (règles `stone_ore_replaceables` / `deepslate_ore_replaceables`), comme les minerais vanilla.
- Le toucher de soie rend le bloc de minerai ; chaque bloc de minerai se fond (four et haut fourneau) en sa matière.
- Les minerais n'apparaissent que dans les chunks générés après l'installation ; les notes de version le diront.
- Les features (`configured_feature`, `placed_feature`) sont écrites par le datagen ; elles sont ajoutées aux biomes par `BiomeModifications.addFeature` (étape `UNDERGROUND_ORES`).
- Tags : `c:ores`, `c:ores/neodymium`, `c:ores/silicon`, `c:ores/oil_shale`, `c:ores_in_ground/stone`, `c:ores_in_ground/deepslate`, `mineable/pickaxe`, `needs_stone_tool` (schiste), `needs_iron_tool` (néodyme, silicium) ; `c:raw_materials/neodymium`, `c:ingots/neodymium`, `c:gems/silicon`.

### Fonte

| Entrée | Sortie | Four | Haut fourneau | XP |
|---|---|---|---|---|
| `raw_neodymium`, `deepslate_neodymium_ore` | `neodymium_ingot` | 200 ticks | 100 ticks | 1,0 |
| `silicon_crystal` | `silicon_wafer` | 200 ticks | — | 0,5 |
| `silicon_ore`, `deepslate_silicon_ore` | `silicon_crystal` | 200 ticks | 100 ticks | 0,7 |
| `oil_shale` | `bitumen` | 200 ticks | 100 ticks | 0,1 |

## 5. Machines

### 5.1 Comportement commun

- **Emplacements** : 3 entrées, 1 énergie, 1 sortie ; l'extrudeuse a en plus 1 emplacement de moule.
- **Recette** : sans forme. Chaque ingrédient de la recette (au plus 3, chacun avec une quantité) doit occuper un emplacement d'entrée distinct, en quantité suffisante ; les entrées non utilisées doivent être vides. C'est l'entrée qui décide de la recette (aucune sélection), sauf le moule pour l'extrudeuse.
- **Énergie** : une opération coûte 1 unité. Quand une opération doit démarrer et que la réserve est vide, la machine consomme un objet d'énergie : poudre de redstone = 8 unités, bloc de redstone = 72 unités. La réserve est sauvegardée avec la machine.
- **Durée** : 100 ticks par opération (5 s, deux fois plus vite que le four), pour toutes les recettes de ce sous-projet. La durée est un champ de la recette.
- **Blocages** : si la sortie ne peut pas recevoir le résultat (pleine ou objet différent), la progression s'arrête sans se perdre. Si les entrées changent et ne correspondent plus à la recette en cours, la progression revient à zéro. Sans énergie, la machine attend, progression conservée.
- **Entonnoirs** : par le dessus → entrées (chaque objet va dans l'emplacement qui le contient déjà, sinon dans le premier vide) ; par les côtés → énergie (redstone seulement) ; par le dessous → extraction de la sortie. Le moule n'est jamais accessible aux entonnoirs.
- **État** : propriété de bloc `lit` vraie pendant une opération ; face avant `_on` animée, petit son en boucle et particules propres à chaque machine. Propriété `facing` (orientée vers le joueur à la pose).
- **Casse** : la machine lâche son contenu, sauf la réserve d'énergie déjà consommée. Elle se mine à la pioche (outil minimal : pierre).
- **Écran** : emplacements, barre de progression, jauge d'énergie (réserve sur 72), bouton du livre de recettes. Fond d'interface propre à chaque machine.
- **Performance** : une machine sans opération possible ne fait rien par tick ; la recherche de recette n'a lieu que quand son inventaire change.

### 5.2 Recettes des machines

| Machine | Entrées | Moule | Sortie |
|---|---|---|---|
| Bobineuse | 1 lingot de cuivre | — | 4 `copper_wire` |
| | 4 `copper_wire` | — | 1 `voice_coil` |
| Magnétiseur | 1 lingot de fer + 1 poudre de redstone | — | 1 `iron_magnet` |
| | 1 `neodymium_ingot` + 1 lingot de fer | — | 2 `neodymium_magnet` |
| Graveur | 1 `circuit_board` + 2 `copper_wire` + 1 poudre de redstone | — | 1 `simple_circuit` |
| | 1 `silicon_wafer` + 2 pépites d'or + 1 poudre de redstone | — | 1 `chip` |
| Extrudeuse | 1 `bitumen` | `sheet_mold` | 2 `plastic_sheet` |
| | 3 `bitumen` | `casing_mold` | 1 `plastic_casing` |
| | 2 `bitumen` | `membrane_mold` | 1 `reinforced_membrane` |
| | 1 `bitumen` | `board_mold` | 2 `circuit_board` |

La poudre de redstone d'une recette est un ingrédient, pas de l'énergie : elle va dans une entrée.

### 5.3 Type de recette

- Un type et un sérialiseur par machine : `ravecraft:winding`, `ravecraft:magnetizing`, `ravecraft:etching`, `ravecraft:extruding`, partageant un même format :

  ```json
  {
    "type": "ravecraft:etching",
    "ingredients": [
      { "ingredient": "ravecraft:circuit_board", "count": 1 },
      { "ingredient": "ravecraft:copper_wire", "count": 2 },
      { "ingredient": "minecraft:redstone", "count": 1 }
    ],
    "mold": "ravecraft:board_mold",
    "result": { "id": "ravecraft:simple_circuit", "count": 1 },
    "processing_time": 100
  }
  ```

  `mold` n'existe que pour `extruding` (obligatoire) ; `processing_time` vaut 100 par défaut.
- Les recettes sont écrites par le datagen et modifiables par datapack.

### 5.4 Livre de recettes

- Chaque machine a un onglet de livre de recettes (une `RecipeBookCategory` enregistrée par machine) et affiche ses recettes avec un `RecipeDisplay` propre au mod : ingrédients avec quantités, moule, résultat, et l'énergie en emplacement fantôme comme le carburant du four.
- Le menu des machines étend `RecipeBookMenu` et renvoie `RecipeBookType.FURNACE` : ouvrir ou filtrer le livre dans une machine le fait aussi dans le four (D8).
- Clic sur une recette : le serveur place les ingrédients depuis l'inventaire du joueur (Maj : le plus possible) ; ce qui manque apparaît en fantôme, moule compris pour l'extrudeuse.
- Les recettes se débloquent comme celles de la table de craft (section 6.2).

## 6. Table de craft et déblocages

### 6.1 Recettes

Légende : `I` lingot de fer, `C` lingot de cuivre, `R` poudre de redstone.

| Résultat | Recette |
|---|---|
| `coil_winder` | `III / CRC / III` |
| `magnetizer` | `IRI / RIR / IRI` |
| `etcher` | `IGI / RAR / III` (`G` verre, `A` éclat d'améthyste) |
| `extruder` | `IPI / IFI / III` (`P` piston, `F` four) |
| `sheet_mold` | `III / IXI`, `X` plaque de pression lourde |
| `casing_mold` | `III / IXI`, `X` seau |
| `membrane_mold` | `III / IXI`, `X` bol |
| `board_mold` | `III / IXI`, `X` poudre de redstone |
| `membrane` ×2 | sans forme : 2 papiers + 1 plaque de plastique |
| `speaker_driver` | ` M  / IVI /  G ` (`M` membrane, `V` bobine mobile, `G` aimant en fer) |
| `woofer` | ` M  / IVI /  G ` (`M` membrane renforcée, `V` bobine mobile, `G` aimant en néodyme) |
| `amplifier_board` | sans forme : 1 puce + 1 circuit simple + 2 fils de cuivre |
| `portable_speaker` | ` D  /  B  /  S ` (`D` haut-parleur simple, `B` boîtier en plastique, `S` circuit simple) |
| `party_speaker` | `IWI / CPC / IAI` (`W` haut-parleur de grave, `P` enceinte portable, `A` carte d'amplification) |

- Les recettes provisoires des deux enceintes (`FFF / NRN / FFF` et `INI / CPC / IRI`) sont supprimées. Les recettes de teinture (`*_dyed`) restent inchangées.
- L'enceinte de soirée sort neuve (recette à forme) : la couleur, le nom et la file de l'enceinte portable utilisée ne sont pas repris, comme aujourd'hui.

### 6.2 Déblocage

- Chaque recette de table de craft, de fonte et de machine a un critère `has_<ingrédient>` **par ingrédient distinct**, combinés en « ou » : elle se débloque dès que le joueur possède l'un d'eux.
- Les machines et les moules se débloquent donc avec le fer ou le cuivre, très tôt.

### 6.3 Infobulles

- Chaque objet produit par une machine affiche une ligne grisée « Fabriqué dans : *machine* » (`Made in: …`), sous les autres lignes, sans Maj.
- Chaque moule affiche « Pour l'extrudeuse : *pièce* ».

### 6.4 Onglet créatif et traductions

- Onglet RaveCraft, dans l'ordre de la chaîne : minerais, matières, composants du palier 1, du palier 2, moules, machines, puis les enceintes (déjà présentes).
- Tous les noms et lignes en `en_us.json` et `fr_fr.json`.

## 7. Architecture du code

| Couche | Contenu |
|---|---|
| `core.machine` (Java pur) | `MachineProcess` : réserve d'énergie, progression, démarrage, fin, blocage sortie pleine, remise à zéro sur changement de recette ; `ShapelessMatcher` : affectation des ingrédients (prédicat + quantité) aux emplacements d'entrée ; constantes (`OPERATIONS_PER_DUST = 8`, `OPERATIONS_PER_BLOCK = 72`, `DEFAULT_PROCESSING_TICKS = 100`) |
| `registry` | `ModBlocks`, `ModItems` (nouveaux), `ModBlockEntities`, `ModMenus`, `ModRecipes` (types, sérialiseurs, catégories du livre, display), `ModWorldgen` (clés et `BiomeModifications`) |
| `block` | `MachineBlock` (orientation, `lit`, ouverture du menu, casse) et une sous-classe par machine |
| `blockentity` | `MachineBlockEntity` (inventaire `WorldlyContainer`, sauvegarde, tick, synchronisation), la variante extrudeuse avec l'emplacement de moule |
| `recipe` | `MachineRecipe`, `MachineRecipeInput`, `MachineRecipeDisplay`, sérialiseurs |
| `menu` | `MachineMenu extends RecipeBookMenu` (slots, `ContainerData` progression/énergie, placement des recettes) |
| client | `MachineScreen`, `MachineRecipeBookComponent`, sons et particules |
| datagen | modèles et blockstates, loot des minerais et des machines, tags, recettes (fonte, craft, machines), features |

## 8. Tests

**JUnit (`core.machine`)**
- Une poudre fournit exactement 8 opérations, un bloc 72.
- L'opération dure 100 ticks et ne produit qu'une fois.
- Sortie pleine : la progression s'arrête et reprend quand la sortie se vide.
- Sans énergie : la progression attend et reprend à l'arrivée de redstone.
- Changement d'entrée : la progression revient à zéro.
- `ShapelessMatcher` : correspondance dans n'importe quel ordre d'emplacements, quantité insuffisante refusée, emplacement en trop refusé, deux ingrédients dans le même emplacement refusés.

**GameTests**
- Chaque recette de machine du tableau 5.2 produit son résultat et consomme ses entrées et une unité d'énergie.
- Entonnoirs : dessus → entrées, côté → énergie (refuse autre chose que la redstone), dessous → sortie ; le moule n'est ni inséré ni extrait.
- Extrudeuse sans moule : aucune opération.
- Casser une machine lâche entrées, énergie, sortie et moule.
- Chaque minerai lâche son butin à la pioche prévue, rien avec une pioche trop faible, et lui-même au toucher de soie.
- Chaque feature de minerai est ajoutée aux biomes prévus (vérification du registre des biomes).
- Les nouvelles recettes des deux enceintes existent et les anciennes non.

**En jeu** : monde survie neuf, du minage du schiste à l'enceinte de soirée en suivant le livre ; entonnoirs ; écran et livre de chaque machine ; textures, modèles et animations.

## 9. Visuels

- Propositions avant implémentation (3 à 4 rendus chacune) : 4 minerais, 4 machines (modèle Blockbench, face `_on` animée, fond d'écran), 23 objets.
- Textures produites par `tools/textures/gen_textures.py`, palette commune `C` ; minerais dans le style des minerais vanilla (pierre ou ardoise + taches de la matière).
- Modèles des machines dans `art/models`, exportés en JSON de bloc.

## 10. Mise à jour de la SPEC

À la fin de 7d :
- section 3.7 : contenu livré ;
- section 9 : « Obtention des matériaux » tranchée (les deux), « Énergie » toujours ouverte (la redstone n'est qu'un carburant de machine) ;
- section 11 : étape « Matériaux et composants » cochée avec les liens vers cette spec et les plans.
