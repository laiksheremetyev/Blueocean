use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct TrustedDevice {
    pub device_id: String,
    pub public_key: Vec<u8>,
    pub added_at_ms: i64,
    pub label: String,
}
