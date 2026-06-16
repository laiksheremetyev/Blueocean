use argon2::{
    password_hash::SaltString, Algorithm, Argon2, Params, PasswordHasher, Version,
};
use chacha20poly1305::{
    aead::{Aead, AeadCore, KeyInit, OsRng},
    XChaCha20Poly1305, XNonce,
};
use rand_core::RngCore;
use serde::{Deserialize, Serialize};
use thiserror::Error;
use zeroize::{Zeroize, ZeroizeOnDrop};

const KEY_LEN: usize = 32;
const NONCE_LEN: usize = 24;

#[derive(Debug, Error)]
pub enum CryptoError {
    #[error("key derivation failed")]
    KeyDerivation,
    #[error("encryption failed")]
    Encrypt,
    #[error("decryption failed")]
    Decrypt,
    #[error("invalid key material")]
    InvalidKey,
}

#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct MasterKey([u8; KEY_LEN]);

impl MasterKey {
    pub fn from_bytes(bytes: [u8; KEY_LEN]) -> Self {
        Self(bytes)
    }

    pub fn expose(&self) -> &[u8; KEY_LEN] {
        &self.0
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct KdfParams {
    pub algorithm: String,
    pub memory_kib: u32,
    pub iterations: u32,
    pub parallelism: u32,
    pub salt: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct EncryptedPayload {
    pub nonce: [u8; NONCE_LEN],
    pub ciphertext: Vec<u8>,
}

pub fn derive_master_key(password: &str) -> Result<(MasterKey, KdfParams), CryptoError> {
    let salt = SaltString::generate(&mut OsRng);
    let params = Params::new(64 * 1024, 3, 1, Some(KEY_LEN)).map_err(|_| CryptoError::KeyDerivation)?;
    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params.clone());
    let hash = argon2
        .hash_password(password.as_bytes(), &salt)
        .map_err(|_| CryptoError::KeyDerivation)?;

    let output = hash.hash.ok_or(CryptoError::KeyDerivation)?;
    let mut key = [0u8; KEY_LEN];
    key.copy_from_slice(output.as_bytes());

    Ok((
        MasterKey::from_bytes(key),
        KdfParams {
            algorithm: "argon2id-v1.3".to_string(),
            memory_kib: params.m_cost(),
            iterations: params.t_cost(),
            parallelism: params.p_cost(),
            salt: salt.to_string(),
        },
    ))
}

pub fn encrypt_bytes(key: &MasterKey, plaintext: &[u8], aad: &[u8]) -> Result<EncryptedPayload, CryptoError> {
    let cipher = XChaCha20Poly1305::new_from_slice(key.expose()).map_err(|_| CryptoError::InvalidKey)?;
    let nonce = XChaCha20Poly1305::generate_nonce(&mut OsRng);
    let ciphertext = cipher
        .encrypt(
            &nonce,
            chacha20poly1305::aead::Payload {
                msg: plaintext,
                aad,
            },
        )
        .map_err(|_| CryptoError::Encrypt)?;

    let mut nonce_bytes = [0u8; NONCE_LEN];
    nonce_bytes.copy_from_slice(nonce.as_slice());

    Ok(EncryptedPayload {
        nonce: nonce_bytes,
        ciphertext,
    })
}

pub fn decrypt_bytes(key: &MasterKey, payload: &EncryptedPayload, aad: &[u8]) -> Result<Vec<u8>, CryptoError> {
    let cipher = XChaCha20Poly1305::new_from_slice(key.expose()).map_err(|_| CryptoError::InvalidKey)?;
    cipher
        .decrypt(
            XNonce::from_slice(&payload.nonce),
            chacha20poly1305::aead::Payload {
                msg: &payload.ciphertext,
                aad,
            },
        )
        .map_err(|_| CryptoError::Decrypt)
}

pub fn random_bytes<const N: usize>() -> [u8; N] {
    let mut out = [0u8; N];
    OsRng.fill_bytes(&mut out);
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encrypt_decrypt_round_trip() {
        let (key, _) = derive_master_key("correct horse battery staple").unwrap();
        let encrypted = encrypt_bytes(&key, b"private photo bytes", b"blob:v1").unwrap();
        let decrypted = decrypt_bytes(&key, &encrypted, b"blob:v1").unwrap();
        assert_eq!(decrypted, b"private photo bytes");
    }

    #[test]
    fn aad_mismatch_fails() {
        let (key, _) = derive_master_key("correct horse battery staple").unwrap();
        let encrypted = encrypt_bytes(&key, b"private photo bytes", b"blob:v1").unwrap();
        assert!(decrypt_bytes(&key, &encrypted, b"metadata:v1").is_err());
    }
}
