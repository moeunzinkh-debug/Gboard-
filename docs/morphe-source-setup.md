# ការបញ្ចូល source នេះចូល Morphe Manager — តើយើងខ្វះអ្វី?

> វិភាគលើ [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches) (v3.10.0)
> ធៀបនឹង repo នេះ (v3.11.0) ដោយយកលក្ខខណ្ឌពិតពីកូដរបស់ `MorpheApp/morphe-manager` (v1.30.0)។
> ថ្ងៃធ្វើវិភាគ៖ 2026-09-14។

## 1. សេចក្ដីសង្ខេប

**កូដក្នុង repo នេះគឺពេញលេញហើយ។** ពេល diff ទាំងស្រុងជាមួយ repo របស់គេ គ្មានកសារណាមួយដែលគេមាន
ហើយយើងខ្វះទេ — `gradle/wrapper`, `settings.gradle.kts`, `patches/build.gradle.kts`,
`patches-bundle.json`, `patches-list.json` និង `.github/workflows/release.yml` សុទ្ធតែមាន
(ហើយ `release.yml` របស់យើងដូចគ្នាបេះបិទនឹងរបស់គេ)។

អ្វីដែលខ្វះ **មិនមែនជាកូដទេ** គឺខ្វះ **ការបោះពុម្ពផ្សាយ (publish)**។ Morphe Manager មិនអាន source code
ទាល់តែសោះ វាអានតែរបស់ពីរយ៉ាង៖

1. ឯកសារ `patches-bundle.json` នៅសាខា `main` នៃ repo
2. ឯកសារ bundle `.mpp` ដែល JSON នោះចង្អុលទៅ (`download_url`)

ឥឡូវនេះ (1) នៅចង្អុលទៅ repo របស់គេ ហើយ (2) របស់យើងគ្មាននៅលើ GitHub ទេ។

| អ្វីដែល Manager ត្រូវការ | jasonwu1994 (គេ) | moeunzinkh-debug/Gboard- (យើង) |
| --- | --- | --- |
| Repo សាធារណ នៅលើ GitHub/GitLab | ✅ public | ✅ public |
| `patches-bundle.json` នៅសាខា `main` | ✅ v3.10.0 + `download_url` ទៅ release ខ្លួនឯង | ⚠️ មាន តែជាចម្លងរបស់គេ (v3.10.0 + URL ទៅ `jasonwu1994`) |
| Release tag តាម semver | ✅ `v3.10.0`, `v3.9.0`, `v3.10.0-dev.3` … | ❌ មានតែ tag `Gboardpatch` (workflow មិនរត់) |
| Asset `.mpp` ពេញលេញ | ✅ `patches-3.10.0.mpp` = **7,895,839 bytes** | ❌ `Gboard--main.mpp` = **31,391 bytes** (build ទទេ/មិនពេញ) |
| CI build បាន (secret ពី Morphe registry) | ✅ `MORPHE_PACKAGES_TOKEN` | ❌ workflow មិនធ្លាប់រត់សោះ (Actions runs ទទេ) |
| README + deep link add-source | ✅ 10 KB ពេញលេញ | ❌ README ទទេ (1 byte) |
| `CHANGELOG.md` នៅសាខា `main` | ✅ | ✅ (មាន `3.11.0` នៅកំពូល) |
| រូបតំណាង `patches-bundle.png` | ⬜ ជម្រើស | ⬜ ជម្រើស |

---

## 2. របៀបដែល Morphe Manager ដំណើរការ (ហេតុអ្វីគេ add បាន)

យកចេញពីកូដរបស់ `MorpheApp/morphe-manager`៖

1. **URL ដែលយើងវាយត្រូវបម្លែង** — `PatchBundleRepository.normalizeRemoteBundleUrl()` យក
   `github.com/owner/repo` → `https://raw.githubusercontent.com/owner/repo/main/patches-bundle.json`
   (បើ URL ជា `/tree/<branch>/...` វាប្រើ branch នោះ; បើបើក *Pre-release patches* វាប្តូរទៅ `dev`)។
