# Crypto Model

Initial foundation:
- Argon2id for passphrase-based key derivation
- XChaCha20-Poly1305 for authenticated encryption
- BLAKE3 for plaintext and encrypted blob digests
- Per-chunk encryption with unique nonces
- Additional authenticated data to bind ciphertext to context

Open work:
- Separate key hierarchy for vault, metadata, manifest, and blob keys
- Device public key model for pairing
- Rotation and recovery package format
- Android Keystore wrapping for local unlock material
