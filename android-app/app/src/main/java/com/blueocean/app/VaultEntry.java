package com.blueocean.app;

final class VaultEntry {
    final String id;
    final String comment;
    final String location;
    final long createdAtMs;
    final String photoBlobId;
    final long encryptedPhotoSize;

    VaultEntry(String id, String comment, String location, long createdAtMs) {
        this(id, comment, location, createdAtMs, "", 0L);
    }

    VaultEntry(String id, String comment, String location, long createdAtMs, String photoBlobId, long encryptedPhotoSize) {
        this.id = id;
        this.comment = comment;
        this.location = location;
        this.createdAtMs = createdAtMs;
        this.photoBlobId = photoBlobId;
        this.encryptedPhotoSize = encryptedPhotoSize;
    }
}