2. **ការត្រួតពិនិត្យពេលវាយ URL មានតែទម្រង់ប៉ុណ្ណោះ** — `rememberUrlValidation()` គ្រាន់តែហៅ
   `normalizeRemoteBundleUrl()` មើលថាវា parse បាន ឬអត់។ ដូច្នេះប្រអប់ **Add** នៅតែបើកដដែល
   ទោះ release របស់យើងមិនមានក៏ដោយ — វាហាក់ដូចជា add បាន ប៉ុន្តែ bundle បើកមិនបានទេ។
3. **ឯកសារតែមួយដែលវាអាន** — `network/dto/PatchesReleaseInfo.kt` ត្រូវការ
   `version`, `download_url`, `created_at`, `description` (+ `signature_download_url` ជម្រើស)។
   បាត់វាលដែលមិនអនុញ្ញាត null → parse បរាជ័យ → card បង្ហាញ **Metadata N/A**។
   `version` ត្រូវជា semver (Manager បន្ថែម `v` ឲ្យស្វ័យប្រវត្តិ) ដើម្បីបរៀបធៀប update;
   `created_at` ត្រូវជា ISO-8601។
4. **វាទាញយកតែ `download_url`** — `RemotePatchBundle.download()` ទាញ asset នោះទៅជា `patches.jar`
   ក្នុងថតរបស់ source នីមួយៗ។ Manager **មិន** ស្វែងរក release ដោយខ្លួនឯងទេ (លើកលែងតែ source ផ្លូវការរបស់ Morphe)។
   បើ `download_url` ចង្អុលទៅ repo គេ → អ្នកកំពុងដំឡើង **bundle របស់គេ** ទោះបញ្ចូល repo ខ្លួនឯងក៏ដោយ។
5. **បន្ទាប់មកវាបើក `.mpp` ជា zip និងអាន `META-INF/MANIFEST.MF`** — `requireNonEmptyBundleFile()` មិនឲ្យ
   ឯកសារតូចពេកឆ្លងកាត់ ហើយ `loadMetadata()` ហៅ `PatchBundle.Loader.metadata(bundle)` ដើម្បីបញ្ជី patch
   (លេខ «Patches 40» នៅលើ card ចេញពីចំណុចនេះ)។ បើ `.mpp` គ្មាន dex → load បរាជ័យ → card បង្ហាញ error/0 patches។
6. **កំណែ patcher** - manifest ក្នុង bundle មានវាល `Patcher-Version` (Gradle plugin ដាក់អោយស្វ័យបរវត្តិ
   ពី dependency `app.morphe:morphe-patcher`)។ `isPatcherOutdated()` ប្រៀបធៀបវាជាមួយ patcher ដែល Manager ដាក់
   (Manager v1.30.0 = patcher **1.13.0**)។ យើង pin **1.8.0** ដែលទាបជាង ដូច្នេះយើងគ្មានបញ្ហានេះទេ
   (តែកុំ bump លើស 1.13.0 មុន Manager អាប់ដេត)។
7. **របស់ផ្សេងដែល Manager ទាញ ប៉ុន្តែមិនរារាំង** — `CHANGELOG.md` នៅក្បែរ JSON (មើល changelog),
   `patches-bundle.png` (រូបតំណាង; បើគ្មាន → ប្រើ avatar របស់ owner នៅលើ GitHub),
   និងវាល `Name/Description/Source/Author/Contact/Website/License` ដែលកំណត់ដោយ `patches { about { ... } }`។

> ⚠️ **សាខាត្រូវតែឈ្មោះ `main`** (ឬ `dev` សម្រាប់ pre-release)។ Manager មិនសួរ default branch ទេ។
> Repo នេះមាន `main` ហើយ ✅ ប៉ុន្តែកូដដែលនៅសាខា `arena/*` មិនត្រូវ Manager មើលឃើញទេ រហូតដល់ merge ចូល `main`។

