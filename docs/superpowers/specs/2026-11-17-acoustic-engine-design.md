# Moteur acoustique (étape 4 du socle audio) — spec de conception

> Sous-projet : **Socle audio**, étape 4 (`feat/acoustic-engine`), découpée en trois branches : **4a** (cette spec, détaillée), **4b** et **4c** (esquissées ici, chacune aura sa propre spec).
> Spec parente : [2026-11-17-audio-core-design.md](2026-11-17-audio-core-design.md), section 4, décisions **D6** (simulation avancée) et **D14** (8 sources par défaut).

## 1. Objectif

Remplacer le rendu provisoire des étapes 3a et 3b (une `SoundInstance` vanilla à atténuation linéaire sur ~16 blocs) par un **moteur acoustique réaliste** : sources OpenAL dédiées, loi de distance physique, profil sonore de l'appareil, directivité, absorption de l'air, limite de sources audibles. Les étapes 4b et 4c y brancheront l'étouffement à travers les murs, puis la réverbération et les échos.

### Découpage
| Branche | Contenu |
|---|---|
| **4a** `feat/acoustic-render` | Pool de sources OpenAL + EFX, profil d'appareil, distance, direction, directivité, absorption de l'air, égaliseur, limite de sources, rayon de relais suivant la portée, réglages serveur et client. |
| **4b** `feat/acoustic-occlusion` | Étouffement à travers les blocs (matières acoustiques). |
| **4c** `feat/acoustic-reverb` | Réverbération de la salle et échos. |

### Critères de réussite (4a)
- En s'éloignant d'une enceinte, le niveau baisse comme dans la réalité (−6 dB par doublement de distance) jusqu'au silence en bord de portée, sans coupure nette.
- La spatialisation gauche/droite suit la tête du joueur.
- Avec plus d'enceintes que de sources, seules les plus fortes sonnent ; les autres reprennent au bon endroit dès qu'elles redeviennent parmi les plus fortes.
- Les réglages du joueur sont dans **Options › Musique et sons** ; le modèle acoustique est réglable par un fichier serveur et identique pour tous les joueurs.
- Ce qui existe est conservé : relais, Opus, file partagée, tampon de gigue et décodage ne changent pas. Seul l'étage de sortie (`SpeakerSoundInstance`) est remplacé.

## 2. Décisions prises

| # | Décision |
|---|---|
| A1 | Rendu **le plus réaliste** : loi physique, pas de courbe « jeu vidéo » adoucie (adoucissable par réglage serveur). |
| A2 | **Portée du profil** : 32 blocs pour l'enceinte portable à volume maximal. Le rayon de relais serveur suit cette portée. Remplace la portée « ~16 blocs » de la spec parente (§4.1). |
| A3 | **Sources OpenAL dédiées** (approche A), gérées par RaveCraft dans le contexte OpenAL de Minecraft, et non des `SoundInstance` vanilla : la réserve vanilla de 2 à 8 canaux de streaming est partagée avec la musique et les juke-box, et ne donne pas accès aux filtres EFX par source. |
| A4 | Modèle acoustique dans **`config/ravecraft-server.json`**, envoyé aux joueurs à la connexion. |
| A5 | Réglages personnels dans les **options de Minecraft** (écran Musique et sons, fichier `options.txt`), pas dans un fichier du mod. Remplace, pour ces réglages, le `ravecraft-client.json` de la spec parente (§5.4). |

## 3. Architecture (4a)

```
ReceivedStreams ──(PCM mono 48 kHz)──► AcousticRenderer ──► pool de N sources OpenAL (streaming)
LocalPlayback (boucle locale) ───────►      │                    gain, position, filtre EFX
                                            ├─ SourceSelector (core) : quels flux ont une source
                                            ├─ Propagation   (core) : gain, fondu, absorption
                                            ├─ Directivity   (core) : gain selon l'angle
                                            └─ Equalizer     (core) : timbre du profil, sur le PCM
```

### 3.1 `core.acoustics` (Java pur, testé en JUnit)
- **`DeviceProfile`** (record) : `range` (blocs, à volume maximal), `bassCutDb`, `bassCutHz`, `Directivity`. Profil `PORTABLE` : 32 blocs, −6 dB, 150 Hz, omnidirectionnel.
- **`Directivity`** : `OMNI`, ou cône `(halfAngleDeg, rearGainDb)`. `gain(cosAngle)` vaut 1 dans le demi-angle, décroît ensuite en cosinus jusqu'à `rearGainDb` à l'arrière (180°). Seul `OMNI` est utilisé en 4a ; le cône est codé et testé pour les futures sonos.
- **`Propagation`** : fonctions pures de la distance `d` et du volume `v` (0 à 1) :
  - `range(profile, v) = profile.range × v` ;
  - `distanceGain(d) = 1 / max(d, 1)^rolloff` ;
  - `fade(d, range)` : 1 jusqu'à `(1 − fadeFraction) × range`, puis demi-cosinus jusqu'à 0 exactement à `range`, 0 au-delà ;
  - `gain(d, v, profile, cosAngle) = v × distanceGain(d) × fade(d, range) × directivity.gain(cosAngle)` ;
  - `highFrequencyGain(d) = 10^(−airAbsorptionDbPerBlock × d / 20)`, borné à [0,05 ; 1].
