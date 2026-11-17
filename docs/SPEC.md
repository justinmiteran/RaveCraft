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

✅ Chaque modèle a sa puissance, comme dans la réalité.

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
  - [ ] Étape 1/5 (PR `feat/portable-speaker`, plan [2026-11-17-portable-speaker.md](superpowers/plans/2026-11-17-portable-speaker.md)) : enceinte portable posable et transportable, avec identité persistante, état marche/arrêt, volume mémorisé (réglage à venir avec l'interface). Recette **provisoire** vanilla `FFF / NRN / FFF` (lingot de fer, bloc musical, redstone), à remplacer par le sous-projet « Matériaux et composants ». (code fusionnable, vérification en jeu en attente)

### Prévu plus tard (décidé pendant le socle audio)
- Contrôle des appareils par **propriétaire + invités + verrou** (pour l'instant, contrôle ouvert à tous).
- Recette définitive de l'enceinte portable avec les matériaux du mod (pour l'instant, recette provisoire vanilla).
