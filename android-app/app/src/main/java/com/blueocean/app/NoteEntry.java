package com.blueocean.app;

final class NoteEntry {
    final String id;
    final String title;
    final String body;
    final long createdAtMs;

    NoteEntry(String id, String title, String body, long createdAtMs) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.createdAtMs = createdAtMs;
    }
}
