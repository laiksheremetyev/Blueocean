package com.blueocean.app;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

final class PhotoStore {
    static final class SavedPhoto {
        final String id;
        final long encryptedSize;

        SavedPhoto(String id, long encryptedSize) {
            this.id = id;
            this.encryptedSize = encryptedSize;
        }
    }

    private static final String KEY_ALIAS = "blue_ocean_blob_key_v1";
    private static final byte[] MAGIC = new byte[]{'B', 'O', 'B', '1'};
    private static final byte[] PIN_MAGIC = new byte[]{'B', 'O', 'P', '1'};
    private static final int GCM_TAG_BITS = 128;
    private static final int PIN_SALT_BYTES = 16;
    private static final int PIN_ITERATIONS = 220_000;
    private static final int PIN_KEY_BITS = 256;

    private final File blobDir;
    private final SecureRandom secureRandom = new SecureRandom();

    PhotoStore(Context context) {
        File vaultDir = new File(context.getFilesDir(), "blue_ocean_vault");
        blobDir = new File(vaultDir, "blobs");
    }

    SavedPhoto saveJpeg(byte[] jpegBytes) throws Exception {
        return saveJpeg(jpegBytes, "");
    }

    SavedPhoto saveJpeg(byte[] jpegBytes, String pin) throws Exception {
        if (!blobDir.exists() && !blobDir.mkdirs()) {
            throw new IllegalStateException("Could not create blob directory");
        }

        String id = UUID.randomUUID().toString();
        File outFile = new File(blobDir, id + ".blob");

        if (pin != null && !pin.trim().isEmpty()) {
            byte[] salt = new byte[PIN_SALT_BYTES];
            secureRandom.nextBytes(salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, derivePinKey(pin, salt));
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(jpegBytes);
            try (FileOutputStream out = new FileOutputStream(outFile, false)) {
                out.write(PIN_MAGIC);
                out.write(salt.length);
                out.write(iv.length);
                out.write(salt);
                out.write(iv);
                out.write(ciphertext);
                out.flush();
            }
            return new SavedPhoto(id, outFile.length());
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(jpegBytes);

        try (FileOutputStream out = new FileOutputStream(outFile, false)) {
            out.write(MAGIC);
            out.write(iv.length);
            out.write(iv);
            out.write(ciphertext);
            out.flush();
        }

        return new SavedPhoto(id, outFile.length());
    }

    byte[] loadJpeg(String blobId) throws Exception {
        return loadJpeg(blobId, "");
    }

    byte[] loadJpeg(String blobId, String pin) throws Exception {
        if (blobId == null || blobId.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing blob id");
        }

        File inFile = new File(blobDir, blobId + ".blob");
        byte[] raw = readAll(inFile);
        if (startsWith(raw, PIN_MAGIC)) {
            return decryptPinBlob(raw, pin);
        }
        if (raw.length < MAGIC.length + 2) {
            throw new IllegalStateException("Invalid photo blob");
        }

        for (int i = 0; i < MAGIC.length; i++) {
            if (raw[i] != MAGIC[i]) {
                throw new IllegalStateException("Invalid photo blob header");
            }
        }

        int ivLength = raw[MAGIC.length] & 0xFF;
        int ivStart = MAGIC.length + 1;
        int cipherStart = ivStart + ivLength;
        if (ivLength <= 0 || cipherStart > raw.length) {
            throw new IllegalStateException("Invalid photo blob IV");
        }

        byte[] iv = new byte[ivLength];
        System.arraycopy(raw, ivStart, iv, 0, ivLength);
        byte[] ciphertext = new byte[raw.length - cipherStart];
        System.arraycopy(raw, cipherStart, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private byte[] decryptPinBlob(byte[] raw, String pin) throws Exception {
        if (pin == null || pin.trim().isEmpty()) {
            throw new IllegalStateException("PIN required for cloud blob");
        }
        if (raw.length < PIN_MAGIC.length + 3) {
            throw new IllegalStateException("Invalid PIN photo blob");
        }
        int saltLength = raw[PIN_MAGIC.length] & 0xFF;
        int ivLength = raw[PIN_MAGIC.length + 1] & 0xFF;
        int saltStart = PIN_MAGIC.length + 2;
        int ivStart = saltStart + saltLength;
        int cipherStart = ivStart + ivLength;
        if (saltLength <= 0 || ivLength <= 0 || cipherStart > raw.length) {
            throw new IllegalStateException("Invalid PIN photo blob header");
        }
        byte[] salt = new byte[saltLength];
        System.arraycopy(raw, saltStart, salt, 0, saltLength);
        byte[] iv = new byte[ivLength];
        System.arraycopy(raw, ivStart, iv, 0, ivLength);
        byte[] ciphertext = new byte[raw.length - cipherStart];
        System.arraycopy(raw, cipherStart, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derivePinKey(pin, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    byte[] loadEncryptedBlob(String blobId) throws Exception {
        if (blobId == null || blobId.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing blob id");
        }
        File inFile = new File(blobDir, blobId + ".blob");
        return readAll(inFile);
    }

    SavedPhoto importEncryptedBlob(byte[] encryptedBlob) throws Exception {
        if (!blobDir.exists() && !blobDir.mkdirs()) {
            throw new IllegalStateException("Could not create blob directory");
        }
        if (!startsWith(encryptedBlob, PIN_MAGIC) && !startsWith(encryptedBlob, MAGIC)) {
            throw new IllegalStateException("Unsupported Blue Ocean blob");
        }
        String id = UUID.randomUUID().toString();
        File outFile = new File(blobDir, id + ".blob");
        try (FileOutputStream out = new FileOutputStream(outFile, false)) {
            out.write(encryptedBlob);
            out.flush();
        }
        return new SavedPhoto(id, outFile.length());
    }

    int countBlobs() {
        String[] files = blobDir.list((dir, name) -> name.endsWith(".blob"));
        return files == null ? 0 : files.length;
    }

    boolean deleteBlob(String blobId) {
        if (blobId == null || blobId.trim().isEmpty()) {
            return false;
        }
        File file = new File(blobDir, blobId + ".blob");
        return !file.exists() || file.delete();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private SecretKey derivePinKey(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PIN_ITERATIONS, PIN_KEY_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private boolean startsWith(byte[] raw, byte[] magic) {
        if (raw == null || raw.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (raw[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
