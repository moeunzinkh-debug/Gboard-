# Changelog

All notable changes to **Gboard Patches (moeunzinkh)** are documented here.
This source starts its own version line at 1.0.0; the inherited 3.x history lives in
[docs/upstream-changelog.md](docs/upstream-changelog.md).

## [Unreleased]

## [1.0.2](https://github.com/moeunzinkh-debug/Gboard-/releases/tag/v1.0.2) (2026-09-15)

### ✨ Fixes

* **Gboard — Gemini Translation:** tapping the toolbar button now reliably opens the Google
  Translate style panel inside the keyboard: language chips with ⇄ swap, a source card that
  mirrors the focused field, and a result card with ✓ insert and copy actions. The previous
  docking asked the framework's inputArea frame for extra padding and pulled the bar into it with
  a negative margin — when the IME window did not grow, the bar stayed invisible and the tap
  appeared to do nothing. The bar now docks straight into Gboard's input view (a FrameLayout)
  above the keyboard rows with elevation, the same placement the calculator suggestion strip
  already uses, and when the latched input view is stale or missing it is recovered from the IME
  window's own inputArea.
* **Gboard — Gemini Translation:** the panel opens even before a Gemini API key is configured;
  the result card carries an inline hint about the missing key instead of the tap degrading to a
  toast. A missing key no longer hides the translation UI.

### ✨ 修正

* **Gboard — Gemini Translation:** 點工具列按鈕現在會確實在鍵盤內開啟 Google Translate 風格的翻譯
  面板：語言 chip 與 ⇄ 交換、原文卡片（自動同步輸入框）、翻譯結果卡片（✓ 插入、複製）。舊的停靠
  方式依賴 inputArea 增加 padding 並以負邊距把翻譯列拉進去 — IME 視窗沒長高時整列隱形、點了像
  沒反應；現在直接停進 Gboard 的 input view、浮在鍵盤列上方（與計算機列相同做法），view 過期時
  也會從 IME 視窗重新找回。
* **Gboard — Gemini Translation:** 尚未設定 Gemini API 金鑰也會先開啟面板，並在結果卡片內顯示提示，
  不再只跳 toast。

### ✨ ការកែសម្រួល (ភាសាខ្មែរ)

* **Gboard — Gemini Translation:** ពេលចុចប៊ូតុងបកប្រែ ឥឡូវបង្ហាញផ្ទាំងបកប្រែរចនាបថ Google
  Translate នៅក្នុងក្ដារចុះជាក់លាក់ — ភាសាដើម/គោលដៅ មាន ⇄ ដើម្បីប្ដូរ ប្រអប់អត្ថបទដើម
  (ស្វ័យបរវត្តិពីប្រអប់បញ្ចូល) និងប្រអប់លទ្ធផល ព្រមទាំង ✓ បញ្ចូល និងប៊ូតុងចម្លង។ ការដាក់
  របារពីមុនពឹងផ្អែកលើការបន្ថែម padding ដល់ inputArea ដែលពេលខ្លះធ្វើឱ្យរបារមិនបង្ហាញសោះ;
  ឥឡូវរបារត្រូវដាក់ចូលក្នុង input view របស់ Gboard ផ្ទាល់ ពីលើជួរក្ដារចុច ដូចនឹងរបារ
  ការគណនាដែរ។
* **Gboard — Gemini Translation:** ផ្ទាំងបើកបានទាំងពេលមិនទាន់ដាក់ Gemini API key ហើយបង្ហាញ
  ការណែនាំនៅក្នុងប្រអប់លទ្ធផល។

## [1.0.1](https://github.com/moeunzinkh-debug/Gboard-/releases/tag/v1.0.1) (2026-09-15)

### ✨ Fixes

* **Gboard — Gemini Translation:** the toolbar button no longer opens a full-screen dialog that
  covers the app and hides the keyboard. It now docks a Google Translate style bar inside Gboard's
  own input window: back button, source and target language chips (tap to pick, ⇄ to swap, both
  saved back into Patches settings), the text to translate and the result, with ↻ to translate,
  ✓ to write the translation into the field and a copy button. The keyboard stays fully usable
  below the bar; when there is no live keyboard to dock into, the tap still translates in place.
