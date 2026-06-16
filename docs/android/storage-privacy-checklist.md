# Storage Privacy Checklist

## Must Use

- `context.filesDir` for encrypted blobs and metadata
- `context.cacheDir` only for short-lived plaintext capture temp files
- `delete()` and best-effort cleanup immediately after encryption
- `FLAG_SECURE` for private screens
- In-memory image decoding for previews

## Must Avoid

- `MediaStore.Images`
- `Environment.getExternalStoragePublicDirectory`
- `getExternalStorageDirectory`
- Shared external app folders for sensitive media
- Image loader disk cache for decrypted bytes
- Plaintext Room database fields for comments or location
- Plaintext EXIF copies in app storage

## Verification

After capture:
- Check Android system gallery: image must not appear.
- Check public filesystem collections: image must not appear.
- Inspect app internal files: no JPEG/PNG plaintext signatures outside short-lived temp during capture.
- Restart app: locked state must show no decrypted previews.
