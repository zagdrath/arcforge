# Releasing

A release is a pushed `vX.Y.Z` tag. `.github/workflows/release.yml` builds that commit, runs the game tests and
publishes a GitHub Release with two jars and the version's section of `CHANGELOG.md` as its notes. Its `curseforge`
job then uploads the mod jar and the same notes to
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/arcforge) (project 1714407). `build.yml` runs the same
build and game tests on every push, so a release shouldn't be the first place a failure shows up.

## The two jars

- `arcforge-X.Y.Z+26.3.jar` is the mod, versioned by `mod_version` in `gradle.properties`. Players install it. It
  goes on the GitHub Release and on CurseForge.
- `arcforge-api-A.B.C.jar` is the API on its own (see [API.md](API.md)), versioned by `ArcforgeApi.API_VERSION`. Mod
  developers compile against it; players don't install it, since the mod jar already contains its classes. It goes
  on the GitHub Release only, never on CurseForge.

The GitHub Release's notes end with a line saying which jar is which. If the API changed, raise `API_VERSION` (and
its `MAJOR` / `MINOR` / `PATCH` parts) before the release, following the API's own semantic versioning; if it didn't,
the release attaches the same API version as the last one.

## Cutting a release

1. Pick the version from the rules at the top of `CHANGELOG.md` (the Unreleased section suggests one).
2. In `CHANGELOG.md`:
   - rename `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD` and delete its "Suggested version" line;
   - add a new, empty `## [Unreleased]` above it;
   - at the bottom, point `[Unreleased]` at `https://github.com/zagdrath/arcforge/compare/vX.Y.Z...HEAD`, and add
     `[X.Y.Z]: https://github.com/zagdrath/arcforge/compare/vPREVIOUS...vX.Y.Z`.
3. Set `mod_version=X.Y.Z` in `gradle.properties`.
4. Check it all before tagging:

   ```sh
   bash .github/scripts/release-notes.sh X.Y.Z      # the same checks release.yml runs; writes release-notes.md
   ./gradlew build runGameTestServer
   ```

   `build/libs` should then have `arcforge-X.Y.Z+26.3.jar` and `arcforge-api-A.B.C.jar`.

5. Commit ("Release X.Y.Z: ..."), then tag and push:

   ```sh
   git tag -a vX.Y.Z -m "Arcforge X.Y.Z"
   git push origin main vX.Y.Z
   ```

6. Watch the Release run on the Actions tab. When it's green, the release is on the Releases page with both jars,
   and the mod jar is on CurseForge (it shows there once CurseForge has approved it, usually within minutes).

A tag with a pre-release part (`v2.6.0-beta.1`, with `mod_version=2.6.0-beta.1`) is published as a pre-release.

## CurseForge

The upload is `.github/scripts/curseforge_upload.py`. It sends:

- the mod jar and the version's changelog section, as Markdown (without the GitHub Release's line about the jars);
- the display name "Arcforge X.Y.Z";
- the game versions Minecraft 26.3, NeoForge, Java 25, Client and Server;
- `jei` and `jade` as optional dependencies;
- the file type: release, or beta / alpha for a pre-release version.

It finds CurseForge's game version ids by name on each upload, and fails if CurseForge doesn't list the Minecraft
version yet.

It needs a CurseForge upload API token in the `CURSEFORGE_TOKEN` repository secret. Make the token at
authors.curseforge.com (account settings, API tokens), then run `gh secret set CURSEFORGE_TOKEN -R
zagdrath/arcforge` and paste it in. Don't commit the token or put it anywhere else. To check the metadata without
uploading, run the script with `--dry-run` and the token in `CURSEFORGE_TOKEN` (the arguments are in release.yml
and at the top of the script).

## If the release run fails

If only the `curseforge` job failed (a bad token, CurseForge down, an unknown game version), the GitHub Release is
already out: fix the cause, for example the secret, and use **Re-run failed jobs** on the run. It retries only the
upload, with the jar and notes the `release` job kept.

Otherwise the tag is already pushed but no release exists. Fix the problem, commit, then move the tag:

```sh
git push origin :refs/tags/vX.Y.Z
git tag -f -a vX.Y.Z -m "Arcforge X.Y.Z"
git push origin vX.Y.Z
```
