# Setup Checklist

## Local Machine

- Install Rust toolchain.
- Install Android Studio.
- Install Android SDK.
- Install Android NDK.
- Install `cargo-ndk` if JNI builds use cargo-ndk.
- Configure `ANDROID_HOME` and `ANDROID_NDK_HOME`.

## Rust Targets

```bash
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add i686-linux-android
rustup target add x86_64-linux-android
```

## Verify Core

```bash
cd core
cargo test
```

## Verify Android

```bash
cd android-app
./gradlew assembleDebug
```

## Decisions Needed Before Coding Android

- Minimum Android SDK version
- Compose Material version
- JNI vs UniFFI bridge
- PIN-only unlock vs PIN plus biometric
- Whether import from Android Photo Picker is part of MVP
- Whether location is required at capture time or optional per photo
