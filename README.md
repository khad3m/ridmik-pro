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
- **Setup (once)**: open *Ridmik Pro → Settings → Voice typing*, paste a free API key from [aistudio.google.com/apikey](https://aistudio.google.com/apikey), tap **Fetch model list** to load every model your key can use straight from Google (no hardcoded list — you can also type a model name manually), then **Save & Test**. On the first mic tap Android asks for the microphone permission.
- If the chosen model is ever rejected, voice typing automatically falls back to other models from the fetched list. Errors (bad key, quota, offline, region blocks) surface as clear, specific messages — typing itself never breaks.

### 2. Small cleanups

- The "update available" checker that pointed at the original public repo was removed (this repo is private; updates come from its own releases).
- `applicationId` is **`com.ridmik.pro`** and the launcher label is **Ridmik Pro**, so it installs **side by side** with the original OpenRidmik — no need to uninstall anything.

Everything else (Avro/Probhat/Jatiyo layouts, suggestions, emoji, clipboard, themes, numpad) is unchanged from the upstream project.

## 📲 Install

1. GitHub Actions builds a **signed release APK** on every push to `main` and publishes it as the **`latest`** release — download `RidmikPro-signed.apk` from [Releases](../../releases) and install it (allow "install unknown apps" if asked).
2. Open **Ridmik Pro → Enable Ridmik Pro**, switch it on in the system keyboard list, then tap **Switch keyboard** and pick it.
3. In *Settings → Voice typing*: paste your Gemini API key, tap **Fetch model list from Google**, pick a model (or type one manually) and press **Save & Test** — the status line turns green when everything works.

## 🏷️ Versioned releases

Push a tag to build a named signed release:

```bash
git tag v1.2.1 && git push origin v1.2.1
```

The Release workflow outputs `RidmikPro-v1.2.1.apk` as a GitHub release. All signed builds share one key, so they update in place.

Full release history: [CHANGELOG.md](CHANGELOG.md).

## 🏗️ Build from source

```bash
git clone https://github.com/khad3m/ridmik-pro.git
cd ridmik-pro
gradle assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK (API 34), Gradle 8.9.

Based on [OpenRidmik](https://github.com/devfahim00/OpenRidmik) (MIT license, see [LICENSE](LICENSE)). Voice typing uses the [Google Gemini API](https://ai.google.dev/) free tier.
