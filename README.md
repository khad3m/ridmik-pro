<div align="center">

# ⌨️ Ridmik Pro

**Private build of OpenRidmik with Gemini-powered voice typing (Bangla + English)**

Avro phonetic · Probhat · Jatiyo · Emoji · Clipboard manager · Number pad · Themes · **🎤 Voice typing**

</div>

---

## What is different from OpenRidmik?

This repository is a private fork of [devfahim00/OpenRidmik](https://github.com/devfahim00/OpenRidmik) with two changes:

### 1. 🎤 Voice typing with the Gemini API (free tier)

- A **mic button at the top-right corner** of the keyboard (next to the toolbar strip).
- Tap the mic → speak in **Bangla, English or a mix of both** → the recognised text lands at the cursor with natural punctuation.
- The audio is recorded on-device (16 kHz WAV) and sent **directly to Google's Gemini API with your own key** — no other server in between, nothing stored.
- Recording stops by itself after a pause (1.8 s of quiet) or a 25 s cap, or tap the mic again to stop early. Tap while "Recognising…" shows to cancel.
- **Setup (once)**: open *Ridmik Pro → Settings → Voice typing*, paste a free API key from [aistudio.google.com/apikey](https://aistudio.google.com/apikey), optionally run *Test key & connection*. On the first mic tap Android asks for the microphone permission.
- Model picker cycles `gemini-2.5-flash` → `gemini-2.5-flash-lite` → `gemini-2.0-flash`; if a model is ever unavailable the app automatically retries with the next one. Errors (bad key, quota, offline) surface as clear toasts — typing itself never breaks.

### 2. Small cleanups

- The "update available" checker that pointed at the original public repo was removed (this repo is private; updates come from its own releases).
- `applicationId` is **`com.ridmik.pro`** and the launcher label is **Ridmik Pro**, so it installs **side by side** with the original OpenRidmik — no need to uninstall anything.

Everything else (Avro/Probhat/Jatiyo layouts, suggestions, emoji, clipboard, themes, numpad) is unchanged from the upstream project.

## 📲 Install

1. GitHub Actions builds an APK on every push to `main` and publishes it as the **`debug-latest`** release — download `RidmikPro-debug.apk` from [Releases](../../releases) and install it (allow "install unknown apps" if asked).
2. Open **Ridmik Pro → Enable Ridmik Pro**, switch it on in the system keyboard list, then tap **Switch keyboard** and pick it.
3. Paste your Gemini API key in *Settings → Voice typing* and you are done.

## 🏷️ Signed releases

Push a tag to build a signed release (uses the repo's keystore secrets):

```bash
git tag v1.1.0 && git push origin v1.1.0
```

The Release workflow outputs `RidmikPro-v1.1.0.apk` as a GitHub release. All signed builds share one key, so they update in place.

## 🏗️ Build from source

```bash
git clone https://github.com/khad3m/ridmik-pro.git
cd ridmik-pro
gradle assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK (API 34), Gradle 8.9.

Based on [OpenRidmik](https://github.com/devfahim00/OpenRidmik) (MIT license, see [LICENSE](LICENSE)). Voice typing uses the [Google Gemini API](https://ai.google.dev/) free tier.