---

## 3. អ្វីដែលគេមាន ហើយយើងខ្វះ

### 3.1 `patches-bundle.json` ត្រូវចង្អុលទៅ release របស់ខ្លួនឯង

មុនកែ ឯកសាររបស់យើងជាចម្លងរបស់គេ៖

```json
"download_url": "https://github.com/jasonwu1994/Gboard-patches/releases/download/v3.10.0/patches-3.10.0.mpp"
```

លទ្ធផល៖ add repo យើង → Manager ដំឡើង bundle របស់គេ (40 patches គ្មាន `Gemini Translation` របស់យើង)
ហើយ **Open in browser** នឹងទៅ `github.com/moeunzinkh-debug/Gboard-/releases/tag/v3.10.0` → 404។
ឥឡូវបានកែទៅ `https://github.com/moeunzinkh-debug/Gboard-/releases/download/v3.11.0/patches-3.11.0.mpp`
ហើយ — វានឹងដំណើរការលុះត្រាតែ release នោះមានកើត។

### 3.2 Asset `.mpp` ត្រូវតែជា bundle ពេញលេញ

* គេ៖ `patches-3.10.0.mpp` = **7.9 MB** ដែលកើតចេញពី `./gradlew :patches:buildAndroid`
  (plugin រុំ `runtimeClasspath` ចូល jar → D8 បង្កើត `classes.dex` → បញ្ចូលចូល zip ដដែល ហើយដាក់
  `archiveExtension = "mpp"`)។
* យើង៖ `Gboard--main.mpp` = **31 KB** នៅលើ release `Gboardpatch` — នេះមិនមែន bundle ពេញលេញទេ
  (គ្មាន dex/extension គ្រាន់តែ jar តូច ឬឯកសារដែល upload ដោយដៃ)។ Manager នឹងទាញយកបានជោគជ័យ
  ប៉ុន្តែបរាជ័យនៅពេល load វា។

របៀបត្រួតពិនិត្យខ្លីៗ៖ `unzip -l patches-<version>.mpp` ត្រូវបង្ហាញ `classes.dex`
(+ `classes2.dex` …) និង `META-INF/MANIFEST.MF`។

### 3.3 Tag semver + workflow

`release.yml` (ដូចគ្នារវាង repo ទាំងពីរ)៖

* រត់តែនៅពេល push tag `v*` ឬ `workflow_dispatch`
* build ដោយ `./gradlew :patches:buildAndroid generatePatchesList` ជាមួយ
  `GITHUB_TOKEN=${{ secrets.MORPHE_PACKAGES_TOKEN }}`
* ជំហាន **Validate synced release metadata** បរាជ័យភ្លាម បើ
  `tag` ≠ `version` ក្នុង `gradle.properties` ≠ `version` ក្នុង `patches-bundle.json`
  ឬ `download_url` មិនស្មើ `https://github.com/<owner>/<repo>/releases/download/<tag>/patches-<version>.mpp`
  ឬ `signature_download_url` មិនទទេ
* upload asset `patches/build/libs/patches-<version>.mpp`

→ tag `Gboardpatch` មិនធ្វើឲ្យ workflow រត់ ហើយឈ្មោះ asset `Gboard--main.mpp` ក៏ខុសអ្វីដែល workflow រំពឹង។
repo នេះមាន Actions run **សូន្យ** ព្រោះគ្មាន tag `v*` និងគ្មាន secret។

### 3.4 Secret សម្រាប់ build (ជំហានដែលងាយខ្វះ)

`settings.gradle.kts` ដកហូត plugin `app.morphe.patches` version `1.3.3` ពី
`https://maven.pkg.github.com/MorpheApp/registry`។ GitHub Packages **មិនអនុញ្ញាត anonymous read** ទេ
ដូច្នេះត្រូវការ PAT ដែលមាន scope `read:packages`៖

