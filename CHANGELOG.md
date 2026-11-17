# Changelog

All notable changes to **Ridmik Pro** — an OpenRidmik fork with Gemini voice typing.
Versioned releases: <https://github.com/khad3m/ridmik-pro/releases>

## [v1.2.2] – 2026-11-17

### New

- **Ctrl shortcut bar**: a Ctrl button in the toolbar opens a pill bar — Select all, Cut, Copy, Paste, Undo, Redo, ←Word, Word→. While it is active, letter keys act as Ctrl combinations and the bar closes after one action.
- **Voice waveform**: the voice bar shows an animated 5-bar waveform that follows the live microphone level while recording and a busy animation while recognising (replaces the blinking dot), plus a **Done** button to stop recording manually.
- **Voice settings**: language mode (Auto / Bangla only / English only), auto-punctuation on/off, silence auto-stop speed (fast 1.2 s / normal 1.8 s / relaxed 2.8 s) and haptic feedback on voice start / stop / commit.
- **Clipboard panel upgrade**: item count ("N saved · M pinned"), a Pinned/All filter, relative timestamps ("2m ago"), char counts, a PINNED badge with accent outline, tap-a-card-to-paste, a one-tap copy button per card, long clips span the full grid width — and the history now keeps 60 unpinned items (was 40).
- **Kaomoji & Symbols** group in the emoji panel.
- **Live settings**: theme, layout, keyboard height and toggles now apply immediately — the running keyboard reloads itself when a setting changes, no reopen needed.

### Improved

- **RAM**: suggestion dictionaries load through streams instead of full strings, only the active language's dictionary is warmed up, and the emoji cache is cleared on memory pressure (`onTrimMemory` / `onLowMemory`).
- **Mic permission flow**: after granting permission, voice typing resumes automatically within a 30-second window — even if the keyboard was closed and reopened in between; a denied permission now resets the pending state cleanly.
- The user dictionary is saved when the service shuts down, and the keyboard rebuilds itself on orientation change.
- The key-pop preview no longer clips at the top of the keyboard.
- Voice requests: model names are trimmed and `models/` prefixes stripped everywhere, the model filter excludes image / robotics / computer-use models, "thought" response parts are skipped, and the API-key field has a Show/Hide toggle.

## [v1.2.1] – 2026-11-17

### Voice typing — no more hidden 400 errors

- `400 Bad request` responses now surface **Google's actual error message** instead of a bare "Bad request (400)" with no detail, so the real cause is never hidden again.
- Error-classification order fixed: a **region-blocked key** ("User location is not supported for the API use") was previously caught by the generic "not supported" branch and misreported as a dead model. It is now recognised before any model logic and reported as *"Google blocks the Gemini API from your region/network. The key is fine — try another network (or VPN), then retry."*
- **Self-healing retry:** when a model rejects extra generation fields (`thinkingConfig`, `temperature`, …), the request is retried once with a bare contents-only body — voice typing keeps working on picky models.
- The model test request (`ping`) is now minimal (no `generationConfig`), so every `generateContent` model passes it.
- Pasted API keys are cleaned automatically: stray spaces, newlines and smart quotes are stripped before any request.
- **Fetch model list** retries without `pageSize` if Google rejects the parameter.
- **Save & Test is two-step:** the key alone is validated first via `GET /models` (that request cannot fail for model reasons), then the chosen model is tested. On failure the status shows **"Key is OK ✓ — but \<model\> failed: \<reason\>"** instead of a vague error. The fallback catalogue is refreshed in the same step.

### Keyboard UI

- Alphabet ↔ ?123 / #+= switching is now a **quiet 120 ms in-place cross-fade** — the slide was removed. The keyboard shell stays put, so a page switch reads as *the same keyboard whose keys simply changed*, not as a panel transition.

## [v1.2.0] – 2026-11-17

### Voice typing

- **Live model catalogue:** the model list is fetched live from Google (`GET /models`), filtered to models that accept `generateContent` (TTS / image / embedding / live models excluded). Nothing is hardcoded — you always see every model your key can use, including newly released ones.
- Settings voice section redesigned: rounded premium API-key / model input boxes, **Fetch model list from Google** button, manual model entry and a **Save & Test** button with an inline status line (green when working, red with a specific reason when not).
- The automatic model fallback now uses the fetched catalogue instead of a hardcoded chain.
- Region-block errors ("location is not supported") are reported clearly.

### Keyboard UI

- Smooth cross-fade transition between the alphabet and the ?123 pages.

### Build & repo

- Removed the Telegram channel promo and developer branding from Settings.
- CI now builds a **signed release APK on every push** (debug builds removed) and publishes it as the **`latest`** release.
- `versionCode` follows the CI run number, so every build installs over the previous one.

## [v1.1.0] – 2026-11-17

First signed release.

### Added — Gemini voice typing (Bangla + English)

- **Mic button** at the top-right corner of the keyboard strip.
- On-device **16 kHz WAV** capture; recording stops by itself after a pause (1.8 s of quiet once speech was heard) or a 25 s cap; tap the mic again to stop early, or tap while "Recognising…" shows to cancel.
- Audio is sent **directly to the Google Gemini API with the user's own free key** — no middle server, nothing stored.
- Transcription tuned for **Bangla–English mixed speech**: every word stays in the language it was spoken in (never translated), natural punctuation and spacing added.
- Automatic model fallback (gemini-2.5-flash → flash-lite → 2.0-flash in this version) when the chosen model is rejected.
- Settings: API key, model picker, key test, free-key link and a mic on/off toggle.
- `MicPermissionActivity` hosts the `RECORD_AUDIO` dialog for the IME.

### Changed

- Removed the in-app updater that pointed at the upstream public repo (updates come from this repo's own releases).
- `applicationId` → **`com.ridmik.pro`** with launcher label **Ridmik Pro**, so it installs **side by side** with the original OpenRidmik — nothing to uninstall.

## Base (upstream OpenRidmik)

Everything below is unchanged from the upstream project and existed before v1.1.0: Avro phonetic, Probhat and Jatiyo layouts · suggestions · emoji panel · clipboard manager · number pad · themes · toolbar strip.

---

## Install

Grab `RidmikPro-v1.2.1-signed.apk` from the [v1.2.1 release](https://github.com/khad3m/ridmik-pro/releases/tag/v1.2.1) — or `RidmikPro-signed.apk` from the [`latest` release](https://github.com/khad3m/ridmik-pro/releases/tag/latest) for the current `main`. All signed builds share one key, so they update in place.
