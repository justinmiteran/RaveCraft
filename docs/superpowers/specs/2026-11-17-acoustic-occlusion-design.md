# Étouffement à travers les blocs (étape 4b du socle audio) — spec de conception

> Sous-projet : **Socle audio**, étape 4b (`feat/acoustic-occlusion`).
> Spec parente : [2026-11-17-acoustic-engine-design.md](2026-11-17-acoustic-engine-design.md) (étape 4a, rendu acoustique). Remplace son esquisse §6.

## 1. Objectif

Le son des enceintes est étouffé par ce qui se trouve entre l'enceinte et l'auditeur. Un mur laisse passer les basses et coupe les aigus, l'eau sépare deux mondes sonores, le vide n'en transmet aucun, et le son contourne les obstacles par les portes et les couloirs.

### Critères de réussite
- À travers un mur de pierre, on entend la fête « de l'autre côté » : environ −15 dB global, aigus presque coupés. Trois ou quatre murs donnent le silence.
- Ouvrir une porte pendant la lecture rend le son plus clair et plus fort, sans saut ni clic, en une demi-seconde environ pour le volume (recalcul 4 fois par seconde, puis lissage sur 200 ms) ; le timbre suit avec jusqu'à 0,4 s de plus (file d'OpenAL, §4.4).
- Une enceinte dans une pièce fermée par une porte ouverte s'entend par la porte (détour) et non comme si les murs n'existaient pas.
- Sous l'eau, la musique de la surface devient lointaine et sourde ; deux joueurs sous l'eau s'entendent normalement.
- Dans une dimension sans air (mod spatial configuré en vide), aucune enceinte ne s'entend.
- Les matières, les milieux et leurs valeurs sont réglés par le serveur et identiques pour tous les joueurs ; l'affectation des blocs et des fluides se fait par tags, donc par datapack.
- Aucune baisse de FPS perceptible avec 16 enceintes audibles.

## 2. Décisions prises

| # | Décision |
|---|---|
| O1 | **Chemin direct plus détours** : le son suit la ligne droite et des chemins contournant les obstacles par des ouvertures proches ; les meilleurs chemins sont additionnés en énergie. |
| O2 | **Matière d'un bloc** : tag `#ravecraft:acoustic/<matière>` d'abord, sinon son de pas (`SoundType`), sinon pierre pour un bloc solide. Le résultat est pondéré par la forme du bloc. |
| O3 | **Réalisme « ressenti »** : environ −15 dB global à travers un bloc de pierre, aigus majoritairement coupés ; trois à quatre blocs mènent au silence ; la laine isole fort, le verre et le bois peu. Toutes les valeurs sont configurables. |
| O4 | **Milieux** (air, eau, lave, vide…) distincts des matières : un milieu a une impédance acoustique, une perte par bloc et une perte des aigus par bloc. La perte à chaque changement de milieu est **calculée depuis les impédances** (loi physique), adoucie par un facteur. Le vide (impédance 0) ne transmet rien. |
| O5 | **Listes ouvertes** : la config peut ajouter des matières et des milieux ; leurs blocs et fluides leur sont affectés par tags. Un milieu peut être celui d'une dimension entière. |
| O6 | Calcul **côté client**, réparti sur les ticks ; le serveur et le relais ne changent pas (le rayon de relais suit la portée, pas les murs). |
| O7 | Sound Physics Remastered ne traite que les sons vanilla : l'occlusion RaveCraft **reste active** en sa présence (sa présence est seulement notée dans le log). Remplace la règle « désactivé si un mod de physique du son est détecté » de la spec 4a §6. |

## 3. Architecture

```
thread client (20 Hz)                             thread audio RaveCraft (100 Hz)
───────────────────────                           ──────────────────────────────
OcclusionScheduler                                AcousticRenderer
  ├ chaque tick : 1/5 des flux audibles             ├ gain = propagation × 10^(−perte/20)
  │   → chaque flux recalculé à 4 Hz                ├ filtre EFX : GAIN + GAINHF
  ├ WorldAcousticGrid (lecture du monde)            │   (air × étouffement des aigus)
  │   BlockAcoustics : matière + remplissage        └ lissage ~200 ms
  │   milieu : fluide ou dimension
  └ OcclusionTracer (core, Java pur)
      chemin direct + détours                     SourceSelector : choisit les sources
      → perte globale dB, perte aigus dB  ──────►   sur le gain étouffé
        (volatile dans RenderStream)
```

