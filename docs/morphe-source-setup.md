# Making this repository a working Morphe patch source

Analysis of [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches) (v3.10.0)
against this repository, with the requirements taken from the client source itself
(`MorpheApp/morphe-manager` v1.30.0). Date of analysis: 2026-09-14.

## 1. Summary

The source tree is **complete**. A file-by-file diff against the upstream repository shows nothing
that upstream has and this repo lacks: `gradle/wrapper`, `settings.gradle.kts`,
`patches/build.gradle.kts`, `patches-bundle.json`, `patches-list.json` and
`.github/workflows/release.yml` are all present, and `release.yml` is byte-identical to upstream's.

What was missing is **publishing**, not code. Morphe Manager never reads a repository's code; it
reads exactly two artifacts from the `main` branch:

1. `patches-bundle.json` — the manifest (version, timestamp, release notes, download URL)
2. the `.mpp` bundle that the manifest's `download_url` points at

Before this analysis, (1) still described the *upstream* v3.10.0 release and (2) did not exist here
as a real bundle.

| Requirement | upstream (jasonwu1994) | this repo (moeunzinkh-debug/Gboard-) |
| --- | --- | --- |
| Public GitHub/GitLab repository | public | public |
| `patches-bundle.json` on `main` | v3.10.0, `download_url` → own release | was a copy of upstream's manifest, pointing at `jasonwu1994` |
| Semver release tag | `v3.10.0`, `v3.9.0`, `v3.10.0-dev.3` … | only a non-semver tag `Gboardpatch` (never triggers the workflow) |
| Complete `.mpp` asset | `patches-3.10.0.mpp` = 7,895,839 bytes | `Gboard--main.mpp` = 31,391 bytes (no dex → cannot be loaded) |
| CI able to build | `MORPHE_PACKAGES_TOKEN` secret present | never ran (`actions/runs` is empty) |
| README with add-source link | present | was empty (1 byte) |
| `CHANGELOG.md` on `main` | present | present (now starting at 1.0.0) |
| `patches-bundle.png` icon | optional, not present | optional, not present |

## 2. How Morphe Manager resolves a source

From `MorpheApp/morphe-manager`:

1. **URL translation.** `PatchBundleRepository.normalizeRemoteBundleUrl()` turns
   `github.com/owner/repo` into
   `https://raw.githubusercontent.com/owner/repo/main/patches-bundle.json`. A URL containing
   `/tree/<branch>/...` keeps that branch; enabling *Pre-release patches* rewrites it to `dev`.
2. **Branch names are hardcoded.** `RemotePatchBundle.BRANCH_STABLE = "main"` and
   `BRANCH_DEV = "dev"`. The default branch of the repository is never queried, so a repo whose
   default branch is `master` (or whose manifest only lives on a feature branch) is invisible to
   the manager — *Metadata N/A* at best.
3. **The manifest is the only metadata source.** `network/dto/PatchesReleaseInfo.kt` requires
   `version`, `download_url`, `created_at` and `description`; `signature_download_url` is optional
   (empty string counts as absent). `version` is normalised to `v<semver>` for update comparison,
   and `created_at` must parse as ISO-8601 (a missing timezone is treated as UTC).
4. **Only `download_url` is downloaded.** `RemotePatchBundle.download()` stores that asset as
   `patches.jar` in the source's directory. For a third-party JSON source the manager does *not*
   enumerate GitHub releases, so a `download_url` aimed at another repository silently installs
   that other project's bundle.
5. **The bundle is a jar/zip with a manifest.** The Gradle plugin (`app.morphe.patches`) writes
   `Name`, `Description`, `Version`, `Timestamp`, `Source`, `Author`, `Contact`, `Website`,
   `License` and `Patcher-Version` into `META-INF/MANIFEST.MF`, and `buildAndroid` merges the D8
   output (`classes*.dex`) into the same archive. On load, `PatchBundleSource.load()` rejects files
   under the minimum size and `PatchBundleRepository.loadMetadata()` calls
   `PatchBundle.Loader.metadata(bundle)` — that is where the "Patches 40/41" count on the card
   comes from. A bundle without dex therefore downloads fine but shows an error / zero patches.
