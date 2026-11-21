# Étape 6 — enceinte mobile et enceinte de soirée

Dernière étape du socle audio ([SPEC](../../SPEC.md), étape 6/6), avant la release `v0.1.0`. Elle s'appuie sur la spec du socle ([2026-11-17-audio-core-design.md](2026-11-17-audio-core-design.md)), celle du moteur acoustique ([2026-11-17-acoustic-engine-design.md](2026-11-17-acoustic-engine-design.md)) et celle de l'interface ([2026-11-19-speaker-ui-design.md](2026-11-19-speaker-ui-design.md)).

## 1. Objectif

Avoir sa musique avec soi : une enceinte joue quand on la tient, quand elle est dans l'inventaire ou quand on la porte sur le dos, et une deuxième enceinte, plus puissante, porte le son d'une vraie soirée en plein air.

### Constat de départ

La spec du socle prévoyait qu'une enceinte cassée « continue de jouer », mais ce n'est pas livré : tout le système audio repère l'enceinte par la **position de son bloc** (`SpeakerKey` = dimension + position), soit environ 28 fichiers (sessions, relais, payloads, rendu client, interface). Un clic droit dans le vide avec l'objet ne fait qu'allumer ou éteindre (comportement provisoire de `PortableSpeakerItem`).

### Découpage

Une spec, deux PR successives, chacune testable seule :

