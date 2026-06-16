# Telegram Mini App Feasibility

## Summary

Blue Ocean can have a Telegram Mini App companion, but it cannot fully replace the native Android app for the strict local privacy requirements.

Telegram Mini Apps are web applications running inside Telegram. They are useful for an encrypted cloud viewer, account onboarding, notes UI, and lightweight upload flows. They are not a good primary implementation for a camera-first encrypted vault that must guarantee no local gallery entries, no plaintext filesystem artifacts, and no decrypted thumbnail cache.

## What Can Work

- Notes-like or calculator-like Blue Ocean surface inside Telegram.
- PIN unlock in the Mini App UI.
- Client-side encryption before upload using WebCrypto or Rust compiled to WASM.
- Encrypted comments and location metadata.
- Zero-knowledge relay storage.
- Encrypted cloud gallery after unlock.
- Optional home screen shortcut through Telegram-supported Mini App features.

## What Is Limited

- Camera access depends on Telegram WebView and browser capabilities.
- The app cannot use native Android internal storage directly.
- The app cannot use Android Keystore directly; it depends on Telegram Mini App storage/biometry APIs where available.
- The app cannot enforce Android `FLAG_SECURE` on Telegram's WebView.
- The app cannot guarantee that Telegram WebView, OS picker, or browser layers never create temporary artifacts.
- The app cannot prevent screenshots at the native Android window level.
- Offline-first encrypted storage is weaker than in the native app.

## Recommended Role

Use Telegram Mini App as a companion client:
- Browse encrypted cloud photos after PIN unlock.
- Add or edit encrypted comments.
- View encrypted location metadata.
- Upload selected photos with explicit user action.
- Manage account/device pairing.

Keep the native Android app as the primary client:
- Camera capture.
- Strict internal-only encrypted storage.
- No MediaStore writes.
- No public filesystem artifacts.
- No disk thumbnails.
- `FLAG_SECURE` private screens.
- Android Keystore integration.

## Shared Architecture

```text
Rust core
 ├── Android: JNI / UniFFI library
 └── Telegram Mini App: WASM crypto package

Relay server
 ├── encrypted blobs
 ├── encrypted metadata operations
 └── device/account sync queues

Clients
 ├── Android app: primary secure local vault
 └── Telegram Mini App: encrypted cloud companion
```

## Decision

Do not replace the Android MVP with a Mini App. Build Android first, then add a Mini App once the crypto format, manifest format, and relay API are stable.
