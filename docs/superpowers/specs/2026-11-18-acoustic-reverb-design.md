# Réverbération, échos et couleur sous l'eau (étape 4c du socle audio) : spec de conception

> Sous-projet : **Socle audio**, étape 4c (`feat/acoustic-reverb`).
> Spec parente : [2026-11-17-acoustic-engine-design.md](2026-11-17-acoustic-engine-design.md). Celle-ci remplace son esquisse §7. Elle s'appuie sur l'occlusion de 4b ([2026-11-17-acoustic-occlusion-design.md](2026-11-17-acoustic-occlusion-design.md)) : grille acoustique, matières, milieux, perception de l'auditeur.

## 1. Objectif

Le son des enceintes prend la couleur du lieu où il résonne. Une salle de pierre résonne, une salle tapissée de laine sonne mate, le plein air sonne sec. Une fête dans une grotte, entendue depuis l'entrée, sonne caverneuse. Face à une grande paroi lointaine, on entend un écho distinct. La tête sous l'eau, le son devient épais et enfermé.

### Critères de réussite
- Une salle fermée en pierre résonne nettement. La même salle tapissée de laine sonne mate, avec une décroissance courte et peu d'aigus. Le plein air n'a presque pas de réverbération.
- Une enceinte dans une grotte, entendue depuis l'extérieur, sonne caverneuse et étouffée par les murs.
- Face à une paroi réfléchissante entre 17 et 35 blocs, par exemple un ravin, on entend un écho net et discret (une ou deux répétitions). Ailleurs, aucun écho.
- La tête sous l'eau ou dans la lave, le son est épais et enfermé. Sous l'eau, un léger flottement (chorus) s'y ajoute ; le serveur peut le couper.
- Se déplacer ou changer de pièce fait glisser la réverbération, sans saut ni clic.
- Les valeurs sont réglées par le serveur et identiques pour tous les joueurs.
- Aucune baisse de FPS perceptible avec 16 enceintes audibles.

## 2. Décisions prises