6. **Patcher compatibility gate.** `isPatcherOutdated()` compares the bundle's `Patcher-Version`
   with the one shipped in the manager (`BuildConfig.PATCHER_VERSION`; v1.30.0 ships 1.13.0).
   This repo pins 1.8.0, so it loads on any current manager. Bumping `morphe-patcher` past the
   version the installed manager carries makes the source unusable until the manager updates.
7. **Nice-to-have files, never blocking.** `CHANGELOG.md` next to the manifest feeds the changelog
   dialog; `patches-bundle.png` next to it becomes the source icon (otherwise the owner avatar is
   used); the `about { }` block in `patches/build.gradle.kts` fills the bundle manifest.

> Changelog detail that matters when restarting versioning: `ChangelogParser.entriesNewerThan()`
> keeps entries whose version is *strictly greater* than the installed one. With a 1.0.0 install,
> leftover `3.x` headings in `CHANGELOG.md` would be presented as changes still to come, which is
> why the inherited history moved to `docs/upstream-changelog.md`.

## 3. What was missing here, item by item

### 3.1 The manifest pointed at someone else's release

```
"https://github.com/jasonwu1994/Gboard-patches/releases/download/v3.10.0/patches-3.10.0.mpp"
```

Adding `moeunzinkh-debug/Gboard-` therefore installed upstream's bundle: 40 patches, no
`Gemini Translation`, and the card's *Open in browser* link resolved to
`moeunzinkh-debug/Gboard-/releases/tag/v3.10.0` → 404. The manifest now declares
`1.0.0` with

```
"https://github.com/moeunzinkh-debug/Gboard-/releases/download/v1.0.0/patches-1.0.0.mpp"
```

which only becomes valid once that release exists.

### 3.2 The release asset was not a bundle

Upstream's `patches-3.10.0.mpp` (7.9 MB) comes from `./gradlew :patches:buildAndroid`: the plugin
bundles `runtimeClasspath` into the jar, runs D8 (`minApi 26`, release mode) and merges the dex
into the same archive, with `archiveExtension = "mpp"`.

This repository only had `Gboard--main.mpp` (31 KB) on a tag called `Gboardpatch`: no dex, no
extension, so the manager would download it and fail to load it. Sanity check for any build:

```bash
unzip -l patches/build/libs/patches-1.0.0.mpp | sed -n 1,25p   # classes.dex must appear
```

### 3.3 No semver tag, so the workflow never ran

`release.yml` triggers on `v*` (or `workflow_dispatch`) and, in *Validate synced release metadata*,
fails the run unless:

* the tag is `v<version>` and matches `gradle.properties`;
* `patches-bundle.json` carries the same `version`;
* its `download_url` equals `https://github.com/<owner>/<repo>/releases/download/<tag>/patches-<version>.mpp`;
* `signature_download_url` is empty.

It then publishes `patches/build/libs/patches-<version>.mpp` as the release asset. A tag named
`Gboardpatch` and an asset named `Gboard--main.mpp` satisfy none of that, which is also why this
repository has zero Actions runs.

### 3.4 No `MORPHE_PACKAGES_TOKEN`, so CI cannot resolve the plugin

