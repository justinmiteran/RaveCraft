# Publier une version

## Numérotation

Versionnage sémantique `MAJEUR.MINEUR.CORRECTIF`, défini à **un seul endroit** : `mod_version` dans `gradle.properties`. Il est injecté dans `fabric.mod.json` et dans le nom du jar (`ravecraft-X.Y.Z.jar`).

| Version | Quand |
|---|---|
| `0.x.y` | Avant la première version stable : tout peut encore changer (mondes non garantis compatibles). Publiée en **pré-release**. |
| Correctif `x.y.Z` | Corrections de bugs, sans nouveau contenu. |
| Mineure `x.Y.0` | Nouveau contenu (un sous-projet, de nouveaux blocs…) compatible avec les mondes existants. |
| Majeure `X.0.0` | Changement qui casse les mondes, les recettes ou la config existants. |

Un suffixe (`1.2.0-beta.1`) publie aussi en pré-release. La version de Minecraft (26.3) figure dans le titre de la release.

## Procédure

1. Vérifier que `main` est à jour et que la CI est verte.
2. Changer `mod_version` dans `gradle.properties` (ex. `0.2.0`).
3. Cocher l'étape correspondante dans `docs/SPEC.md`.
4. Vérifier en local, puis committer :
   ```bash
   ./gradlew build
   git commit -am "Release 0.2.0"
   ```
5. Créer et pousser le tag, **précédé de `v`** :
   ```bash
   git tag v0.2.0
   git push origin main v0.2.0
   ```

Le workflow `release` (`.github/workflows/release.yml`) prend alors le relais :
- il refuse le tag s'il ne correspond pas à `mod_version` ;
- il relance le build complet (tests JUnit et GameTests) ;
- il crée la release GitHub avec le jar du mod et des notes générées à partir des commits.

## Si ça échoue

- **Tag ≠ `mod_version`** : supprimer le tag (`git tag -d v0.2.0 && git push origin :refs/tags/v0.2.0`), corriger, recommencer.
- **Tests rouges** : aucune release n'est créée. Corriger sur `main`, puis déplacer le tag de la même façon.

## Plus tard

Publication sur Modrinth et CurseForge : à ajouter au workflow `release` quand le mod sera prêt à être diffusé publiquement. Il faudra un jeton API stocké dans les secrets du dépôt.
