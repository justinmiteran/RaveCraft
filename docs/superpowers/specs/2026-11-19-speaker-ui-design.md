# Interface de l'enceinte (étape 5a du socle audio) : spec de conception

> Sous-projet : **Socle audio**, étape 5a, branche `feat/speaker-ui`.
> Spec parente : [2026-11-17-audio-core-design.md](2026-11-17-audio-core-design.md). Prolonge la file partagée de l'étape 3b ([2026-11-17-shared-queue-design.md](2026-11-17-shared-queue-design.md)).
> Maquettes de travail (non versionnées) : `art/proposals/speaker_ui_mc_speaker.png` (concept 2), `speaker_ui_library.png` (bibliothèque et popup).

## 1. Objectif

Remplacer l'écran minimal de l'étape 3b par **l'interface complète de l'enceinte** : un vrai lecteur (barre de progression, précédent, aléatoire, répétition, volume, marche/arrêt), une bibliothèque limitée aux dossiers de musique choisis par le joueur, et une infobulle d'objet qui présente l'enceinte.

L'étape 5 est découpée en deux PR :
- **5a (cette spec)** : infobulle, lecteur complet, nouvel écran, bibliothèque et dossiers de musique.
- **5b (spec à part)** : réordonner la file par glisser-déposer, favoris, option pour couper les notifications, et autres réglages de configuration.

### Critères de réussite
- Survoler l'enceinte dans l'inventaire affiche sa puissance, sa portée et sa directivité ; avec **Maj**, la fiche détaillée.
- La barre de progression avance en continu, affiche le temps écoulé et la durée, et se déplace au clic ou au glisser ; tous les joueurs entendent le saut.
- Précédent, aléatoire et les trois modes de répétition se comportent comme au §4.2, et sont partagés par tous les joueurs de l'enceinte.
- Le volume se règle de 0 à 100 % depuis l'écran ; la portée change pour tout le monde. Le bouton marche/arrêt allume ou éteint l'enceinte.
- La bibliothèque n'affiche que les dossiers choisis par le joueur. « Ajouter un dossier… » ouvre la boîte de sélection de dossier du système, ou le navigateur en jeu si elle ne peut pas s'ouvrir.
- L'écran reprend le style du concept 2 et reste lisible à l'échelle d'interface 2, 3 et 4.

## 2. Décisions de cadrage

