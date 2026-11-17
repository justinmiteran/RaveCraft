# File partagée (étape 3b du socle audio) — spec de conception

> Sous-projet : **Socle audio**, étape 3b, branche `feat/shared-queue` (partie de `feat/audio-relay`).
> Spec parente : [2026-11-17-audio-core-design.md](2026-11-17-audio-core-design.md), décisions **D10 à D12**.

## 1. Objectif

Remplacer « le joueur qui appuie sur Play diffuse sa propre file locale » (étape 3a) par une **file partagée tenue par le serveur** pour chaque enceinte. Chaque morceau est diffusé depuis le PC de **celui qui l'a ajouté** (D10). Le serveur choisit le morceau suivant et ordonne au bon joueur de le diffuser.

### Critères de réussite
- Deux joueurs ajoutent des morceaux à la même enceinte ; ils s'enchaînent, chacun diffusé depuis le PC de son propriétaire, et tout le monde à portée les entend.
- Les morceaux d'un joueur déconnecté sont **sautés mais conservés**, grisés ; ils redeviennent jouables quand il revient (D11).
- Un fichier introuvable ou illisible est **retiré** de la file ; seul son propriétaire est prévenu, par un toast (D12).
- Pause puis reprise repartent du bon endroit, à environ 1 s près.
- La file survit à la casse de l'enceinte, à sa repose et au redémarrage du serveur (en pause).
- Aucun chemin de fichier ne quitte le PC de son propriétaire.

## 2. Décisions de cadrage

| # | Décision | Écarté |
|---|---|---|
| Q1 | **Interface minimale** : ajouter, liste de la file (titre, ajouté par, absents grisés, retirer), lecture/pause, suivant, vider. Le reste attend l'étape 5 | Logique serveur seule ; lecteur complet |
| Q2 | **D13 reportée** : une enceinte cassée s'arrête (sa file est conservée) | Son qui suit le porteur ou l'objet au sol |
| Q3 | **La file est stockée dans l'enceinte** : `BlockEntity` posée, composant de l'objet cassé | Sauvegarde globale par `speakerId` ; mémoire seule |
| Q4 | **L'état n'est envoyé qu'aux joueurs qui ont l'écran ouvert** | Synchronisation du `BlockEntity` à tous |
| Q5 | Pas de précédent, d'historique, d'aléatoire, de répétition, de réordonnancement ni de volume dans l'écran : **étape 5** | — |
| Q6 | Titre = **nom du fichier** sans extension. Artiste et durée : plus tard | Lecture des tags |
| Q7 | Notification « morceau retiré » = **toast** vanilla, toujours actif ; l'option pour la désactiver arrive avec la configuration (étape 5) | Message dans le chat |

## 3. Architecture

```
Écran de l'enceinte (client)        Serveur                              PC du propriétaire du morceau
« Ajouter » ──── QueueAdd ────────► File de l'enceinte (core)
commandes ────── SpeakerCommand ──► Gestionnaire de lecture
écran ouvert ─── SpeakerWatch ────►   (choisit le suivant, saute
◄──────────────── SpeakerState ────    les absents, estime la position) ── StreamOrder(entryId, position) ──►
                                                                   ◄── StreamResult (échec) ──────────── table locale
                                   ── TrackRemoved ──────────────────────────────────────────────────►   entryId → fichier
                                                                   ◄── AudioChunk / StreamClose (3a) ──
```