| PR | Branche | Contenu |
|---|---|---|
| 6a | `feat/carried-speaker` | Enceinte mobile : repérage par identifiant, son qui suit le porteur ou l'objet au sol, interface depuis l'objet (clic droit dans le vide, touche *Ouvrir l'enceinte*), ligne de touche dans l'infobulle. Avec l'enceinte portable. (Cette spec voyage avec elle.) |
| 6b | `feat/party-speaker` | Enceinte de soirée : nouveau bloc et objet, profil par appareil, son dirigé, port sur le dos (emplacement du plastron, Trinkets Updated en option), modèle, textures, recette |

### Critères de réussite

- Je casse une enceinte qui joue : elle continue sans coupure audible, le son part de l'objet au sol, puis de moi quand je la ramasse ; les autres joueurs l'entendent se déplacer avec moi.
- Je range l'enceinte dans un coffre : la lecture se met en pause ; je la reprends : elle repart là où elle s'était arrêtée.
- J'appuie sur **M** avec une enceinte dans l'inventaire : son interface s'ouvre, sans la poser ni la tenir.
- L'enceinte de soirée s'entend à environ 96 blocs devant sa grille et nettement moins derrière.
- Je la porte sur le dos (plastron, ou emplacement « dos » de Trinkets s'il est installé) : elle s'affiche dans mon dos et le son part derrière moi.

## 2. Décisions de cadrage

| # | Décision | Écarté |
|---|---|---|
| D1 | Deux PR : enceinte mobile d'abord (avec la portable), puis l'enceinte de soirée | Une seule PR |
| D2 | Nom « Enceinte de soirée » (*Party Speaker*), id `ravecraft:party_speaker` | « Soundbox » (trop proche d'une marque) |
| D3 | 120 W à 87 dB de sensibilité, soit environ 108 dB à 1 m et environ 96 blocs, 3 fois la portable | 216 W, 126 dB : plafond de 256 blocs atteint d'emblée |
| D4 | Basses renforcées : coupure de −2 dB sous 80 Hz | Même courbe que la portable |
| D5 | Son dirigé vers la grille : plein volume à ±60° de l'axe, −10 dB juste derrière. La portable reste omnidirectionnelle | Omnidirectionnelle |
| D6 | Port sur le dos : emplacement du plastron (vanilla), plus l'emplacement « dos » de **Trinkets Updated** en dépendance optionnelle | Plastron seul ; affichage sans emplacement ; Accessories (pas de version 26.x) |
| D7 | Touche *Ouvrir l'enceinte*, **M** par défaut, réglable, pour les deux enceintes, en main, dans l'inventaire ou sur le dos | Clic droit seulement |
| D8 | La touche choisie est rappelée dans l'infobulle des deux objets | — |
| D9 | Apparence : proposition v3 (`art/proposals/soundbox/soundbox_v3.png`) | Propositions A à D, v1, v2 |
| D10 | Objet rangé dans un conteneur : pause, reprise quand il revient dans un inventaire de joueur, au sol ou posé (règle du socle, inchangée) | Arrêt de la session |

## 3. PR 6a — Enceinte mobile

### 3.1 Identité et hôte

L'enceinte est désormais repérée par son **identifiant** (`SpeakerData.id`, déjà persistant) et non plus par la position de son bloc. À chaque instant, elle a au plus un **hôte**, l'endroit où vivent ses données et d'où part le son :

| Hôte | Données | Position du son | Axe du son (pour la directivité) |
|---|---|---|---|
| Bloc posé | block entity | centre du bloc | `facing` du bloc |
| Porteur (main, inventaire, plus tard le dos) | composant `SpeakerData` de l'objet | yeux du joueur (main), poitrine (inventaire) | direction du regard ; dos en 6b |
| Objet au sol | composant de l'`ItemEntity` | position de l'entité | aucun (omnidirectionnel) |
| Rangé (conteneur, joueur déconnecté) | composant de l'objet | — | — : session en pause |

Un **registre serveur** en mémoire (`SpeakerHosts`) associe `speakerId → hôte`. Il est mis à jour sur événement, jamais par un scan de blocs ou de chunks :

- **pose et casse** du bloc ;
- **objet au sol** : chargement et déchargement des `ItemEntity` qui portent un `SpeakerData` (événements d'entité Fabric) ;
- **porteur** : l'objet signale son porteur à chaque tick d'inventaire (`Item#inventoryTick` du serveur, appelé par vanilla pour chaque objet de l'inventaire d'un joueur, emplacements d'équipement compris). Le coût est celui d'une écriture en mémoire par enceinte portée et par tick ;
- **perte de vue** : une enceinte portée qui n'a plus signalé son porteur depuis 20 ticks, et qui n'est réapparue ni posée ni au sol, est considérée **rangée** : sa session passe en pause (D10) ;
- **déconnexion** du porteur : pause immédiate.

**Doublons** (copie en créatif, glitch) : si un identifiant déjà actif ailleurs réapparaît, le nouveau venu reçoit un nouvel identifiant à son enregistrement, avec une copie de la file, en pause. Une seule enceinte joue par identifiant.

### 3.2 Sessions et relais

- Les tables du serveur (`SpeakerPlayback`, `SpeakerWatchers`, `StreamSessions`, `NowPlaying`) passent de `SpeakerKey` à l'identifiant. Les payloads remplacent la position du bloc par l'identifiant.
- Quand l'hôte change pendant la lecture (casse, ramassage, lancer), la **session continue** : même `sessionId`, même horloge. Seule la position du son change.
- **Auditeurs** : pour un bloc, les joueurs qui suivent le chunk (inchangé) ; pour un porteur ou un objet au sol, les joueurs qui suivent cette entité (suivi d'entités vanilla, maintenu sur événement). La portée acoustique reste calculée par le client.
- **Position côté client** : un payload `SpeakerHostPayload(speakerId, hôte)` part vers les auditeurs à chaque changement d'hôte : bloc (dimension et position) ou entité (id réseau). Le client lit ensuite la position de l'entité à chaque image, ce qui rend le déplacement fluide sans paquet supplémentaire.
- **Changement de dimension** du porteur : la session continue ; les auditeurs sont ceux du suivi d'entité dans la nouvelle dimension.
- **Persistance** : quand l'enceinte est portée, toute modification (file, réglages) est écrite dans le composant de l'objet. Le serveur retrouve la pile par son identifiant dans l'inventaire du porteur (41 emplacements d'un seul joueur, au moment de la commande seulement).

### 3.3 Contrôle et interface

- **Qui peut agir** : le porteur, et tout joueur à portée d'interaction de la position actuelle de l'enceinte (règle du socle, appliquée à la position de l'hôte).
- **Ouvrir l'interface** :
  - bloc posé : clic droit (inchangé) ;
  - objet en main : **clic droit dans le vide** (remplace l'allumage provisoire) ;
  - touche **Ouvrir l'enceinte** (`key.ravecraft.open_speaker`, **M** par défaut, catégorie *RaveCraft* dans Options → Commandes). Sans écran ouvert, elle ouvre, dans l'ordre : l'enceinte portée sur le dos (6b), celle de la main principale, celle de la main secondaire, puis la première de la barre rapide, puis du reste de l'inventaire. Sans enceinte, elle ne fait rien.
- L'écran (`SpeakerScreen`) s'ouvre par identifiant. S'il est ouvert sur une enceinte qui change d'hôte, il reste ouvert ; il se ferme si l'enceinte sort de portée ou est rangée.
- **Infobulle** (portable, et enceinte de soirée en 6b) : une ligne grise sous les valeurs de base, « [M] : ouvrir l'interface depuis l'inventaire », avec le nom réel de la touche choisie. Si la touche n'est pas attribuée, la ligne n'apparaît pas.

### 3.4 Hors 6a

L'enceinte de soirée, le port sur le dos et le son dirigé d'un porteur sont en 6b. En 6a, un porteur émet de façon omnidirectionnelle (la portable l'est de toute façon).

## 4. PR 6b — Enceinte de soirée

### 4.1 Bloc et objet

- `ravecraft:party_speaker` : bloc à géométrie custom (12 × 18 × 10 pixels, plus les coins), orientable (`facing`), propriété `powered`, block entity avec `SpeakerData` et couleur de teinture, comme la portable. Le code commun aux deux enceintes est factorisé (bloc, block entity, objet) plutôt que copié.
- **Interactions** identiques à la portable (§ 3.3), plus le port sur le dos.
- **Teinture** de la grille, comme le tissu de la portable (couleur par défaut noire).
- **Recette provisoire** (vanilla, à remplacer par les matériaux du mod plus tard), en forme :

  ```
  I N I
  C P C
  I R I
  ```
  `I` lingot de fer, `N` bloc musical, `C` lingot de cuivre, `P` enceinte portable, `R` bloc de redstone. L'enceinte portable utilisée est consommée avec sa file.

### 4.2 Fiche technique

- `DeviceProfile.PARTY` = 120 W, 87 dB, coupure de basses −2 dB sous 80 Hz, directivité `Directivity(60, -10)`.
- Le profil devient une donnée **de chaque enceinte** (selon son type), et non plus la constante unique `portable` : le serveur la configure comme aujourd'hui (fichier de réglages acoustiques et `AcousticSettingsPayload`, avec une entrée `party` à côté de `portable`), le client choisit l'égaliseur et la portée selon le type de l'enceinte qui joue.
- Portée attendue au volume maximal : environ 96 blocs dans l'axe (seuil 68 dB, plafond serveur 256).
- **Infobulle** : valeurs de base (puissance, portée), fiche complète sous Maj (sensibilité, basses, directivité), plus la ligne de touche (§ 3.3).

### 4.3 Son dirigé

L'axe suit l'hôte : `facing` du bloc posé ; regard du joueur quand elle est en main ou dans l'inventaire ; **vers l'arrière du joueur** quand elle est sur le dos (grille tournée vers l'extérieur du dos) ; aucun axe au sol (omnidirectionnelle).

### 4.4 Port sur le dos

- **Emplacement du plastron** : l'objet est équipable (composant d'équipement vanilla, emplacement `CHEST`), sans modèle d'armure ; on ne peut donc pas porter de plastron en même temps.
- **Trinkets Updated** (optionnel, version 26.3, licence MIT) : si le mod est présent (`FabricLoader.isModLoaded`), l'objet est aussi accepté dans l'emplacement « dos » de Trinkets (slot à confirmer dans le plan selon les groupes de Trinkets Updated). Le mod n'est qu'une dépendance de compilation : sans lui, rien ne casse et aucune classe Trinkets n'est chargée.
- **Rendu** : une couche de rendu du joueur dessine le modèle de l'enceinte dans le dos, réduit pour tenir sur le torse (environ 0,6), grille vers l'extérieur, avec la teinture et la LED allumée ou éteinte.
- **Hôte** : porteur, avec l'axe du dos (§ 4.3). La touche M ouvre en priorité l'enceinte portée sur le dos.

### 4.5 Apparence

Proposition v3 :

- grille maillée teintable (seule partie teinte), avec les deux haut-parleurs devinés derrière ;
- logo égaliseur en cuivre peint en haut à gauche de la grille, jamais teint ;
- coins en cubes de caoutchouc 3 × 3 × 3 ;
- panneau de commandes peint à plat en haut d'un côté (volume −, lecture, volume +, LED verte allumée ou éteinte) ;
- côtés et dessus en caoutchouc sombre uni.

Textures produites par `tools/textures/gen_textures.py` (palette et caoutchouc de la portable), modèle Blockbench `art/models/party_speaker.bbmodel`, export JSON. Vérification visuelle (rendu Blockbench et `runClient`).

## 5. Erreurs et limites

- Porteur tué : l'enceinte tombe avec ses objets et continue de jouer au sol ; détruite (lave, feu, cactus, disparition), la session s'arrête (règle du socle).
- Enceinte lancée dans le vide ou disparue : la session s'arrête et l'identifiant est oublié.
- Serveur qui redémarre : toutes les enceintes repartent en pause, où qu'elles soient (comme aujourd'hui pour les blocs).
- Trinkets absent ou d'une version incompatible : l'emplacement du plastron reste disponible ; l'échec d'intégration est journalisé une fois.
- Enceinte dans un conteneur porté (shulker dans l'inventaire, bundle) : considérée rangée (pause), car vanilla n'appelle pas son tick d'inventaire.

## 6. Tests

- **JUnit** (`core`) :
  - choix de l'enceinte à ouvrir avec la touche (ordre de priorité, inventaire sans enceinte) ;
  - suivi des hôtes : perte de vue après 20 ticks, reprise, doublon d'identifiant ;
  - portée de `DeviceProfile.PARTY` (entre 90 et 100 blocs) et directivité (plein gain à 60°, −10 dB à 180°).
- **GameTests** :
  - casser une enceinte qui joue : la session continue avec le même identifiant ;
  - la ranger dans un coffre : pause ; la reprendre : reprise ;
  - commande d'un joueur hors de portée de l'hôte : refusée ;
  - 6b : équiper l'enceinte de soirée au plastron : l'hôte est le porteur.
- **En jeu** (`runClient`, à deux joueurs si possible) : déplacement du son avec le porteur, objet au sol, coffre, touche M, infobulle ; en 6b, portée devant et derrière, dos au plastron, avec et sans Trinkets Updated.

## 7. Hors périmètre

- Contrôle par propriétaire, invités et verrou (prévu plus tard dans le socle).
- Recettes définitives avec les matériaux du mod.
- Enceintes portées par d'autres entités (armure de mob, support d'armure).
- Batterie ou autonomie.
