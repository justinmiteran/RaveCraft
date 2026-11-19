# Vue de debug acoustique en jeu : spec de conception

> Sous-projet : **Socle audio**, outil de debug (`feat/acoustic-debug-view`).
> S'appuie sur l'occlusion ([2026-11-17-acoustic-occlusion-design.md](2026-11-17-acoustic-occlusion-design.md)) et la réverbération ([2026-11-18-acoustic-reverb-design.md](2026-11-18-acoustic-reverb-design.md)). Ne modifie aucun calcul audio.

## 1. Objectif

Voir dans le monde ce que le moteur acoustique calcule, comme F3+B montre les hitbox : par où passe le son d'une enceinte, quels blocs le freinent et de combien, quelles ouvertures il emprunte pour contourner un mur, et comment le moteur perçoit la pièce où l'on se trouve (rayons, surfaces, écho).

C'est un outil de développement et de réglage. Il n'a pas d'option joueur et ne change rien au son.

### Critères de réussite
- **F3+U** fait défiler : chemins → pièces → les deux → rien. Chaque appui affiche un message de debug dans le chat, comme F3+B.
- Les deux couches sont aussi deux entrées de F3+F6, catégorie RaveCraft : `ravecraft:sound_paths` et `ravecraft:sound_rooms`. F3+U et F3+F6 agissent sur le même état, que vanilla sauvegarde.
- Avec une enceinte derrière un mur percé d'une porte, on voit le chemin direct virer au rouge en traversant le mur, le bloc traversé étiqueté (matériau, dB), le détour par la porte avec la porte marquée, et la perte totale au-dessus de l'enceinte.
- Dans une salle de pierre, on voit les rayons et leurs impacts, le résumé de la pièce (ouverture, libre parcours, absorption, irrégularité, RT60), et le groupe de rayons de l'écho quand il y en a un.
- Couches décochées : aucun enregistrement pendant les traçages, aucune allocation, aucun gizmo. Le coût se réduit à deux lectures de booléen par tick et par image.
- Couches cochées : aucun traçage supplémentaire. On affiche ce que les traçages habituels (4×/s pour l'occlusion, toutes les 2 s ou au déplacement pour la pièce) ont calculé.

## 2. Décisions prises

| # | Décision |
|---|---|
| V1 | **Deux couches.** *Chemins* : chemin direct, blocs traversés, détours retenus, ouvertures. *Pièces* : rayons de la pièce de l'auditeur, son résumé, l'écho, et les pièces des enceintes S1/S2. |
| V2 | **Style « perte cumulée »** (proposition C retenue) : le chemin direct est découpé bloc par bloc et sa couleur suit la perte cumulée ; les détours sont en pointillés. |
| V3 | **Activation** : deux entrées F3+F6 (cases on/off des visualiseurs vanilla) et un combo F3+U qui les règle à la suite. La touche est une `KeyMapping` de la catégorie Debug, réassignable. |
| V4 | **Rendu par les gizmos vanilla** (`net.minecraft.gizmos.Gizmos`), émis dans `LevelExtractionEvents.END_EXTRACTION` de Fabric. C'est le mécanisme des hitbox et des bordures de chunk. |
| V5 | **Aucun traçage pour l'affichage.** Le traceur d'occlusion accepte une capture optionnelle, remplie pendant ses calculs normaux. La sonde de pièce garde déjà ses impacts. |
| V6 | **Client seul**, sans réseau ni config serveur. |

## 3. Activation

### 3.1 Entrées F3+F6
- Deux `DebugScreenEntry` sans texte (comme `DebugEntryNoop` en vanilla), dans la catégorie RaveCraft déjà utilisée par `AcousticDebugEntry` : `ravecraft:sound_paths` et `ravecraft:sound_rooms`.
- Une couche est active quand `minecraft.debugEntries.isCurrentlyEnabled(id)` est vrai. Les trois états vanilla s'appliquent donc : « toujours », « avec F3 » (visible seulement écran F3 ouvert) et « jamais ».
- Comme toutes les entrées ajoutées, elles sont désactivées par défaut. Elles sont masquées quand le serveur impose « reduced debug info » (`isAllowed` par défaut).

### 3.2 Combo F3+U
- Une `KeyMapping` `key.ravecraft.debug.soundView`, touche U par défaut, catégorie `KeyMapping.Category.DEBUG`, enregistrée par `KeyMappingHelper` de Fabric.
- Un mixin sur `KeyboardHandler.handleDebugKeys` (injection en tête, annulable) : si la touche correspond à l'événement, il passe au mode suivant, affiche le message de debug et renvoie `true`. Vanilla n'appelle `handleDebugKeys` que si F3 est enfoncée.
- Mode courant = (chemins actifs, pièces actives). Mode suivant dans le cycle **rien → chemins → pièces → les deux → rien**. Le cycle est calculé en `core` (`SoundViewMode`).
- Une entrée allumée par F3+U prend l'état que F3+B donnerait : « avec F3 » si l'écran F3 est ouvert, sinon « toujours » (même règle que `DebugScreenEntryList.toggleStatus`). Une entrée éteinte passe à « jamais ».
- Message (traduit en/fr), préfixé comme les messages vanilla (`[Debug]:` en jaune gras) : « Vue sonore : chemins », « … : pièces », « … : chemins et pièces », « … : désactivée ».

## 4. Couche « chemins »

### 4.1 Ce qui est dessiné, pour chaque enceinte audible (8 au plus, les plus fortes)
- **Chemin direct**, de l'enceinte vers l'oreille. Il est découpé aux points d'entrée des voxels qui perdent quelque chose. Chaque tronçon prend la couleur de la perte cumulée depuis l'enceinte (§4.3).
- **Voxels traversés** qui perdent de l'énergie : chacun est encadré (cuboïde en traits, couleur de sa propre perte) et porte une étiquette flottante `<nom> −x.x dB`. Le nom est celui de la matière (`stone`, `glass`…), du milieu traversé (`water`) ou de l'interface (`air→water`). 64 au plus par chemin ; au-delà, la fin est tronquée et l'étiquette de l'enceinte l'indique (`+N`).
- **Détours retenus** (1 à 3, ceux que le traceur a réellement sommés) : une polyligne en pointillés source → point(s) de passage → oreille, colorée selon la perte totale du détour.
- **Ouvertures** : chaque point de passage d'un détour retenu est marqué d'un petit cube cyan (le voxel du point), avec une étiquette `−x.x dB` (perte du détour).
- **Total** au-dessus de l'enceinte : `S<id> −x.x dB (aigus −y.y)`, soit l'occlusion combinée publiée. Pour un chemin muet (vide sur le trajet) : `S<id> muet`.

### 4.2 Capture dans le traceur (`core`)
- Nouvelle classe mutable `PathCapture` (`core.acoustics.occlusion`) passée en paramètre optionnel de `OcclusionTracer.occlusion(...)`. Avec `null`, le comportement et les allocations sont exactement ceux d'aujourd'hui.
- Pendant le **seul tracé direct**, `trace` y ajoute un `Crossing(x, y, z, name, lossDb)` pour chaque voxel dont la contribution large bande est > 0 : matière (pondérée par le remplissage, comme le calcul), absorption du milieu, ou perte d'interface. Les voxels d'extrémité (enceinte, tête) n'apportent rien et ne sont pas notés. Au-delà de 64, le compteur `truncated` augmente sans rien stocker.
- Les détours candidats ne sont pas capturés. Seuls ceux qui sont finalement retenus le sont, avec leurs points et leur perte (`Detour(points, lossDb)`). `Route` porte donc ses points quand une capture est présente.
- `capture.direct(PathLoss)` reçoit le résultat du tracé direct, et `capture.result(Occlusion)` l'occlusion combinée.
- Le traceur parcourt de l'oreille vers l'enceinte. Le snapshot (§6) remet les croisements dans le sens du son (enceinte → oreille) pour le cumul.

### 4.3 Échelle de couleur (`core`)
`LossColors.forLoss(dB)` renvoie une couleur ARGB opaque : vert `#5DCAA5` à 0 dB, jaune `#EF9F27` à 10 dB, rouge `#E24B4A` à 30 dB et au-delà, avec une interpolation linéaire par canal entre ces paliers. Le muet est rouge. Les ouvertures sont en cyan `#85B7EB`.

## 5. Couche « pièces »

### 5.1 Pièce de l'auditeur
- **Rayons** depuis le point où la dernière mesure a commencé (l'oreille à cet instant), jusqu'à leur impact :
  - rayon fermé : trait plein, couleur selon l'absorption de la surface touchée (vert = réfléchit, 0 ; rouge = absorbe, 1 ; même dégradé que §4.3 sur 0→1), et un point à l'impact ;
  - rayon ouvert : trait gris en pointillés jusqu'à sa longueur maximale, sans point.
- **Écho** : les impacts du groupe retenu par `EchoFinder` sont en jaune, avec un petit cadre sur la face touchée.
- **Résumé**, étiquette 1,2 bloc au-dessus du point de mesure, sur 2 lignes :
  `ouvert 35 % | parcours 6.2 | absorption 0.18 | irrégulier 0.40`
  `RT60 1.84 s | écho 0.12 s (gain 0.21)` ou `écho : aucun`.
- Tête dans un fluide : pas de rayons (la mesure est remplacée par le préréglage). Une seule étiquette, `dans water (préréglage)`.
- Avant la première mesure : rien.

### 5.2 Pièces des enceintes S1/S2
Mêmes rayons, depuis le centre de l'enceinte qui occupe le slot, avec une opacité réduite (40 %), sans écho. L'étiquette se limite à une ligne : `S1 · RT60 1.20 s`.

### 5.3 Données (`core`)
- `EchoFinder` expose le groupe retenu : nouvelle méthode `EchoFinder.group(hits, settings)` qui renvoie les **indices** des rayons du groupe (liste vide sans écho). `find` est réécrit sur `group`, à comportement identique (tests existants verts).
- Les directions des rayons sont déterministes (`RayDirections.sphere(n)`) : le snapshot les recalcule à partir du nombre de rayons, sans changer `RoomProbe`. Le point d'impact d'un rayon fermé vaut `origine + direction × distance`.

## 6. Snapshots

Records immuables dans `core.acoustics.debug.view`, sans dépendance Minecraft :

```
PathView(int sessionId, Vec source, Vec listener, double lossDb, double highLossDb, boolean mute,
         List<Crossing> crossings /* sens du son */, int truncated, List<Detour> detours)
Crossing(int x, int y, int z, String name, double lossDb, double cumulativeDb)
Detour(List<Vec> points, double lossDb)
RoomView(Vec origin, List<Ray> rays, RoomShape shape, double rt60, Echo echo, @Nullable String fluid)
Ray(Vec end, boolean open, double absorption, boolean echo)
Vec(double x, double y, double z)
```

Construits par des fabriques pures et testables : `PathView.of(sessionId, source, listener, capture, occlusion)` et `RoomView.of(origin, hits, rayLength, settings, echoIndices)`.

Côté client, `SoundViewSnapshots` (package `client.audio.render`) les détient :
- `Map<Integer, PathView>` : `OcclusionScheduler` y place la vue de chaque enceinte tracée dans le tick (la rotation garde la dernière de chacune) et retire les sessions qui ne sont plus audibles.
- `@Nullable RoomView listener` et `RoomView[2] slots` : `ReverbScheduler` les remplace à la fin de chaque mesure.
- Quand une couche est désactivée, ses données sont vidées et plus rien n'est capturé.

Tout se passe sur le thread client : les ticks (`END_CLIENT_TICK`) et l'extraction de rendu (`END_EXTRACTION`) y tournent tous les deux. Les snapshots étant immuables, le rendu ne voit jamais un état à moitié écrit.

## 7. Rendu

`SoundViewRenderer` (client), abonné à `LevelExtractionEvents.END_EXTRACTION` :
1. Si aucune couche n'est active, il sort tout de suite.
2. Sinon, il émet des gizmos à partir des snapshots :
   - `Gizmos.line` pour les tronçons et les rayons (largeur 2,5 pour le chemin direct, 1,5 sinon) ;
   - les pointillés, faute de primitive dédiée, sont des tirets de 0,4 bloc tous les 0,8 bloc ;
   - `Gizmos.cuboid(BlockPos, padding, GizmoStyle.stroke(...))` pour les voxels et les ouvertures ;
   - `Gizmos.point` pour les impacts ;
   - `Gizmos.billboardText` pour les étiquettes.
3. Les gizmos des chemins sont `setAlwaysOnTop()`, sinon le mur cache justement ce qu'on veut voir. Les rayons de pièce ne le sont pas, pour rester lisibles quand on est dedans.

**Plafonds par image** : 8 chemins × (64 voxels + 3 détours), 3 pièces × 48 rayons. Avec ces limites, la vue reste sous ~1 000 primitives.

L'état « couche active » est lu dans `debugEntries`. Les schedulers le consultent à chaque tick pour décider de passer une `PathCapture` ou non.

## 8. Fichiers

| Fichier | Rôle |
|---|---|
| `core/acoustics/debug/view/SoundViewMode.java` | cycle rien → chemins → pièces → les deux |
| `core/acoustics/debug/view/LossColors.java` | couleurs de perte et d'absorption |
| `core/acoustics/debug/view/{PathView,RoomView,Crossing,Detour,Ray,Vec}.java` | snapshots immuables et leurs fabriques |
| `core/acoustics/occlusion/PathCapture.java` | capture optionnelle du traceur |
| `core/acoustics/occlusion/OcclusionTracer.java` | paramètre `PathCapture` facultatif |
| `core/acoustics/reverb/EchoFinder.java` | `group(...)` |
| `client/audio/render/SoundViewSnapshots.java` | dernières vues publiées |
| `client/audio/render/OcclusionScheduler.java`, `ReverbScheduler.java` | capture et publication quand la couche est active |
| `client/debug/SoundViewEntries.java` | les deux entrées F3+F6 et l'état des couches |
| `client/debug/SoundViewKey.java` | `KeyMapping` et changement de mode |
| `client/debug/SoundViewRenderer.java` | gizmos |
| `client/mixin/KeyboardHandlerMixin.java` | F3+U |
| `lang/en_us.json`, `fr_fr.json` | nom de la touche et messages |

## 9. Tests

JUnit (`src/test`, package `core`) :
- `SoundViewModeTest` : cycle complet, construction depuis les deux booléens.
- `LossColorsTest` : paliers 0/10/30 dB exacts, interpolation, valeurs au-delà, muet.
- `OcclusionTracerTest` / `DetourTest` : avec une capture, un mur de pierre donne un croisement au bon voxel avec la bonne perte, l'eau donne absorption et interfaces, les extrémités ne sont pas notées, la troncature à 64 fonctionne. Les détours capturés sont exactement ceux qui ont été sommés, avec leurs points. Sans capture, les résultats sont identiques (tests existants).
- `EchoFinderTest` : `group` renvoie les indices attendus, `find` est inchangé.
- `PathViewTest`, `RoomViewTest` : ordre enceinte → oreille, cumul, points d'impact, rayons ouverts, marquage de l'écho, cas du fluide.

Le rendu et la touche ne sont pas testables en JUnit : ils sont vérifiés en jeu par l'utilisateur. Scène de contrôle : une salle de pierre fermée, une enceinte dans la pièce voisine, une porte ouverte entre les deux, et un bassin d'eau pour les interfaces.

## 10. Hors périmètre
- Pas de visualisation pour les sons vanilla, ni de réglage en direct des paramètres.
- Pas de rendu serveur ni de partage entre joueurs.
- Pas d'historique : seul le dernier calcul est affiché.
