package com.blueocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PatternStore {
    private static final String PREFS = "blue_ocean_gate";
    private static final String KEY_SALT = "pattern_salt";
    private static final String KEY_HASH = "pattern_hash";
    private static final String KEY_FAILURES = "pattern_failures";
    private static final String KEY_LOCK_UNTIL = "pattern_lock_until";
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int MAX_FREE_FAILURES = 5;
    private static final long LOCK_MS = 30_000L;

    private final SharedPreferences prefs;
    private final SecureRandom secureRandom = new SecureRandom();

    PatternStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean hasPattern() {
        return prefs.contains(KEY_SALT) && prefs.contains(KEY_HASH);
    }

    long lockedForMs() {
        long remaining = prefs.getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis();
        return Math.max(remaining, 0L);
    }

    void setup(String pattern) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(pattern, salt);
        prefs.edit()
                .putString(KEY_SALT, encode(salt))
                .putString(KEY_HASH, encode(hash))
                .putInt(KEY_FAILURES, 0)
                .putLong(KEY_LOCK_UNTIL, 0L)
                .apply();
    }

    boolean verify(String pattern) throws Exception {
        if (lockedForMs() > 0L) {
            return false;
        }

        String encodedSalt = prefs.getString(KEY_SALT, "");
        String encodedHash = prefs.getString(KEY_HASH, "");
        if (encodedSalt.isEmpty() || encodedHash.isEmpty()) {
            return false;
        }

        byte[] salt = decode(encodedSalt);
        byte[] expected = decode(encodedHash);
        byte[] actual = derive(pattern, salt);
        boolean ok = MessageDigest.isEqual(expected, actual);

        if (ok) {
            prefs.edit()
                    .putInt(KEY_FAILURES, 0)
                    .putLong(KEY_LOCK_UNTIL, 0L)
                    .apply();
        } else {
            int failures = prefs.getInt(KEY_FAILURES, 0) + 1;
            SharedPreferences.Editor editor = prefs.edit().putInt(KEY_FAILURES, failures);
            if (failures >= MAX_FREE_FAILURES) {
                editor.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + LOCK_MS);
            }
            editor.apply();
        }

        return ok;
    }

    private byte[] derive(String pattern, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pattern.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private byte[] decode(String value) {
        return Base64.decode(value, Base64.NO_WRAP);
    }
}
