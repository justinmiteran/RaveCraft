# Socle audio — spec de conception

- **Date** : 2026-11-17
- **Sous-projet** : « Socle audio » de [docs/SPEC.md](../../SPEC.md) (section 10), palier 1 « Nomade »
- **Statut** : design validé en brainstorming, en attente de relecture de cette spec
- **Livrable final** : release `v0.1.0`

## 1. Objectif

Permettre à un joueur d'écouter **sa propre musique, stockée sur son PC** (disque ou vraie clé USB), via une **enceinte portable** en jeu, et de la faire **entendre aux joueurs autour de lui**, avec un **rendu sonore réaliste** (distance, direction, étouffement par les murs, réverbération, échos).

Ce sous-projet pose les fondations réutilisées par tout le matériel futur : la chaîne de diffusion réseau et le **moteur acoustique unique**, que chaque appareil paramètre par un profil.

### Critères de réussite
1. En solo, je pose l'enceinte, je choisis un dossier ou un `.m3u` sur mon PC, la musique joue avec un son spatialisé qui change selon le lieu (plein air, grotte, pièce fermée, derrière un mur).
2. En multijoueur, les joueurs à portée entendent la même musique au même moment, **sans posséder les fichiers**.
3. Plusieurs joueurs alimentent une **file d'attente partagée**, et tout le monde peut la contrôler.
4. L'enceinte se porte et se pose sans couper la musique.
5. Aucun chemin de fichier ne quitte le PC du joueur. Le serveur reste stable face à un client malveillant (limites et validations).
6. `./gradlew build` vert : tests JUnit et GameTests.

## 2. Décisions prises

| # | Décision | Alternatives écartées |
|---|---|---|
| D1 | **Tout le monde à portée entend**, sans avoir les fichiers | Seulement le joueur local ; musique distribuée par un dossier du serveur |
| D2 | La musique vient **du PC du joueur** (disques, clés USB). **Pas d'objet « clé USB » en jeu** | Fichiers copiés et stockés sur le serveur |
| D3 | Playlists = **dossiers** + fichiers **`.m3u` / `.m3u8`**, plus une **file d'attente** modifiable en jeu | Playlists complètes créées en jeu (prévues avec le matériel DJ) |
| D4 | Enceinte **posable et portable** | Bloc seul ; objet seul |
| D5 | **Pas de batterie** pour l'instant ; un point d'extension est prévu pour l'énergie | Batterie simple rechargeable |
| D6 | **Simulation avancée** : distance, direction, étouffement par les murs, réverbération, échos. **Un seul moteur**, chaque appareil n'est qu'un jeu de paramètres | Distance seule ; distance et réverbération |
| D7 | **Contrôle ouvert à tous** pour l'instant | Propriétaire et invités, avec verrou : **prévu plus tard** |
| D8 | Transport : **relais par le serveur Minecraft**, audio compressé en **Opus** | Canal UDP séparé ; envoi du fichier entier |
| D9 | Recette **provisoire en vanilla** | Recette définitive avec des matériaux du mod (sous-projet « Matériaux ») |
| D10 | **File partagée** : chaque morceau est diffusé depuis le PC de **celui qui l'a ajouté** | Une seule source par enceinte |
| D11 | Morceau dont le propriétaire est **absent** : **sauté** quand on arrive dessus, mais **conservé** dans la file | Retrait ; remise en tête de file |
| D12 | Fichier **illisible ou introuvable** : sauté et **retiré** de la file. Seul son propriétaire est prévenu, par une **notification discrète hors chat**, désactivable | Message dans le chat pour tous |
| D13 | **Casser l'enceinte ne l'éteint pas** : elle redevient un objet et continue de jouer | Arrêt à la casse |
| D14 | Sources audibles simultanées : **8 par défaut, réglable**, les plus proches en priorité | Pas de limite |

## 3. Architecture

### 3.1 Vue d'ensemble

