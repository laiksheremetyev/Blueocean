package com.blueocean.app;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class MegaRelayClient {
    static final class Status {
        final boolean connected;
        final String email;
        final String folder;
        final String mode;
        final String lastError;

        Status(boolean connected, String email, String folder, String mode, String lastError) {
            this.connected = connected;
            this.email = email;
            this.folder = folder;
            this.mode = mode;
            this.lastError = lastError;
        }
    }

    static final class RemoteFolder {
        final String name;
        final String path;
        final int objectCount;

        RemoteFolder(String name, String path, int objectCount) {
            this.name = name;
            this.path = path;
            this.objectCount = objectCount;
        }
    }

    static final class RemoteFile {
        final String name;
        final String path;
        final long fileSize;

        RemoteFile(String name, String path, long fileSize) {
            this.name = name;
            this.path = path;
            this.fileSize = fileSize;
        }
    }

    static final class Listing {
        final String folder;
        final java.util.List<RemoteFolder> folders;
        final java.util.List<RemoteFile> files;

        Listing(String folder, java.util.List<RemoteFolder> folders, java.util.List<RemoteFile> files) {
            this.folder = folder;
            this.folders = folders;
            this.files = files;
        }
    }

    private final String baseUrl;
    private final String telegramUserId;
    private final String deviceId;
    private final String deviceToken;

    MegaRelayClient(String baseUrl, String telegramUserId, String deviceId, String deviceToken) {
        this.baseUrl = trimSlash(baseUrl);
        this.telegramUserId = telegramUserId == null ? "" : telegramUserId.trim();
        this.deviceId = deviceId == null ? "" : deviceId.trim();
        this.deviceToken = deviceToken == null ? "" : deviceToken.trim();
    }

    Status status() throws Exception {
        JSONObject json = getJson("/mega/status");
        return new Status(
                json.optBoolean("connected"),
                json.optString("email"),
                json.optString("folder"),
                json.optString("mode"),
                json.optString("last_error")
        );
    }

    Listing listFolder(String folder) throws Exception {
        JSONObject json = getJson("/mega/list?folder=" + encode(folder));
        java.util.List<RemoteFolder> folderOut = new java.util.ArrayList<>();
        java.util.List<RemoteFile> fileOut = new java.util.ArrayList<>();
        org.json.JSONArray folders = json.optJSONArray("folders");
        if (folders != null) {
            for (int i = 0; i < folders.length(); i++) {
                JSONObject item = folders.getJSONObject(i);
                folderOut.add(new RemoteFolder(
                        item.optString("name"),
                        item.optString("path"),
                        item.optInt("object_count")
                ));
            }
        }
        org.json.JSONArray files = json.optJSONArray("files");
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject item = files.getJSONObject(i);
                fileOut.add(new RemoteFile(
                        item.optString("name"),
                        item.optString("path"),
                        item.optLong("size")
                ));
            }
        }
        return new Listing(json.optString("folder"), folderOut, fileOut);
    }

    byte[] downloadFile(String path) throws Exception {
        HttpURLConnection connection = open("/mega/download?path=" + encode(path));
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        byte[] body = readAllBytes(stream);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("MEGA download failed: " + code + " " + new String(body, StandardCharsets.UTF_8));
        }
        return body;
    }

    String createFolder(String parent, String name) throws Exception {
        JSONObject body = new JSONObject();
        body.put("parent", parent == null ? "" : parent.trim());
        body.put("name", name == null ? "" : name.trim());
        JSONObject json = postJson("/mega/mkdir", body);
        return json.optString("path");
    }

    Status connect(String email, String password, String folder) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email == null ? "" : email.trim());
        body.put("password", password == null ? "" : password);
        body.put("folder", folder == null ? "BlueOcean" : folder.trim());
        JSONObject json = postJson("/mega/connect", body);
        return new Status(
                json.optBoolean("connected"),
                json.optString("email"),
                json.optString("folder"),
                json.optString("mode"),
                json.optString("last_error")
        );
    }

    void uploadEncryptedBlob(String folder, String filename, byte[] encryptedBlob) throws Exception {
        String boundary = "BlueOceanMegaBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = open("/mega/upload");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream out = connection.getOutputStream()) {
            writePart(out, boundary, "folder", folder == null ? "" : folder.trim());
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(encryptedBlob);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        String body = code >= 200 && code < 300
                ? readAll(connection.getInputStream())
                : readAll(connection.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("MEGA upload failed: " + code + " " + body);
        }
    }

    private JSONObject getJson(String path) throws Exception {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod("GET");
        return new JSONObject(readResponse(connection));
    }

    private JSONObject postJson(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new JSONObject(readResponse(connection));
    }

    private HttpURLConnection open(String path) throws Exception {
        if (baseUrl.isEmpty()) {
            throw new IllegalStateException("MEGA relay URL is missing");
        }
        URL url = new URL(baseUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        if (!telegramUserId.isEmpty()) {
            connection.setRequestProperty("X-Telegram-User-Id", telegramUserId);
            connection.setRequestProperty("X-Telegram-User", telegramUserId);
        }
        if (!deviceId.isEmpty() && !deviceToken.isEmpty()) {
            connection.setRequestProperty("X-BlueOcean-Device-Id", deviceId);
            connection.setRequestProperty("X-BlueOcean-Device-Token", deviceToken);
        }
        return connection;
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("MEGA relay " + code + ": " + body);
        }
        return body;
    }

    private String readAll(InputStream stream) throws Exception {
        return new String(readAllBytes(stream), StandardCharsets.UTF_8);
    }

    private byte[] readAllBytes(InputStream stream) throws Exception {
        if (stream == null) {
            return new byte[0];
        }
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private void writePart(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String trimSlash(String value) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }
}