| Composant | Où | Rôle |
|---|---|---|
| **File** `SpeakerQueue` | `core.queue` (Java pur, JUnit) | Entrées ordonnées, morceau courant, position mémorisée, lecture/pause ; choix du suivant en sautant les absents ; retrait ; plafonds. Immuable ou copiée avant persistance |
| **Entrée** `QueueEntry` | `core.queue` | `entryId` (UUID), `ownerUuid`, `ownerName` (≤ 16), `title` (≤ 128) |
| **Horloge de lecture** `PlaybackClock` | `core.queue` | Position estimée = position de départ + temps écoulé depuis l'ouverture de la session |
| **Table locale** `TrackTable` | logique `core.queue`, fichier `client` | `entryId` → chemin absolu, plafonnée à 5 000 entrées (les plus anciennes oubliées). JSON lu et écrit par Gson côté client dans `config/ravecraft-tracks.json` ; jamais envoyée |
| **Gestionnaire de lecture** `SpeakerPlayback` | `main` · `world.audio` | Applique les commandes, ouvre et ferme les sessions (`StreamSessions` de la 3a), envoie `StreamOrder`, traite `StreamResult`, `StreamClose`, connexions et déconnexions |
| **Spectateurs** `SpeakerWatchers` | `main` · `world.audio` | Qui a l'écran de quelle enceinte ouvert ; reçoit `SpeakerState` à chaque changement |
| **Émetteur** `StreamEmitter` | `client` | Gagne une position de départ : décode et jette jusqu'à ce point |
| **Lecture locale** `LocalPlayback` | `client` | Devient un simple exécutant : reçoit `StreamOrder`, résout `entryId` dans la table, lance ou arrête l'émetteur, répond `StreamResult` ou `StreamClose` |
| **Écran** `SpeakerBrowserScreen` | `client.screen` | Navigateur à gauche (« Ajouter »), file à droite, lecteur en bas. **Maquettes de disposition montrées avant d'être codées** |

### Persistance
- `SpeakerData` gagne un champ **optionnel** `queue` (entrées, `currentEntryId` optionnel, `positionMs`). Les mondes et objets existants sans ce champ se chargent avec une file vide.
- `playing` n'est **pas** persisté : au chargement, toute enceinte est en pause.
- Le transfert bloc ↔ objet existe déjà (étape 1) ; la file suit le même chemin.

## 4. Comportements

### 4.1 Commandes
Tout joueur **à portée d'interaction** (`isWithinBlockInteractionRange(pos, 1.0)`) d'une enceinte posée peut : ajouter, retirer, vider, lecture, pause, suivant. Une commande hors de portée, sur une enceinte absente ou avec un `entryId` inconnu est ignorée.

- **Ajouter** : à la fin de la file. Au-delà de 200 entrées, les morceaux en trop sont refusés. Un `entryId` déjà présent est refusé. Ajouter à une file en pause ne lance pas la lecture.
- **Lecture** sur une enceinte éteinte : sans effet (le joueur doit l'allumer, comme aujourd'hui).
- **Vider** : coupe la session en cours et vide la file.

### 4.2 Cycle d'un morceau
1. **Lecture** : le serveur prend le morceau courant s'il est jouable, sinon le premier jouable après lui (dans l'ordre de la file, en bouclant jusqu'à lui). *Jouable* = son propriétaire est connecté.
2. Il ouvre une session et envoie à ce propriétaire `StreamOrder(start, sessionId, entryId, positionMs)`.
3. Le client du propriétaire résout `entryId` dans sa table :
   - **introuvable ou illisible** (y compris un échec de décodage en cours de route) → `StreamResult(sessionId)`. Le serveur ferme la session, **retire** l'entrée, envoie `TrackRemoved(title)` au propriétaire et passe au suivant ;
   - sinon il décode, saute jusqu'à `positionMs` sans rien envoyer, puis diffuse.
4. **Fin naturelle** (`StreamClose(finished)`) : les auditeurs jouent leur tampon (3a), l'entrée **quitte** la file, le suivant démarre.
5. **Pause** : le serveur ferme la session (coupure nette) et mémorise la position estimée. **Lecture** : nouvelle session depuis cette position.
6. **Suivant** : coupure nette, l'entrée courante quitte la file, le suivant démarre.
7. **Plus rien de jouable** (file vide ou seulement des absents) : l'enceinte passe en pause.

### 4.3 Cas particuliers

| Situation | Comportement |
|---|---|
| Le propriétaire du morceau en cours se déconnecte | Session fermée, passage au suivant jouable. Ses autres entrées restent, grisées |
| Il revient | Ses entrées redeviennent jouables à leur place ; une enceinte en pause y reste |
| On retire le morceau en cours | Comme « suivant » |
| Enceinte éteinte, cassée, chunk déchargé | Session fermée, pause, position mémorisée. La file reste dans le bloc ou passe dans l'objet |
| Redémarrage du serveur | File et position restaurées, en pause |
| Propriétaire loin de l'enceinte ou dans une autre dimension | Il continue de diffuser |
| Le client ignore un `StreamOrder` (vieux client, table effacée) | Traité comme un échec de lecture : l'entrée est retirée |
| Un joueur ouvre l'écran | Il reçoit l'état complet ; puis à chaque changement, tant que l'écran est ouvert |

