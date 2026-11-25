# Direction du son calculée — spec de conception

Première des quatre PR de la tranche « Enceintes reliées » ([SPEC](../../SPEC.md), gamme d'enceintes et amplification). Elle remplace la directivité de la spec du moteur acoustique ([2026-11-17-acoustic-engine-design.md](2026-11-17-acoustic-engine-design.md), § 3.1) et celle de l'enceinte de soirée ([2026-11-21-party-speaker-design.md](2026-11-21-party-speaker-design.md), D5 et § 4.2).

## 1. Objectif

On décrit une enceinte par sa caisse et ses haut-parleurs, chacun tourné dans une direction. Le moteur en calcule la façon dont le son part dans l'espace, séparément pour les basses, les médiums et les aigus. Aucun cône et aucune perte à l'arrière ne sont écrits à la main.

### Critères de réussite

- Derrière l'enceinte de soirée, les basses restent presque entières, les voix baissent nettement et les aigus disparaissent.
- En tournant autour de la portable, le son ne change pas : ses haut-parleurs sont tournés tout autour.
- Dans l'axe de chaque enceinte, rien ne change : même niveau, même timbre, même portée qu'avant.
- Le calcul accepte des haut-parleurs à des endroits différents : les groupes d'enceintes et les murs (PR suivantes) utiliseront le même.

### Découpage de la tranche

| PR | Branche | Contenu |
|---|---|---|
| 1 | `feat/speaker-radiation` | Cette spec, appliquée à la portable et à l'enceinte de soirée |
| 2 | `feat/linked-speakers` | Lecteur, tête, caisson, pied, câble simple, mono ; enceintes collées rendues comme un seul groupe |
| 3 | `feat/cable-laying` | Touret, câble déroulé en marchant et fixé d'un clic aux murs et au plafond |
| 4 | `feat/stereo` | Sorties gauche et droite, bouton mono/stéréo |

Les PR 2 à 4 auront une spec commune.

## 2. Décisions

| # | Décision | Écarté |
|---|---|---|
| D1 | Calcul physique : haut-parleur (piston) monté sur une sphère de même volume que la caisse, formule exacte en champ lointain | Règle simplifiée « plus c'est aigu, plus c'est dirigé » ; cône d'OpenAL (angles fixés à la main) |
| D2 | Appliqué à toutes les enceintes, existantes comprises | Seulement aux nouvelles |
| D3 | La portable a des haut-parleurs tournés tout autour : elle est omnidirectionnelle par calcul | Cas particulier « omni » |
| D4 | Trois bandes : basses, médiums, aigus | Niveau unique |
| D5 | Les filtres sont ceux du mod, sur le PCM, comme l'étouffement | Filtre passe-bande EFX (ne sait que couper, dépend du périphérique) |
| D6 | La caisse est une donnée du code, par type d'enceinte | Réglage dans `server.json5` |

## 3. Description d'une enceinte

- **`Driver`** (record) : azimut et élévation (degrés, 0 = axe de l'enceinte), position dans la caisse (mètres : droite, haut, avant), rayon (mètres), fréquences basse et haute de son filtre (0 = aucune).
- **`Cabinet`** (record) : largeur, hauteur, profondeur (mètres, 1 bloc = 1 m) et liste de `Driver`. Rayon de la sphère équivalente : `b = (3·L·H·P / 4π)^(1/3)`.
- **`Cabinets.of(SpeakerKind)`** : la caisse de chaque type.

| Enceinte | Caisse (L × H × P) | Haut-parleurs |
|---|---|---|
| Portable | 0,875 × 0,5625 × 0,5625 | 4 de rayon 4 cm sous 2 500 Hz et 4 de rayon 1,25 cm au-dessus, à 0°, 90°, 180° et 270°, position au centre |
| Soirée | 0,75 × 1 × 0,62 | 1 de rayon 13 cm sous 1 800 Hz (0,25 m sous le centre) et 1 de rayon 2,5 cm au-dessus (0,25 m au-dessus du centre), vers l'avant |

`DeviceProfile` perd sa `Directivity` et garde puissance, sensibilité et coupure de basses. Le timbre dans l'axe reste donné par la coupure de basses.

## 4. Calcul

### 4.1 Un haut-parleur

Piston de rayon `a` sur une sphère de rayon `b`, à la fréquence `f` (`k = 2πf / 343`, `z = kb`, `θ` l'angle avec son axe) :

```
p(θ) = Σ m=0..M  (−i)^m · U_m / h'_m(z) · P_m(cos θ)
U_0  = ½ (1 − P_1(cos θ0))      U_m = ½ (P_{m−1}(cos θ0) − P_{m+1}(cos θ0))
θ0   = asin(min(1, a / b))      M   = ⌊z + 4·z^(1/3) + 10⌋
```

`h_m` est la fonction de Hankel sphérique de première espèce et `P_m` le polynôme de Legendre, tous deux par récurrence montante. `p` est divisé par `|p(0)|` : chaque haut-parleur vaut 1 dans son axe.

### 4.2 Une enceinte

Dans la direction `u`, les haut-parleurs s'ajoutent en **pression**, avec leur phase :

```
P(u, f) = Σ w(f) · p(angle entre l'axe du haut-parleur et u) · e^(−i·k·(position · u))
w(f)    = (f/bas)^4 / (1 + (f/bas)^4)  ×  1 / (1 + (f/haut)^4)      (terme absent si la fréquence vaut 0)
```

- **Bandes** : basses 63–200 Hz, médiums 500–2 000 Hz, aigus 4 000–12 000 Hz ; moyenne de `|P|²` sur 9 fréquences espacées géométriquement, bornes comprises.
- **Lissage** : chaque direction est la moyenne de 7 points, son centre et 6 points à 10° autour. Cela gomme les pics d'une sphère parfaite et les interférences étroites, qu'on n'entend pas en vrai.
- **Normalisation** : chaque bande vaut 0 dB dans l'axe.
- **Grille** : azimut et élévation tous les 10°, interpolation linéaire des puissances entre les points. Plancher à −48 dB.

### 4.3 Valeurs obtenues

Calcul jetable de ces formules en Python, avec les caisses du § 3, en dB dans le plan horizontal. Enceinte de soirée :

| Bande | 0° | 30° | 60° | 90° | 120° | 150° | 180° |
|---|---|---|---|---|---|---|---|
| Basses | 0 | −0,3 | −1,2 | −2,6 | −3,5 | −2,8 | −2,2 |
| Médiums | 0 | −1,3 | −4,0 | −7,6 | −11,9 | −15,8 | −11,8 |
| Aigus | 0 | −2,9 | −7,6 | −12,6 | −20,8 | −29,5 | −27,5 |

Portable, sur tout le tour : basses entre −0,1 et 0 dB, médiums entre −1,6 et +1,1 dB, aigus entre −0,5 et +1,1 dB.

Limites connues : la caisse est une sphère et non une boîte ; la formule vaut loin de l'enceinte ; un pavillon n'est représenté que par la taille de son haut-parleur.

## 5. Rendu

### 5.1 `core.acoustics.radiation` (Java pur)

- **`PistonOnSphere`** : § 4.1.
- **`RadiationPattern`** : § 4.2. `at(azimut, élévation)` et `around()` renvoient un **`BandGains`** (basses, médiums, aigus, linéaires). `around()` est la moyenne en puissance sur l'azimut, à élévation nulle.
- **`RadiationFilter`** : applique un `BandGains` au PCM mono. Deux plateaux RBJ (coins 315 Hz et 2 800 Hz) et un gain, ramenés au plus fort des trois pour ne jamais dépasser le niveau d'origine ; le plus fort est rendu par le gain de la source. Les gains glissent par pas de 1 ms, sans clic, comme `OcclusionFilter`. Avec trois gains égaux, le signal passe intact.
- **`Directivity` est supprimée**, ainsi que les méthodes de `Propagation` et `SpeakerAxis` qui l'utilisent. `Propagation.gain` prend un gain de direction.

### 5.2 Direction entendue

`SpeakerAxis` garde ses règles (bloc : `facing` ; main et inventaire : regard ; dos : derrière le porteur) et donne l'azimut et l'élévation de l'auditeur vus de l'enceinte. Une enceinte au sol, sans axe, prend `around()` : l'objet tourne sur lui-même.

À moins de 1 bloc de l'enceinte, la direction n'a plus de sens : les gains passent progressivement à 1 entre 1 bloc et 0. Le porteur entend donc presque entièrement son enceinte, où qu'elle pointe.

### 5.3 Client

- `RenderStream` porte le type de l'enceinte. `AudioWorker` calcule les `BandGains` à chaque pas, met le plus fort dans le gain de la source et donne les écarts au `RadiationFilter`.
- Le filtre agit sur les blocs mis en file : un changement de timbre s'entend en 400 ms au plus, comme l'étouffement.
- Option « Effets acoustiques » désactivée : pas de filtre, le gain de direction est celui des médiums.
- Les motifs sont calculés une fois, au démarrage du client, hors du thread de rendu. Une enceinte entendue avant la fin du calcul joue sans direction. Le serveur n'en a pas besoin : le rayon de relais ne dépend pas de la direction.
- La réverbération suit le son entendu, comme aujourd'hui.

### 5.4 Réseau, infobulle

- `AcousticSettingsPayload` perd les deux valeurs de directivité. Le fichier `server.json5` ne les contenait pas : il ne change pas. Le client et le serveur doivent avoir la même version du mod, comme aujourd'hui.
- **Infobulle** : « omnidirectionnelle » si les médiums perdent moins de 3 dB sur tout le tour ; sinon « direction avant » (enceinte de soirée).

## 6. Tests

**JUnit**
- `PistonOnSphereTest` : 1 dans l'axe ; à basse fréquence (`kb` très petit) presque égal partout (moins de 0,2 dB) ; plus dirigé quand la fréquence monte ; aucune valeur non finie de 20 Hz à 20 kHz, pour `b` de 0,05 à 2 m.
- `RadiationPatternTest` : soirée, tableau du § 4.3 à 0,5 dB près (1,5 dB sous −20 dB) ; portable à ±3 dB sur tout le tour dans les trois bandes ; 0 dB dans l'axe ; quatre caissons côte à côte dirigent plus les basses vers l'avant qu'un seul ; interpolation continue au passage de 360° à 0° et aux pôles ; plancher respecté.
- `RadiationFilterTest` : sinus de 60, 1 000 et 8 000 Hz aux niveaux demandés (±1 dB) ; gains égaux, signal identique ; sortie dans [−1, 1] ; pas de saut à la jointure de deux blocs ni pendant un glissement.
- `SpeakerAxisTest` : azimut et élévation pour un bloc, un porteur et le dos ; près de l'enceinte, gains à 1.
- `PropagationTest`, `AcousticSettingsTest`, `AcousticSettingsPayloadTest` : adaptés. `DirectivityTest` est supprimé avec sa classe.

**En jeu**
- Tourner autour de l'enceinte de soirée : basses présentes derrière, voix et aigus faibles ; devant, comme avant.
- Tourner autour de la portable : pas de différence.
- Enceinte de soirée sur le dos : le porteur l'entend bien, un autre joueur placé derrière lui l'entend mieux que devant.
- Passer vite derrière l'enceinte, la casser et la reposer pendant la lecture : pas de clic.
- « Effets acoustiques » désactivé : le niveau change encore avec la direction, pas le timbre.

## 7. Hors périmètre

- Nouveaux modèles, groupes, câbles, stéréo (PR 2 à 4).
- Réverbération alimentée par la puissance totale de l'enceinte.
- Ombre acoustique du porteur, réflexion sur le sol et le mur proche.
