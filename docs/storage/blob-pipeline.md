# Blob Pipeline

```text
file bytes
 ↓
fixed chunk split
 ↓
chunk encryption
 ↓
content-addressed blob write
 ↓
manifest entry creation
 ↓
encrypted metadata update
```

The local blob store writes encrypted bytes under a digest-derived path. Plaintext files and previews must stay out of Android external storage and MediaStore.
