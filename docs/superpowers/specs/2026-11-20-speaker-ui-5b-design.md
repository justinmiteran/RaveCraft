# Étape 5b — file réordonnable, notifications, tags et infobulles

Suite de l'étape 5a ([2026-11-19-speaker-ui-design.md](2026-11-19-speaker-ui-design.md)) dans le socle audio ([SPEC](../../SPEC.md), étape 5/6).

## 1. Objectif

Rendre l'enceinte plus agréable pendant une session à plusieurs : on réorganise la file à la souris, on sait ce qu'on entend sans ouvrir l'écran, et on voit les informations d'un morceau avant de l'ajouter.

### Découpage

Une spec, trois PR successives, chacune testable seule :

| PR | Branche | Contenu |
|---|---|---|
| 1 | `feat/queue-reorder` | Glisser-déposer dans la file (cette spec voyage avec elle) |
| 2 | `feat/speaker-notifications` | Toast « En lecture » de l'enceinte la plus forte, option *Notifications RaveCraft* |
| 3 | `feat/track-info` | Lecture des tags texte, infobulle des morceaux de la bibliothèque, titre partagé « Artiste — Titre », infobulle de la barre de progression |

### Critères de réussite

- On glisse un morceau « à venir » ailleurs dans la file ; tous les joueurs voient le nouvel ordre et la lecture le suit, aléatoire compris.
- Quand un morceau démarre sur l'enceinte qu'on entend le plus fort, un toast l'annonce ; l'option permet de n'avoir que les erreurs, ou rien.
- Survoler un morceau de la bibliothèque montre son titre et sa durée ; avec **Maj**, son chemin et ses métadonnées.
- Survoler la barre de progression montre le temps à l'endroit visé.

## 2. Décisions de cadrage

| # | Décision | Écarté |
|---|---|---|
| D1 | Tout le monde déplace n'importe quel morceau (comme pour le retrait) | Seulement ses propres morceaux |
| D2 | On ne glisse que dans « à venir » ; le morceau en cours reste en haut | Déplacer aussi le morceau en cours ; boutons ↑ ↓ |
| D3 | Le glisser est générique dans `RowList` ; la ligne dit si elle se glisse | Glisser codé dans `QueuePanel` seul |
| D4 | Toast seulement pour l'enceinte entendue le plus fort | Un toast par enceinte audible |
| D5 | Toast au démarrage d'un morceau, et quand une autre enceinte devient la plus forte depuis 3 s | Démarrage seulement |
| D6 | Tags texte lus par le mod, en Java pur (pas de bibliothèque externe) | Infos de fichier seulement ; bibliothèque de tags |
| D7 | « Artiste — Titre » dans l'infobulle et dans la file partagée ; la ligne de la bibliothèque garde le nom de fichier | Partout (indexation bien plus lente) ; infobulle seulement |
| D8 | Pas de favoris, pas d'infobulle sur les lignes de la file, boutons du lecteur inchangés | — |

## 3. PR 1 — Réordonner la file

### Comportement

- Dans la file, on appuie sur un morceau « à venir » ou « déjà joué » (sous le séparateur « Tour suivant ») et on le fait glisser. Le morceau en cours et le séparateur ne se glissent pas.
- Pendant le glisser, une ligne d'insertion de 1 px (couleur `CURRENT`) montre où il tombera ; la ligne glissée est estompée à sa place d'origine. Approcher du haut ou du bas de la liste la fait défiler (une ligne tous les 2 ticks).
- On ne peut déposer que dans « à venir » : entre le morceau en cours et le séparateur (ou la fin de la liste sans séparateur). Plus bas, la ligne d'insertion se colle à la dernière position valide.
- Relâcher envoie le déplacement ; relâcher à sa place d'origine, ou hors de la liste, n'envoie rien. Un appui sans mouvement de plus de 3 px reste un clic : le ✕ et le reste se comportent comme aujourd'hui.
- Un morceau déjà joué déposé dans « à venir » sera rejoué dans ce tour.

### Règles de la file (`core`)

`SpeakerQueue.move(UUID entryId, int index)` : place `entryId` en position `index` de « à venir » (0 = juste après le morceau en cours), et renvoie une nouvelle file.

