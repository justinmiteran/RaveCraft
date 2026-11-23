# Matériaux et composants (paliers 1 et 2)

Deuxième sous-projet ([SPEC](../../SPEC.md), sections 3.7, 4 et 10), après le socle audio livré en `v0.1.0`. Il remplace les recettes provisoires des enceintes ([2026-11-21-party-speaker-design.md](2026-11-21-party-speaker-design.md)) par une vraie chaîne de fabrication.

## 1. Objectif

Fabriquer son matériel comme un vrai équipement : extraire des matières, les transformer dans un petit atelier de machines spécialisées, puis assembler les composants en enceintes. La chaîne donne envie d'explorer (deux minerais, une nouvelle roche et le quartz du Nether) et de construire (quatre machines), et sert de base aux paliers suivants, qui ajouteront leurs propres composants.

### Périmètre

- ✅ Le système de fabrication et les matériaux des paliers 1 (*Nomade*) et 2 (*Maison / soirée*) seulement.
- ✅ Surtout de la transformation de matériaux vanilla, plus des minerais propres au mod ; le néodyme (sous terre) et le silicium (tiré du quartz du Nether) sont des objectifs d'exploration du palier 2.
- ✅ Une nouvelle roche, le schiste, avec ses dérivés décoratifs comme le tuf ; le schiste bitumineux en est la variante riche en bitume.
- ✅ Des machines spécialisées qui fonctionnent comme un four, plutôt qu'un établi en grille.
- ✅ Du pétrole, traité comme un minerai solide (pas de fluides).
- ✅ Les moules de l'extrudeuse sont des objets à fabriquer et à placer dans la machine.
- ✅ Les recettes des machines apparaissent dans le livre de recettes vanilla, comme celles du four.
- 💡 Noms, répartition des recettes entre machines, énergie en redstone, bobine mobile, emplacement des minerais, valeurs chiffrées.

Hors périmètre : les composants des paliers 3 à 5, les fluides (seaux, tuyaux, réservoirs), l'électricité au sens large (question ouverte « Énergie » de la SPEC, qui reste ouverte : la redstone ne sert ici que de carburant aux machines), JEI/EMI.

### Critères de réussite

- Dans un monde survie neuf, je fabrique l'enceinte portable puis l'enceinte de soirée à partir de zéro, en ne m'aidant que du livre de recettes.
- Chaque recette (table de craft, four, machines) apparaît dans le livre dès que je ramasse **l'un** de ses ingrédients.
- Je trouve le schiste et le schiste bitumineux près de la surface sans guide ; le néodyme demande de descendre dans l'ardoise des abîmes, le silicium d'aller chercher du quartz dans le Nether.
- Je branche des entonnoirs sur une machine : elle tourne seule tant qu'elle a des entrées et de la redstone.

### Découpage

Une spec, quatre PR successives, chacune avec un build vert et testable seule :

| PR | Branche | Contenu |
|---|---|---|
| 7a | `feat/materials-ores` | 2 minerais, famille du schiste (13 blocs), 3 blocs de stockage, génération, matières premières, recettes de fonte et de taille |
| 7b | `feat/machine-core` | Base commune des machines (logique `core`, bloc, block entity, inventaire latéral, menu, écran, livre de recettes, type de recette), livrée avec la **bobineuse** |
| 7c | `feat/machines` | Magnétiseur, graveur, extrudeuse, moules |
| 7d | `feat/components-recipes` | Composants des paliers 1 et 2, assemblages à la table de craft, nouvelles recettes des enceintes, critères de déblocage, infobulles « Fabriqué dans », onglet créatif, mise à jour de `docs/SPEC.md` |

Chaque PR commence par ses propositions visuelles (3 à 4 rendus par modèle ou texture), que l'utilisateur choisit avant l'implémentation.

## 2. Décisions de cadrage

