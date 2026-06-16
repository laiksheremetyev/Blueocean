# Android Local MVP Plan

## Stack

- Kotlin
- Jetpack Compose
- CameraX
- Android Keystore
- Rust core through JNI or UniFFI
- Internal app storage
- WorkManager later for MEGA encrypted backup sync

## Screens

- Notes home
- Unlock
- Vault gallery
- Camera
- Photo detail
- Edit metadata
- Settings

## Data Flow

```text
CameraX capture
 ↓
private app temp file or memory buffer
 ↓
Rust core encrypts chunks
 ↓
encrypted blobs in internal storage
 ↓
encrypted metadata row
 ↓
plaintext temp deleted
```

## Metadata

Each photo stores encrypted metadata:
- file id
- capture timestamp
- comment
- location
- media type
- dimensions
- manifest id

## Key Handling

- PIN unlock derives or unwraps local vault material.
- Android Keystore wraps persistent unlock material.
- Session keys live in memory only.
- Session keys are zeroized on lock/background where possible.

## Camera Rules

- Capture only after visible user action.
- Do not use `MediaStore`.
- Do not write camera output into shared public collections.
- If CameraX requires a file target, use an internal app cache file and delete it immediately after encryption.
- Do not enable third-party image loaders with disk caches for decrypted media.

## MEGA Upload Rules

- Upload only encrypted Blue Ocean blobs and encrypted manifests.
- Automatic upload must be a visible user setting.
- Do not store MEGA passwords.
- Do not store vault keys in MEGA.
- First cloud milestone is backup-only, not full multi-device reconciliation.

## Discreet Mode Rules

- The default surface may be a real notes or calculator feature owned by Blue Ocean.
- Do not copy another app's icon, name, package, branding, or UI.
- Do not claim to be a system app.
- Vault entry must be user-controlled through PIN or biometric unlock.