`settings.gradle.kts` resolves `app.morphe.patches` (1.3.3) from
`https://maven.pkg.github.com/MorpheApp/registry`. GitHub Packages does not allow anonymous
reads, so the build needs a PAT with `read:packages`, consumed by CI through the
`MORPHE_PACKAGES_TOKEN` secret and locally through `gpr.user` / `gpr.key` (or the
`GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables).

### 3.5 Identity: everything said "upstream"

The bundle manifest in `patches/build.gradle.kts` declared `source`, `contact`, `website` and
`author` of the other project, and the README was empty. Both now describe this source
(`moeunzinkh`'s Gboard patches, version 1.0.0), with upstream credit kept where it belongs:
git history, `docs/upstream-changelog.md`, and the license section.

## 4. Publishing steps (what still has to happen outside the repository)

1. **Settings → Secrets and variables → Actions → New repository secret**
   name `MORPHE_PACKAGES_TOKEN`, value = a PAT with `read:packages`
   (fine-grained: `Contents: Read only` on `MorpheApp/registry`).
2. **Settings → Actions → General → Workflow permissions** → *Read and write permissions*
   (`release.yml` needs `contents: write` to create the release).
3. Keep metadata in sync on `main` (done here for 1.0.0): `gradle.properties`,
   `patches-bundle.json` (`version` + `download_url`), `CHANGELOG.md` heading, `patches-list.json`.
   Check with:
   ```bash
   python3 scripts/validate-source-metadata.py      # must print OK
   ```
4. Publish through CI:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   Or, without CI: `./gradlew :patches:buildAndroid generatePatchesList`, then upload
   `patches/build/libs/patches-1.0.0.mpp` to a release tagged `v1.0.0` (asset name must match).
5. Verify from a machine with internet access:
   ```bash
   curl -sL  https://raw.githubusercontent.com/moeunzinkh-debug/Gboard-/main/patches-bundle.json
   curl -sIL https://github.com/moeunzinkh-debug/Gboard-/releases/download/v1.0.0/patches-1.0.0.mpp | head -5
   ```
   Both must return 200 (or 302 → 200) and the `.mpp` must be multiple MB. Note that
   raw.githubusercontent.com serves edge-cached copies for a few minutes after a push.
6. On the phone: **Sources → `+` → Remote** → `https://github.com/moeunzinkh-debug/Gboard-`
   (or the deep link `https://morphe.software/add-source?github=moeunzinkh-debug/Gboard-`).
   The card must show **Patches 41** and **Version v1.0.0**.

## 5. Troubleshooting map

| Symptom in Morphe Manager | Actual cause | Fix |
| --- | --- | --- |
| "Invalid source URL" | The pasted URL is neither a repo URL nor a `.json` URL (e.g. a `.mpp` download link) | paste `https://github.com/<owner>/<repo>` or `.../patches-bundle.json` |
| Card shows **Metadata N/A** | No `patches-bundle.json` on `main` (or the manifest lives on another branch) | push the manifest to `main`; default branch must be usable as `main` |
| Download fails / source has 0 patches | `.mpp` asset missing (404) or is the wrong file | publish the release through `release.yml`, or upload `patches-<version>.mpp` yourself |
| Someone else's patch list appears | `download_url` still points at another repository | retarget `patches-bundle.json` at your own release |
| Bundle downloads but patch list is empty | `.mpp` has no dex (`jar` was run without `buildAndroid`) | rebuild with `:patches:buildAndroid` and re-publish |
| Manager says it must be updated | bundle `Patcher-Version` newer than the manager's patcher | keep `morphe-patcher` at or below the manager's version, or update the manager |
| CI fails while resolving the plugin | `MORPHE_PACKAGES_TOKEN` missing or lacking `read:packages` | add/replace the secret |
| CI fails at *Validate synced release metadata* | tag / `patches-bundle.json` / `gradle.properties` disagree | run `scripts/validate-source-metadata.py` before tagging |
| Added, but never updates | manifest edited on a branch other than `main`/`dev` | merge it into `main` |

## 6. Changed in this commit

| File | Change |
| --- | --- |
| `gradle.properties` | `version` 3.11.0 → **1.0.0** (this source starts its own line) |
| `patches-bundle.json` | version and `download_url` retargeted to this repository's `v1.0.0` release; refreshed `created_at` and release notes (EN + 中文) |
| `patches-list.json` | generated `version` synced to 1.0.0 |
| `patches/build.gradle.kts` | `about { }` (name / author / contact / website / source) now describes this project instead of the upstream one |
| `CHANGELOG.md` | new 1.0.0 entry at the top; `3.x` history moved out |
| `docs/upstream-changelog.md` | the inherited upstream history, kept for reference |
| `README.md` | written from scratch for this source: add instructions, patch table, requirements, build/release steps, version policy, license and provenance |
| `scripts/validate-source-metadata.py` | pre-publish validator mirroring `release.yml` plus the Morphe manifest contract |
| `docs/morphe-source-setup.md` | this document |

Unchanged on purpose: `.github/workflows/release.yml`, `settings.gradle.kts`, the Gradle wrapper
and all patch sources.

### Optional follow-ups

* Rename the repository away from the trailing `-` (`Gboard-`) so shared links cannot lose a
  character; after renaming, update `patches-bundle.json`, `patches { about { } }` and the README.
* Add `patches-bundle.png` beside `patches-bundle.json` for a source icon of our own.
* If dev builds should be installable, keep a `dev` branch with its own `patches-bundle.json`;
  users with *Pre-release patches* enabled read `dev` and get whichever channel has the higher version.
