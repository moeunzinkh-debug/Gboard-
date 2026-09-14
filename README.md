<h1 align="center">Gboard Patches (moeunzinkh fork)</h1>

<p align="center">
  Morphe patches for Gboard &mdash; upstream global + Taiwan-focused enhancements, plus our own additions.
</p>

<p align="center">
  <a href="https://github.com/moeunzinkh-debug/Gboard-/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/moeunzinkh-debug/Gboard-?display_name=tag&label=Release&style=for-the-badge"></a>
  <a href="https://morphe.software/add-source?github=moeunzinkh-debug/Gboard-"><img alt="Add to Morphe" src="https://img.shields.io/badge/Morphe-Add%20Source-00A8FF?style=for-the-badge"></a>
  <a href="https://github.com/jasonwu1994/Gboard-patches"><img alt="Upstream" src="https://img.shields.io/badge/Upstream-jasonwu1994%2FGboard--patches-6e5494?style=for-the-badge"></a>
</p>

## Add this source in Morphe Manager

* Deep link (tap on the phone): <https://morphe.software/add-source?github=moeunzinkh-debug/Gboard->
* Or manually: **Sources -> `+` -> Remote** and paste
  `https://github.com/moeunzinkh-debug/Gboard-`

Morphe Manager never looks at your code. It reads exactly one file,
`patches-bundle.json`, from the `main` branch of this repository, and downloads the `.mpp`
bundle that file points at. If the release that the manifest references does not exist, the
source cannot be installed. See [docs/morphe-source-setup.md](docs/morphe-source-setup.md)
for the full checklist of everything a Morphe source needs.

បញ្ចូលតាម Morphe Manager ដោយចុច link ខាងលើ ឬវាយ `https://github.com/moeunzinkh-debug/Gboard-`
ក្នុង Sources -> `+` -> Remote។ Manager អានតែឯកសារ `patches-bundle.json` នៅសាខា `main`
រួចទាញយកឯកសារ `.mpp` តាម `download_url` ដូច្នេះត្រូវមាន GitHub Release ជាមុនសិន។

## Patches (41)