| # | Décision | Écarté |
|---|---|---|
| D1 | Paliers 1 et 2 seulement | Tous les paliers d'un coup |
| D2 | Transformation de vanilla (dont le quartz du Nether) + 2 minerais et le schiste du mod | Vanilla seul ; minerais seuls |
| D3 | Quatre machines spécialisées style four : bobineuse, magnétiseur, graveur, extrudeuse | Établi en grille 3×3 ; liste façon tailleur de pierre ; établi à gabarit ; un seul poste de soudure |
| D4 | Chaque machine fait un seul geste (enrouler, aimanter, graver, mouler) ; l'assemblage se fait à la table de craft | Machines qui assemblent les haut-parleurs |
| D5 | Énergie : poudre de redstone (8 opérations) ou bloc de redstone (72) dans un emplacement dédié | Charbon ; électricité |
| D6 | Pétrole = schiste bitumineux, minerai solide qui donne du bitume | Fluides ; bioplastique d'algues |
| D7 | Moules en objets, non consommés, sans usure, dans un emplacement inaccessible aux entonnoirs | Boutons de sélection dans l'écran |
| D8 | Recettes des machines dans le livre vanilla, onglet par machine, état ouvert/filtré partagé avec le four (`RecipeBookType.FURNACE`) | Panneau de recettes maison ; mixin pour un nouveau `RecipeBookType` |
| D9 | Silicium tiré du quartz du Nether au haut fourneau (réaliste : le silicium industriel vient du quartz) | Minerai de silicium dans la pierre et l'ardoise ; cristal sur les parois des grottes |
| D10 | Déblocage d'une recette par n'importe lequel de ses ingrédients | Déblocage par l'ingrédient le plus rare |
| D11 | Schiste en roche à part entière (poches comme l'andésite) avec sa famille décorative comme le tuf ; le schiste bitumineux reprend sa texture avec des veines de bitume | Schiste bitumineux seul |
| D12 | Textures du schiste, du schiste bitumineux et du minerai de néodyme en plusieurs variantes tirées au hasard par bloc (blockstate `variants`), pour un rendu organique sans motif répété | Une texture unique ; rotation aléatoire |
| D13 | Le fond d'ardoise du minerai de néodyme est la texture `deepslate.png` de Minecraft, copiée sans modification, comme les minerais vanilla ; le lingot de néodyme reprend la forme du lingot de fer vanilla, recolorée (choix de l'utilisateur, exceptions à la règle « aucune texture Mojang », signalées dans `LICENSE-ASSETS.md`) | Superposition en jeu sur le modèle vanilla ; fond et lingot redessinés |

## 3. Contenu

### 3.1 Blocs

| Id | Nom (fr / en) | Rôle |
|---|---|---|
| `oil_shale` | Schiste bitumineux / Oil Shale | minerai de bitume |
| `deepslate_neodymium_ore` | Minerai de néodyme des abîmes / Deepslate Neodymium Ore | minerai de néodyme |
| `shale` | Schiste / Shale | roche |
| `shale_stairs`, `shale_slab`, `shale_wall` | Escalier en schiste, Dalle de schiste, Muret de schiste / Shale Stairs, Shale Slab, Shale Wall | décoration |
| `polished_shale` | Schiste poli / Polished Shale | décoration |
| `polished_shale_stairs`, `polished_shale_slab`, `polished_shale_wall` | Escalier en schiste poli, Dalle de schiste poli, Muret de schiste poli / Polished Shale Stairs, Polished Shale Slab, Polished Shale Wall | décoration |
| `shale_bricks` | Briques de schiste / Shale Bricks | décoration |
| `shale_brick_stairs`, `shale_brick_slab`, `shale_brick_wall` | Escalier en briques de schiste, Dalle de briques de schiste, Muret de briques de schiste / Shale Brick Stairs, Shale Brick Slab, Shale Brick Wall | décoration |
| `chiseled_shale` | Schiste sculpté / Chiseled Shale | décoration (onde sonore gravée) |
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
| | `silicon` | Silicium / Silicon |
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
| Moules | `blank_mold` | Moule vierge / Blank Mold |
| | `sheet_mold` | Moule à plaque / Sheet Mold |
| | `casing_mold` | Moule à boîtier / Casing Mold |
| | `membrane_mold` | Moule à membrane / Membrane Mold |
| | `board_mold` | Moule à support / Board Mold |

