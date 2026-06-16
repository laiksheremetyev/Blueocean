use serde::{Deserialize, Serialize};

pub const DEFAULT_CHUNK_SIZE: usize = 1024 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Chunk {
    pub index: u32,
    pub offset: u64,
    pub data: Vec<u8>,
}

pub fn fixed_chunks(bytes: &[u8], chunk_size: usize) -> Vec<Chunk> {
    assert!(chunk_size > 0, "chunk_size must be greater than zero");

    bytes
        .chunks(chunk_size)
        .enumerate()
        .map(|(index, data)| Chunk {
            index: index as u32,
            offset: (index * chunk_size) as u64,
            data: data.to_vec(),
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn creates_fixed_chunks() {
        let chunks = fixed_chunks(b"abcdef", 2);
        assert_eq!(chunks.len(), 3);
        assert_eq!(chunks[1].offset, 2);
        assert_eq!(chunks[2].data, b"ef");
    }
}
