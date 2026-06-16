use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum SyncOperationKind {
    PutManifest,
    DeleteFile,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SyncOperation {
    pub op_id: String,
    pub device_id: String,
    pub sequence: u64,
    pub kind: SyncOperationKind,
    pub encrypted_payload: Vec<u8>,
}