- **`Equalizer`** : filtre en plateau grave (biquad, formules RBJ, Q = 0,707) appliqué au PCM mono avant OpenAL. Garde son état d'une trame à l'autre ; à gain 0 dB, il laisse le signal intact. Le signal de sortie est borné à [−1, 1].
- **`SourceSelector`** : à partir des gains calculés (avant volumes du joueur), choisit les `N` flux à gain non nul les plus forts. Égalité tranchée par identifiant de session, pour un résultat stable. Un flux déjà actif garde sa source tant qu'un autre ne le dépasse pas de plus de 1 dB (hystérésis anti-va-et-vient).
- **`AcousticSettings`** (record) : `rolloff` [0,25 ; 3] défaut 1, `fadeFraction` [0,05 ; 0,5] défaut 0,2, `airAbsorptionDbPerBlock` [0 ; 1] défaut 0,05, et le profil `portable` (`range` [4 ; 128] défaut 32, `bassCutDb` [−24 ; 0] défaut −6, `bassCutHz` [40 ; 500] défaut 150). Lecture et écriture JSON (Gson), valeurs hors bornes ramenées dans les bornes avec avertissement, fichier illisible → valeurs par défaut.

### 3.2 Côté serveur
- **`AcousticConfig`** : charge `config/ravecraft-server.json` au démarrage du serveur (le crée avec les valeurs par défaut s'il manque) et garde l'`AcousticSettings` en mémoire. Pas de rechargement à chaud.
- **`AcousticSettingsPayload`** : envoyé à chaque joueur à la connexion (`ServerPlayConnectionEvents.JOIN`).
- **Rayon de relais** : `AudioRelay` remplace la constante `StreamFormat.RELAY_RANGE` (24) par `range(portable, v) + 8` blocs de marge (mouvements entre deux paquets), calculé à chaque paquet relayé avec le volume actuel de l'enceinte. À volume 0, personne ne reçoit le flux.

### 3.3 Côté client (`client.audio.render`)
- **`AcousticRenderer`** : seul point de sortie audio des enceintes.
  - Reçoit le PCM mono de chaque flux (`ReceivedStreams`, et la boucle locale du propriétaire).
  - À chaque image : calcule le gain de chaque flux (position du centre du bloc, auditeur = caméra), passe la liste à `SourceSelector`, attribue ou libère les sources, puis règle pour chaque source active la position, le gain final (× volumes du joueur) et le filtre passe-bas EFX (`AL_LOWPASS_GAINHF = highFrequencyGain`).
  - Modèle de distance OpenAL désactivé (`AL_NONE`) : le gain vient entièrement de `Propagation`. Pas d'effet Doppler.
  - Chaque flux passe par son `Equalizer`, qu'il ait une source ou non.
  - **Flux virtuels** : un flux sans source continue d'être décodé et de consommer son tampon au rythme réel, en silence, pour reprendre au bon endroit quand il obtient une source.
- **`AcousticSettingsClient`** : réglages reçus du serveur ; valeurs par défaut tant que rien n'est reçu ; oubliés à la déconnexion.
- **Options du joueur** (mixins) :
  - `SoundOptionsScreen.addOptions` (fin) : trois lignes ajoutées — curseur **« RaveCraft »** (0 à 100 %, défaut 100 %), curseur **« Enceintes simultanées »** (2 à 32, défaut 8), bouton **« Effets acoustiques »** (activé par défaut) ;
  - `Options.processOptions` : sauvegarde dans `options.txt` sous `ravecraft_volume`, `ravecraft_maxSources`, `ravecraft_efx`.
  - Volume final d'une source : gain acoustique × volume général × catégorie « Juke-box / Blocs musicaux » × volume RaveCraft. Ces réglages n'influent ni sur le choix des sources ni sur la portée côté serveur.
- **Suppression** : `SpeakerSoundInstance` et `NetworkAudioStream` (remplacés par le renderer).

## 4. Cycle de vie et cas limites

| Situation | Comportement |
|---|---|
| Changement d'un réglage du joueur | Appliqué à l'image suivante ; réduire « Enceintes simultanées » libère tout de suite les sources en trop (les flux concernés deviennent virtuels). |
| Jeu en pause (solo) | Sources mises en pause, puis reprise sans rafale. |
| Rechargement du moteur son (F3+T, changement de périphérique) | Pool détruit avant l'arrêt du moteur, recréé après son redémarrage ; les flux continuent sans redémarrer le morceau. |
| Déconnexion, changement de monde | Toutes les sources libérées, réglages serveur oubliés. |
| EFX absent, ou « Effets acoustiques » désactivé | Pas de filtre passe-bas ; distance, direction et égaliseur restent actifs. Avertissement unique dans le log si EFX manque. |
| Plus de flux que de sources | Flux en trop virtuels (§3.3). |
| Autre dimension | Seuls les flux de la dimension du joueur sont rendus (inchangé). |
| Enceinte éteinte, cassée ou déchargée | Le flux s'arrête comme aujourd'hui ; sa source est libérée. |
| `ravecraft-server.json` invalide | Valeurs ramenées dans leurs bornes ou valeurs par défaut, avertissement dans le log, pas de plantage. |

## 5. Tests (4a)

**JUnit (`core.acoustics`)**
- `PropagationTest` : 2 blocs → 0,5 et 4 blocs → 0,25 avec `rolloff` 1 ; gain plein sous 1 bloc ; exposants 0,5 et 2 ; portée = 32 × `v` ; fondu à 1 avant 80 % de la portée et à 0 exactement à la portée ; 0 au-delà ; `v` = 0 → 0 partout ; absorption = 0,05 dB × d, avec son plancher.
- `DirectivityTest` : `OMNI` vaut 1 partout ; cône à 1 dans l'axe, décroissant hors axe, `rearGainDb` à l'arrière.
- `EqualizerTest` : sinus 60 Hz atténué d'environ 6 dB, sinus 1 kHz inchangé (±0,5 dB) ; 0 dB → signal identique ; pas de discontinuité à la jointure de deux trames ; sortie dans [−1, 1].
- `SourceSelectorTest` : garde les N plus forts ; égalités stables ; hystérésis de 1 dB ; promotion et rétrogradation quand N change ; un gain nul n'obtient jamais de source.
- `AcousticSettingsTest` : aller-retour JSON ; bornes ; fichier corrompu → défauts ; aller-retour du codec réseau.

**GameTests**
- Un joueur à 30 blocs reçoit le flux d'une enceinte à 100 % (rayon 32 + 8 = 40), pas d'une enceinte à 50 % (rayon 16 + 8 = 24).
- Un changement de volume change aussitôt l'ensemble des destinataires.
- Un `range` modifié dans les réglages serveur est appliqué au relais.
- Les réglages sont envoyés au joueur à la connexion.

**Vérification en jeu** (consignée dans la PR)
- Baisse progressive jusqu'au silence vers 32 blocs ; spatialisation gauche/droite en tournant la tête.
- 9 enceintes : seules les 8 plus proches sonnent, échanges sans coupure audible.
- Les trois réglages de Musique et sons agissent en direct et survivent au redémarrage.
- F3+T pendant la lecture : le son revient sans redémarrer le morceau. Pause solo : son en pause.
- `rolloff` 0,5 dans le fichier serveur : son plus doux à distance.

## 6. Esquisse de 4b — étouffement

- Table de **matières acoustiques** en `core` (laine, pierre, bois, verre, terre, eau, air…) : perte globale et perte des aigus par bloc traversé.
- Tracé source → auditeur à ~4 Hz par source audible, sur les seuls blocs traversés ; résultat mis en cache et invalidé sur changement de bloc.
- Effet appliqué via le filtre passe-bas EFX de 4a (`AL_LOWPASS_GAIN` et `GAINHF`) : les aigus sont plus atténués que les basses. Le gain étouffé participe au choix des sources.
- Désactivé si un mod de physique du son (par exemple Sound Physics Remastered) est détecté.

## 7. Esquisse de 4c — réverbération et échos

- ~24 rayons autour de l'auditeur, longueur bornée : ouverture, taille et absorption de l'espace → paramètres de réverbération EFX (temps de décroissance, densité, diffusion, gain) sur un emplacement d'effet auxiliaire partagé.
- Échos distincts dans les grands espaces à parois lointaines, délai tiré de la distance des parois.
- Recalcul quand l'auditeur se déplace d'au moins 1 bloc ou qu'un bloc change dans la zone mesurée. Même règle de compatibilité que 4b.

## 8. Points à vérifier pendant le plan

- Thread des appels OpenAL : le contexte ALC de Minecraft est-il utilisable depuis le thread de rendu, ou faut-il passer par `SoundEngineExecutor` ?
- Point d'accroche du rechargement du moteur son (`SoundEngine.reload`/`destroy`, mixin ou événement Fabric) pour détruire et recréer le pool au bon moment.
- Disponibilité d'EFX sur le périphérique (`ALC_EXT_EFX`) et nombre maximal de sources accordées par le pilote, en plus des sources vanilla.
- Signature exacte de `Options.processOptions` / `FieldAccess` et de `OptionInstance` (curseurs entiers et booléens) en 26.3.
- Pause du jeu : événement ou état à interroger côté client.

## 9. Mises à jour liées

- Spec parente : renvois vers cette spec ajoutés en §4.1, §4.3 et §5.4 (portée 32 blocs, sources dédiées, réglages dans les options de Minecraft).
- `docs/SPEC.md` : avancement de l'étape 4 à chaque branche fusionnée.