* **Gboard — Gemini Translation:** fixed a patch-time crash (`Collection is empty`) that aborted
  the whole session on some APK/patcher combinations. The one-line `onStartInputView` entry call
  is validated against the expected 18.0.3 method shape and encoded directly instead of going
  through smali compilation, so patching succeeds wherever the method is intact — and names the
  actual mismatch when the APK really differs.

### ✨ 修正

* **Gboard — Gemini Translation:** 點工具列按鈕不再跳出蓋住畫面、讓鍵盤消失的全螢幕對話框；
  現在會直接停靠在 Gboard 鍵盤視窗內，鍵盤上方出現翻譯列（返回鍵、來源／目標語言 chip、
  ⇄ 交換、原文與結果），↻ 翻譯、✓ 寫回輸入框、另有複製按鈕，鍵盤可照常使用；
  若當下沒有可停靠的鍵盤，則退回原本的選取文字就地翻譯。
* **Gboard — Gemini Translation:** 修正在某些 APK／patcher 組合下 patch 階段因
  `Collection is empty` 導致整個 session 中斷的問題；`onStartInputView` 單行進入點改為先驗證
  18.0.3 方法形狀再直接編碼呼叫，不再經過 smali 編譯。方法完好即可成功打入；
  APK 真的不符時也會明確回報差異內容。

### ✨ ការកែសម្រួល (ភាសាខ្មែរ)

* **Gboard — Gemini Translation:** បានកែកំហុស `Collection is empty` ដែលធ្វើឱ្យដំណើរការ
  patch ទាំងមូលបរាជ័យនៅពេលអនុវត្តលើការបញ្ចូលគ្នាខ្លះនៃ APK/patcher។ ឥឡូវនេះ
  ការហៅចូល `onStartInputView` ត្រូវបានផ្ទៀងផ្ទាត់ជាមុន ហើយសរសេរជា instruction
  ដោយផ្ទាល់ ដោយមិនឆ្លងកាត់ការចម្លង smali ទៀតទេ — ដូច្នេះការ patch ទៅជាជោគជ័យ។
* **Gboard — Gemini Translation:** ប៊ូតុងបកប្រែលើរបារឧបករណ៍ លែងបើកផ្ទាំងពេញអេក្រង់
  ដែលបិទក្ដារចុចទៀតហើយ; វាដាក់របារបកប្រែនៅក្នុងបង្អួចក្ដារចុចរបស់ Gboard ផ្ទាល់។

## [1.0.0](https://github.com/moeunzinkh-debug/Gboard-/releases/tag/v1.0.0) (2026-09-14)

### ✨ Highlights

* **Gboard:** first release published as an independent Morphe patch source of this repository.
  `patches-bundle.json` and the `.mpp` asset both come from `github.com/moeunzinkh-debug/Gboard-`,
  so installing or updating these patches no longer depends on any other project's releases.
* **Gboard:** 41 patches included, among them `Gemini Translation` (translate the selected text
  from the Access Point toolbar; API key, model and target language live in Patches settings and
  the key stays on-device), `Backup & Restore`, `Web Clipboard`, `FTP Server`, `Simple Calculator`
  and `Clipboard Enhancements`.
* **Gboard:** bundle metadata (name, author, contact, website) now points at this repository.
* **Gboard:** version numbers restart at 1.0.0 and follow semver from here on.

### ✨ 重點

* **Gboard:** 首次以本 repository 獨立的 Morphe patch source 發布;`patches-bundle.json` 與 `.mpp`
  都來自 `github.com/moeunzinkh-debug/Gboard-`,不再依賴其他專案的 release。
* **Gboard:** 內含 41 個 Patch,包括 `Gemini Translation`(在 Access Point 工具列翻譯選取文字,
  API 金鑰僅儲存於裝置上)、`Backup & Restore`、`Web Clipboard`、`FTP Server`、`Simple Calculator`
  與 `Clipboard Enhancements`。
* **Gboard:** bundle 的 metadata(name / author / contact / website)改指向本 repository。
* **Gboard:** 版本由 1.0.0 重新開始,後續更新遵循 semver。
