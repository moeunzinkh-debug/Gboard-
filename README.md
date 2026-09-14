# Gboard Patches (moeunzinkh)

A Morphe patch source for Gboard, maintained and published independently from this repository.
**41 patches**, version **1.0.0** — every file a Morphe client needs is served from
`github.com/moeunzinkh-debug/Gboard-`, not from any other project.

Version numbering for this source starts at **1.0.0** and does not continue the inherited `3.x`
line; that history is preserved in [docs/upstream-changelog.md](docs/upstream-changelog.md).

## Add the source in Morphe Manager

1. Tap this deep link on the phone:
   <https://morphe.software/add-source?github=moeunzinkh-debug/Gboard->
2. Or do it by hand: **Sources → `+` → Remote**, paste `https://github.com/moeunzinkh-debug/Gboard-`,
   press **Add**.
3. A healthy install shows **Patches 41** and **Version v1.0.0** on the source card.

The manager never inspects the repository's code. It reads exactly two things from the `main`
branch of this repo:

| File | Role |
| --- | --- |
| `patches-bundle.json` | declares `version`, `created_at`, release notes (`description`) and `download_url` |
| `patches-<version>.mpp` (release asset) | the actual bundle: patch dex, extension dex and resources |

So a GitHub Release carrying `patches-1.0.0.mpp` must exist before the source can be added.
The full requirement list is documented in [docs/morphe-source-setup.md](docs/morphe-source-setup.md).

## Patches

Target: `com.google.android.inputmethod.latin`, Gboard version(s) currently declared:
`18.0.3.954559732-release-arm64-v8a`

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

This table is generated from `patches-list.json` (`./gradlew generatePatchesList`).

## Requirements

* Android 8.0 (API 26) or newer — same floor as Morphe itself.
* A Morphe Manager whose bundled patcher is **1.8.0 or newer** (the bundle manifest declares
  `Patcher-Version: 1.8.0`). A source built against a newer patcher than the installed manager
  cannot be loaded.
* The installed Gboard version must appear in each patch's `Compatibility` declaration.

## Building locally

The `app.morphe.patches` Gradle plugin is resolved from the Morphe GitHub Packages registry, which
rejects anonymous reads, so a token is required:

```bash
export GITHUB_ACTOR=<github-username>
export GITHUB_TOKEN=<pat-with-read:packages>
./gradlew :patches:buildAndroid generatePatchesList
unzip -l patches/build/libs/patches-1.0.0.mpp | head     # must list classes.dex
```

A `.mpp` of a few tens of kilobytes means the build did not dex anything, and the manager will
fail to load it. A complete bundle for this project is roughly 8 MB.

## Releasing

```bash
# 1. bump gradle.properties version, patches-bundle.json (version + download_url) and CHANGELOG.md
python3 scripts/validate-source-metadata.py          # must print OK
git commit -am "chore: release <version>"
git push origin main
# 2. let CI build and publish
git tag v<version> && git push origin v<version>
```

`.github/workflows/release.yml` then builds with `:patches:buildAndroid generatePatchesList` and
attaches `patches-<version>.mpp` to a new GitHub Release. Prerequisites in repository settings:

* secret `MORPHE_PACKAGES_TOKEN` — a PAT with `read:packages` (Morphe registry access for CI)
* Actions → General → Workflow permissions → **Read and write permissions**

Version policy: `fix:` bumps the patch number, `feat:` the minor one, breaking changes the major
one, and pre-releases are tagged `1.1.0-dev.N` with the manifest mirrored on the `dev` branch so
that users who enable *Pre-release patches* get them.

## Optional polish

* `patches-bundle.png` next to `patches-bundle.json` becomes the source icon in the manager.
* A distinct icon and name keep this source visually separate from other Gboard patch sources.

## License and provenance

* Licensed under **GPL-3.0** (see [LICENSE](LICENSE)) with the conditions in [NOTICE](NOTICE).
* This project continues work published upstream under the same GPL-3.0 licence; upstream authorship
  is preserved in the git history and in [docs/upstream-changelog.md](docs/upstream-changelog.md).
* Per `NOTICE` §7c, the name and branding of this source are distinct from both the upstream
  project and the Morphe project itself.

## Support

* Issues and feature requests: <https://github.com/moeunzinkh-debug/Gboard-/issues>
* How Morphe Manager resolves a source, and a troubleshooting table:
  [docs/morphe-source-setup.md](docs/morphe-source-setup.md)
