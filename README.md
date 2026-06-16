# Blue Ocean

Blue Ocean is a private encrypted photo storage platform with an in-app camera, local-first storage, encrypted metadata, chunked blobs, and zero-knowledge cloud sync.

Primary target:
- Android

Future targets:
- Windows
- Linux
- macOS

## Security Boundary

This repository is for a user-consented private photo vault. It must not implement covert capture, deceptive app impersonation, silent exfiltration, or features intended to bypass a device owner's awareness.

Allowed privacy features:
- Clear app identity and user-controlled vault lock
- Encrypted local storage
- Zero-knowledge sync
- Screenshot blocking while the vault is open
- Session key clearing on background
- Discreet utility mode, such as a real Blue Ocean Notes or Blue Ocean Calculator surface, when it does not impersonate another app or misrepresent camera/sync behavior
- Decoy/demo content only when it does not misrepresent camera or upload behavior
- In-app camera capture after explicit user action

Disallowed features:
- Hidden camera capture
- Masquerading as a third-party, system, banking, security, workplace, or otherwise unrelated app
- Uploading photos without clear user intent
- Storing plaintext media or plaintext metadata
- Using public external storage or MediaStore for sensitive vault files

## Current Phase

FOUNDATION DEVELOPMENT

Implemented:
- Repository structure
- Rust crypto foundation
- Fixed-size chunking
- Content-addressed local blob storage
- Manifest model
- End-to-end core pipeline test

Next:
- Local Android MVP
- CameraX capture into memory or private temp file
- Encrypted comments and location metadata
- MEGA backup backend for encrypted blobs
- Android JNI bridge
- Encrypted metadata database
- Relay API contract
- Device trust pairing

## First Milestone

See:
- [MVP scope](docs/product/mvp.md)
- [Discreet mode](docs/product/discreet-mode.md)
- [Telegram Mini App feasibility](docs/product/telegram-mini-app-feasibility.md)
- [MEGA cloud backend](docs/sync/mega-backend.md)
- [Telegram backend](docs/sync/telegram-backend.md)
- [TGFinder integration](docs/sync/tgfinder-integration.md)
- [Android implementation plan](docs/android/local-mvp.md)
- [Storage privacy checklist](docs/android/storage-privacy-checklist.md)
- [Setup checklist](docs/ops/setup-checklist.md)

## Repository Structure

```text
core/           Rust crypto and storage engine
android-app/    Android client scaffold
relay-server/   Zero-knowledge relay scaffold
docs/           Architecture documentation
tools/          Debugging and recovery tools
```

## Build Core

```bash
cd core
cargo build
```

## Run Tests

```bash
cd core
cargo test
```
