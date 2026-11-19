# RaveCraft — cahier des charges

> Document de vision : il décrit **le but et la structure** du mod. Les choix techniques seront faits au moment où chaque partie sera développée, dans une spec dédiée (`docs/superpowers/specs/`).
>
> Légende : ✅ demandé ou retenu · 💡 proposition à valider · ⏳ retenu, mais plus tard · 🤔 idée à étudier, pas sûre · ❓ question ouverte.

---

## 1. Vision

RaveCraft apporte dans Minecraft **du vrai matériel de sonorisation, de DJ et d'éclairage, fonctionnel et simulé de façon réaliste** : enceintes, platines DJ, tables de mixage, projecteurs, consoles lumière…

Le joueur commence avec une simple enceinte portable qui lit sa musique, puis il progresse jusqu'à monter de vrais systèmes : club, salle de spectacle, festival, soundsystem de rave.

## 2. Principes fondateurs

1. ✅ **Réel et utilisable.** Chaque élément fonctionne réellement en jeu : il joue de la vraie musique, mixe, éclaire. Ce n'est pas de la décoration.
2. ✅ **Simulation réaliste du son, de la technique et de la lumière.** Le comportement s'inspire de la réalité : puissance, portée, câblage, chaîne de signal.
3. ✅ **Plusieurs modèles par type d'élément, chacun avec sa puissance**, comme dans la réalité. Cela vaut pour tous les éléments : enceintes, amplis, platines, projecteurs, consoles…
4. ✅ **Une vraie progression d'accès en jeu.** On commence petit (enceinte portable) et on débloque le matériel professionnel au fil de la partie.
5. ✅ **Chaque élément a sa recette.** Les recettes seront définies plus tard, au cas par cas.
6. ✅ **Des matériaux propres au mod.** Les recettes peuvent demander des matériaux qui n'existent pas dans Minecraft et que RaveCraft ajoute.
7. ✅ **Ouvert sur le monde réel**, à terme : musique du PC (clé USB, playlists), plateformes de streaming, platine DJ physique branchée au PC.

## 3. Familles d'éléments

### 3.1 Sources sonores (lecture)
- ✅ **Enceinte portable** : le point d'entrée. Elle lit des playlists et permet de passer d'une musique à l'autre.
- ✅ **Soundbox** : deuxième enceinte portable, plus grosse et plus puissante que la première. Prévue à la fin du socle audio, avant la première version (`v0.1.0`). Elle peut être **tenue en main ou portée sur le dos**, compatible avec les mods qui ajoutent un emplacement de sac à dos.
- ✅ **Clé USB** : objet qui contient de la musique et qu'on insère dans un lecteur ou une platine, comme un DJ.
- ✅ **Playlists** : charger une playlist de musiques.
- ✅ **Plateformes de streaming** (Spotify, Deezer…) : connexion prévue plus tard.
- 💡 Lecteurs intermédiaires (chaîne hi-fi, lecteur de salon) entre l'enceinte portable et le matériel DJ.

### 3.2 Matériel DJ
- ✅ **Platines DJ** (plusieurs modèles).
- ✅ **Lecture depuis une clé USB**, comme un vrai DJ.
- 💡 **Table de mixage DJ** : mélange de plusieurs platines (crossfader, égaliseur, volume par voie).
- ✅ **Compatibilité future avec une vraie platine DJ branchée sur le PC**, pour mixer en jeu avec un contrôleur physique.