* CI → secret ឈ្មោះ `MORPHE_PACKAGES_TOKEN` (គេមាន យើងមិនមាន)
* build ក្នុងម៉ាស៊ីនផ្ទាល់ → `gpr.user` / `gpr.key` ក្នុង `gradle.properties` (ឬ env `GITHUB_ACTOR` / `GITHUB_TOKEN`)

### 3.5 ចំណុចខ្សោយតូចៗទៀតរបស់យើង (មិនរារាំង add ទេ តែគួរកែ)

* README ទទេ → អត់មាន deep link add-source និងបញ្ជី patch (ឥឡូវបានសរសេរថ្មី)។
* គ្មានប្រវត្តិ tag ចាស់ៗ → user មិនអាចត្រឡប់ជំនាន់ ឬប្ើ pre-release channel បានទេ។
* ឈ្មោះ repo `Gboard-` បញ្ចប់ដោយ `-` ងាយបាត់អក្សរពេលចម្លង link (ជម្រើស៖ rename ទៅ `Gboard-patches`)។
* មិនទាន់មាន `patches-bundle.png` → card ប្រើ avatar របស់ `moeunzinkh-debug`។

ផ្ទុយទៅវិញ របស់យើងមាន **41 patches (v3.11.0)** ច្រើនជាងគេ 40 (v3.10.0) ដោយសារ patch `Gemini Translation`។

---

## 4. អ្វីដែលត្រូវកែបន្ថែម ទើប add បាន (ជំហាន 1 ទៅ 6)

1. **Repo Settings → Secrets and variables → Actions → New repository secret**
   ឈ្មោះ `MORPHE_PACKAGES_TOKEN` តម្លៃ = PAT មាន scope `read:packages`
   (Fine-grained: `Contents: Read only` លើ `MorpheApp/registry`)។
2. **Settings → Actions → General → Workflow permissions** → *Read and write permissions*
   (release.yml ត្រូវការ `contents: write` ដើម្បីបង្កើត release)។
3. **Commit metadata ឲ្យត្រូវគ្នា** (បានធ្វើរួចក្នុង commit នេះ)៖ `gradle.properties version = 3.11.0`
   + `patches-bundle.json` (v3.11.0, download_url ទៅ release ខ្លួនឯង)។ ត្រួតពិនិត្យ៖
   `python3 scripts/validate-source-metadata.py` → ត្រូវបោះ `OK`។
4. **បោះពុម្ពផ្សាយតាម CI** (បន្ទាប់ពី merge ចូល `main`)៖
   ```bash
   git tag v3.11.0
   git push origin v3.11.0
   ```
   រង់ចាំ run ជាប់ → release `v3.11.0` មាន asset `patches-3.11.0.mpp`។
   បើចង់បោះពុម្ពផ្សាយពីម៉ាស៊ីនផ្ទាល់វិញ៖
   `./gradlew :patches:buildAndroid generatePatchesList` រួច upload
   `patches/build/libs/patches-3.11.0.mpp` ទៅ release tag `v3.11.0` (ឈ្មោះ asset ត្រូវដូចគ្នា)។
5. **ត្រួតពិនិត្យមុនបើកលើទូរស័ព្ទ**
   ```bash
   curl -sL https://raw.githubusercontent.com/moeunzinkh-debug/Gboard-/main/patches-bundle.json
   curl -sIL https://github.com/moeunzinkh-debug/Gboard-/releases/download/v3.11.0/patches-3.11.0.mpp | head -5
   ```
   ទាំងពីរត្រូវ 200 (ឬ 302 → 200) ហើយ `.mpp` ត្រូវមានទំហំប្រមាណ ៨ MB។
   ចំណាំ៖ raw.githubusercontent.com មាន cache ប៉ុន្មាននាទី ដូច្បើម្បីប្តូរ JSON ហើយឃើញភ្លាមៗគឺត្រូវរង់ចាំ។
6. **លើទូរស័ព្ទ** Sources → `+` → Remote → `https://github.com/moeunzinkh-debug/Gboard-`
   (ឬចុច `https://morphe.software/add-source?github=moeunzinkh-debug/Gboard-`) → Add។
   Card ត្រូវបង្ហាញ **Patches 41** និង **Version v3.11.0**។

