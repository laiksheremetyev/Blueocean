# Architecture Overview

```text
Blue Ocean Android Client
 ├── UI Layer
 ├── Vault Layer
 ├── Metadata DB
 ├── Blob Storage
 ├── Sync Queue
 └── Rust Core Bridge

Relay Server
 ├── Blob Transport
 ├── Operation Queue
 └── Metadata Relay

Rust Core
 ├── Encryption
 ├── Chunking
 ├── Manifest System
 ├── Recovery
 └── Device Trust
```

The relay server stores opaque encrypted blobs and operation payloads only. It must not receive plaintext media, plaintext metadata, user passphrases, or derived vault keys.