```
PC du propriétaire du morceau                 Serveur Minecraft                  PC de chaque auditeur (propriétaire inclus)
┌──────────────────────────────┐   paquets   ┌─────────────────────────┐ paquets ┌───────────────────────────────────┐
│ Bibliothèque musicale (local)│   Opus ~100 │ Enceinte (état, file)   │ ──────► │ Récepteur (tampon ~1 s, décodage) │
│ Décodeur → PCM → Émetteur    │ ──────────► │ Relais (auditeurs à     │         │ Moteur acoustique (core + OpenAL) │
│ (Opus, horodatage, séquence) │  ms + seq   │ portée, limites)        │         │ Interface de l'enceinte           │
└──────────────────────────────┘             └─────────────────────────┘         └───────────────────────────────────┘
          ▲ ordres (jouer, pause, reprendre, arrêter)  │  ▲ commandes de n'importe quel joueur à portée
          └────────────────────────────────────────────┘  │
```

Le serveur **fait autorité sur l'état** de l'enceinte. Il ne décode ni n'encode jamais le son : il **relaie** des paquets opaques.

### 3.2 Composants

| Composant | Source set / package | Rôle |
|---|---|---|
| **Bibliothèque musicale** | `client` · `client.audio.library` | Liste les racines du PC (disques, clés USB) ; parcourt les dossiers ; lit les `.m3u`/`.m3u8` (via le parseur de `core`) ; extrait titre, artiste et durée. Tourne hors du thread de rendu. |
| **Décodeurs** | `client` · `client.audio.decode` | MP3, FLAC, OGG Vorbis, WAV vers PCM 16 bits, puis mono 48 kHz (rééchantillonnage si besoin) : OpenAL ne spatialise que le mono. Bibliothèques Java embarquées en jar-in-jar (`include`) ; **licences compatibles LGPL-3.0 obligatoires**, choix arrêté dans le plan. |
| **Émetteur** | `client` · `client.audio.stream` | Découpe le PCM en trames de 20 ms, encode en Opus mono 48 kHz (~64 kb/s, Concentus en Java pur, BSD-3), regroupe environ 5 trames par paquet (~100 ms), avec un **identifiant de session** et un **numéro de séquence**. Avance maximale de 2 à 3 s sur la lecture. |
| **Enceinte (bloc + objet)** | `main` · `block`, `blockentity`, `item`, `registry` | Porte l'état (section 3.4). L'état suit l'enceinte sous toutes ses formes : bloc, objet en inventaire, objet au sol. |
| **Gestionnaire de lecture** | `main` · `world.audio` | Côté serveur : sessions, choix du morceau suivant, ordres aux clients propriétaires, gestion des absences et des retraits. S'appuie sur la logique de file de `core`. |
| **Relais** | `main` · `network` | Reçoit les paquets audio, vérifie l'émetteur, les transmet aux **auditeurs à portée**. Liste d'auditeurs maintenue sur événement (changement de chunk ou de dimension, connexion, déconnexion, déplacement de l'enceinte), jamais par un scan à chaque tick. |
| **Récepteur** | `client` · `client.audio.stream` | Tampon de gigue d'environ 1 s par session, remise en ordre, décodage Opus, gestion des pertes (silence ou dissimulation Opus), rattrapage. |
| **Moteur acoustique** | calcul : `core.acoustics` · rendu : `client.audio.render` | Section 4. |
| **Interface** | `client` · `client.screen` | Section 5.3. |
| **Config client** | `client` · `client.config` | `config/ravecraft-client.json` et la table locale des morceaux (section 5.4). |

### 3.3 Règles de découpage
- **`core` (Java pur, testé en JUnit)** : parseur `.m3u`, logique de la file (suivant, précédent, aléatoire, répétition, absents, retraits), calculs acoustiques, découpage et séquencement des paquets, tampon de gigue (logique), limiteur de débit, profils d'appareils.
- **`src/main`** : aucun import `net.minecraft.client`.
- **`src/client`** : tout ce qui touche au PC du joueur, à OpenAL, à l'interface et au décodage.

### 3.4 État d'une enceinte (persistant, côté serveur)

| Champ | Description |
|---|---|
| `speakerId` | UUID unique, conservé à vie (bloc ↔ objet) |
| `powered` | allumée ou éteinte |
| `playing` | lecture ou pause |
| `volume` | 0–100 |
| `shuffle`, `repeat` | mode aléatoire ; répétition : aucune, file, morceau |
| `queue` | entrées à venir : `entryId` (UUID), `ownerUuid`, `ownerName`, `title`, `artist`, `durationMs` |
| `current` | entrée en cours et position de lecture |
| `history` | entrées déjà jouées, au maximum 50, pour la commande « précédent » |

Stockage : `BlockEntity` quand l'enceinte est posée, **composant de données** (`DataComponentType`) sur l'objet quand elle est portée ou au sol. Le transfert est fait à la pose et à la casse. Un **registre en mémoire** côté serveur (`speakerId` → localisation actuelle : bloc, joueur porteur, entité objet) sert au relais et au calcul de la portée ; il est mis à jour sur événement.

## 4. Moteur acoustique

### 4.1 Profil d'appareil (`core.acoustics.DeviceProfile`)
- `maxSpl` : niveau sonore maximal, d'où découle la **portée** ;
- `bassGain`, `midGain`, `trebleGain` : réponse en fréquence ;
- `directivity` : omnidirectionnel ou cône orienté (angle et atténuation hors axe), exposé dès maintenant pour les futures sonos.

**Profil de l'enceinte portable** : portée d'environ 16 blocs, basses faibles, omnidirectionnelle.

### 4.2 Effets
| Effet | Modèle | Fréquence de calcul |
|---|---|---|
| **Distance et direction** | Loi inverse de la distance (−6 dB par doublement), absorption des aigus par l'air proportionnelle à la distance, spatialisation OpenAL | position mise à jour à chaque image |
| **Étouffement** | Tracé source → auditeur ; chaque bloc traversé applique une perte selon sa **matière acoustique** (table en `core` : laine, pierre, bois, verre, terre, eau, air…). Filtre passe-bas : les **aigus sont plus atténués que les basses** | ~4 Hz par source audible |
| **Réverbération** | ~24 rayons autour de l'auditeur, de longueur limitée. On en déduit l'**ouverture** (part de rayons sans impact), la **taille** (distance moyenne des impacts) et l'**absorption** (matières touchées), puis les paramètres de réverbération EFX (temps de décroissance, densité, diffusion, gain) | quand l'auditeur se déplace d'au moins 1 bloc, ou qu'un bloc change dans la zone mesurée |
| **Échos** | Espaces très grands ou ouverts avec parois lointaines (grandes grottes, canyons) : écho distinct, avec un délai calculé à partir de la distance des parois | avec la réverbération |

Les calculs ne lisent que les blocs traversés par les rayons, dans un rayon borné. Les résultats sont **mis en cache** et invalidés sur changement de bloc (événement), conformément aux règles de performance.

### 4.3 Rendu (`client.audio.render`)
- Les sources musicales sont des sources de **streaming** OpenAL. Le rendu applique le gain, un filtre passe-bas (EFX `AL_FILTER_LOWPASS`) et des emplacements d'effet auxiliaires (réverbération et écho EFX). `EXTEfx` est fourni par la LWJGL OpenAL embarquée dans Minecraft 26.3 (vérifié).
- **Intégration au moteur son de Minecraft** : `AudioStream` et `Channel` (streaming) existent en 26.3 (vérifié). Le plan choisira entre passer par le `SoundEngine` vanilla (`SoundInstance` + `AudioStream` maison) ou gérer des sources OpenAL dédiées dans le même contexte. Contrainte : la réserve vanilla de canaux de streaming est de **2 à 8 canaux** (`clamp(√canaux, 2, 8)` dans `Library.init`) et elle est partagée avec la musique de fond et les juke-box.
- **Limite de sources audibles** : 8 par défaut, réglable, les plus proches en priorité. Un mur d'enceintes futur comptera pour **un seul système**.
- **Volume** : catégorie Minecraft « Juke-box / Blocs musicaux » multipliée par le volume RaveCraft de la config.
- **Repli** : si EFX n'est pas disponible, on garde la distance et la direction seules, avec un message dans le log.
- **Compatibilité** : si un mod de physique du son est détecté (par exemple Sound Physics Remastered), réverbération et étouffement RaveCraft sont désactivés pour éviter un double traitement. La détection est à vérifier dans le plan.

## 5. Contenu en jeu

### 5.1 Enceinte portable — `ravecraft:portable_speaker`
- Bloc à **géométrie custom** (pas un cube plein), orientable (`facing`), propriété `powered` pour la texture allumée ou éteinte.
- **Interactions** :
  - objet en main + clic droit sur un bloc : pose ;
  - clic droit dans le vide : ouvre l'interface (version portée) ;
  - clic droit sur l'enceinte posée : ouvre l'interface ;
  - casse : l'enceinte redevient un objet et **continue de jouer**.
- **Objet au sol** : le son part de l'entité. **Détruit** (lave, feu, cactus, disparition) : la session s'arrête. **Rangé dans un conteneur** : pause, puis reprise quand l'enceinte est reprise en main ou posée.
- **Modèle** : Blockbench (`art/models/portable_speaker.bbmodel`), export JSON. **Textures originales** par `tools/textures/gen_textures.py` (`portable_speaker`, `portable_speaker_on`). Vérification visuelle obligatoire (rendu Blockbench et `runClient`).

### 5.2 Recette provisoire
```
F F F      F = lingot de fer
N R N      N = bloc musical
F F F      R = poudre de redstone
```
Marquée **provisoire** dans `docs/SPEC.md` et par un commentaire dans `RaveCraftRecipeProvider`. Elle sera remplacée par le sous-projet « Matériaux et composants ».

### 5.3 Interface de l'enceinte
| Zone | Contenu |
|---|---|
| **Mon PC** (gauche) | Racines (disques, clés USB), navigation dans les dossiers, fichiers audio et `.m3u`. Actions : ajouter un morceau ; ajouter tout un dossier ou une playlist. Favoris. |
| **File d'attente** (droite) | Titre, durée, **ajouté par**. Morceaux en suspens (propriétaire absent) grisés. Retirer, réordonner par glisser-déposer. |
| **Lecteur** (bas) | Titre en cours avec progression, précédent, lecture/pause, suivant, aléatoire, répétition, volume, allumer/éteindre. |

### 5.4 Configuration client
- `config/ravecraft-client.json` : volume RaveCraft, sources audibles maximum (défaut 8), notifications « morceau retiré » activées ou non, dossiers favoris.
- `config/ravecraft-tracks.json` : table locale `entryId` → chemin absolu du fichier. Elle n'est **jamais envoyée** et survit au redémarrage, ce qui permet de rejouer ses morceaux après une reconnexion.
- Format JSON lu et écrit sans dépendance externe (Gson est fourni par Minecraft).

### 5.5 Divers
- **Onglet créatif** « RaveCraft ».
- **Traductions** `en_us.json` et `fr_fr.json`.
- **Suppression** de `example_block`, `example_item` et des tests associés du template (`Cooldown` reste un modèle, à supprimer s'il ne sert plus).

## 6. Comportements

### 6.1 Commandes
Tout joueur **à portée d'interaction** de l'enceinte (ou son porteur) peut : allumer/éteindre, lecture/pause, précédent, suivant, régler le volume, activer l'aléatoire et la répétition, ajouter, retirer et réordonner dans la file.

- **Éteindre** : arrête la session et coupe le son. File, morceau courant et position sont conservés. **Allumer** : l'enceinte est en pause sur le morceau courant.
- **Précédent** : si le morceau joue depuis plus de 3 s, retour au début ; sinon, morceau précédent de l'historique.
- **Aléatoire** : l'ordre de lecture est tiré au hasard sans modifier l'ordre affiché ; il est recalculé à chaque modification de la file.

### 6.2 Cycle d'un morceau
1. Le serveur choisit l'entrée suivante.
2. **Propriétaire absent** : l'entrée est **sautée** (D11). Elle reste à sa place dans la file, grisée, et le serveur passe à la suivante. Si toutes les entrées restantes sont dans ce cas, l'enceinte s'arrête en pause.
3. Sinon, le serveur ouvre une **session** (`sessionId`) et envoie au client propriétaire l'ordre de diffuser `entryId` depuis une position donnée. Il prévient les auditeurs à portée.
4. Le client propriétaire résout `entryId` dans sa table locale :
   - **fichier introuvable ou illisible** : il répond « échec ». Le serveur **retire** l'entrée, le propriétaire reçoit une notification discrète, et on passe au morceau suivant ;
   - sinon, il décode, encode et émet.
5. Fin du morceau, ou ordre « suivant » : l'entrée passe dans l'historique et on revient à l'étape 1.
6. **Pause** : le serveur coupe le relais et ordonne au propriétaire d'arrêter d'émettre en mémorisant la position. **Reprise** : nouvelle session à partir de cette position.

### 6.3 Cas particuliers
| Situation | Comportement |
|---|---|
| Le propriétaire du morceau en cours se déconnecte | Fin de la session, passage au morceau suivant. Ses autres entrées restent dans la file et seront sautées tant qu'il est absent. |
| Le propriétaire revient | Ses entrées redeviennent jouables à leur place. |
| Un auditeur entre dans la zone | Il reçoit les paquets à partir de cet instant et entend après sa mise en tampon. |
| Un auditeur a un réseau lent | Silence court chez lui seulement, puis rattrapage (paquets trop anciens ignorés). |
| Chunk de l'enceinte posée déchargé | Fin de la session, l'enceinte passe en pause. |
| Redémarrage du serveur | L'état est restauré, en pause. |
| Plusieurs enceintes avec le même propriétaire | Chaque session est indépendante. Un client peut émettre plusieurs flux, dans la limite de son débit. |

## 7. Réseau

### 7.1 Paquets (`CustomPacketPayload` + `StreamCodec`)
| Sens | Paquet | Contenu |
|---|---|---|
| C → S | `SpeakerCommand` | `speakerId`, commande et argument borné |
| C → S | `QueueAdd` | `speakerId`, liste d'entrées (titre, artiste, durée) — **jamais de chemin** |
| S → C | `SpeakerState` | état synchronisé pour l'affichage (file, en cours, modes, volume, allumée) |
| S → C | `StreamOrder` | au propriétaire : démarrer, arrêter, reprendre `entryId` à une position, `sessionId` |
| C → S | `StreamResult` | échec de lecture (introuvable, illisible, format non supporté) |
| C → S | `AudioChunk` | `sessionId`, séquence, trames Opus |
| S → C | `AudioChunk` | relayé tel quel, avec la position de la source pour les auditeurs |
| S → C | `SessionStart` / `SessionEnd` | prévient les auditeurs |

### 7.2 Limites, plafonnées avant envoi et vérifiées à la réception
| Limite | Valeur de départ |
|---|---|
| Taille d'un `AudioChunk` | ≤ 2 Ko |
| Débit audio par client émetteur | ≤ ~200 kb/s (limiteur à seau de jetons) ; au-delà, paquets ignorés |
| File d'attente | 200 entrées ; titre et artiste ≤ 128 caractères |
| `QueueAdd` | ≤ 50 entrées par paquet |
| Historique | 50 entrées |
| Avance de l'émetteur | 2 à 3 s |

### 7.3 Sécurité
- **Aucun chemin de fichier** n'est envoyé : seulement les métadonnées et l'audio.
- Le serveur **valide chaque commande** : portée d'interaction, bornes des valeurs, existence de l'enceinte et de l'entrée.
- Un `AudioChunk` dont l'émetteur n'est pas le propriétaire de la session active est **ignoré**.
- Les codecs de liste sont plafonnés **avant** l'envoi, pour éviter la déconnexion du joueur (piège connu de `ByteBufCodecs.list`).

## 8. Performances
- Aucun scan de blocs ou de chunks par tick. Auditeurs, salle et étouffement sont calculés **sur événement**, ou à fréquence bornée pour l'étouffement.
- Décodage, encodage, parcours de fichiers et calculs acoustiques dans des **threads dédiés**.
- Le serveur ne fait que relayer : coût proportionnel au nombre d'auditeurs × ~64 kb/s.

## 9. Tests

| Type | Couverture |
|---|---|
| **JUnit (`core`)** | Parseur `.m3u`/`.m3u8` (chemins relatifs et absolus, commentaires `#EXTINF`, encodage UTF-8 ou latin-1, lignes vides) ; file : suivant, précédent (règle des 3 s), aléatoire, répétition, saut des absents, retrait des illisibles, bornes ; acoustique : atténuation par distance, perte par matière, passe-bas, estimation de salle et échos sur des grilles simulées ; séquencement et tampon de gigue (désordre, pertes, doublons, rattrapage) ; limiteur de débit |
| **GameTests** | Poser, casser et ramasser en conservant `speakerId`, la file et l'état ; recette ; sélection des auditeurs à portée ; commandes appliquées et rejetées hors de portée ; entrée d'un propriétaire absent sautée et conservée ; plafonds de file |
| **Vérification en jeu** | `runClient` : lecture locale, interface, différence audible entre plein air, grotte, pièce et derrière un mur. Multijoueur : serveur local et deux clients (mode hors ligne) pour la synchronisation, la file partagée et la déconnexion ou reconnexion du propriétaire |

## 10. Livraison

Branches successives, chacune avec un build vert et une pull request (ordre définitif fixé dans le plan) :

1. `feat/portable-speaker` : bloc, objet, état, registre en mémoire, recette, modèle, textures, onglet, traductions, suppression des exemples.
2. `feat/local-playback` : bibliothèque musicale, parseur `.m3u`, décodeurs, lecture locale simple.
3. En deux branches :
   - 3a. `feat/audio-relay` : Opus, paquets, relais, récepteur ; le joueur qui lance la lecture diffuse sa propre file locale à tous ceux à portée.
   - 3b. `feat/shared-queue` : file partagée et gestionnaire de lecture côté serveur (`StreamOrder` piloté par le serveur, absents, illisibles : D10–D12).
4. `feat/acoustic-engine` : profils, distance, étouffement, réverbération, échos, limite de sources.
5. `feat/speaker-ui` : interface complète, configuration, notifications.

Puis mise à jour de `docs/SPEC.md` et release **`v0.1.0`** (procédure [docs/RELEASING.md](../../RELEASING.md)).

## 11. Hors périmètre (prévu plus tard)
- Contrôle par propriétaire, invités et verrou (D7).
- Batterie et énergie (D5).
- Recettes avec les matériaux du mod (D9).
- Création et gestion de playlists complètes en jeu.
- Streaming (Spotify, Deezer…), matériel DJ, logiciels externes.
- Systèmes multi-enceintes (murs, line arrays) : le moteur les prévoit, mais ils ne sont pas livrés ici.

## 12. Points à vérifier pendant le plan
- Bibliothèques de décodage MP3, FLAC et OGG et d'encodage/décodage Opus en **Java pur**, avec **licences compatibles LGPL-3.0**, et intégration jar-in-jar.
- Choix d'intégration du rendu : `SoundEngine` vanilla ou sources OpenAL dédiées (section 4.3).
- API exactes en 26.3 : `DataComponentType`, synchronisation d'un `BlockEntity`, paquets `CustomPacketPayload` sur le canal play, événements Fabric de connexion et de déconnexion, entité objet au sol.
- Détection des mods de physique du son.
- Le registre des positions d'enceintes (PR 2) doit attribuer un nouvel id à une enceinte dont l'id entre en collision avec un id déjà enregistré, car les copies de l'inventaire créatif peuvent atteindre le mode survie.
