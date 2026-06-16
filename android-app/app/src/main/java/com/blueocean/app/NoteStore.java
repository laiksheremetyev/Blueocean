package com.blueocean.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class NoteStore {
    private static final String PREFS = "blue_ocean_notes";
    private static final String KEY_NOTES = "notes";

    private final SharedPreferences prefs;

    NoteStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<NoteEntry> readNotes() {
        List<NoteEntry> notes = new ArrayList<>();
        String raw = prefs.getString(KEY_NOTES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.getString("id");
                if (id.startsWith("demo-")) {
                    continue;
                }
                notes.add(new NoteEntry(
                        id,
                        obj.optString("title", ""),
                        obj.optString("body", ""),
                        obj.optLong("createdAtMs", 0L)
                ));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return notes;
    }

    void addNote(String title, String body) {
        List<NoteEntry> notes = readNotes();
        notes.add(0, new NoteEntry(
                UUID.randomUUID().toString(),
                title.trim(),
                body.trim(),
                System.currentTimeMillis()
        ));
        writeNotes(notes);
    }

    void updateNote(String id, String title, String body) {
        List<NoteEntry> notes = readNotes();
        List<NoteEntry> updated = new ArrayList<>();
        for (NoteEntry note : notes) {
            if (note.id.equals(id)) {
                updated.add(new NoteEntry(note.id, title.trim(), body.trim(), note.createdAtMs));
            } else {
                updated.add(note);
            }
        }
        writeNotes(updated);
    }

    void deleteNote(String id) {
        List<NoteEntry> notes = readNotes();
        List<NoteEntry> kept = new ArrayList<>();
        for (NoteEntry note : notes) {
            if (!note.id.equals(id)) {
                kept.add(note);
            }
        }
        writeNotes(kept);
    }

    private void writeNotes(List<NoteEntry> notes) {
        JSONArray array = new JSONArray();
        try {
            for (NoteEntry note : notes) {
                JSONObject obj = new JSONObject();
                obj.put("id", note.id);
                obj.put("title", note.title);
                obj.put("body", note.body);
                obj.put("createdAtMs", note.createdAtMs);
                array.put(obj);
            }
            prefs.edit().putString(KEY_NOTES, array.toString()).apply();
        } catch (Exception ignored) {
            // Notes are the visible utility surface; failed writes should not affect the vault.
        }
    }
}
