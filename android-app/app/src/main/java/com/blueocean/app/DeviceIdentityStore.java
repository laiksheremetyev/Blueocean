package com.blueocean.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.UUID;

final class DeviceIdentityStore {
    private static final String PREFS = "blue_ocean_device_identity";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final SharedPreferences prefs;

    DeviceIdentityStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensure();
    }

    String deviceId() {
        return prefs.getString("deviceId", "");
    }

    String deviceToken() {
        return prefs.getString("deviceToken", "");
    }

    String pairCommand() {
        return "/pair " + deviceId() + " " + deviceToken();
    }

    private void ensure() {
        if (!deviceId().isEmpty() && !deviceToken().isEmpty()) {
            return;
        }
        prefs.edit()
                .putString("deviceId", "bo-" + UUID.randomUUID())
                .putString("deviceToken", randomHex(32))
                .apply();
    }

    private String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        StringBuilder out = new StringBuilder(bytes * 2);
        for (byte b : data) {
            out.append(HEX[(b >> 4) & 0x0F]);
            out.append(HEX[b & 0x0F]);
        }
        return out.toString();
    }
}