### 4.4 Ce qui disparaît de la 3a
`StreamOpenPayload`, le renouvellement de session par le propriétaire, les numéros de demande et la prise de contrôle « le dernier qui appuie sur Play gagne ». Le serveur est seul à ouvrir des sessions. `SessionTable`, le relais, le récepteur et le drain de fin de piste sont conservés.

## 5. Réseau

### 5.1 Paquets
| Sens | Paquet | Contenu |
|---|---|---|
| C → S | `QueueAdd` | `pos`, liste ≤ 50 de (`entryId`, `title` ≤ 128) — **jamais de chemin** |
| C → S | `SpeakerCommand` | `pos`, commande (`PLAY`, `PAUSE`, `NEXT`, `REMOVE`, `CLEAR`), `entryId` optionnel |
| C → S | `SpeakerWatch` | `pos`, ouvert ou fermé |
| S → C | `SpeakerState` | `pos`, `playing`, `currentEntryId` optionnel, `positionMs`, entrées ≤ 200 de (`entryId`, `ownerName`, `title`, propriétaire connecté) |
| S → C | `StreamOrder` | `sessionId`, `pos`, démarrer ou arrêter, `entryId`, `positionMs` |
| C → S | `StreamResult` | `sessionId` (échec de lecture) |
| S → C | `TrackRemoved` | `title` |
| — | `AudioChunk`, `RelayedChunk`, `SessionEnd`, `StreamClose` | inchangés (3a) |

### 5.2 Limites
| Limite | Valeur |
|---|---|
| File | 200 entrées |
| `QueueAdd` | 50 entrées par paquet, 4 paquets par seconde et par joueur au plus |
| Titre | 128 caractères ; nom du propriétaire 16 |
| `SpeakerState` | envoyé au plus une fois par tick et par enceinte, aux spectateurs seulement |
| Table locale | 5 000 entrées |

Les listes sont plafonnées **avant** l'envoi (piège `ByteBufCodecs.list`) et revérifiées à la réception.

### 5.3 Sécurité
- Le serveur valide chaque commande : portée, existence de l'enceinte et de l'entrée, bornes.
- `StreamResult` et `StreamClose` ne sont acceptés que du propriétaire de la session.
- Un spectateur qui s'éloigne hors de portée cesse de recevoir `SpeakerState`.

## 6. Tests

| Type | Couverture |
|---|---|
| **JUnit (`core`)** | `SpeakerQueue` : ajout et plafond, doublon refusé, suivant en sautant les absents, bouclage, tous absents → rien, retrait du courant, vider, pause/position ; `PlaybackClock` ; `TrackTable` : aller-retour, plafond ; codecs des nouveaux paquets (aller-retour, dépassement refusé) ; `SpeakerData` sans champ `queue` se décode |
| **GameTests** | Ajout et commandes appliqués à portée, refusés hors de portée ; propriétaire absent sauté et conservé ; `StreamResult` retire l'entrée ; casse puis repose conservent la file ; éteindre met en pause et mémorise la position ; fin naturelle passe au suivant |
| **En jeu** | Deux joueurs (serveur local) : morceaux ajoutés par chacun enchaînés depuis le bon PC ; déconnexion de A (sauté, grisé) puis retour (rejoué) ; fichier renommé retiré avec toast chez son propriétaire seulement ; pause/reprise au bon endroit ; redémarrage du serveur (file restaurée, en pause) |

## 7. Hors périmètre
- D13 (l'enceinte cassée continue de jouer).
- Précédent, historique, aléatoire, répétition, réordonnancement, volume dans l'écran, favoris, option de notification : **étape 5**.
- Tags (artiste, durée).

## 8. Points à vérifier pendant le plan
- API 26.3 des toasts (`SystemToast`, `ToastManager`) et événements Fabric de connexion (`ServerPlayConnectionEvents.JOIN`) pour rendre jouables les entrées d'un joueur qui revient et rafraîchir les spectateurs.
- Coût de la recherche de position : décoder sans envoyer jusqu'à `positionMs` (quelques secondes de calcul pour un long morceau ; fait sur le thread de l'émetteur).
- Où vit le composant de données à la casse : vérifier que le loot existant copie bien le nouveau champ.