### តារាងដោះស្រាយបញ្ហាបន្ថែម (quick fix table)

| រោគសញ្ញាក្នុង Morphe Manager | មូលហេតុពិត | វិធីដោះស្រាយ |
| --- | --- | --- |
| «Invalid source URL» | វាយ URL ដែលមិនមែន repo ឬ `.json` (ឧ. ចម្លងតំណ download `.mpp`) | វាយ `https://github.com/<owner>/<repo>` ឬ `.../patches-bundle.json` |
| card បង្ហាញ **Metadata N/A** | គ្មាន `patches-bundle.json` នៅសាខា `main` (ឬ default branch មិនមែន `main`) | push JSON ចូល `main`; កំណត់ default branch = `main` |
| download បរាជ័យ / source មាន 0 patches | asset `.mpp` 404 ឬជាឯកសារខុស (31 KB) | រត់ release.yml ឬ upload `patches-<version>.mpp` ឡើងវិញ |
| patch របស់គេចេញមក (40) មិនមែនរបស់យើង (41) | `download_url` នៅចង្អុលទៅ repo `jasonwu1994` | កែ `patches-bundle.json` ឲ្យចង្អុលទៅ release ខ្លួនឯង |
| bundle ទាញបាន ប៉ុន្តែ patch list ទទេ | `.mpp` គ្មាន dex (រត់តែ `jar` មិនរត់ `buildAndroid`) | `./gradlew :patches:buildAndroid` រួច publish ឡើងវិញ |
| Manager ទាមទារឲ្យអាប់ដេត Manager | `Patcher-Version` ក្នុង bundle ខ្ពស់ជាងរបស់ Manager | កុំ bump `morphe-patcher` លើសកំណែដែល Manager ដាក់ |
| CI បរាជ័យនៅជំហាន resolve plugin | គ្មាន secret `MORPHE_PACKAGES_TOKEN` | បន្ថែម secret (PAT `read:packages`) |
| CI បរាជ័យនៅ «Validate synced release metadata» | tag / JSON / gradle.properties មិនត្រូវគ្នា | រត់ `scripts/validate-source-metadata.py` មុន tag |
| add បាន ប៉ុន្តែមិនអាប់ដេត | កែ JSON នៅសាខាផ្សេងក្រៅ `main`/`dev` | merge ចូល `main` |

---

## 5. អ្វីដែលបានកែក្នុង commit នេះ

| ឯកសារ | ការផ្លាស់ប្តូរ |
| --- | --- |
| `patches-bundle.json` | `3.10.0` → `3.11.0`; `download_url` ប្តូរពី repo គេ → release របស់យើង; `created_at` ធ្វើបច្ចុប្បន្ភាព; `description` = កំណត់ហេតុ 3.11.0 (EN + 中文) |
| `README.md` | សរសេរថ្មី: deep link add-source, តារាង patch 41, ការ build/release, របៀបត្រួតពិនិត្យ metadata, និងការដាក់ប្រភពដើម (upstream credit) |
| `scripts/validate-source-metadata.py` | ស្គ្រីបត្រួតពិនិត្យ metadata មុនបោះពុម្ពផ្សាយ (ស្របតាមជំហាន validate ក្នុង `release.yml` + លក្ខខណ្ឌរបស់ Morphe Manager) |
| `docs/morphe-source-setup.md` | ឯកសារវិភាគនេះ |

**មិនបានកែ** (ដើម្បីរក្សាភាពស្របគ្នាជាមួយ upstream)៖ `.github/workflows/release.yml`,
`settings.gradle.kts`, `gradle.properties`, និងកូដ patch ទាំងអស់។
អ្វីដែលនៅខ្វះគឺ **secret + tag `v3.11.0` + release asset** ដែលត្រូវធ្វើក្រៅ repo (ចំណុច 4 ខាងលើ)។