- Sans aléatoire : l'ordre de lecture est l'ordre de la liste ; le morceau est retiré de la liste et réinséré juste après l'élément « à venir » qui le précédera.
- Avec aléatoire : « à venir » est la liste `pending` du tour ; le morceau y est inséré à `index` (retiré de sa place s'il y était). L'ordre de `entries` ne change pas.
- `index` est borné à `[0, nombre d'à venir après retrait]`. Un id inconnu, ou le morceau en cours, laisse la file inchangée.
- `displayOrder()` et `nextRoundIndex()` restent la vérité de l'affichage : après `move`, l'élément est à `1 + index` dans `displayOrder()`.

### Réseau

Nouvelle commande `MOVE` dans `SpeakerCommandPayload`, qui réutilise ses champs existants : `entryId` pour le morceau, `value` pour l'index (borné à `0..MAX_ENTRIES` par le serveur). Le format du paquet ne change pas. Le serveur applique `move`, puis renvoie l'état comme pour les autres commandes. Elle est refusée hors de portée de l'enceinte, comme les autres.

### Écran

- `ListRow` gagne `draggable()` (faux par défaut).
- `RowList` gère l'appui, le seuil de 3 px, la ligne d'insertion, le défilement au bord et le relâchement ; il appelle `onMove(int from, int to)`, à redéfinir par la liste, avec des index de ligne. Il demande à la liste la plage de dépôt valide (`dropRange()`).
- `QueuePanel` déclare ses lignes « à venir » et « déjà joué » glissables, donne la plage « à venir », et traduit `onMove` en commande `MOVE`.

## 4. PR 2 — Notifications

### Toast « En lecture »

- Le serveur dit à chaque joueur ce que joue une session, une seule fois par session et par joueur (`NowPlayingPayload` : session, enceinte, morceau, titre, nom de qui l'a ajouté) : au joueur qui diffuse quand elle démarre, et à chaque auditeur juste avant son premier paquet de son, donc aussi à celui qui arrive en cours de morceau. Sans lui, un auditeur ne connaît ni le titre ni le nom : la file de l'enceinte ne va qu'aux joueurs qui ont son écran ouvert.
- Le client choisit chaque seconde l'enceinte **audible la plus forte** (gain calculé par le rendu acoustique, occlusion comprise). Elle devient la « source suivie » si elle est la plus forte depuis 3 s (la première fois : tout de suite).
- Un toast apparaît quand la source suivie démarre un morceau, et quand la source suivie change et qu'elle joue. Le même morceau sur la même enceinte n'est jamais annoncé deux fois de suite.
- Contenu : « En lecture » ; en dessous, le titre (« Artiste — Titre » après la PR 3) et « ajouté par X ». Toast système vanilla, une seule instance remplacée si un nouveau arrive.
- Pas de toast tant que l'écran de cette enceinte est ouvert : on y voit déjà le morceau. Les morceaux qu'on diffuse soi-même sont annoncés comme les autres.

### Option

*Notifications RaveCraft* dans Options › Musique et sons, enregistrée avec les autres options du mod : **toutes** (par défaut), **erreurs seulement** (le toast « morceau retiré » existant), **aucune**.

## 5. PR 3 — Tags et infobulles

### Lecture des tags (`core`, Java pur)

`TrackInfo` : titre, artiste, album, année, genre (chacun facultatif), plus format, débit (kb/s), fréquence (Hz), canaux et durée. Lecteurs :

| Format | Tags lus | Infos techniques |
|---|---|---|
| MP3 | ID3v2.3/2.4 (`TIT2`, `TPE1`, `TALB`, `TDRC`/`TYER`, `TCON`), sinon ID3v1 | 1re trame MPEG |
| FLAC | bloc `VORBIS_COMMENT` | `STREAMINFO` |
| Ogg | commentaires Vorbis (ou Opus) | en-tête d'identification |
| WAV | chunk `LIST`/`INFO` (`INAM`, `IART`, `IPRD`, `ICRD`, `IGNR`) | chunk `fmt ` |

- Lecture limitée au début du fichier (et aux 128 derniers octets pour ID3v1), au plus 1 Mo ; un tag mal formé est ignoré, jamais une exception qui remonte. Textes coupés à 128 caractères. Encodages ID3 : ISO-8859-1, UTF-16 avec BOM, UTF-16BE, UTF-8.
- La durée reprend `TrackDuration` existant.
- Testé sur de petits fichiers construits par les tests (octets écrits à la main), un par format, plus les cas cassés.

### Cache côté client

Les infos sont lues au premier survol, sur le thread d'arrière-plan des listes (`OffThread`), et gardées en mémoire par chemin et date de modification (512 entrées au plus, la plus ancienne sortant). En attendant, l'infobulle affiche « … ».

### Infobulle d'un morceau de la bibliothèque

- Sans Maj : « Artiste — Titre » (ou le nom du fichier sans extension s'il n'y a pas les deux tags), puis la durée (« 3:40 »), puis en gris « Maj pour plus d'infos ».
- Avec Maj : le chemin complet, puis album, année, genre (lignes absentes si vides), puis « FLAC · 1 411 kb/s · 44,1 kHz · stéréo ».
- Les playlists et les dossiers gardent leur infobulle de 5a.

### Titre partagé

Quand on ajoute un morceau à la file, le titre envoyé au serveur devient « Artiste — Titre » s'il a ces deux tags, sinon le nom du fichier comme aujourd'hui. Les infos sont lues au moment de l'ajout, en arrière-plan, avant l'envoi (au plus 200 morceaux d'un coup, déjà la limite de la file).

### Barre de progression

Au survol de la barre, une infobulle « 2:14 / 3:40 » donne le temps à l'endroit visé ; pendant un glisser, le temps de la poignée. Rien si la durée est inconnue.

## 6. Erreurs et limites

- Une commande `MOVE` sur un morceau retiré entre-temps ne fait rien (id inconnu).
- Un fichier illisible au survol donne une infobulle réduite au nom du fichier, sans durée.
- Le choix de la source suivie et des toasts se fait chez le joueur. Sur le réseau, seuls s'ajoutent `MOVE` et `NowPlayingPayload` (une fois par session et par joueur).

## 7. Tests

- JUnit : `SpeakerQueue.move` (sans et avec aléatoire, répétition tout, déjà joué ramené, bornes, id inconnu, morceau en cours) ; lecteurs de tags par format et cas cassés ; choix de la source suivie (règle des 3 s, pas de double annonce) dans une classe `core` testable.
- GameTest : la commande `MOVE` réordonne la file d'une enceinte posée.
- En jeu : glisser dans une longue file avec défilement, toasts en passant d'une enceinte à l'autre, infobulles sur MP3, FLAC, Ogg et WAV avec et sans tags.

## 8. Hors périmètre

Favoris, pochette, tags dans les lignes de la bibliothèque, infobulles de la file, verrouillage propriétaire/invité.
