use serde::{Deserialize, Serialize};

use crate::{crypto::EncryptedPayload, storage::BlobRef};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ChunkManifestEntry {
    pub index: u32,
    pub plaintext_offset: u64,
    pub plaintext_size: u64,
    pub blob: BlobRef,
    pub nonce: [u8; 24],
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct FileManifest {
    pub version: u16,
    pub file_id: String,
    pub plaintext_size: u64,
    pub plaintext_hash: String,
    pub chunks: Vec<ChunkManifestEntry>,
}

impl FileManifest {
    pub fn new(file_id: String, plaintext: &[u8], chunks: Vec<ChunkManifestEntry>) -> Self {
        Self {
            version: 1,
            file_id,
            plaintext_size: plaintext.len() as u64,
            plaintext_hash: blake3::hash(plaintext).to_hex().to_string(),
            chunks,
        }
    }
}

pub fn manifest_entry(
    index: u32,
    plaintext_offset: u64,
    plaintext_size: u64,
    blob: BlobRef,
    encrypted: &EncryptedPayload,
) -> ChunkManifestEntry {
    ChunkManifestEntry {
        index,
        plaintext_offset,
        plaintext_size,
        blob,
        nonce: encrypted.nonce,
    }
}
