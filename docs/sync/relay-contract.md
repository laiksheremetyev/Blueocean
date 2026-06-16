# Relay Contract

The relay is zero-knowledge:
- Accept encrypted blobs by digest
- Accept encrypted operation payloads
- Return pending operations for a device
- Never parse media metadata
- Never derive, receive, or store vault keys

Authentication and device trust are separate from vault encryption. A server account compromise must not decrypt vault contents.

## MEGA Backend Variant

MEGA can replace the first-party relay for backup storage in the first cloud milestone. The same zero-knowledge rule applies: upload only Blue Ocean encrypted blobs, encrypted manifests, and encrypted operation payloads.

The first MEGA milestone is backup-only. Multi-device sync and deletion propagation must wait until the device trust and operation log model is implemented.
