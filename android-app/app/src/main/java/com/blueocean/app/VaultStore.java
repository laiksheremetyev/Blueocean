package com.blueocean.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

final class VaultStore {
    private static final String KEY_ALIAS = "blue_ocean_metadata_key_v1";
    private static final byte[] MAGIC = new byte[]{'B', 'O', 'M', '1'};
    private static final int GCM_TAG_BITS = 128;

    private final File metadataFile;

    VaultStore(Context context) {
        File dir = new File(context.getFilesDir(), "blue_ocean_vault");
        metadataFile = new File(dir, "metadata.enc");
    }

    List<VaultEntry> readEntries() throws Exception {
        if (!metadataFile.exists()) {
            return new ArrayList<>();
        }

        byte[] raw = readAll(metadataFile);
        if (raw.length < MAGIC.length + 2) {
            throw new IllegalStateException("Invalid vault metadata file");
        }

        for (int i = 0; i < MAGIC.length; i++) {
            if (raw[i] != MAGIC[i]) {
                throw new IllegalStateException("Invalid vault metadata header");
            }
        }

        int ivLength = raw[MAGIC.length] & 0xFF;
        int ivStart = MAGIC.length + 1;
        int cipherStart = ivStart + ivLength;
        if (ivLength <= 0 || cipherStart > raw.length) {
            throw new IllegalStateException("Invalid vault metadata IV");
        }

        byte[] iv = new byte[ivLength];
        System.arraycopy(raw, ivStart, iv, 0, ivLength);
        byte[] ciphertext = new byte[raw.length - cipherStart];
        System.arraycopy(raw, cipherStart, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return parseEntries(new JSONArray(new String(plaintext, StandardCharsets.UTF_8)));
    }

    VaultEntry addEntry(String comment, String location) throws Exception {
        return addEntry(comment, location, "", 0L);
    }

    VaultEntry addEntry(String comment, String location, String photoBlobId, long encryptedPhotoSize) throws Exception {
        List<VaultEntry> entries = readEntries();
        VaultEntry entry = new VaultEntry(
                UUID.randomUUID().toString(),
                comment.trim(),
                location.trim(),
                System.currentTimeMillis(),
                photoBlobId == null ? "" : photoBlobId,
                encryptedPhotoSize
        );
        entries.add(0, entry);
        writeEntries(entries);
        return entry;
    }

    boolean deleteEntry(String entryId) throws Exception {
        List<VaultEntry> entries = readEntries();
        List<VaultEntry> kept = new ArrayList<>();
        boolean deleted = false;
        for (VaultEntry entry : entries) {
            if (entry.id.equals(entryId)) {
                deleted = true;
            } else {
                kept.add(entry);
            }
        }
        if (deleted) {
            writeEntries(kept);
        }
        return deleted;
    }

    String storageStatus() {
        if (!metadataFile.exists()) {
            return "Файл metadata.enc еще не создан";
        }
        return "metadata.enc: " + metadataFile.length() + " байт, внутреннее хранилище приложения";
    }

    private void writeEntries(List<VaultEntry> entries) throws Exception {
        JSONArray array = new JSONArray();
        for (VaultEntry entry : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", entry.id);
            obj.put("comment", entry.comment);
            obj.put("location", entry.location);
            obj.put("createdAtMs", entry.createdAtMs);
            obj.put("photoBlobId", entry.photoBlobId);
            obj.put("encryptedPhotoSize", entry.encryptedPhotoSize);
            array.put(obj);
        }

        byte[] plaintext = array.toString().getBytes(StandardCharsets.UTF_8);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);

        File parent = metadataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create vault directory");
        }

        try (FileOutputStream out = new FileOutputStream(metadataFile, false)) {
            out.write(MAGIC);
            out.write(iv.length);
            out.write(iv);
            out.write(ciphertext);
            out.flush();
        }
    }

    private List<VaultEntry> parseEntries(JSONArray array) throws Exception {
        List<VaultEntry> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            entries.add(new VaultEntry(
                    obj.getString("id"),
                    obj.optString("comment", ""),
                    obj.optString("location", ""),
                    obj.optLong("createdAtMs", 0L),
                    obj.optString("photoBlobId", ""),
                    obj.optLong("encryptedPhotoSize", 0L)
            ));
        }
        return entries;
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
