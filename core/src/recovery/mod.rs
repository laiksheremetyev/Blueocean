use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RecoveryPackage {
    pub version: u16,
    pub vault_id: String,
    pub encrypted_manifests: Vec<Vec<u8>>,
}