| # | Décision |
|---|---|
| R1 | **Espace mesuré autour de l'auditeur et autour des enceintes** : ton lieu, plus le lieu des 2 enceintes audibles les plus fortes qui ne sont pas dans ton espace. |
| R2 | **Écho réaliste et rare** : seulement face à une grande paroi réfléchissante lointaine, discret, une ou deux répétitions. |
| R3 | **Sous l'eau** : un préréglage de réverbération propre au fluide ; le **chorus** est une option de la config serveur, activée par défaut pour l'eau. |
| R4 | **Approche hybride** : réverbération et écho en EFX (emplacements d'effet auxiliaires, 2 envois par source), chorus dans notre traitement du PCM. |
| R5 | Seules les enceintes RaveCraft sont concernées ; les sons vanilla ne changent pas (comme la perception de l'auditeur en 4b, et compatible avec Sound Physics Remastered). |
| R6 | Pas de suivi des changements de blocs : les mesures sont refaites périodiquement (2 s pour l'auditeur, 5 s pour une enceinte). |

## 3. Mesure d'un espace (`core`)

Calcul en Java pur dans `com.ravecraft.mod.core.acoustics.reverb`, sur l'interface `AcousticGrid` de 4b. Testable en JUnit.

### 3.1 Rayons
- Depuis un point, **N rayons** répartis uniformément sur la sphère (spirale de Fibonacci, directions fixes et déterministes). N dépend de la qualité joueur : basse 16, moyenne 32, haute 48.
- Longueur maximale **64 blocs** (configurable). Le parcours des voxels réutilise celui du traceur de 4b (DDA).
- Un rayon **s'arrête** sur le premier voxel dont la matière le bouche à moitié ou plus (`fill(axe) ≥ 0,5`), ou au **passage dans un autre milieu** (surface de l'eau vue depuis l'air, et l'inverse). Il note alors la distance et la matière touchée (ou le milieu, pour une surface de fluide).
- Un rayon qui atteint la longueur maximale sans rien toucher est **ouvert**.
- La mesure accepte un `TraceBudget`. Si le budget s'épuise, elle reprend au tick suivant là où elle s'était arrêtée.

### 3.2 Grandeurs dérivées (`RoomShape`)
- **Ouverture** `o` : part de rayons ouverts (0 à 1).
- **Libre parcours moyen** `l` : distance moyenne des rayons qui ont touché, en blocs (1 bloc = 1 m). Sans impact, `l` = longueur maximale.
- **Absorption moyenne** `ᾱ` : moyenne des absorptions des surfaces touchées. Un rayon ouvert compte comme absorption totale (α = 1).
- **Irrégularité** `i` : chaque rayon qui touche note la face franchie (axe et plan de bloc). Des murs plats et lisses ne montrent que quelques grandes faces (une boîte en montre 6) ; une grotte, des piliers ou des meubles en montrent beaucoup. `i = (F − 6) / min(N − 6, 18)`, borné à 0–1, où `F` est le nombre de faces distinctes et `N` le nombre de rayons qui ont touché une face : 0 pour une boîte, 1 dès 24 faces distinctes. Avec 6 impacts ou moins, `i` = 0,5 (indécidable).
- **RT60** (Eyring) : `RT60 = 0,161 · V / (−S · ln(1 − ᾱ))` ; avec `l = 4V/S`, cela donne `RT60 = 0,161 · l / (−4 · ln(1 − ᾱ))`. Exemple : salle de pierre de libre parcours 5 blocs → ≈ 10 s, ramené au plafond ; la même en laine → ≈ 0,17 s. Le résultat est borné entre 0,1 s et `maxDecaySeconds` (6 s par défaut, au plus 20 s, limite EAX).

### 3.3 Absorption des matières et des milieux
Chaque matière de 4b gagne une clé `absorption` (0 à 1, configurable) :

| Matière | pierre | verre | métal | bois | terre | neige | feuillage | laine |
|---|---|---|---|---|---|---|---|---|
| Absorption | 0,02 | 0,03 | 0,05 | 0,10 | 0,30 | 0,40 | 0,50 | 0,70 |

Une surface de fluide touchée par un rayon a l'absorption `surfaceAbsorption` du milieu situé derrière elle : eau 0,01, lave 0,05, air 1 (un rayon parti de l'eau vers l'air sort). Le vide absorbe tout (1).

### 3.4 Écho
- On cherche une **paroi lointaine** : au moins `echo.minRays` rayons (4 par défaut) qui touchent une surface d'absorption ≤ `echo.maxAbsorption` (0,2), entre `echo.minDistance` (17) et `echo.maxDistance` (35) blocs, et dont les distances sont à ±15 % de leur médiane (pour chaque rayon candidat, son groupe réunit les candidats à ±15 % de sa distance ; `d` est la médiane du groupe retenu).
- S'il y a plusieurs groupes, on garde celui qui a le plus de rayons, puis le plus proche.
- Délai : `2 · d / 343` s (0,099 à 0,204 s pour 17 à 35 blocs ; la limite de l'effet EFX Echo est 0,207 s).
- Niveau : `(1 − α_paroi) · (rayons du groupe / N)`, multiplié par `echo.gain` (configurable), et atténué en `17 / d`.
- Sans paroi de ce genre, l'écho est à zéro. Une paroi au-delà de 35 blocs ne fait pas d'écho distinct, mais elle compte toujours dans la réverbération.

### 3.5 Réglages EAX (`ReverbParams`)
Fonction pure `RoomShape → ReverbParams`, toujours dans les bornes EAX :

| Réglage EAX | Valeur |
|---|---|
| `DECAY_TIME` | RT60 |
| `DECAY_HFRATIO` | `1 − 0,6 · ᾱ_aigus`, borné à 0,1–1,0 : une salle absorbante perd ses aigus plus vite |
| `GAIN` (réverbération tardive) | `maxWetGain · (1 − o)^1,5` : presque nul en plein air |
| `REFLECTIONS_GAIN` | `GAIN · (1 − 0,5 · i)` : des murs lisses renvoient des premières réflexions franches, une grotte les éparpille |
| `REFLECTIONS_DELAY` | `min(0,3, l / 343)` |
| `LATE_REVERB_DELAY` | `min(0,1, l / 343)` |
| `DENSITY` | `clamp(8 / l, 0, 1)` : une petite salle est plus dense |
| `DIFFUSION` | `0,4 + 0,6 · i` : murs lisses → queue granuleuse qui « sonne » ; lieu irrégulier → queue douce et fondue |
| `AIR_ABSORPTION_GAINHF` | 0,994 (valeur par défaut EAX) |

`ᾱ_aigus` est la moyenne des absorptions des surfaces touchées, sans les rayons ouverts.

### 3.6 Même espace ou pas
Une enceinte est **dans ton espace** si son chemin direct perd moins de `sameSpaceLossDb` (6 dB par défaut), selon l'occlusion de 4b (perte globale du meilleur chemin). Une enceinte sans occlusion connue est considérée dans ton espace.

## 4. Rendu (client, thread audio)

### 4.1 Emplacements d'effet
Créés avec le pool de sources, détruits et recréés avec lui (rechargement du moteur son) :
- **L** : réverbération de ton lieu (`AL_EFFECT_EAXREVERB`).
- **S1, S2** : réverbération des lieux de 2 enceintes.
- **E** : écho (`AL_EFFECT_ECHO`).

Si EFX manque ou si un emplacement ne peut pas être créé, on s'en passe sans erreur : l'enceinte n'envoie rien vers lui.

### 4.2 Envois par enceinte
- **Envoi 0 → réverbération de son espace** : L si elle est dans ton espace, sinon S1 ou S2 si elle fait partie des 2 plus fortes hors de ton espace (gain entendu, comme pour le choix des sources de 4a), sinon L.
- **Envoi 1 → E.**
- Le signal envoyé est le même PCM que le son direct (égaliseur, filtre d'occlusion, chorus). Le gain de la source et l'atténuation par la distance s'appliquent aussi. Le son réverbéré suit donc l'occlusion sans calcul de plus.
- Chaque envoi a son filtre EFX, dont le gain sert au **fondu** : quand l'envoi 0 change d'emplacement, il descend à 0 en 100 ms, change de cible, puis remonte en 100 ms.
- Le nombre d'envois réellement disponibles est lu (`ALC_MAX_AUXILIARY_SENDS`) : avec 1 seul envoi, pas d'écho ; avec 0, ni réverbération ni écho.

### 4.3 Transitions
Les réglages EAX d'un emplacement glissent vers leur nouvelle cible sur 1 s (interpolation linéaire, 10 pas par seconde au plus). Changer d'emplacement d'enceinte (S1 ↔ S2 ↔ L) passe par le fondu de l'envoi (§4.2).

### 4.4 Tête dans un fluide
Quand `ListenerPerception` indique que la tête est dans un fluide (règle de 4b), L prend le **préréglage du fluide** (§5) au lieu de la mesure. L'écho est coupé. S1 et S2 ne changent pas.

### 4.5 Chorus
- Dans notre traitement, après le filtre d'occlusion de 4b : une **ligne à retard modulée** (retard de base 8 ms, modulé par une sinusoïde de vitesse `chorusRateHz` et de profondeur `chorusDepthMs`, interpolation linéaire). La sortie est 100 % modulée, ce qui donne une ondulation de hauteur et non un doublement.
- Actif quand la tête est dans un fluide dont le préréglage a `chorus: true`. L'entrée et la sortie glissent sur 200 ms (fondu entre signal direct et signal modulé).
- Le chorus ne dépend pas d'EFX.

### 4.6 Désactivation
- Option joueur « Effets acoustiques » (4a) désactivée : ni réverbération, ni écho, ni chorus.
- Section `reverb` désactivée par le serveur : ni réverbération, ni écho, ni chorus.

## 5. Configuration serveur

Dans `config/ravecraft-server.json5`, envoyée à chaque joueur, chaque réglage commenté. Comme en 4b, un fichier ancien ou incomplet est complété et réécrit, l'original gardé en `.bak`.

- `occlusion.materials[*].absorption` (0 à 1) ; `occlusion.media[*].surfaceAbsorption` (0 à 1).
- Section `"reverb"` :

| Clé | Défaut | Bornes |
|---|---|---|
| `enabled` | `true` | |
| `rayLength` | 64 | 16–128 |
| `maxDecaySeconds` | 6 | 0,5–20 |
| `maxWetGain` | 0,32 | 0–1 |
| `sameSpaceLossDb` | 6 | 0–60 |
| `echo.minDistance` | 17 | 10–35 |
| `echo.maxDistance` | 35 | `echo.minDistance`–35 |
| `echo.minRays` | 4 | 1–16 |
| `echo.maxAbsorption` | 0,2 | 0–1 |
| `echo.gain` | 0,6 | 0–1 |
| `echo.feedback` | 0,15 | 0–0,5 |
| `echo.damping` | 0,5 | 0–0,99 |

- Les réglages d'écho sont regroupés dans la sous-section `reverb.echo`.
- Préréglages par fluide, `reverb.media` (liste ouverte, par nom de milieu) :

| Clé | Eau | Lave | Bornes |
|---|---|---|---|
| `decaySeconds` | 1,2 | 0,6 | 0,1–20 |
| `density` | 1 | 1 | 0–1 |
| `diffusion` | 1 | 1 | 0–1 |
| `gain` | 0,4 | 0,3 | 0–1 |
| `decayHfRatio` | 0,3 | 0,2 | 0,1–2 |
| `chorus` | `true` | `false` | |
| `chorusRateHz` | 0,4 | 0,25 | 0,05–5 |
| `chorusDepthMs` | 1,5 | 2 | 0–10 (la profondeur appliquée reste sous le retard de base de 8 ms) |

Un fluide sans préréglage garde la mesure de l'espace.

## 6. Planification (client, fin de tick)

Un `ReverbScheduler` voisin de `OcclusionScheduler`, après lui :
- **Espace de l'auditeur** : mesuré quand l'oreille s'est déplacée d'au moins 1 bloc depuis la dernière mesure, et au moins toutes les 2 s.
- **Espaces S1, S2** : on choisit les 2 enceintes audibles les plus fortes hors de ton espace (§3.6). Un espace est mesuré quand son enceinte y entre, puis toutes les 5 s.
- **Budget** de voxels par tick, distinct de celui de l'occlusion, fixé par l'option « Qualité de l'occlusion » (basse 1 500, moyenne 3 000, haute 6 000). Une mesure en cours garde l'ancienne valeur publiée jusqu'à sa fin.
- Les résultats (`ReverbParams`, écho, affectations S1/S2) sont publiés vers le thread audio par champs volatils, comme l'occlusion.
- Jamais d'exception hors du tick : un échec est journalisé au plus une fois par minute, l'ancienne mesure reste.
- **Écran de debug** : l'entrée `ravecraft:reverb` (désactivée par défaut, à activer dans F3 + F6) affiche la dernière mesure de ton espace (ouverture, libre parcours, absorption, irrégularité), les réglages EAX qui en sortent (décroissance, diffusion, réflexions, gain) et l'écho ; dans un fluide, le préréglage utilisé.
- **Autres entrées de debug du moteur acoustique** (même activation, F3 + F6) :
  - `ravecraft:speakers` : enceintes audibles, combien jouent sur le plafond de sources, et pour les 4 plus fortes la distance, le niveau entendu, l'occlusion (perte et aigus), l'emplacement de réverbération (L, S1, S2) et si la source est coupée ;
  - `ravecraft:listener` : milieu autour de la tête et sa perception (perte, aigus, flou, chorus), état de l'option d'effets, de l'EFX (nombre d'envois) et des réglages serveur d'occlusion et de réverbération ;
  - `ravecraft:streams` : par flux, tampon en ms, sous-alimentations, trames perdues, débit reçu en kb/s, titre et propriétaire du morceau ;
  - `ravecraft:perf` : voxels utilisés au dernier tick contre le budget (occlusion et réverbération), sondes en cours, durée moyenne et maximale d'un pas du fil audio.

## 7. Tests

**JUnit (`core`)**, sur des grilles simulées (`TestGrid` de 4b) :
- Directions des rayons : N directions unitaires, réparties (pas deux trop proches), déterministes.
- Salle fermée 9×5×9 en pierre : ouverture 0, RT60 dans l'intervalle attendu. La même salle en laine : RT60 bien plus court, `DECAY_HFRATIO` plus bas.
- Plein air (sol seul) : ouverture > 0,4, gain de réverbération presque nul.
- Petite salle contre grande salle : délai des réflexions et densité ordonnés.
- Irrégularité : boîte de pierre → 0 ; même salle couverte de bosses → au-dessus de 0,4 ; trop peu d'impacts → 0,5. Salle lisse : diffusion 0,4 et réflexions égales au gain ; salle irrégulière : diffusion 1 et réflexions à la moitié du gain.
- Écho : paroi de pierre à 25 blocs → délai 2·25/343 s ; paroi à 50 blocs → aucun écho ; paroi en laine à 25 blocs → aucun écho.
- Surface d'eau : un rayon s'arrête à la surface avec l'absorption de l'eau.
- Budget : une mesure interrompue puis reprise donne le même résultat qu'une mesure d'un trait.
- `ReverbParams` : toujours dans les bornes EAX, même pour des valeurs extrêmes.
- Chorus : désactivé → signal intact ; actif → retard borné, pas de saut entre deux blocs, entrée et sortie sans clic.
- Choix des emplacements : les 2 plus fortes hors de ton espace, stabilité quand l'ordre ne change pas.
- Configuration : bornes, aller-retour JSON et réseau, fichier ancien complété.

**GameTest** : mesure d'une vraie salle de pierre construite dans le monde, à travers `WorldAcousticGrid` (ouverture 0, RT60 dans l'intervalle attendu).

## 8. Vérification en jeu
- [ ] Salle de pierre : réverbération nette ; même salle en laine : mate.
- [ ] Plein air : son sec.
- [ ] Fête dans une grotte, entendue depuis l'entrée : caverneuse et étouffée.
- [ ] Ravin de 20 à 35 blocs : écho net et discret.
- [ ] Tête sous l'eau : son épais et enfermé ; chorus activé dans la config : léger flottement.
- [ ] Changer de pièce : transition sans saut ni clic.
- [ ] Grande salle lisse contre grotte de même taille : la salle sonne plus dure et plus nette, la grotte plus douce (F3 : irrégularité basse contre haute).
- [ ] 16 enceintes : pas de baisse de FPS perceptible.

## 9. Points à vérifier pendant le plan
- `ALC_MAX_AUXILIARY_SENDS` réellement accordé par le contexte de Minecraft (2 attendus avec OpenAL Soft).
- Création et rattachement des emplacements (`alGenAuxiliaryEffectSlots`, `AL_EFFECTSLOT_EFFECT`) et des filtres d'envoi (`AL_AUXILIARY_SEND_FILTER`) depuis le thread audio du mod.
- `AL_EFFECT_EAXREVERB` disponible ; sinon repli sur `AL_EFFECT_REVERB` (mêmes réglages sans les délais d'écho EAX).
- Réutilisation du parcours de voxels du traceur de 4b, sans dupliquer le DDA.
