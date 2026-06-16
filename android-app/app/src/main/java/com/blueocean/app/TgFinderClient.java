package com.blueocean.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class TgFinderClient {
    static final class Workspace {
        final boolean ready;
        final int rootFolderId;
        final String hint;

        Workspace(boolean ready, int rootFolderId, String hint) {
            this.ready = ready;
            this.rootFolderId = rootFolderId;
            this.hint = hint;
        }
    }

    static final class RemoteFile {
        final int id;
        final String filename;
        final long fileSize;

        RemoteFile(int id, String filename, long fileSize) {
            this.id = id;
            this.filename = filename;
            this.fileSize = fileSize;
        }
    }

    static final class RemoteFolder {
        final int id;
        final String name;
        final int objectCount;

        RemoteFolder(int id, String name, int objectCount) {
            this.id = id;
            this.name = name;
            this.objectCount = objectCount;
        }
    }

    static final class Listing {
        final java.util.List<RemoteFolder> folders;
        final java.util.List<RemoteFile> files;

        Listing(java.util.List<RemoteFolder> folders, java.util.List<RemoteFile> files) {
            this.folders = folders;
            this.files = files;
        }
    }

    static final class PairingStart {
        final String code;
        final String botStartUrl;
        final String pairCommand;

        PairingStart(String code, String botStartUrl, String pairCommand) {
            this.code = code;
            this.botStartUrl = botStartUrl;
            this.pairCommand = pairCommand;
        }
    }

    private final String baseUrl;
    private final String telegramUserId;
    private final String deviceId;
    private final String deviceToken;
    private final boolean sendDeviceHeaders;

    TgFinderClient(String baseUrl, String telegramUserId, String deviceId, String deviceToken) {
        this(baseUrl, telegramUserId, deviceId, deviceToken, false);
    }

    TgFinderClient(String baseUrl, String telegramUserId, String deviceId, String deviceToken, boolean sendDeviceHeaders) {
        this.baseUrl = trimSlash(baseUrl);
        String user = telegramUserId == null ? "" : telegramUserId.trim();
        this.telegramUserId = user;
        this.deviceId = deviceId == null ? "" : deviceId.trim();
        this.deviceToken = deviceToken == null ? "" : deviceToken.trim();
        this.sendDeviceHeaders = sendDeviceHeaders;
    }

    Workspace workspace() throws Exception {
        JSONObject json = getJson("/folders/workspace");
        JSONObject root = json.optJSONObject("root");
        return new Workspace(
                json.optBoolean("ready"),
                root == null ? 0 : root.optInt("id"),
                json.optString("hint")
        );
    }

    int resolveTelegramUserId() throws Exception {
        JSONObject json = getJson("/devices/resolve", false, true);
        return json.getInt("telegram_user_id");
    }

    PairingStart startPairing() throws Exception {
        JSONObject body = new JSONObject();
        body.put("device_id", deviceId);
        body.put("device_token", deviceToken);
        HttpURLConnection connection = open("/devices/pairing/start", false, false);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        JSONObject json = new JSONObject(readResponse(connection));
        return new PairingStart(
                json.optString("code"),
                json.optString("bot_start_url"),
                json.optString("pair_command")
        );
    }

    java.util.List<RemoteFile> listFiles(int folderId) throws Exception {
        return listFolder(folderId).files;
    }

    Listing listFolder(int folderId) throws Exception {
        JSONObject json = getJson("/folders/ls?parent_id=" + folderId);
        JSONArray dirs = json.optJSONArray("directories");
        JSONArray files = json.optJSONArray("files");
        java.util.List<RemoteFolder> folderOut = new java.util.ArrayList<>();
        java.util.List<RemoteFile> fileOut = new java.util.ArrayList<>();
        if (dirs != null) {
            for (int i = 0; i < dirs.length(); i++) {
                JSONObject dir = dirs.getJSONObject(i);
                folderOut.add(new RemoteFolder(
                        dir.optInt("id"),
                        dir.optString("name"),
                        dir.optInt("object_count")
                ));
            }
        }
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                fileOut.add(new RemoteFile(
                        file.optInt("id"),
                        file.optString("filename"),
                        file.optLong("file_size")
                ));
            }
        }
        return new Listing(folderOut, fileOut);
    }

    byte[] downloadFile(int fileId) throws Exception {
        HttpURLConnection connection = open("/files/" + fileId + "/download");
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        byte[] body = readAllBytes(stream);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("TGFinder download failed: " + code + " " + new String(body, StandardCharsets.UTF_8));
        }
        return body;
    }

    int findOrCreateFolder(int rootFolderId, String folderName) throws Exception {
        String name = folderName == null ? "" : folderName.trim();
        if (name.isEmpty()) {
            return rootFolderId;
        }
        Integer existing = findFolderByName(getArray("/folders/tree"), name);
        if (existing != null) {
            return existing;
        }

        JSONObject body = new JSONObject();
        body.put("parent_id", rootFolderId);
        body.put("folder_name", name);
        JSONObject created = postJson("/folders/mkdir", body);
        return created.getInt("id");
    }

    void uploadEncryptedBlob(int folderId, String filename, byte[] encryptedBlob) throws Exception {
        String boundary = "BlueOceanBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = open("/folders/upload");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream out = connection.getOutputStream()) {
            writePart(out, boundary, "folder_id", String.valueOf(folderId));
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(encryptedBlob);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        String body = code >= 200 && code < 300
                ? readAll(connection.getInputStream())
                : readError(connection);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("TGFinder upload failed: " + code + " " + body);
        }
    }

    private JSONObject getJson(String path) throws Exception {
        return getJson(path, true, sendDeviceHeaders);
    }

    private JSONObject getJson(String path, boolean includeTelegramUser, boolean includeDevice) throws Exception {
        HttpURLConnection connection = open(path, includeTelegramUser, includeDevice);
        connection.setRequestMethod("GET");
        return new JSONObject(readResponse(connection));
    }

    private JSONArray getArray(String path) throws Exception {
        HttpURLConnection connection = open(path, true, sendDeviceHeaders);
        connection.setRequestMethod("GET");
        return new JSONArray(readResponse(connection));
    }

    private JSONObject postJson(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = open(path, true, sendDeviceHeaders);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new JSONObject(readResponse(connection));
    }

    private HttpURLConnection open(String path) throws Exception {
        return open(path, true, sendDeviceHeaders);
    }

    private HttpURLConnection open(String path, boolean includeTelegramUser, boolean includeDevice) throws Exception {
        if (baseUrl.isEmpty()) {
            throw new IllegalStateException("TGFinder URL is missing");
        }
        URL url = new URL(baseUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        if (includeTelegramUser && !telegramUserId.isEmpty()) {
            connection.setRequestProperty("X-Telegram-User-Id", telegramUserId);
            connection.setRequestProperty("X-Telegram-User", telegramUserId);
        }
        if (includeDevice && !deviceId.isEmpty() && !deviceToken.isEmpty()) {
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
            throw new IllegalStateException("TGFinder " + code + ": " + body);
        }
        return body;
    }

    private String readError(HttpURLConnection connection) {
        try {
            return readAll(connection.getErrorStream());
        } catch (Exception e) {
            return "";
        }
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

    private Integer findFolderByName(JSONArray nodes, String name) throws Exception {
        if (nodes == null) {
            return null;
        }
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (name.equalsIgnoreCase(node.optString("name"))) {
                return node.optInt("id");
            }
            Integer child = findFolderByName(node.optJSONArray("children"), name);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private String trimSlash(String value) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