| Patch | What it does |
| --- | --- |
| Access Points menu style | 切換新版或舊版 Access Points 選單樣式 |
| Add Gboard Signature Bypass | 攔截 Gboard 的簽章白名單檢查並強制通過 |
| Advanced Voice Typing | 啟用進階語音輸入（包含自動標點功能），並另外為不支援進階語音輸入的繁體中文語音啟用自動標點 |
| AI Writing Tools | 啟用 AI 撰寫工具，支援所有語言 |
| Backup & Restore | 匯出或還原全部 Patches 設定，並備份、比較及還原 Gboard PB/XML flag store |
| Change emoji size | 變更表情符號大小 |
| Clipboard Custom Character Limit | 自訂每個文字剪貼簿項目的最大字元數 |
| Clipboard Enhancements | 增強剪貼簿的保留時間、數量上限、預覽行數、倒數/建立時間、順序編號與欄數 |
| Close Proactive Suggestions | 在主動建議列顯示關閉按鈕 |
| Custom Symbols | 新增獨立的特殊符號分頁，長按逗號->愛心 |
| Developer options | 啟用 開發人員選項 與 Flag 編輯器，你可以自己修改Flag的值 |
| Emojis, stickers & GIFs Tab Order | 自訂 Gboard「Emojis, stickers & GIFs」底部 tabs 的排序，支援拖曳調整 |
| Enable accessibility layout | 啟用無障礙鍵盤配置 |
| Enable cursor trackpad mode | 長按空白鍵開啟游標觸控板與鎖定模式 |
| Enable Inline Autofill Suggestions | 啟用內嵌自動填入建議 |
| Enable OCR / Scan Text | 啟用 OCR / 掃描文字功能，支援 拉丁、中文、日文、韓文 與 天城文 辨識後端 |
| Enable split keyboard | 啟用分離式鍵盤 |
| English QWERTY Up-Flick Uppercase | 英文 QWERTY 鍵盤上滑大小寫 |
| Floating Web Search | 直接從 Gboard 開啟懸浮網頁，快速搜尋需要的資訊。 |
| FTP Server | 新增區域網路 FTP 伺服器，支援檔案瀏覽、傳輸與下載續傳 |
| G Logo on Spacebar | 在空白鍵顯示 G Logo，並隱藏語言名稱 |
| Gemini Translation | 在 Access Point 工具列新增 Gemini 翻譯按鈕，自動偵測語言並以 Gemini API 翻譯選取文字或整個輸入框；API 金鑰可在 Patches 設定中儲存。 |
| Grammar Checker | 啟用 修正和建議 > 文法檢查 |
| Hyperspeed Typing Animation | 持續快速輸入時顯示動畫，並支援所有鍵盤 |
| Incognito Mode Toggle | 在 Access Point 工具列新增無痕模式切換按鈕，並可設定無痕模式下是否啟用剪貼簿與語音輸入 |
| Inline Suggestions | 啟用 修正和建議 > 智慧撰寫 |
| Key Shape Selection | 啟用圓角按鍵，主題詳情 > 按鍵形狀 |
| Latin Globe Key Ignore Interval | 新增英文鍵盤地球鍵忽略時間覆寫，可獨立控制輸入後切語言延遲 |
| Long-Press Editing Shortcuts | 在英文 QWERTY 與注音鍵盤加入全選、復原、複製、剪下、貼上與重做長按快捷鍵 |
| Package Rename | 將套件名稱改成 dev.jason.com.google.android.inputmethod.latin，並可自訂 App 名稱，以便共存安裝 |
| Quick Insert | 啟用快速插入面板與工具列入口 |
| Rounded Keyboard Panel | 自訂鍵盤面板哪些角落呈現圓角，並分別設定上方與下方半徑。 |
| Settings Homepage Override | 允許切換新版或舊版 Gboard 設定頁面 |
| Simple Calculator | 直接輸入算式，在 Gboard 推薦列顯示可捲動算式與答案。 |
| Swipeable Custom Top Row | 滑動鍵盤第一排，在原生列與可自訂文字/JavaScript 列之間切換 |
| Top Toolbar Item Count | 自訂 Gboard 頂端工具列項目數量 |
| Use Bluetooth Microphone | 啟用 語音輸入 -> 使用藍芽麥克風 |
| Web Clipboard | 新增手機自架的 Web Clipboard，支援瀏覽器同步、配對碼與快速設定開關 |
| Zhuyin Bottom Row Key Sizes | 調整注音鍵盤底排按鍵大小 |
| Zhuyin Quick Traditional/Simplified Toggle | 注音 ㄥ 上滑快速切換繁簡 |
| Zhuyin Slide Input | 注音鍵盤支持上下滑輸入 |

## Building locally

Local builds resolve the `app.morphe.patches` Gradle plugin from the Morphe GitHub Packages
registry, which needs a GitHub token with `read:packages`:

```bash
export GITHUB_ACTOR=<your-github-user>
export GITHUB_TOKEN=<pat-with-read:packages>
./gradlew :patches:buildAndroid generatePatchesList
# result: patches/build/libs/patches-<version>.mpp  (~8 MB, contains classes*.dex)
python3 scripts/validate-source-metadata.py   # check the manifest before publishing
```

A sanity check on the produced bundle: `unzip -l patches/build/libs/patches-*.mpp` must list
`classes.dex` and `META-INF/MANIFEST.MF`. A bundle of a few tens of kilobytes is an empty
build and Morphe Manager will refuse to load it.

## Releasing (how the source becomes addable)

```bash
python3 scripts/validate-source-metadata.py          # must pass
git tag v<version-in-gradle.properties>
git push origin v<version-in-gradle.properties>      # .github/workflows/release.yml builds + publishes
```

The workflow requires the repository secret `MORPHE_PACKAGES_TOKEN` (a PAT with `read:packages`),
and it fails fast when `gradle.properties`, `patches-bundle.json` and the tag disagree.

## Upstream & credits

Based on [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches)
(GPL-3.0). All upstream patches, docs and design remain the work of Jason Wu and contributors;
this fork adds our own patches (see [CHANGELOG.md](CHANGELOG.md)).
