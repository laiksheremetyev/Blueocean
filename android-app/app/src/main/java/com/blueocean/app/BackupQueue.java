package com.blueocean.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class BackupQueue {
    static final class Item {
        final String blobId;
        final long encryptedSize;
        final String target;
        final String status;
        final String error;
        final long createdAtMs;

        Item(String blobId, long encryptedSize, String target, String status, String error, long createdAtMs) {
            this.blobId = blobId;
            this.encryptedSize = encryptedSize;
            this.target = target;
            this.status = status;
            this.error = error;
            this.createdAtMs = createdAtMs;
        }
    }

    private final File queueFile;

    BackupQueue(Context context) {
        File dir = new File(context.getFilesDir(), "blue_ocean_vault");
        queueFile = new File(dir, "backup_queue.json");
    }

    void enqueuePhoto(String blobId, long encryptedSize) throws Exception {
        enqueuePhoto(blobId, encryptedSize, true, true);
    }

    void enqueuePhoto(String blobId, long encryptedSize, boolean mega, boolean tgFinder) throws Exception {
        JSONArray queue = readQueue();
        long now = System.currentTimeMillis();
        if (mega) {
            queue.put(newItem(blobId, encryptedSize, "mega", now));
        }
        if (tgFinder) {
            queue.put(newItem(blobId, encryptedSize, "tgfinder", now));
        }
        writeQueue(queue);
    }

    int pendingCount() {
        return pendingItems().size();
    }

    java.util.List<Item> pendingItems() {
        java.util.List<Item> out = new java.util.ArrayList<>();
        try {
            JSONArray queue = readQueue();
            for (int i = 0; i < queue.length(); i++) {
                JSONObject item = queue.getJSONObject(i);
                if ("pending".equals(item.optString("status"))) {
                    out.add(toItem(item));
                }
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    java.util.List<Item> pendingItemsForTarget(String target) {
        java.util.List<Item> out = new java.util.ArrayList<>();
        try {
            JSONArray queue = readQueue();
            for (int i = 0; i < queue.length(); i++) {
                JSONObject item = queue.getJSONObject(i);
                if ("pending".equals(item.optString("status")) && itemTargets(item).contains(target)) {
                    out.add(toItem(item, target));
                }
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    java.util.List<Item> allItems() {
        java.util.List<Item> out = new java.util.ArrayList<>();
        try {
            JSONArray queue = readQueue();
            for (int i = queue.length() - 1; i >= 0; i--) {
                out.add(toItem(queue.getJSONObject(i)));
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    void markUploaded(String blobId, String target) throws Exception {
        JSONArray queue = readQueue();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.getJSONObject(i);
            if (blobId.equals(item.optString("blobId"))
                    && "pending".equals(item.optString("status"))
                    && itemTargets(item).contains(target)) {
                item.put("status", "uploaded_" + target);
                item.put("uploadedAtMs", System.currentTimeMillis());
            }
        }
        writeQueue(queue);
    }

    void markFailed(String blobId, String reason) throws Exception {
        markFailed(blobId, "", reason);
    }

    void markFailed(String blobId, String target, String reason) throws Exception {
        JSONArray queue = readQueue();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.getJSONObject(i);
            boolean targetMatches = target == null || target.isEmpty() || itemTargets(item).contains(target);
            if (blobId.equals(item.optString("blobId"))
                    && "pending".equals(item.optString("status"))
                    && targetMatches) {
                item.put("status", "failed");
                item.put("error", reason == null ? "" : reason);
                item.put("failedAtMs", System.currentTimeMillis());
            }
        }
        writeQueue(queue);
    }

    void clear() throws Exception {
        writeQueue(new JSONArray());
    }

    private JSONArray readQueue() throws Exception {
        if (!queueFile.exists()) {
            return new JSONArray();
        }
        byte[] raw = readAll(queueFile);
        if (raw.length == 0) {
            return new JSONArray();
        }
        return new JSONArray(new String(raw, StandardCharsets.UTF_8));
    }

    private JSONObject newItem(String blobId, long encryptedSize, String target, long createdAtMs) throws Exception {
        JSONObject item = new JSONObject();
        item.put("type", "encrypted_photo_blob");
        item.put("blobId", blobId);
        item.put("encryptedSize", encryptedSize);
        item.put("target", target);
        item.put("status", "pending");
        item.put("createdAtMs", createdAtMs);
        return item;
    }

    private Item toItem(JSONObject item) {
        return toItem(item, item.optString("target", legacyTargetLabel(item)));
    }

    private Item toItem(JSONObject item, String target) {
        return new Item(
                item.optString("blobId"),
                item.optLong("encryptedSize"),
                target,
                item.optString("status"),
                item.optString("error"),
                item.optLong("createdAtMs")
        );
    }

    private java.util.List<String> itemTargets(JSONObject item) {
        java.util.List<String> targets = new java.util.ArrayList<>();
        String target = item.optString("target", "");
        if (!target.isEmpty()) {
            targets.add(target);
            return targets;
        }
        JSONArray legacy = item.optJSONArray("targets");
        if (legacy != null) {
            for (int i = 0; i < legacy.length(); i++) {
                String value = legacy.optString(i);
                if (!value.isEmpty()) {
                    targets.add(value);
                }
            }
        }
        return targets;
    }

    private String legacyTargetLabel(JSONObject item) {
        java.util.List<String> targets = itemTargets(item);
        if (targets.isEmpty()) {
            return "";
        }
        return String.join("+", targets);
    }

    private void writeQueue(JSONArray queue) throws Exception {
        File parent = queueFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create vault directory");
        }
        try (FileOutputStream out = new FileOutputStream(queueFile, false)) {
            out.write(queue.toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
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