### 3.1 `core.acoustics.occlusion` (Java pur, testé en JUnit)

| Classe | Rôle |
|---|---|
| `AcousticMaterial` | record `(name, lossDb, highLossDb)` : perte d'un bloc plein, globale et supplémentaire sur les aigus. |
| `AcousticMedium` | record `(name, impedance, lossDbPerBlock, highLossDbPerBlock)`. |
| `OcclusionSettings` | record : `enabled`, `maxLossDb`, `interfaceScale`, `interfaceHighRatio`, `detours`, `bendLossDb`, `bendHighLossDb`, listes de matières et de milieux, table `dimension → milieu`. `DEFAULT`, `clamped(warn)`, plafonds de taille. |
| `Interfaces` | perte à un changement de milieu : `T = 4·Z₁·Z₂ / (Z₁+Z₂)²`, perte = `−10·log10(T) × interfaceScale` (infinie si `T = 0`), aigus : `perte × (1 + interfaceHighRatio)`. |
| `AcousticGrid` | interface lue par le traceur : `cell(x, y, z)` → matière (ou aucune), remplissage par axe (X, Y, Z), milieu. Implémentée côté jeu par `WorldAcousticGrid`, et en test par une grille en mémoire. |
| `OcclusionTracer` | `trace(grid, from, to, budget)` → `PathLoss(lossDb, highLossDb, lengthBlocks)` ou muet ; `occlusion(grid, listener, source, settings, rolloff, maxPathLength, budget)` → `Occlusion(lossDb, highLossDb)` avec détours. |
| `TraceBudget` | compteur de blocs visités, partagé par tous les tracés d'un tick ; le tracé s'arrête proprement quand il est épuisé. |

### 3.2 Couche Minecraft

| Classe | Source set | Rôle |
|---|---|---|
| `world.acoustics.BlockAcoustics` | main | Pour un `BlockState` : matière (tags de la config, puis `SoundType`, puis pierre si le bloc a une collision) et remplissage par axe tiré de la forme de collision. Cache par état, vidé au rechargement des tags et au changement des réglages. Testable par GameTest. |
| `world.acoustics.MediumResolver` | main | Milieu d'une position : tag de fluide `#ravecraft:medium/<nom>` du fluide présent, sinon milieu de la dimension (`dimensions`), sinon `air`. |
| `client.audio.occlusion.WorldAcousticGrid` | client | `AcousticGrid` sur le `ClientLevel` ; chunk non chargé → air sans matière. |
| `client.audio.occlusion.OcclusionScheduler` | client | À chaque fin de tick client : choisit les flux à recalculer, appelle le traceur dans le budget, publie le résultat dans `RenderStream`. |

