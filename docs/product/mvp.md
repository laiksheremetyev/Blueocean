# Blue Ocean MVP

## Goal

Build an Android-only local encrypted photo vault that proves the core privacy model before cloud sync is added.

## In Scope

- PIN unlock for the private vault
- Optional biometric unlock after PIN setup
- Discreet Blue Ocean Notes surface as a real notes feature
- In-app camera capture with explicit shutter action
- Add photos through Android Photo Picker if import is enabled
- Per-photo comment
- Per-photo location metadata:
  - current location after runtime permission grant
  - manual location field as fallback
  - optional EXIF import when importing existing media
- Encrypted local photo storage
- Encrypted metadata storage
- In-app gallery
- Memory-only decrypted previews
- Screenshot blocking on private screens
- Session key clearing when app backgrounds

## Out of Scope for MVP

- Cloud sync
- Multi-device pairing
- Recovery packages
- Video
- Desktop clients
- Background upload
- Any hidden camera behavior
- Third-party, system, or unrelated app impersonation

## Acceptance Criteria

- A photo captured in Blue Ocean does not appear in the system gallery.
- No plaintext captured image is written to public external storage.
- No decrypted thumbnail is persisted on disk.
- App-specific internal storage contains only encrypted blob bytes and encrypted metadata.
- Locking the app clears decrypted previews and session keys.
- Reopening the vault with the correct PIN restores the private gallery.
- Wrong PIN attempts are rate-limited.
