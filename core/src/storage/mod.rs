use std::{
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
};

use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum StorageError {
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("blob not found: {0}")]
    NotFound(String),
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct BlobRef {
    pub digest: String,
    pub size: u64,
}

#[derive(Debug, Clone)]
pub struct BlobStore {
    root: PathBuf,
}

impl BlobStore {
    pub fn open(root: impl AsRef<Path>) -> Result<Self, StorageError> {
        let root = root.as_ref().to_path_buf();
        fs::create_dir_all(&root)?;
        Ok(Self { root })
    }

    pub fn put(&self, encrypted_bytes: &[u8]) -> Result<BlobRef, StorageError> {
        let digest = blake3::hash(encrypted_bytes).to_hex().to_string();
        let path = self.path_for_digest(&digest);

        if !path.exists() {
            if let Some(parent) = path.parent() {
                fs::create_dir_all(parent)?;
            }
            let mut file = fs::File::create(path)?;
            file.write_all(encrypted_bytes)?;
            file.sync_all()?;
        }

        Ok(BlobRef {
            digest,
            size: encrypted_bytes.len() as u64,
        })
    }

    pub fn get(&self, digest: &str) -> Result<Vec<u8>, StorageError> {
        let path = self.path_for_digest(digest);
        if !path.exists() {
            return Err(StorageError::NotFound(digest.to_string()));
        }

        let mut out = Vec::new();
        fs::File::open(path)?.read_to_end(&mut out)?;
        Ok(out)
    }

    fn path_for_digest(&self, digest: &str) -> PathBuf {
        let prefix = digest.get(..2).unwrap_or("xx");
        self.root.join(prefix).join(digest)
    }
}