| # | Décision | Écarté |
|---|---|---|
| U1 | **Infobulle option A** (§4.1) : trois lignes de base et « Maj : plus d'infos » ; avec Maj, la fiche détaillée | Tout afficher sans Maj ; aucune infobulle |
| U2 | **Lecteur option A** : répétition à trois états (désactivée, toute la file, un morceau), aléatoire sans repasser deux fois par le même morceau dans un tour, précédent « intelligent » | Répétition à deux états ; précédent toujours vers le morceau d'avant |
| U3 | **La file devient une playlist avec curseur** : les morceaux joués restent dans la file. L'écran n'affiche que le morceau en cours et les suivants | File qui se vide au fil de la lecture (3b) |
| U4 | Modes de lecture (répétition, aléatoire) **enregistrés avec l'enceinte et partagés** | Modes propres à chaque joueur |
| U5 | **Durée lue par le PC du propriétaire** et envoyée au serveur au démarrage du morceau ; inconnue = temps écoulé seul | Durée calculée par le serveur ; durée lue à l'ajout |
| U6 | **Déplacement dans le morceau** : un seul ordre au relâchement ; le serveur relance la diffusion du propriétaire à la nouvelle position (coupure d'environ 0,5 à 1 s) | Envoi continu pendant le glisser |
| U7 | **Commandes ouvertes à tous** les joueurs, comme en 3b. Le verrouillage propriétaire/invité viendra plus tard | Seul celui qui a posé l'enceinte commande |
| U8 | **Style concept 2** : fenêtre de conteneur vanilla et bande de tissu teinté pour le lecteur (§5) | Fenêtre brute ; reproduction littérale de l'enceinte |
| U9 | **Sources de musique abstraites** (`MusicSource`), onglets sur le côté de la fenêtre : seule « Mon PC » est active, « Clé USB » et « Streaming » sont affichées verrouillées, « bientôt » | Écran lié aux fichiers locaux |
| U10 | **Bibliothèque = arborescence dépliable des dossiers choisis**, gérés dans une popup « Dossiers… » (liste et ajout) | Navigation libre sur tout le disque (3b) ; dossier fixe façon packs de ressources |
| U11 | **Boîte native** de sélection de dossier (`lwjgl-tinyfd`), **navigateur en jeu en secours** | Boîte native seule ; navigateur en jeu seul |
| U12 | La liste des dossiers choisis est **propre à chaque joueur**, sur son PC (`config/ravecraft-library.json`), jamais envoyée | Liste stockée dans l'enceinte |

## 3. Architecture

| Composant | Où | Rôle |
|---|---|---|
| `SpeakerQueue` (modifié) | `core.queue` | Playlist avec curseur, ordre de lecture, historique, modes. Logique pure, testée en JUnit (§4.2) |
| `PlayMode` (nouveau) | `core.queue` | `RepeatMode { OFF, ALL, ONE }` et `shuffle` (booléen) |
| `QueueEntry` (modifié) | `core.queue` | Gagne `durationMs` (optionnel, renseigné quand le propriétaire l'a lu) |
| `TrackDuration` (nouveau) | `core.audio.decode` | Durée d'un fichier sans le décoder entièrement : en-tête WAV, `STREAMINFO` FLAC, en-tête Xing/VBRI MP3 (sinon estimation au débit constant), dernière position de granule Ogg. `OptionalLong` si inconnue |
| `SpeakerSettings` (inchangé) | `core.speaker` | `powered` et `volume` (0–100) déjà présents |
| `SpeakerPlayback` (modifié) | `main` · `world.audio` | Nouvelles commandes (§4.3), relance au déplacement, enchaînement selon le mode |
| `SpeakerCommandPayload` (modifié) | `network.payload` | Commandes `PREVIOUS`, `SEEK`, `VOLUME`, `POWER`, `REPEAT`, `SHUFFLE` en plus de l'existant, avec une valeur entière |
| `SpeakerStatePayload` (modifié) | `network.payload` | Gagne la durée de chaque entrée, les modes, le volume, l'alimentation et l'index « tour suivant » |
| `StreamStartedPayload` (nouveau) | `network.payload` | C→S, envoyé par le PC du propriétaire quand la diffusion démarre : `sessionId`, `durationMs` (−1 si inconnue). `StreamResultPayload` reste le signal d'échec |
| `MusicSource` (nouveau) | `client` · `client.library` | Interface : `id()`, `available()`, `roots()`, `children(node)`, `search(text)`, `tracksOf(node)`. Implémentation `LocalFolderSource` (« Mon PC ») au-dessus de `MusicLibrary` |
| `LibraryFolders` (nouveau) | logique `core.library`, fichier `client` | Liste ordonnée des dossiers choisis, sans doublon ni dossier inclus dans un autre, plafonnée à 32. Lue et écrite en JSON dans `config/ravecraft-library.json` |
| `FolderPicker` (nouveau) | `client.library` | Ouvre `tinyfd_selectFolderDialog` sur un fil à part, rend le résultat au fil client. Signale l'échec pour que l'écran bascule sur le navigateur en jeu |
| `SpeakerScreen` (remplace `SpeakerBrowserScreen`) | `client.screen` | Fenêtre du concept 2 : lecteur, onglets de source, bibliothèque, file (§5) |
| `LibraryTree` (nouveau widget) | `client.screen` | Arborescence dépliable, chargée paresseusement hors du fil de rendu |
| `FoldersPopup` (nouveau) | `client.screen` | Popup P2 : dossiers choisis, ajout par boîte native, navigateur en jeu de secours |
| `SpeakerTooltip` (modifié) | `client` | Lignes de base et détail avec Maj (§4.1) |

`MusicLibrary` reste dans `core.library` ; `roots()` ne sert plus qu'au navigateur de secours.

### Dépendance ajoutée
`org.lwjgl:lwjgl-tinyfd:3.4.3`, version **identique** au LWJGL de Minecraft 26.3, avec ses natifs Windows, macOS et Linux, embarqués dans le jar par `include`. La version est figée à côté de celle de Minecraft dans `gradle.properties`, avec un commentaire qui rappelle de la suivre à chaque montée de version.

## 4. Comportements

### 4.1 Infobulle

Sans Maj :
```
Enceinte portable
20 W · portée 32 blocs · omnidirectionnelle
3 morceaux en file
Maj : plus d'infos                         (gris foncé)
```
Avec Maj :
```
Enceinte portable
20 W · omnidirectionnelle
Sensibilité : 85 dB
Basses : −6 dB sous 150 Hz
Volume enregistré : 70 % → portée 32 blocs
En cours : Night Drive                     (si un morceau est en cours)
3 morceaux en file
```
- La portée est calculée par `Propagation.range` avec les réglages acoustiques reçus du serveur, comme aujourd'hui.
- Sans Maj, la portée est celle du volume enregistré. La ligne de la file n'apparaît que si la file n'est pas vide.
- Toutes les chaînes sont traduites (`en_us`, `fr_fr`). Les unités suivent la langue.

### 4.2 Lecture

**File et curseur.** La file garde tous ses morceaux et un curseur sur le morceau en cours.
- **Fin naturelle d'un morceau**, ou **Suivant** :
  - répétition `ONE`, fin naturelle : le même morceau repart du début. Avec **Suivant**, on avance comme en `ALL` ;
  - répétition `ALL` : on passe au morceau suivant ; après le dernier, on repart au premier (nouveau tour) ;
  - répétition `OFF` : on passe au suivant ; après le dernier, la lecture s'arrête en pause, le curseur reste sur le dernier morceau, position 0.
- **Aléatoire** : le suivant est tiré parmi les morceaux **pas encore joués dans le tour**. Quand tous l'ont été, un nouveau tour commence (`ALL`) ou la lecture s'arrête (`OFF`). Activer l'aléatoire ne change pas le morceau en cours.
- **Précédent** : si le morceau joue depuis plus de 3 s, il repart du début. Sinon, on revient au morceau joué juste avant, d'après un **historique** de 50 morceaux au plus. L'historique est tenu par le serveur, en mémoire seulement. S'il est vide, le morceau repart du début.
- Les morceaux d'un **propriétaire absent** sont sautés mais conservés, comme en 3b. Si aucun morceau n'est jouable, la lecture se met en pause.
- Un fichier **illisible** est retiré, comme en 3b.
- **Retirer** le morceau en cours passe au suivant selon le mode. **Vider** retire tout ; les modes ne changent pas.
- **Ajouter** place les morceaux en fin de file. Dans le tour en cours, ils comptent comme pas encore joués.

**Affichage de la file.** L'écran montre le morceau en cours, puis les suivants dans l'ordre de lecture.
- En aléatoire, cet ordre est l'ordre déjà tiré pour le reste du tour. Le serveur tire l'ordre du tour au début de celui-ci, et le refait quand on ajoute des morceaux.
- En `ALL`, les morceaux déjà joués réapparaissent après une séparation **« Tour suivant »**.
- Les morceaux joués avant le morceau en cours ne sont pas affichés.

**Position et durée.**
- Le serveur estime la position comme en 3b (`PlaybackClock`). Il l'envoie dans `SpeakerState` ; l'écran l'interpole entre deux états.
- Au démarrage d'un morceau, le PC du propriétaire lit la durée (`TrackDuration`) et l'envoie par `StreamStarted`. Le serveur l'enregistre sur l'entrée, qui la garde ensuite.
- Durée inconnue : la barre affiche seulement le temps écoulé et ne permet pas le déplacement.

**Déplacement.** Le clic ou le glisser sur la barre ne déplace que l'affichage local. Au relâchement, l'écran envoie `SEEK` avec la position en millisecondes.
- Le serveur borne la position à `[0, durée]`, ferme la session en cours et rouvre une diffusion depuis le PC du propriétaire à cette position, comme une reprise après pause.
- En pause, il mémorise seulement la nouvelle position.

**Volume et alimentation.**
- Curseur de 0 à 100 %, boutons − et + par pas de 5 %. Pendant le glisser, l'écran envoie au plus 4 `VOLUME` par seconde, plus un dernier au relâchement.
- Le serveur borne la valeur et l'enregistre dans `SpeakerSettings.volume`, qui suit l'objet. La portée change pour tout le monde.
- `POWER` bascule `SpeakerSettings.powered`, avec le même effet que l'interaction existante sur le bloc.

### 4.3 Commandes réseau

`SpeakerCommandPayload(pos, command, entryId, value)` : `value` est un `VAR_LONG` ignoré par les commandes qui n'en ont pas besoin.

| Commande | Valeur | Contrôle serveur |
|---|---|---|
| `PLAY`, `PAUSE`, `NEXT`, `CLEAR` | — | Inchangé |
| `REMOVE` | — (`entryId`) | Inchangé |
| `PREVIOUS` | — | — |
| `SEEK` | position en ms | Bornée à `[0, durée]` ; refusée si la durée est inconnue |
| `VOLUME` | 0–100 | Bornée ; au plus 8 par seconde et par joueur, le reste est ignoré |
| `POWER` | 0 ou 1 | — |
| `REPEAT` | 0, 1, 2 | Valeur hors plage ignorée |
| `SHUFFLE` | 0 ou 1 | — |

Toutes les commandes vérifient, comme en 3b, que le joueur est à portée d'interaction et que le bloc est une enceinte.

`SpeakerStatePayload` gagne : `repeat`, `shuffle`, `volume`, `powered`, `durationMs` par entrée (`VAR_LONG`, −1 si inconnue) et `nextRoundIndex` (−1 sans séparation). Les entrées sont envoyées **dans l'ordre d'affichage** (en cours, suivants, puis « Tour suivant »), toujours plafonnées à 200.

### 4.4 Persistance

`SpeakerData.queue` gagne, en champs **optionnels** pour garder les mondes existants : `repeat` (défaut `ALL`), `shuffle` (défaut `false`), l'ordre du tour et les morceaux déjà joués dans le tour, et `durationMs` sur chaque entrée. L'historique de Précédent n'est pas persisté.

### 4.5 Bibliothèque et dossiers

- **Onglets de source** sur le bord gauche de la fenêtre : « Mon PC » (actif), « Clé USB » et « Streaming » (verrouillés, infobulle « Bientôt »). Les onglets viennent de la liste des `MusicSource` ; une source non disponible est verrouillée.
- **Arborescence** : les racines sont les dossiers choisis, dans l'ordre de la liste. Déplier un dossier liste ses sous-dossiers, playlists et morceaux (`MusicLibrary.list`) **hors du fil de rendu**. Une ligne « Chargement… » s'affiche en attendant. Plafond de 2 000 lignes par dossier, comme aujourd'hui.
- Chaque ligne affiche son icône de type, son nom et, pour un dossier, son nombre de morceaux. Le bouton **+** ajoute un morceau, une playlist ou les morceaux directs d'un dossier.
- La durée d'un morceau n'est pas lue dans la bibliothèque en 5a : seule la file l'affiche, une fois le morceau démarré.
- **Recherche** : filtre par nom les lignes déjà chargées de l'arbre (pas de parcours du disque).
- **Aucun dossier choisi** : l'arbre affiche un message et un bouton « Choisir un dossier… ». Au tout premier lancement, la liste est préremplie avec le dossier `Musique` du joueur s'il existe.
- **Dossier disparu** (clé débranchée, dossier renommé) : il reste dans la liste, grisé, avec l'infobulle « Introuvable ».

**Popup « Dossiers… »**
- La liste des dossiers choisis : chemin, nombre de morceaux, bouton ✕ pour retirer.
- **« Ajouter un dossier… »** ouvre la boîte native sur un fil à part. Pendant ce temps, l'écran affiche « Choisis un dossier dans la fenêtre du système… » et un bouton « Annuler ».
- Le dossier choisi est ajouté. S'il est déjà présent, ou contenu dans un dossier déjà choisi, un message le signale et rien ne change. S'il contient un dossier déjà choisi, il le remplace.
- **Secours** : si `tinyfd` ne peut pas se charger, lève une erreur, ou si l'appel ne répond pas, la popup affiche le **navigateur en jeu**. C'est un navigateur de dossiers seulement : disques, dossier personnel, « .. » et « Ajouter ce dossier ». Un bouton « Navigateur intégré » permet aussi de l'ouvrir volontairement.
- Sur macOS, l'appel natif passe par le fil principal du jeu. Si cela gèle l'écran, le secours est utilisé d'office. À vérifier pendant l'essai de la tâche 1 du plan.

## 5. Écran

Fenêtre de conteneur vanilla : gris `#C6C6C6`, contour noir aux coins coupés, biseaux clair et sombre, zones enfoncées façon emplacements, boutons vanilla. Les textures de l'écran sont générées par `gen_textures.py`.

1. **Titre** : nom de l'enceinte (nom personnalisé de l'objet sinon nom du modèle).
2. **Bande de tissu** teinte avec la couleur de l'enceinte (la même trame que la texture du bloc, teinte par `SpeakerFabricTint`) :
   - le logo égaliseur à 4 barres en cuivre, animé par le niveau sonore du morceau quand on l'entend, et immobile sinon ;
   - le titre, et « ajouté par <joueur> · <source> » ;
   - le temps écoulé en cuivre clair, la barre de progression en cuivre, la durée ;
   - la LED verte, allumée quand l'enceinte est sous tension.
3. **Commandes** : aléatoire, précédent, lecture/pause (en cuivre), suivant, répétition (icône différente pour les trois états) ; puis volume −, curseur, + ; puis marche/arrêt, icône verte si allumée.
4. **Bibliothèque** à gauche (§4.5), avec les onglets de source sur le bord de la fenêtre, et le bouton « Dossiers… » en dessous.
5. **File** à droite : la ligne en cours en jaune, « ajouté par », la croix ✕ par ligne, propriétaires absents en gris, la séparation « Tour suivant », et le bouton « Vider » en dessous.

Chaque modèle d'enceinte aura sa disposition, qui rappelle son modèle sans le reproduire. En 5a, seule l'enceinte portable existe ; le reste de l'écran est commun. Les icônes et l'agencement final sont montrés en rendu (`runClient`) à l'utilisateur avant la fin de la PR.

## 6. Tests

**JUnit (`core`)**
- `SpeakerQueue` :
  - enchaînement dans les trois modes de répétition, fin de file en `OFF` ;
  - aléatoire sans répétition dans un tour, ajout en cours de tour ;
  - Précédent avant et après 3 s, historique plafonné à 50 ;
  - saut des absents ; retrait du morceau en cours ; ordre d'affichage et `nextRoundIndex`.
- `TrackDuration` : petits fichiers WAV, FLAC, MP3 (avec et sans en-tête Xing) et Ogg créés dans le test ; fichier tronqué = inconnu.
- `LibraryFolders` : doublons, dossier inclus ou englobant, plafond de 32, lecture d'un JSON abîmé, qui donne une liste vide.
- `SpeakerData` : chargement d'une file 3b sans les nouveaux champs.

**GameTests**
- `SEEK`, `VOLUME`, `POWER`, `REPEAT`, `SHUFFLE` modifient l'état de l'enceinte ; valeurs hors plage bornées ou ignorées.
- La fin d'un morceau en `OFF` sur le dernier morceau met en pause.

**En jeu** (checklist de la PR) : infobulle avec et sans Maj ; déplacement dans un morceau entendu par un deuxième joueur ; volume qui change la portée ; boîte native sous Windows, et secours forcé ; échelles d'interface 2 à 4.

## 7. Hors périmètre (5b et plus tard)

Réordonner la file, favoris, option pour couper les toasts, autres réglages de configuration (étape 5b). Lecture des tags (artiste, pochette), durées dans la bibliothèque, sources Clé USB et Streaming, verrouillage propriétaire/invité (plus tard).