`RenderStream` reçoit deux champs `volatile double occlusionLossDb, occlusionHighLossDb`. `AcousticRenderer` les lisse (rampe d'environ 200 ms) et les applique : gain du flux multiplié par `10^(−perte/20)` (le gain étouffé sert aussi au `SourceSelector`), perte des aigus appliquée par un passe-bas propre au mod (voir §4.4). Le filtre EFX d'OpenAL ne porte plus que l'absorption de l'air : dans OpenAL Soft, c'est une étagère fixée vers 5 kHz, qui laisse passer les médiums.

## 4. Matières

### 4.1 Affectation
1. Tag de blocs `#ravecraft:acoustic/<nom>` pour chaque matière de la config (le premier tag qui contient l'état gagne, dans l'ordre de la config). Le datagen génère les tags des 8 matières de base, en y incluant les tags vanilla pertinents (`#minecraft:leaves` → feuillage, `#minecraft:wool` et tapis → laine, `#minecraft:planks`, `#minecraft:logs` → bois, etc.). Ces tags sont nécessaires car les feuilles ont le son de pas de l'herbe.
2. Sinon le `SoundType` du bloc, par une table codée vers les matières de base (pierre, bois, verre, métal, terre, neige, laine, feuillage). La liste exacte des constantes est établie pendant le plan, d'après les sources 26.3.
3. Sinon `stone` si le bloc a une forme de collision, aucune matière sinon (air, herbe haute, fleurs, torches…).

Une matière de base retirée de la config reprend ses valeurs par défaut. Une matière ajoutée ne s'applique qu'aux blocs de son tag.

### 4.2 Remplissage (par axe X, Y, Z)
- **Panneau étanche** : si une boîte de la forme de collision tient entièrement dans le bloc, couvre toute la face perpendiculaire à l'axe et mesure entre 2/16 et 4/16 d'épaisseur le long de l'axe, le remplissage vaut 1 (porte et trappe fermées, vitre de face). Le seuil bas est 2/16 et non 3/16, car une vitre ne fait que 2/16 d'épaisseur. Le plafond de 4/16 évite qu'une dalle compte comme un mur plein. Une boîte qui dépasse du bloc (barrière, muret : 1,5 de haut) ne ferme jamais un axe.
- **Sans collision** (herbe haute, torche) : aucune matière, sauf si le bloc porte un tag `#ravecraft:acoustic/<nom>` ; la table des sons de pas ne s'applique qu'aux blocs qui ont une collision.
- **Sinon** : part du volume du bloc occupée par la forme, chaque boîte étant d'abord rognée au bloc, plafonnée à 1 (dalle 0,5, escalier 0,75, barrière 0,25, muret ≈ 0,44, porte ouverte vue de côté ≈ 0,19, tapis 1/16).

### 4.3 Valeurs par défaut (par bloc plein)

| Matière | `lossDb` | `highLossDb` |
|---|---|---|
| foliage | 2 | 6 |
| glass | 5 | 12 |
| wood | 8 | 18 |
| snow | 10 | 24 |
| earth | 12 | 24 |
| stone | 15 | 30 |
| metal | 18 | 20 |
| wool | 20 | 36 |

`highLossDb` est une perte **supplémentaire**, mesurée à 4 kHz. À travers un bloc de pierre : −15 dB global, et en plus ≈ −2 dB à 100 Hz, −30 dB à 4 kHz. Ces valeurs suivent l'ordre de grandeur de la loi de masse (un mur de béton de 20 cm isole ≈ 46 dB à 100 Hz et ≈ 78 dB à 4 kHz, soit ≈ 30 dB d'écart), compressé pour le jeu ; elles se règlent en jeu.

### 4.4 Filtre des aigus

`core.acoustics.occlusion.OcclusionFilter` : passe-bas d'ordre 1 (bilinéaire, −6 dB par octave comme la loi de masse) appliqué au PCM mono de chaque flux dans `RenderStream.nextBlock`, après l'égaliseur de l'appareil. Sa coupure est choisie pour perdre exactement la perte des aigus du chemin à 4 kHz : ≈ 125 Hz derrière un bloc de pierre (30 dB), ≈ 500 Hz derrière du bois (18 dB), ≈ 1 kHz derrière du verre (12 dB). Coupure plancher 20 Hz (≈ 46 dB à 4 kHz au plus). Sans perte, le son passe intact. Une nouvelle perte est atteinte en glissant sur le bloc de 100 ms (coefficients mis à jour chaque milliseconde), sans clic. L'option « Effets acoustiques » le désactive comme le filtre EFX.

Contrepartie : le filtre agit avant la file d'OpenAL (jusqu'à 4 blocs de 100 ms d'avance), donc un changement de timbre arrive jusqu'à 0,4 s après le changement de volume.

## 5. Milieux

| Milieu | `impedance` (rayl) | `lossDbPerBlock` | `highLossDbPerBlock` | Interface avec l'air (défauts) |
|---|---|---|---|---|
| air | 415 | 0 | 0,05 | — |
| water | 1 480 000 | 0 | 0,02 | ≈ −20 dB, aigus ≈ −30 dB |
| lava | 6 000 000 | 3 | 6 | ≈ −24 dB |
| vacuum | 0 | 0 | 0 | aucun son ne passe |

- `interfaceScale` = 0,67 (1 = réel, air/eau ≈ −30 dB) ; `interfaceHighRatio` = 0,5.
- L'absorption de l'air de 4a (`airAbsorptionDbPerBlock`) devient `highLossDbPerBlock` du milieu `air` ; `Propagation.highFrequencyGain` lit désormais cette valeur. L'absorption s'applique sur la distance directe, comme en 4a ; les pertes par bloc des autres milieux s'appliquent sur la longueur parcourue dans chacun.
- Tags de fluides générés : `#ravecraft:medium/water` (inclut `#minecraft:water`) et `#ravecraft:medium/lava` (inclut `#minecraft:lava`).
- `dimensions` : `{ "<dimension>": "<milieu>" }`, vide par défaut. Exemple : `"ad_astra:moon": "vacuum"`.
- Limite assumée : les zones pressurisées des mods spatiaux ne sont pas gérées (une compatibilité dédiée pourra venir plus tard).

