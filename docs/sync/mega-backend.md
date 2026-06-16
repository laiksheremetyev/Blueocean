# MEGA Cloud Backend

## Position

Blue Ocean can use MEGA as a cloud storage backend for automatic backup of encrypted vault data.

The integration must upload only Blue Ocean encrypted blobs, encrypted manifests, and encrypted sync operations. Plaintext photos, comments, location metadata, filenames, and thumbnails must never be sent to MEGA.

## Source Integration

Use the official MEGA SDK path:
- MEGA C++ SDK
- Android bindings / Android example from the official SDK
- MEGA Android client repository as implementation reference

Avoid direct, hand-written MEGA API integration for the first implementation. MEGA's storage model includes client-side encryption and session handling, and the official SDK already implements those details.

The official Android example is in:

```text
meganz/sdk/examples/android/ExampleApp
```

The example uses:
- Java bindings from `bindings/java/nz/mega/sdk`
- native libraries from `app/src/main/jniLibs`
- `MegaApiAndroid`
- `MegaRequestListenerInterface` for requests such as login and fetch nodes
- `MegaTransferListener` for uploads/downloads

The example build expects the SDK source tree next to the Android example. If copied elsewhere, the SDK must be available under:

```text
ExampleApp/app/src/main/jni/mega
```

For Blue Ocean, keep the MEGA SDK outside the main Android app at first and integrate it only after the local encrypted vault works.

## Android Requirement

MEGA SDK currently lists Android 9.0 as the minimum supported Android version. Blue Ocean should set:

```kotlin
minSdk = 28
```

The Android example currently uses a modern Android toolchain and requires NDK when building native libraries manually. The official README specifies NDK `27.1.12297006` or newer for the example build.

Required local tools for building MEGA SDK Android bindings:
- Android Studio
- Android SDK
- Android NDK 27.1.12297006 or newer
- CMake
- SWIG
- autotools: `automake`, `autoconf`
- `libtool`
- common CLI tools: `git`, `wget`, `curl`, `unzip`, `tar`

Required environment variables or equivalent symlinks:

```bash
export NDK_ROOT=/path/to/android/ndk
export ANDROID_HOME=/path/to/android/sdk
export JAVA_HOME=/path/to/jdk
```

## Upload Model

```text
CameraX capture
 ↓
private internal temp file
 ↓
Blue Ocean Rust core encryption
 ↓
encrypted blob + encrypted manifest
 ↓
local sync queue row
 ↓
MEGA upload worker
 ↓
MEGA folder: /BlueOcean/<vault_id>/
```

MEGA SDK operation sequence:

```text
MegaApiAndroid init
 ↓
login(email, password) or fastLogin(session)
 ↓
fetchNodes()
 ↓
find/create /BlueOcean/<vault_id>/ folders
 ↓
startUpload(encrypted_blob_path, remote_folder_node, encrypted_filename, ...)
 ↓
MegaTransferListener confirms completion
 ↓
mark sync queue item as uploaded
```

Relevant SDK API names:
- `MegaApiAndroid`
- `login`
- `fetchNodes`
- `dumpSession`
- `fastLogin`
- `getRootNode`
- `getChildren`
- `createFolder`
- `startUpload`
- `MegaTransferListener`

Remote layout:

```text
/BlueOcean/
  /<vault_id>/
    /blobs/
      /<digest-prefix>/
        <digest>
    /manifests/
      <manifest-id>.bin
    /ops/
      <device-id>/
        <sequence>.bin
```

Remote files remain encrypted Blue Ocean blobs. MEGA must never store plaintext photo previews or thumbnails created by Blue Ocean.

Client context menu for remote files:
- Properties
- Copy
- Paste
- Delete
- Decrypt

`Decrypt` is a local client action:
- requires Blue Ocean unlock
- downloads the encrypted blob if needed
- decrypts locally in memory
- does not ask MEGA to decrypt
- does not write plaintext thumbnails or previews to disk

## User Controls

Automatic upload must be an explicit user setting:
- Off by default
- User chooses and logs into a MEGA account
- User can restrict upload to Wi-Fi
- User can restrict upload to charging
- User can pause sync
- User can see pending / uploaded / failed state

## Secrets

- Do not store the MEGA password.
- Store SDK session material only through Android Keystore-backed storage where possible.
- Do not store Blue Ocean vault keys in MEGA.
- Do not derive Blue Ocean keys from MEGA credentials.

Login flow:
- Ask user for MEGA credentials only in the MEGA setup screen.
- Call MEGA SDK login.
- After successful login, store only the SDK session from `dumpSession`, wrapped by Android Keystore.
- Reconnect with `fastLogin`.
- Provide a visible disconnect action that clears the wrapped session.

## Worker Rules

Use Android WorkManager for uploads:
- Network required
- Exponential backoff
- No plaintext temp files in worker input
- Input should reference encrypted local blob IDs only
- Delete uploaded queue entries only after MEGA SDK confirms success

## Conflict Rules

The first version is backup-only:
- Upload new encrypted blobs.
- Upload encrypted manifests.
- Do not delete remote data automatically.
- Do not attempt multi-device reconciliation until device trust and operation logs are implemented.

Full sync is a later milestone.
