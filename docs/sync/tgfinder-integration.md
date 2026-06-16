# TGFinder Integration

## Position

TGFinder can be used as a Telegram-backed storage backend for Blue Ocean.

The safe default integration is encrypted backup:
- Blue Ocean encrypts photos locally.
- Blue Ocean creates encrypted blobs and encrypted manifests.
- TGFinder stores those encrypted files in the user's Telegram supergroup topics.
- Telegram never receives plaintext photos, comments, coordinates, thumbnails, or vault keys.

Automatic plaintext export is not allowed. Plaintext export can only be a manual user action for selected photos.

## Existing TGFinder Model

TGFinder stores one Telegram supergroup per user. Folders in the UI map to forum topics in that supergroup:

```text
TGFinder root
 ├── #General              forum_topic_id = 1
 ├── Album A               forum_topic_id = message_thread_id
 └── Album B               forum_topic_id = message_thread_id
```

All Blue Ocean files sent to TGFinder are encrypted Blue Ocean blobs. The Telegram supergroup is a transport/storage layer, not a plaintext gallery.

Relevant TGFinder API:

- `GET /folders/workspace`
- `GET /folders/tree`
- `GET /folders/ls?parent_id=<id>`
- `POST /folders/mkdir`
- `POST /folders/upload`

With nginx from TGFinder README, public API paths are prefixed:

```text
https://<host>:88/api/folders/workspace
https://<host>:88/api/folders/mkdir
https://<host>:88/api/folders/upload
```

## Album Mapping

Blue Ocean album names can map to TGFinder folders:

```text
Blue Ocean album "Travel"
 ↓
TGFinder POST /folders/mkdir { "parent_id": <root_id>, "folder_name": "Travel" }
 ↓
Telegram forum topic "Travel"
```

Store the mapping locally:

```json
{
  "blue_ocean_album_id": "album_uuid",
  "tgfinder_folder_id": 123,
  "tgfinder_forum_topic_id": 456
}
```

## Upload Flow

```text
CameraX capture
 ↓
Blue Ocean internal temp file
 ↓
Rust core encryption
 ↓
encrypted blob file
 ↓
Blue Ocean sync queue
 ↓
TGFinder POST /folders/upload
 ↓
Telegram sendDocument into folder topic
```

TGFinder upload request:

```http
POST /api/folders/upload
X-Telegram-User-Id: <user_id>
Content-Type: multipart/form-data

folder_id=<tgfinder_folder_id>
file=<encrypted_blob_file>
```

Recommended encrypted filename:

```text
bo_blob_<blake3_digest>.bin
```

Recommended MIME type:

```text
application/octet-stream
```

## Context Menu

For Blue Ocean encrypted files, TGFinder UI can expose:
- Properties
- Copy
- Paste
- Delete
- Decrypt

`Decrypt` must be a client-side action:
- user must unlock Blue Ocean or provide a paired decrypt token
- TGFinder backend and Telegram bot must not receive vault keys
- decrypted bytes are rendered only in memory
- no plaintext preview or thumbnail is written to disk

TGFinder backend should identify encrypted Blue Ocean blobs by filename convention or metadata:

```text
bo_blob_<blake3_digest>.bin
```

For non-Blue-Ocean files, `Decrypt` should be hidden or disabled.

## Authentication Gap

TGFinder currently uses `X-Telegram-User-Id` for ownership checks. That is acceptable only for early local development.

Before Blue Ocean Android uploads to TGFinder over the internet, TGFinder must add real request authentication:

- validate Telegram Mini App `initData`, or
- issue a short-lived upload token from TGFinder after user login, or
- create a signed device pairing token for Blue Ocean Android.

Do not ship Android upload using only a spoofable `X-Telegram-User-Id` header.

## Automatic Backup Policy

Automatic upload is allowed only for encrypted backup:

- user enables TGFinder backup in Blue Ocean settings
- user selects TGFinder endpoint
- user pairs the Blue Ocean app with TGFinder
- upload runs only for encrypted blobs/manifests
- settings allow Wi-Fi only / charging only / pause
- queue state is visible

## Manual Plaintext Export

Manual export can be added later:

- user unlocks the vault
- user selects specific photos
- user chooses destination TGFinder folder/topic
- app shows confirmation
- app sends plaintext copy intentionally

This must never run as a background automatic job.

## Implementation Milestones

1. Add TGFinder client interface in Android app.
2. Add TGFinder pairing/auth token flow.
3. Add album-to-folder mapping table.
4. Upload encrypted blobs via `POST /folders/upload`.
5. Upload encrypted manifests.
6. Add retry queue and upload state.
7. Later: manual export for selected plaintext photos.