## 6. Tracé

### 6.1 Un segment
Parcours voxel par voxel (Amanatides–Woo) de `from` à `to`. Les voxels de départ et d'arrivée n'apportent pas de matière (le bloc de l'enceinte, la tête du joueur). Pour chaque voxel traversé, avec la longueur `ℓ` parcourue dedans et l'axe de la face d'entrée :
- matière : `perte += lossDb × remplissage(axe) × min(1, ℓ)`, `aigus += highLossDb × remplissage(axe) × min(1, ℓ)` ;
- milieu : `perte += lossDbPerBlock × ℓ`, `aigus += highLossDbPerBlock × ℓ` (sauf l'air, dont l'absorption est appliquée par `Propagation`) ;
- changement de milieu par rapport au voxel précédent (un voxel solide sans fluide garde le milieu courant) : perte d'interface (§3.1). Un milieu d'impédance 0 sur le chemin, extrémités comprises, rend le segment muet.

Un bloc gorgé d'eau compte à la fois comme matière et comme milieu. Les totaux sont plafonnés à `maxLossDb` (le global et les aigus séparément).

### 6.2 Détours
1. Si le chemin direct perd moins de 3 dB, pas de détour.
2. **Points candidats** : 26 directions × distances {3, 6} blocs, autour de l'auditeur et autour de l'enceinte (52 de chaque côté). Gardés s'ils sont dans un voxel sans matière, dans le même milieu que leur extrémité, et reliés à elle par un segment de moins de 3 dB de perte. On retient les 6 plus proches de l'autre extrémité, de chaque côté.
3. **Chemins essayés** : un coude (enceinte → P → auditeur, P de l'un ou l'autre côté) et deux coudes (enceinte → Q → P → auditeur, Q près de l'enceinte, P près de l'auditeur).
4. **Coût d'un chemin** : somme des pertes des segments + `20·log10(longueur / distance directe) × rolloff` + par coude `bendLossDb × angle/90°` (global) et `bendHighLossDb × angle/90°` (aigus), l'angle étant la déviation à ce coude. Un chemin plus long que la portée de l'enceinte est écarté.
5. **Chemins retenus** :
   - Un chemin dont un point coïncide avec une extrémité ou avec l'autre point est écarté, tout comme un segment de longueur nulle. Ainsi, un coude n'est jamais gratuit.
   - Les chemins identiques (mêmes voxels de passage) ne comptent qu'une fois.
   - Un détour qui n'est pas plus long que le chemin direct est écarté. C'est en fait le chemin direct.
   - Un détour n'est gardé que si sa perte est strictement inférieure à celle du chemin direct.
   - Un chemin dont la longueur et les coudes coûtent déjà autant que le chemin direct n'est pas tracé.
6. **Une ouverture compte une fois** : les détours sont pris par perte croissante. Un détour n'entre dans la somme que si son dernier segment (point → auditeur) arrive à au moins 45° de ceux des détours déjà retenus. Limite connue : vues de loin, deux vraies ouvertures à moins de 45° l'une de l'autre comptent comme une seule (≈ 3 dB de moins), à régler en jeu si besoin.
7. **Résultat** : somme en énergie du chemin direct et des 3 meilleurs détours retenus, `−10·log10(Σ 10^(−perteᵢ/10))`, pour la perte globale comme pour les aigus.

Défauts : `detours` = true, `bendLossDb` = 6, `bendHighLossDb` = 10.

### 6.3 Cadence et budget
- Les flux audibles (gain non nul, alimentés, dans la portée) sont triés par gain ; chaque tick traite un cinquième d'entre eux, donc 4 Hz par flux. Un flux qui vient d'être attaché est calculé dès le tick suivant.
- Budget de blocs visités par tick, réglé par l'option client « Qualité de l'occlusion » : Basse 5 000, Moyenne 20 000 (défaut), Haute 60 000. Les flux non traités faute de budget passent en tête au tick suivant ; leur dernière valeur reste appliquée.
- La position de l'auditeur est la caméra ; celle de l'enceinte, le centre de son bloc.

## 7. Configuration

### 7.1 Serveur : section `"occlusion"` de `config/ravecraft-server.json5`

```json5
"occlusion": {
  "enabled": true,
  "maxLossDb": 60,
  "media": {
    "air":    { "impedance": 415,     "lossDbPerBlock": 0, "highLossDbPerBlock": 0.05 },
    "water":  { "impedance": 1480000, "lossDbPerBlock": 0, "highLossDbPerBlock": 0.02 },
    "lava":   { "impedance": 6000000, "lossDbPerBlock": 3, "highLossDbPerBlock": 6 },
    "vacuum": { "impedance": 0,       "lossDbPerBlock": 0, "highLossDbPerBlock": 0 }
  },
  "interfaceScale": 0.67,
  "interfaceHighRatio": 0.5,
  "dimensions": { },
  "materials": {
    "foliage": { "lossDb": 2,  "highLossDb": 4 },
    "glass":   { "lossDb": 5,  "highLossDb": 6 },
    "wood":    { "lossDb": 8,  "highLossDb": 10 },
    "snow":    { "lossDb": 10, "highLossDb": 14 },
    "earth":   { "lossDb": 12, "highLossDb": 12 },
    "stone":   { "lossDb": 15, "highLossDb": 15 },
    "metal":   { "lossDb": 18, "highLossDb": 12 },
    "wool":    { "lossDb": 20, "highLossDb": 20 }
  },
  "detours": true,
  "bendLossDb": 6,
  "bendHighLossDb": 10
}
```

Chaque réglage est précédé d'un commentaire dans le fichier écrit, comme en 4a.

| Réglage | Bornes |
|---|---|
| `maxLossDb` | 20 à 120 |
| `impedance` | 0 à 100 000 000 |
| `lossDbPerBlock`, `highLossDbPerBlock`, `lossDb`, `highLossDb` | 0 à 40 |
| `interfaceScale` | 0 à 1 |
| `interfaceHighRatio` | 0 à 2 |
| `bendLossDb`, `bendHighLossDb` | 0 à 40 |
| nombre de milieux / matières / dimensions | 16 / 32 / 32 |
| noms de milieux et de matières | `[a-z0-9_]`, 1 à 32 caractères |
| noms de dimensions | identifiant valide, 64 caractères au plus |

- Le milieu `air` est toujours présent (ajouté avec ses défauts s'il manque).
- Nom invalide ou milieu inconnu dans `dimensions` : entrée ignorée, avertissement.
- Listes trop longues : tronquées dans l'ordre du fichier, avertissement. Le plafonnement a lieu **avant l'envoi** ; le client borne aussi chaque valeur et chaque taille reçue.
- Ancien fichier sans section `"occlusion"` ou sans un réglage : les manquants prennent leur défaut, et le fichier est réécrit complet et commenté avec les valeurs actuelles, l'ancien étant copié dans `ravecraft-server.json5.bak`. La réécriture passe par un fichier temporaire, déplacé d'un coup sur l'original. Un fichier illisible (erreur de syntaxe, valeur du mauvais type) n'est jamais réécrit : le serveur prend les valeurs par défaut et l'indique dans le journal, jusqu'à ce que l'admin corrige le fichier.
- Ancienne clé `airAbsorptionDbPerBlock` : si `media.air` n'est pas défini, elle devient son `highLossDbPerBlock`. Elle n'est plus écrite.

### 7.2 Réseau
`AcousticSettingsPayload` transporte `OcclusionSettings` (codecs de liste avec les mêmes plafonds que la config). Les tags de blocs et de fluides arrivent par la synchronisation vanilla des tags.

### 7.3 Client (Options › Musique et sons, `options.txt`)
- « Effets acoustiques » (existant) : désactivé, il coupe tout assourdissement des aigus (murs, interfaces, coudes, air) ; la perte globale par les murs reste appliquée.
- « Qualité de l'occlusion » (nouveau) : Basse / Moyenne / Haute (§6.3).
- Sans EFX : même comportement qu'« Effets acoustiques » désactivé.

## 8. Cas limites
- `enabled` = false : aucune occlusion, aucun tracé.
- Enceinte ou auditeur dans un chunk non chargé : voxels considérés comme de l'air.
- `/reload` (tags modifiés) ou nouveaux réglages reçus : cache de `BlockAcoustics` vidé, tous les flux recalculés dès que le budget le permet.
- Changement de dimension : les flux sont déjà détachés par 4a ; le milieu de dimension est relu à chaque tracé.
- Sound Physics Remastered (`sound_physics_remastered`) chargé : ligne d'information dans le log, rien d'autre.
- Bloc solide sans fluide (pierre sous l'eau, enceinte non waterloggable immergée) : il n'a pas de milieu propre et garde le milieu courant, sans interface. Une extrémité dans un tel bloc prend le premier vrai milieu rencontré sur le segment direct.
- Enceinte jamais encore tracée (arrivée, retour à portée) : muette jusqu'à son premier tracé, soit un tick au plus. Si le budget est épuisé, elle reçoit au moins le tracé en ligne droite.
- Bloc d'un autre mod dont la forme de collision lève une exception hors du monde : traité comme un cube plein de sa matière, une ligne dans le log par bloc. Une erreur pendant un tracé est journalisée et la valeur précédente est gardée ; le tick ne plante jamais.

## 9. Tests

### JUnit (`core`, grille en mémoire)
- Vide → 0 dB ; 1 mur de pierre → 15 dB, aigus 30 ; 4 murs → plafond 60 ; une dalle traversée selon Y → moitié.
- Rayon qui frôle l'arête d'un bloc → perte proportionnelle à la faible longueur parcourue.
- Air → eau : ≈ 20 dB, aigus ≈ 30 ; enceinte et auditeur dans l'eau → aucune interface ; milieu `vacuum` sur le chemin ou à une extrémité → muet.
- `Interfaces` : formule, symétrie, Z identiques → 0 dB, Z = 0 → muet.
- Détour : pièce fermée avec porte ouverte → perte bien inférieure au chemin direct ; deux portes → plus fort qu'une ; couloir en L → trouvé par deux coudes ; détour plus long que la portée → écarté ; chemin direct libre → aucun détour essayé.
- Budget épuisé → le tracé s'arrête sans exception et le signale.
- `OcclusionSettings.clamped` : bornes, noms invalides, plafonds de taille, `air` ajouté s'il manque.
- `AcousticSettingsJson` : lecture de la section, migration de `airAbsorptionDbPerBlock`, fichier sans section → défauts et indication de réécriture, chaque réglage commenté.
- `AcousticSettingsPayload` : aller-retour, listes plafonnées et valeurs bornées à la réception.

### GameTests (`BlockAcoustics`, `MediumResolver`, vrais blocs)
- Pierre → stone, planches → wood, feuilles → foliage (malgré leur son d'herbe), laine → wool, bloc sans collision → aucune matière.
- Dalle basse → remplissage Y 0,5 ; porte fermée → 1 sur son axe ; porte ouverte → partiel ; tapis → 1/16 sur Y.
- Bloc gorgé d'eau → matière et milieu `water` ; position dans la lave → `lava`.

### À vérifier en jeu (liste reprise dans la PR)
- Porte ouverte puis fermée pendant la lecture : changement en douceur.
- Plongée sous l'eau près d'une enceinte en surface ; deux joueurs sous l'eau.
- Enceinte dans une cave fermée de 4 blocs de pierre : silence à l'extérieur.
- 16 enceintes audibles : pas de baisse de FPS en qualité Moyenne.
- Sound Physics Remastered installé : pas de double étouffement.

## 10. Points à vérifier pendant le plan
- Constantes `SoundType` disponibles en 26.3 et table de correspondance.
- API de forme de collision (`BlockState.getCollisionShape`, `VoxelShape.toAabbs` ou équivalent) utilisable sans position ni contexte d'entité, et états dont la forme dépend de la position (à traiter sans cache ou par forme vide).
- Lecture du fluide (`BlockState.getFluidState`, `FluidState.is(TagKey)`) et événement Fabric de rechargement des tags côté client.
- Position de la caméra et son état de fluide depuis le thread client.

## 11. Mises à jour liées
- Spec 4a : §6 renvoie vers cette spec ; `airAbsorptionDbPerBlock` y est signalé comme déplacé dans le milieu `air`.
- `docs/SPEC.md` : ligne de l'étape 4b.
- Branche partie de `feat/acoustic-render` (PR #7 non fusionnée), rebasée sur `main` après sa fusion.
