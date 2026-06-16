# Telegram Backend

## Position

Telegram can be used only as an explicit export target or as an encrypted backup transport.

Blue Ocean must not automatically dump decrypted vault photos into a supergroup. That would break the vault security model and can enable non-consensual sharing.

## Safe Modes

### Encrypted Backup Mode

Blue Ocean uploads encrypted blobs and encrypted manifests as Telegram documents.

```text
photo
 ↓
Blue Ocean local encryption
 ↓
encrypted blob
 ↓
Telegram bot sendDocument
```

Telegram receives only opaque encrypted files. The bot, Telegram admins, and group members cannot view the original photos without the Blue Ocean vault key.

### Manual Export Mode

The user selects specific photos, confirms export, and Blue Ocean sends decrypted copies to a chosen Telegram chat.

Rules:
- Off by default
- Requires vault unlock
- Requires explicit selection
- Requires export confirmation
- Shows destination chat title
- Never runs in background
- Writes no plaintext export cache to disk

## Supergroup Organization

Telegram groups do not have filesystem folders. A bot can organize uploads with:
- forum topics in a supergroup
- captions
- hashtags
- message threads

For forum topics:
- the supergroup must have topics enabled
- the bot must be an administrator
- the bot needs topic-management permission to create topics

Blue Ocean app album names can map to Telegram forum topic names.

## Bot API Methods

For encrypted backup:
- `sendDocument`

For explicit plaintext export:
- `sendPhoto`
- `sendDocument` if original quality is required

For topic creation:
- `createForumTopic`

For Blue Ocean encrypted files, a Telegram/TGFinder context menu may include `Decrypt`, but decryption must happen only in a trusted client after local unlock. The bot and server must not receive vault keys or plaintext media.

## TGFinder Integration Boundary

TGFinder can be a separate Telegram bot service that receives upload jobs from Blue Ocean backend.

See the concrete TGFinder integration plan in [tgfinder-integration.md](tgfinder-integration.md).

Allowed API shape:

```json
{
  "mode": "encrypted_backup",
  "album": "Travel",
  "blob_id": "blake3-digest",
  "encrypted_file": "multipart upload"
}
```

Disallowed API shape:

```json
{
  "mode": "auto_plaintext_dump",
  "all_photos": true
}
```

## Recommendation

Implement in this order:
1. Local encrypted vault
2. MEGA encrypted backup
3. Telegram encrypted backup to a private bot chat or private supergroup topic
4. Optional manual export of selected photos

Do not implement automatic plaintext export from the vault.
