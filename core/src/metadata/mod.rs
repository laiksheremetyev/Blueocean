use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct MediaMetadata {
    pub file_id: String,
    pub encrypted_name: Vec<u8>,
    pub media_type: String,
    pub created_at_ms: i64,
}
