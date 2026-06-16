use tempfile::tempdir;
use vault_core::{
    chunking::fixed_chunks,
    crypto::{decrypt_bytes, derive_master_key, encrypt_bytes, EncryptedPayload},
    manifests::{manifest_entry, FileManifest},
    storage::BlobStore,
};

#[test]
fn blob_pipeline_round_trip() {
    let dir = tempdir().unwrap();
    let store = BlobStore::open(dir.path()).unwrap();
    let (key, _) = derive_master_key("test passphrase").unwrap();
    let plaintext = b"photo-bytes-photo-bytes-photo-bytes";

    let mut entries = Vec::new();
    for chunk in fixed_chunks(plaintext, 10) {
        let encrypted = encrypt_bytes(&key, &chunk.data, b"blob:v1").unwrap();
        let blob = store.put(&encrypted.ciphertext).unwrap();
        entries.push(manifest_entry(
            chunk.index,
            chunk.offset,
            chunk.data.len() as u64,
            blob,
            &encrypted,
        ));
    }

    let manifest = FileManifest::new("file-1".to_string(), plaintext, entries);
    let mut restored = Vec::new();
    for entry in manifest.chunks {
        let ciphertext = store.get(&entry.blob.digest).unwrap();
        let payload = EncryptedPayload {
            nonce: entry.nonce,
            ciphertext,
        };
        restored.extend(decrypt_bytes(&key, &payload, b"blob:v1").unwrap());
    }

    assert_eq!(restored, plaintext);
}
