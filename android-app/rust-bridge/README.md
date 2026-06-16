# Rust Bridge

JNI bridge boundary for `vault-core`.

The bridge should expose small commands:
- unlock vault
- import media selected or captured by the user
- encrypt and write blobs
- read and decrypt previews in memory
- enqueue encrypted sync operations