La membrane renforcée est extrudée à partir de 2 plaques de plastique (décidé en 7d : l'extrudeuse garde une seule entrée). La plaque de plastique sert aux membranes (section 6.1) et sera la matière de base des façades et boîtiers des paliers suivants.

## 4. Minerais et génération

| Bloc | Hauteur | Biomes | Filon × par chunk | Outil minimal | Butin (sans toucher de soie) | XP au minage |
|---|---|---|---|---|---|---|
| `oil_shale` | Y 20 à 80 (uniforme) | tous les biomes de l'Overworld ; un second placement dans `#c:is_desert` et `#minecraft:is_badlands` | 17 blocs × 12, + 17 × 8 | pioche en pierre | 1 à 3 `bitumen`, Fortune comme le charbon | 0 à 2 |
| `deepslate_neodymium_ore` | Y −64 à −16 (triangle centré sur −40) | tous les biomes de l'Overworld | 6 blocs × 3, 50 % exposé à l'air retiré | pioche en fer | 1 `raw_neodymium`, Fortune comme le fer | 0 |
| `shale` | Y 0 à 80 (uniforme) | tous les biomes de l'Overworld | poches de 64 blocs × 2, comme l'andésite | pioche en bois | lui-même | — |

- Le filon de schiste bitumineux remplace la pierre (`stone_ore_replaceables`) et le schiste ; le néodyme remplace l'ardoise des abîmes (`deepslate_ore_replaceables`). Les poches de schiste remplacent la pierre (`base_stone_overworld`), comme l'andésite.
- Le silicium n'a pas de minerai : il vient du quartz du Nether (section Fonte).
- Le toucher de soie rend le bloc de minerai ; chaque bloc de minerai se fond (four et haut fourneau) en sa matière.
- Le schiste et ses dérivés se comportent comme le tuf et ses dérivés : résistance 1,5 / 6, sons du tuf (`TUFF`, `POLISHED_TUFF`, `TUFF_BRICKS`), pioche requise, chaque bloc lâche lui-même (la dalle double lâche 2 dalles). Recettes de table de craft et de tailleur de pierre comme la famille du tuf : schiste → poli (2×2) → briques (2×2), 2 dalles de briques → schiste sculpté, escaliers, dalles et murets de chaque base ; au tailleur, chaque base donne ses dérivés et ceux qui suivent dans la chaîne.
- Les minerais n'apparaissent que dans les chunks générés après l'installation ; les notes de version le diront.
- Les features (`configured_feature`, `placed_feature`) sont écrites par le datagen ; elles sont ajoutées aux biomes par `BiomeModifications.addFeature` (étape `UNDERGROUND_ORES`).
- Tags : `c:ores`, `c:ores/neodymium`, `c:ores/oil_shale`, `c:ores_in_ground/stone`, `c:ores_in_ground/deepslate`, `mineable/pickaxe` (tous les blocs), `needs_stone_tool` (schiste bitumineux), `needs_iron_tool` (néodyme), `stairs`, `slabs`, `walls` ; `c:raw_materials/neodymium`, `c:ingots/neodymium`, `c:silicon`.

### Fonte

| Entrée | Sortie | Four | Haut fourneau | XP |
|---|---|---|---|---|
| `raw_neodymium`, `deepslate_neodymium_ore` | `neodymium_ingot` | 200 ticks | 100 ticks | 1,0 |
| quartz du Nether | `silicon` | — | 100 ticks | 0,5 |
| `silicon` | `silicon_wafer` | 200 ticks | — | 0,5 |
| `oil_shale` | `bitumen` | 200 ticks | 100 ticks | 0,1 |

## 5. Machines

### 5.1 Comportement commun

- **Emplacements** : 1 à 3 entrées selon la machine (bobineuse 1, magnétiseur 2, graveur 3, extrudeuse 1), 1 énergie, 1 sortie ; l'extrudeuse a en plus 1 emplacement de moule. Les entrées inutilisées sont masquées et refusent tout ; une recette ne peut pas avoir plus d'ingrédients que sa machine n'a d'entrées.
- **Recette** : sans forme. Chaque ingrédient de la recette (au plus 3, chacun avec une quantité) doit occuper un emplacement d'entrée distinct, en quantité suffisante ; les entrées non utilisées doivent être vides. C'est l'entrée qui décide de la recette (aucune sélection), sauf le moule pour l'extrudeuse.
- **Énergie** : une opération coûte 1 unité. Quand une opération doit démarrer et que la réserve est vide, la machine consomme un objet d'énergie : poudre de redstone = 8 unités, bloc de redstone = 72 unités. La réserve est sauvegardée avec la machine. Les objets d'énergie sont des données : une recette `ravecraft:machine_energy` par objet ou tag (`{"ingredient": …, "operations": 1 à 72}`), synchronisée avec le client, qu'un datapack peut ajouter, modifier ou retirer, comme les carburants des mods techniques.
- **Durée** : 100 ticks par opération (5 s, deux fois plus vite que le four), pour toutes les recettes de ce sous-projet. La durée est un champ de la recette.
- **Blocages** : si la sortie ne peut pas recevoir le résultat (pleine ou objet différent), la progression s'arrête sans se perdre. Si les entrées changent et ne correspondent plus à la recette en cours, la progression revient à zéro. Sans énergie, la machine attend, progression conservée.
- **Entonnoirs** : par le dessus → entrées (chaque objet va dans l'emplacement qui le contient déjà, sinon dans le premier vide) ; par les côtés → énergie (objets d'énergie seulement) ; par le dessous → extraction de la sortie. Le moule n'est jamais accessible aux entonnoirs.
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
| | 2 `plastic_sheet` | `membrane_mold` | 1 `reinforced_membrane` |
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
| `magnetizer` | `INI / CRC / INI` (`N` lingot de néodyme) |
| `etcher` | `IGI / RAR / III` (`G` verre, `A` éclat d'améthyste) |
| `extruder` | `IPI / IHI / III` (`P` piston, `H` haut fourneau) |
| `blank_mold` | ` I  / IKI /  I ` (`K` boule d'argile) |
| `sheet_mold` | sans forme : 1 moule vierge + 1 plaque de pression lourde |
| `casing_mold` | sans forme : 1 moule vierge + 1 bloc de fer |
| `membrane_mold` | sans forme : 1 moule vierge + 1 membrane |
| `board_mold` | sans forme : 1 moule vierge + 1 feuille de plastique |
| `membrane` ×2 | sans forme : 2 papiers + 1 plaque de plastique |
| `speaker_driver` | ` M  / IVI /  G ` (`M` membrane, `V` bobine mobile, `G` aimant en fer) |
| `woofer` | ` M  / IVI /  G ` (`M` membrane renforcée, `V` bobine mobile, `G` aimant en néodyme) |
| `amplifier_board` | sans forme : 1 puce + 1 circuit simple + 2 fils de cuivre |
| `portable_speaker` | `DBD /  S ` (`D` haut-parleur simple, un de chaque côté, `B` boîtier en plastique, `S` circuit simple) |
| `party_speaker` | `IWI / BPB / IAI` (`W` haut-parleur de grave, `B` boîtier en plastique, `P` enceinte portable, `A` carte d'amplification) |

- Les recettes provisoires des deux enceintes (`FFF / NRN / FFF` et `INI / CPC / IRI`) sont supprimées. Les recettes de teinture (`*_dyed`) restent inchangées.
- L'enceinte de soirée sort neuve (recette à forme) : la couleur, le nom et la file de l'enceinte portable utilisée ne sont pas repris, comme aujourd'hui.

### 6.2 Déblocage

- Chaque recette de table de craft, de fonte et de machine a un critère `has_<ingrédient>` **par ingrédient distinct**, combinés en « ou » : elle se débloque dès que le joueur possède l'un d'eux.
- La bobineuse, le graveur, l'extrudeuse et le moule vierge se débloquent donc avec le fer ou le cuivre, très tôt ; le magnétiseur aussi, mais sa fabrication demande du néodyme, donc une descente en profondeur avant la première enceinte (choix de 7d).
- Le moule vierge n'est pas un moule : il ne va pas dans l'emplacement du moule de l'extrudeuse et n'est pas dans le tag des moules. Il s'empile par 16.

### 6.3 Infobulles

- Chaque objet produit par une machine affiche une ligne grisée « Fabriqué dans : *machine* » (`Made in: …`), sous les autres lignes, sans Maj.
- Chaque moule affiche « Pour l'extrudeuse : *pièce* ».

### 6.4 Onglet créatif et traductions

- Onglet RaveCraft, dans l'ordre de la chaîne : minerais, famille du schiste, matières, composants du palier 1, du palier 2, moule vierge, moules, machines, puis les enceintes (déjà présentes).
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
- Chaque feature de minerai et de schiste est ajoutée aux biomes prévus (vérification du registre des biomes).
- Chaque bloc de la famille du schiste lâche lui-même à la pioche ; ses recettes de table de craft et de tailleur de pierre existent.
- Le quartz du Nether donne du silicium au haut fourneau seulement.
- Blocs de néodyme brut, de néodyme et de silicium : 9 objets donnent un bloc et un bloc rend 9 objets ; pioche en fer pour les deux blocs de néodyme, en pierre pour le silicium ; chacun lâche lui-même et porte ses tags `c:storage_blocks`.
- Les nouvelles recettes des deux enceintes existent et les anciennes non.
- Chaque objet produit par une machine affiche « Fabriqué dans », chaque moule « Pour l'extrudeuse », et ces tables correspondent aux recettes chargées.

**En jeu** : monde survie neuf, du minage du schiste à l'enceinte de soirée en suivant le livre ; entonnoirs ; écran et livre de chaque machine ; textures, modèles et animations.

## 9. Visuels

- Propositions avant implémentation (3 à 4 rendus chacune) : minerais et famille du schiste, 4 machines (modèle Blockbench, face `_on` animée, fond d'écran), 23 objets.
- Textures produites par `tools/textures/gen_textures.py`, palette commune `C`.
- Choix de 7a (rendus dans `art/proposals/materials-ores/`, non versionnés) :
  - direction artistique : toujours partir des textures vanilla équivalentes (tuf, ardoise des abîmes, minerais, objets bruts), pas de contour noir sur les objets (le bord prend les tons sombres de la matière), 10 couleurs au plus par bloc ;
  - schiste : 6 variantes, feuilleté, couleurs relevées sur une photo de schiste réel (gris-bleu, gris chaud, rouille, ocre), fentes sombres et bords éclairés, palette commune de 10 couleurs ;
  - schiste bitumineux : 8 variantes sur le fond du schiste, veine de bitume droite dans le sens des feuillets (1 à 2 pixels, sans coulure), ancrée à la même hauteur pour se raccorder d'un bloc à l'autre ;
  - minerai de néodyme : 6 variantes, grains lilas moyens sans contour (comme le minerai de fer) sur l'ardoise des abîmes vanilla (D13) ;
  - schiste poli : dégradés gris-bleu façon tuf poli (8 tons, lignes claires et sombres alternées), cadre discret ;
  - briques de schiste : 8×4 en quinconce, chaque brique gris-bleu ou brun chaud ; schiste sculpté : onde sonore gravée, creux brun chaud, reliefs gris-bleu ;
  - bitume : galet de goudron brillant ; néodyme brut : pépites lilas ; lingot de néodyme : forme du lingot vanilla, argent lilas clair ; silicium : morceau gris-bleu taillé ; plaquette de silicium : disque vu de biais, irisé, sans puces.
  - blocs de stockage, construits sur la structure des blocs de métal vanilla (cadre, reflets en diagonale) : néodyme brut en grosses pépites lilas éclairées en haut à gauche (R1) ; néodyme en bandes de lumière ondulées en diagonale, cadre de tons moyens et liseré clair en haut (O2) ; silicium en éclats de cristal superposés, chaque facette en dégradé par paliers, dans un cadre biseauté (B4).
- Rendus finaux de 7a : `art/proposals/materials-ores/final/` et `summary_final_v2.png` (non versionnés).
- Choix de 7c (rendus dans `art/proposals/`, non versionnés) :
  - machines : même boîtier en acier brossé et même fenêtre que la bobineuse, une façade par machine : bobine de champ entre deux pôles pour le magnétiseur (M1), carte verte sous un laser rouge pour la graveuse (E1), buse chauffée au-dessus d'un bac de goudron pour l'extrudeuse (X2) ; faces `_on` animées (4 images) ;
  - effets en marche : magnétiseur (bourdonnement de balise, étincelles), graveuse (cliquetis de sculk, poussière de redstone), extrudeuse (bulles de lave, fumée) ;
  - écrans : disposition de la bobineuse avec 2, 3 et 1 entrées ; emplacement du moule à gauche de l'entrée de l'extrudeuse, icône vide grise du moule et de son empreinte ;
  - aimants : disques vus de biais, ferrite gris sombre et néodyme lilas, à taches et reflets façon vanilla ;
  - circuits : plaque verte (A), circuit simple avec pistes cuivre, puce noire à pattes ;
  - plastique noir brillant : feuille (première version du rework), boîtier ouvert, membrane renforcée en cône ;
  - moules : cadre gris clair avec l'empreinte du produit en creux (A r2) ;
  - textures des objets gardées en grilles de pixels dans `tools/textures/machine_parts.py` (identiques aux propositions validées).
- Choix de 7d (rendus dans `art/proposals/items7d/`, non versionnés) :
  - membrane : la grille de la membrane renforcée recolorée en papier (A) ; haut-parleur simple et haut-parleur de grave : saladier carré en acier à 4 vis, membrane en retrait, joint sombre (E) ; carte d'amplification : puce et condensateurs bleus sur la plaque de circuit (B) ; moule vierge : le cadre des moules autour d'une plaque d'acier lisse (A) ;
  - recettes : membrane renforcée extrudée à partir de 2 feuilles de plastique (l'extrudeuse garde une seule entrée) ; enceinte portable avec un haut-parleur simple de chaque côté ; enceinte de soirée avec des boîtiers en plastique au lieu du cuivre ; magnétiseur M3 (néodyme et cuivre) ; extrudeuse au haut fourneau (X3) ; moules fabriqués à partir d'un moule vierge (C) et d'un objet qui a la forme de la pièce : plaque de pression lourde (plaque), bloc de fer (boîtier), membrane (membrane renforcée), feuille de plastique (plaque de circuit). Le moule à plaque vient donc en premier : ses feuilles servent aux moules à membrane et à support.
- Modèles des machines dans `art/models`, exportés en JSON de bloc.

## 10. Mise à jour de la SPEC

À la fin de 7d :
- section 3.7 : contenu livré ;
- section 9 : « Obtention des matériaux » tranchée (les deux), « Énergie » toujours ouverte (la redstone n'est qu'un carburant de machine) ;
- section 11 : étape « Matériaux et composants » cochée avec les liens vers cette spec et les plans.