### 3.3 Diffusion du son (enceintes et amplification)
- ✅ **Plusieurs modèles d'enceintes**, chacun avec sa puissance.
- ✅ **Systèmes complexes** :
  - salle de spectacle ;
  - club ;
  - festival, avec des **blocs d'enceintes** (assemblages de plusieurs enceintes) ;
  - **soundsystem de rave** (murs d'enceintes empilées).
- 💡 Distinguer les rôles comme en vrai : enceintes large bande, subwoofers (basses), têtes (aigus), retours de scène.
- 💡 Amplificateurs séparés pour le matériel professionnel, avec une puissance qui limite ce qu'on peut alimenter.

### 3.4 Lumière
- ✅ **Projecteurs** (plusieurs modèles, chacun avec sa puissance).
- ✅ **Consoles lumière** pour piloter les projecteurs.
- 💡 Types de projecteurs inspirés du réel : PAR, lyres (têtes mobiles), lasers, stroboscopes, machines à fumée.
- ✅ Simulation lumière réaliste.

### 3.5 Connexions et câblage
- 💡 Câbles pour relier les éléments entre eux, comme une vraie chaîne : source → table de mixage → amplis → enceintes, et console → projecteurs.

### 3.6 Consoles de mix et de lumière : physiques ou logicielles

✅ Les consoles de mix (DJ) et les consoles lumière peuvent être pilotées de plusieurs façons, au choix du joueur :

| Mode | Description | Statut |
|---|---|---|
| **Console physique en jeu** | Un bloc avec lequel on interagit directement dans le monde : faders, boutons, potards sur le bloc lui-même | ✅ |
| **Logiciel en jeu (le nôtre)** | Une interface logicielle propre au mod, ouverte en jeu (par exemple sur un ordinateur ou une tablette en jeu), qui imite un vrai logiciel de mix ou de lumière | ✅ |
| **Logiciel réel existant** | Piloter le matériel en jeu depuis un vrai logiciel installé sur le PC : logiciel DJ pour le mix, logiciel d'éclairage pour la lumière | ✅ 🤔 faisabilité à étudier |
| **Matériel réel** | Une vraie platine ou console branchée sur le PC (voir 3.2 et section 6) | ✅ ⏳ |

🤔 **À étudier** pour les logiciels réels, avant toute décision :
- le **fonctionnement technique réel** de ces logiciels : comment ils communiquent avec le matériel dans la réalité, ce qu'ils envoient et reçoivent ;
- la **possibilité technique** de les relier au jeu, et à quel niveau (commandes seulement, ou aussi le son et l'état des lumières) ;
- 💡 pistes à regarder : les protocoles standards du métier, MIDI et HID pour les contrôleurs DJ, DMX sur réseau (Art-Net, sACN) pour la lumière, OSC pour le pilotage à distance, synchronisation de tempo entre logiciels ;
- 💡 logiciels à regarder : côté DJ, des logiciels gratuits ou open source comme Mixxx, et les logiciels du commerce les plus répandus ; côté lumière, QLC+ (open source) et les versions PC des consoles professionnelles ;
- les conditions de licence et d'utilisation de chaque logiciel.

Objectif : qu'un DJ ou un éclairagiste puisse utiliser **les mêmes outils et réflexes qu'en vrai**, tout en gardant des consoles **100 % en jeu** pour les joueurs qui n'ont rien d'autre que Minecraft.

### 3.7 Matériaux et composants
- ✅ Nouveaux matériaux ajoutés par le mod et utilisés dans les recettes.
- 💡 Matières premières (nouveaux minerais ou matériaux transformés) et composants électroniques intermédiaires (circuits, haut-parleurs, membranes, LED…), pour donner de la profondeur à la progression.

## 4. Progression en jeu

✅ Une vraie progression d'accès : de l'enceinte portable jusqu'aux systèmes de festival et de rave.

💡 Paliers proposés (à valider et à ajuster) :

| Palier | Thème | Exemples d'éléments | Usage |
|---|---|---|---|
| 1 | **Nomade** | Enceinte portable, clé USB | Écouter sa musique n'importe où |
| 2 | **Maison / soirée** | Enceintes plus puissantes, premier lecteur, premières lumières | Animer une base ou une petite fête |
| 3 | **Club** | Platines DJ, table de mixage, sono de club, console lumière | Mixer devant d'autres joueurs |
| 4 | **Salle de spectacle** | Blocs d'enceintes, amplis, projecteurs pro | Concerts et grandes salles |
| 5 | **Festival / Rave** | Murs d'enceintes, soundsystem, gros éclairage | Événements géants en plein air |

Chaque palier demande des matériaux et composants plus avancés que le précédent.

## 5. Caractéristiques des éléments

✅ Chaque modèle a sa puissance, comme dans la réalité. Ses valeurs principales s'affichent dans l'infobulle de l'objet, et le détail en maintenant Maj (prévu pour `v0.1.0`, étape 5).

💡 Caractéristiques possibles (lesquelles garder, à décider) :
- **Enceintes** : puissance, portée, qualité, plage de fréquences (basses, médiums, aigus).
- **Amplis** : puissance de sortie, nombre de sorties.
- **Platines et lecteurs** : sources acceptées (clé USB, playlist, streaming), fonctions DJ.
- **Projecteurs** : intensité, portée, couleurs, mouvements, effets.
- **Consoles** : nombre de voies ou de projecteurs pilotables.

## 6. Ouverture sur le monde réel (à terme)

- ✅ Lire la musique du PC (clé USB, playlists).
- ✅ Se connecter aux plateformes de streaming (Spotify, Deezer…).
- ✅ Brancher une vraie platine DJ sur le PC et l'utiliser en jeu.
- ✅ Piloter le mix et la lumière depuis de vrais logiciels existants (logiciel DJ, logiciel d'éclairage), selon la faisabilité. Voir 3.6.

## 7. Fonctionnalités complémentaires

### 7.1 Son et ambiance
- ✅ **Réverbération selon le lieu** : le son change selon l'endroit (grotte, salle fermée, plein air). Fait partie de la simulation réaliste.
- 🤔 **Des basses qui se sentent** : légère vibration de l'écran et particules près des subwoofers à fort volume.
- ✅ **Effets de scène** : machines à fumée, canons à confettis, jets de CO2, pyrotechnie.
- ⏳ **Murs LED et écrans** qui affichent des visuels synchronisés avec la musique.

### 7.2 Réalisme technique
- 🤔 **Surcharge** : une enceinte trop poussée sature, chauffe, voire grille ; il faut l'ampli adapté. À condition que ce ne soit pas trop bloquant pour les joueurs ; sinon, **désactivable en option**.
- 🤔 **Énergie** : groupe électrogène pour les festivals en pleine nature, consommation par appareil.
- ✅ **Casque DJ** pour pré-écouter le morceau suivant (cue), comme en vrai.
- ✅ **Structures de scène** : ponts lumière (truss), praticables, scènes modulaires pour accrocher les projecteurs.

### 7.3 Création et diffusion
- ✅ **Micro** pour parler ou chanter dans la sono.
- ⏳ **Radio FM** : un émetteur diffuse le mix, les joueurs l'écoutent sur des radios portables partout dans le monde. Idéal en multijoueur.
- ⏳🤔 **Production musicale** : boîte à rythmes, synthé, séquenceur pour créer de la musique en jeu. Bien plus tard, à voir.

### 7.4 Intégration Minecraft
- ✅ **Progrès (advancements) et livre-guide** qui accompagnent la progression par paliers. **Important** ; leur contenu sera choisi plus tard.
- 🤔 **Redstone** : déclencher les lumières, lire le niveau sonore avec un comparateur, voire servir à l'alimentation électrique. Selon la faisabilité technique.
- 🤔 **Compatibilité avec les disques vanilla** dans les lecteurs du mod. Selon la faisabilité technique.

## 8. Contraintes

- Performance : aucun scan par tick ; données dérivées invalidées sur événement.
- Multijoueur : le serveur fait autorité ; paquets plafonnés et à fréquence limitée.
- Accessibilité : les mécaniques punitives (surcharge, énergie…) ne doivent pas rendre le jeu pénible ; prévoir des options pour les désactiver.

## 9. Questions ouvertes

À trancher quand la partie concernée sera développée :

- ❓ **Multijoueur** : les autres joueurs doivent-ils entendre la musique du DJ ? Si oui, comment leur faire parvenir les fichiers ou le flux ?
- ❓ **Streaming** : les conditions d'utilisation des API de Spotify, Deezer… autorisent-elles la lecture dans un jeu ? À vérifier avant de s'engager.
- ❓ **Énergie** : le matériel doit-il être alimenté (électricité, groupe électrogène, redstone) ? Voir 7.2 et 7.4.
- ❓ **Surcharge** : quel niveau de punition, et quelle option pour la désactiver ? Voir 7.2.
- ❓ **Logiciels réels** : quels logiciels DJ et lumière peut-on relier au jeu, par quel moyen, et sous quelles licences ? Voir 3.6.
- ❓ **Synchronisation son ↔ lumière** : les lumières réagissent-elles automatiquement à la musique (BPM, basses) ?
- ❓ **Niveau de réalisme** : jusqu'où pousser la simulation (atténuation avec la distance, obstacles, saturation, phase entre enceintes…) ?
- ❓ **Obtention des matériaux** : nouveaux minerais dans le monde, transformation de matériaux vanilla, ou les deux ?
- ❓ **Recettes** : définies élément par élément, au moment de leur développement.

## 10. Découpage en sous-projets

Chaque sous-projet aura sa propre spec, son plan et sa réalisation. L'ordre reste à décider.

| Sous-projet | Contenu |
|---|---|
| Socle audio | Lire de la musique (clé USB, playlists) et la diffuser par une enceinte ; démarre avec l'enceinte portable |
| Matériaux et composants | Nouveaux matériaux, composants, recettes |
| Gamme d'enceintes et amplification | Modèles et puissances, systèmes complexes, blocs d'enceintes, soundsystem |
| Matériel DJ | Platines, table de mixage, lecture de clé USB |
| Lumière | Projecteurs, consoles, câblage |
| Synchronisation son ↔ lumière | Les lumières réagissent à la musique |
| Streaming | Connexion aux plateformes |
| Platine physique | Contrôleur DJ réel branché sur le PC |
| Logiciels en jeu | Nos propres interfaces de mix et de lumière, ouvertes en jeu |
| Logiciels réels | Étude puis liaison avec de vrais logiciels DJ et lumière |
| Effets et structures de scène | Fumée, confettis, CO2, pyrotechnie, truss, praticables, scènes modulaires |
| Micro et casque DJ | Voix dans la sono, pré-écoute (cue) |
| Progrès et livre-guide | Advancements et guide qui suivent les paliers |
| Plus tard | Murs LED, radio FM, production musicale |

## 11. Étapes

- [x] Vision et structure du mod (ce document)
- [x] Initialisation du projet (`ravecraft`, package `com.ravecraft.mod`)
- [x] Choisir le premier sous-projet : **Socle audio** (enceinte portable)
- [ ] Socle audio : spec ([2026-11-17-audio-core-design.md](superpowers/specs/2026-11-17-audio-core-design.md)), plan, réalisation, release `v0.1.0`
  - [ ] Étape 1/6 (PR `feat/portable-speaker`, plan [2026-11-17-portable-speaker.md](superpowers/plans/2026-11-17-portable-speaker.md)) : enceinte portable posable et transportable, avec identité persistante, état marche/arrêt, volume mémorisé (réglage à venir avec l'interface). Modèle en tonneau à poignée, tissu teintable comme l'armure en cuir (noir par défaut, couleur conservée quand on la casse). Recette **provisoire** vanilla `FFF / NRN / FFF` (lingot de fer, bloc musical, redstone), à remplacer par le sous-projet « Matériaux et composants ». Icône 2D dans l’inventaire, LED qui suit l’état marche/arrêt. Étanche : elle se pose dans l’eau sans la chasser (waterlog), et l’eau reste quand on la casse (PR `feat/waterlogged-speaker`, à vérifier en jeu). (vérifié en jeu)
  - [ ] Étape 2/6 (PR `feat/local-playback`, plan [2026-11-17-local-playback.md](superpowers/plans/2026-11-17-local-playback.md)) : lecture de fichiers du PC sur l’enceinte, pour le joueur local seulement (le relais vers les autres joueurs est l’étape 3). Formats MP3, OGG, FLAC, WAV et playlists `.m3u`/`.m3u8`, convertis en mono 16 bits pour être spatialisés (catégorie *Juke-box/Blocs musicaux*, atténuation linéaire sur 16 blocs). Clic droit : écran minimal pour parcourir le PC et lancer une piste, un dossier ou une playlist ; **accroupi + clic droit** allume ou éteint l’enceinte. La musique s’arrête si l’enceinte est cassée ou éteinte, ou en quittant le monde ; un fichier illisible est sauté. (vérifié en jeu)
  - [ ] Étape 3a/6 (PR `feat/audio-relay`, plan [2026-11-17-audio-relay.md](superpowers/plans/2026-11-17-audio-relay.md)) : relais du son vers les joueurs proches. Le joueur qui lance la lecture encode sa file locale en Opus mono 48 kHz (~64 kb/s, Concentus) et l’envoie au serveur par paquets de 100 ms, avec 2,5 s d’avance au plus ; le serveur n’accepte que le propriétaire de la session, limite le débit (25 ko/s) et relaie aux joueurs qui suivent le chunk à 24 blocs ou moins. Chaque client joue le flux avec un tampon de gigue d’1 s ; une nouvelle lecture remplace la précédente, la fin naturelle d’une piste laisse finir le tampon. Le son s’arrête si l’enceinte est cassée ou éteinte, ou si l’émetteur se déconnecte. (vérifié en jeu, solo et multijoueur)
  - [ ] Étape 3b/6 (PR `feat/shared-queue`, plan [2026-11-17-shared-queue.md](superpowers/plans/2026-11-17-shared-queue.md)) : file partagée par enceinte, tenue par le serveur et conservée avec l’enceinte (y compris dans l’objet quand on la casse). Chacun ajoute des morceaux de son PC (seuls l’identifiant et le titre partent, jamais le chemin) ; le serveur choisit le suivant et ordonne à celui qui l’a ajouté de le diffuser. Les morceaux d’un joueur absent sont sautés mais gardés, grisés ; un fichier introuvable ou illisible est retiré et seul son propriétaire reçoit un toast. Pause et reprise à la bonne position ; enceinte éteinte ou cassée, arrêt du serveur : file mise en pause. Écran : navigateur à gauche (« Ajouter »), file à droite (retrait par ×), lecteur en bas (lecture/pause, suivant, vider). (vérifié en jeu, multijoueur)
  - [ ] Étape 4a/6 (PR `feat/acoustic-render`, spec [2026-11-17-acoustic-engine-design.md](superpowers/specs/2026-11-17-acoustic-engine-design.md), plan [2026-11-17-acoustic-render.md](superpowers/plans/2026-11-17-acoustic-render.md)) : rendu acoustique dédié. Les flux des enceintes ne passent plus par le moteur sonore vanilla mais par des sources OpenAL propres au mod, sur leur propre thread : loi en 1/d réaliste, portée calculée depuis la fiche technique de l’enceinte (puissance et sensibilité, seuil d’audibilité, plafond) : ≈ 32 blocs × volume pour l’enceinte portable (20 W, 85 dB), avec fondu sur les 20 % finaux, absorption de l’air −0,05 dB/bloc (filtre EFX), coupe des basses −6 dB à 150 Hz, directivité (omni pour la portable, modèle en cône prêt). Au-delà de la limite de sources, seules les enceintes les plus fortes sonnent ; les autres continuent en silence et reviennent au bon endroit du morceau. Le serveur relaie à portée + 8 blocs ; son modèle acoustique se règle dans `config/ravecraft-server.json5` (chaque réglage y est commenté) et est envoyé à chaque joueur. Côté joueur, trois réglages dans Options › Musique et sons : volume RaveCraft, enceintes simultanées (2–32, 8 par défaut), effets acoustiques. (à vérifier en jeu)
  - [ ] Étape 4b/6 (PR `feat/acoustic-occlusion`, spec [2026-11-17-acoustic-occlusion-design.md](superpowers/specs/2026-11-17-acoustic-occlusion-design.md), plan [2026-11-17-acoustic-occlusion.md](superpowers/plans/2026-11-17-acoustic-occlusion.md)) : étouffement du son à travers les blocs et les milieux. Chaque bloc reçoit une matière acoustique (pierre, bois, verre, métal, terre, neige, laine, feuillage), d'abord par tag `#ravecraft:acoustic/<nom>`, sinon d'après son son de pas, sinon pierre s'il a une collision ; les portes, trappes et vitres fermées bouchent leur axe, les dalles et barrières comptent selon leur volume. Les milieux (air, eau, lave, vide…) viennent des tags de fluide `#ravecraft:medium/<nom>` ou de la dimension ; passer de l'air à l'eau coûte ≈ 20 dB, le vide coupe le son. Le son contourne les murs par les ouvertures proches (détours à un ou deux coudes, chaque ouverture comptée une fois). Le client trace chaque enceinte audible 4 fois par seconde dans un budget de voxels (option « Qualité de l'occlusion » : basse, moyenne, haute) et lisse la perte sur 200 ms. Tout se règle dans la section `"occlusion"` de `config/ravecraft-server.json5` (matières et milieux en listes ouvertes pour les autres mods) ; un ancien fichier est complété et réécrit, l'ancien gardé en `.bak`. (à vérifier en jeu)
  - [ ] Étape 4c/6 (PR `feat/acoustic-reverb`, spec [2026-11-18-acoustic-reverb-design.md](superpowers/specs/2026-11-18-acoustic-reverb-design.md)) : réverbération selon le lieu (grotte, salle fermée, plein air) mesurée par des rayons autour de l'auditeur, échos dans les grands espaces, et couleur propre au milieu quand la tête est sous l'eau ou dans la lave (réverbération courte et dense). Livré : réverbération EAX de ton lieu et de celui des 2 enceintes les plus fortes hors de ton espace, écho rare sur paroi dure lointaine (17–35 blocs), préréglages par fluide (chorus actif par défaut sous l'eau), absorption par matière et par milieu, irrégularité du lieu (murs lisses : son plus dur ; grotte : son plus doux), entrées de debug F3 `ravecraft:speakers`, `listener`, `reverb`, `streams` et `perf`, section `"reverb"` de la config serveur ; l'option « qualité d'occlusion » règle aussi le nombre de rayons (16/32/48) et le budget par tick (à vérifier en jeu).
  - [ ] Étape 5/6 : interface complète de l'enceinte, configuration, notifications, en deux PR.
    - [ ] 5a (PR `feat/speaker-ui`, spec [2026-11-19-speaker-ui-design.md](superpowers/specs/2026-11-19-speaker-ui-design.md), plan [2026-11-19-speaker-ui.md](superpowers/plans/2026-11-19-speaker-ui.md)) : infobulle de l'objet (valeurs de base, détails avec Maj) ; lecteur complet : barre de progression déplaçable (un seul saut au relâchement, durée lue dans le fichier WAV, FLAC, MP3 ou Ogg), précédent (retour au début après 3 s, historique de 50 morceaux), aléatoire sans répétition dans un tour, qui ne mélange que les morceaux encore à jouer, répétition à trois états (tout, un, aucune), volume 0–100 qui règle la portée, marche/arrêt, commandes désactivées quand l'enceinte est éteinte ; file en playlist avec curseur et séparateur « Tour suivant » ; nouvel écran adaptatif, de 256 × 216 à 640 × 420 selon l'écran, avec sur grand écran la file sur toute la hauteur à droite (fenêtre vanilla, bande de tissu à la couleur de l'enceinte, titre = nom donné à l'enceinte, conservé quand on la casse) ; bibliothèque en arborescence limitée aux dossiers choisis (32 au plus, `config/ravecraft-library.json`), ajoutés par la boîte de sélection moderne de Windows (préparée à l'ouverture de la fenêtre « Dossiers… »), avec un navigateur intégré qui ne s'ouvre que si cette boîte ne peut pas s'ouvrir ; vue dossiers ou à plat, avec pour chaque dossier le nombre de morceaux qu'il contient, sous-dossiers compris, une infobulle au survol de chaque ligne (nom complet, puis nombre de morceaux d'un dossier ou, à plat, son dossier) un double-clic sur un morceau ou une playlist qui l'ajoute à la file, et un bouton + qui les met tous en file (ses propres morceaux d'abord, puis chaque sous-dossier par ordre alphabétique, dans la limite de la file) ; index de la bibliothèque enregistré (`config/ravecraft-library-index-local.txt`, 50 000 entrées au plus, dossiers système ignorés), affiché tout de suite à l'ouverture puis remis à jour en arrière-plan (« Mise à jour… »), bouton Rafraîchir qui relit sans rien vider ; barres de défilement déplaçables à la souris ; sous-dossiers exclus depuis la fenêtre « Dossiers… » (arbre, panneau au survol), masqués de la bibliothèque ; onglets Clé USB et Streaming verrouillés (« Bientôt »). (à vérifier en jeu)
    - [ ] 5b : réordonner la file, favoris, option pour couper les notifications, configuration ; infobulles d'informations au survol des morceaux et des boutons.
    - **Infobulle des enceintes** (prévue pour `v0.1.0`) : survoler une enceinte dans l'inventaire affiche ses valeurs de base pour le joueur, par exemple sa puissance et son profil (liste exacte des valeurs à définir dans la spec de l'étape). Maintenir **Maj** affiche plus d'informations : la fiche technique détaillée (par exemple sensibilité, portée, directivité). Sans Maj, une ligne grisée indique « Maj pour plus d'infos ».
  - [ ] Étape 6/6 (PR `feat/soundbox`) : **deuxième enceinte portable, de type soundbox**, à la fin du socle audio et avant la release `v0.1.0`. Elle s'appuie sur le profil d'appareil introduit en 4a (puissance, sensibilité, directivité) pour porter plus loin que l'enceinte portable. Elle se **tient en main ou se porte sur le dos** ; compatible avec les mods qui ajoutent un emplacement de sac à dos (par exemple Trinkets ou Accessories, à confirmer dans la spec selon ce qui existe pour 26.3). Fiche technique, modèle, textures et recette à définir dans une spec dédiée, avec propositions visuelles avant réalisation.

### Prévu plus tard (décidé pendant le socle audio)
- Contrôle des appareils par **propriétaire + invités + verrou** (pour l'instant, contrôle ouvert à tous).
- Recette définitive de l'enceinte portable avec les matériaux du mod (pour l'instant, recette provisoire vanilla).
- Lecture des **tags** des fichiers (artiste, pochette) et **durées** affichées dans la bibliothèque ; à rapprocher des infobulles de l'étape 5b.
