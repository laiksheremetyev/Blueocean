package com.blueocean.app;

import android.content.Context;
import android.content.SharedPreferences;

final class BackupConfigStore {
    private static final String PREFS = "blue_ocean_backup_config";
    private static final String DEFAULT_TGFINDER_URL = "https://my-reality-api.duckdns.org:88/api";
    private static final String DEFAULT_MEGA_RELAY_URL = "https://my-reality-api.duckdns.org:88/api";
    private static final String DEFAULT_TG_USER_ID = "";
    private static final String DEFAULT_TG_FOLDER = "BlueOcean";

    private final SharedPreferences prefs;

    BackupConfigStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("deviceHeaderModeV2", false)) {
            prefs.edit()
                    .putBoolean("devicePaired", false)
                    .putBoolean("deviceHeaderModeV2", true)
                    .apply();
        }
        if (!prefs.getBoolean("telegramPairingV3", false)) {
            SharedPreferences.Editor editor = prefs.edit()
                    .putBoolean("devicePaired", false)
                    .putBoolean("telegramPairingV3", true);
            String savedUserId = prefs.getString("tgUserId", "");
            if ("8173946372".equals((savedUserId == null ? "" : savedUserId).trim())) {
                editor.putString("tgUserId", "");
            }
            editor.apply();
        }
        String savedUserId = prefs.getString("tgUserId", "");
        if ("177442660".equals((savedUserId == null ? "" : savedUserId).trim())) {
            prefs.edit().putString("tgUserId", "").apply();
        }
    }

    void save(String megaEmail, String megaFolder, String megaRelayUrl, String tgFinderUrl, String tgUserId, String tgFolderName, String tgFinderToken) {
        prefs.edit()
                .putString("megaEmail", clean(megaEmail))
                .putString("megaFolder", clean(megaFolder))
                .putString("megaRelayUrl", clean(megaRelayUrl))
                .putString("tgFinderUrl", clean(tgFinderUrl))
                .putString("tgUserId", cleanTelegramUserId(tgUserId))
                .putString("tgFolderName", clean(tgFolderName))
                .putString("tgFinderToken", clean(tgFinderToken))
                .apply();
    }

    void restoreTgFinderDefaults() {
        prefs.edit()
                .putString("tgFinderUrl", DEFAULT_TGFINDER_URL)
                .putString("tgUserId", DEFAULT_TG_USER_ID)
                .putString("tgFolderName", DEFAULT_TG_FOLDER)
                .putBoolean("tgFinderEnabled", true)
                .putBoolean("devicePaired", false)
                .apply();
    }

    void setMegaEnabled(boolean enabled) {
        prefs.edit().putBoolean("megaEnabled", enabled).apply();
    }

    void setTgFinderEnabled(boolean enabled) {
        prefs.edit().putBoolean("tgFinderEnabled", enabled).apply();
    }

    boolean isMegaEnabled() {
        return prefs.getBoolean("megaEnabled", false);
    }

    boolean isTgFinderEnabled() {
        return prefs.getBoolean("tgFinderEnabled", true);
    }

    void setDevicePaired(boolean paired) {
        prefs.edit().putBoolean("devicePaired", paired).apply();
    }

    boolean isDevicePaired() {
        return prefs.getBoolean("devicePaired", false);
    }

    String megaEmail() {
        return prefs.getString("megaEmail", "");
    }

    String megaFolder() {
        return prefs.getString("megaFolder", "BlueOcean");
    }

    String megaRelayUrl() {
        String value = prefs.getString("megaRelayUrl", DEFAULT_MEGA_RELAY_URL);
        return value == null || value.trim().isEmpty() ? DEFAULT_MEGA_RELAY_URL : value.trim();
    }

    String tgFinderUrl() {
        return prefs.getString("tgFinderUrl", DEFAULT_TGFINDER_URL);
    }

    String tgUserId() {
        String value = prefs.getString("tgUserId", "");
        return cleanTelegramUserId(value == null || value.trim().isEmpty() ? DEFAULT_TG_USER_ID : value.trim());
    }

    String tgFolderName() {
        return prefs.getString("tgFolderName", DEFAULT_TG_FOLDER);
    }

    String tgFinderToken() {
        return prefs.getString("tgFinderToken", "");
    }

    boolean hasMegaTarget() {
        return isMegaEnabled() && !megaRelayUrl().isEmpty() && !megaEmail().isEmpty() && !megaFolder().isEmpty();
    }

    boolean hasTgFinderTarget() {
        return isTgFinderEnabled() && !tgFinderUrl().isEmpty();
    }

    boolean hasEnabledTarget() {
        return isMegaEnabled() || isTgFinderEnabled();
    }

    String enabledTargetsText() {
        StringBuilder out = new StringBuilder();
        if (isMegaEnabled()) {
            out.append("MEGA");
        }
        if (isTgFinderEnabled()) {
            if (out.length() > 0) {
                out.append(" + ");
            }
            out.append("TGFinder");
        }
        return out.length() == 0 ? "нет активных каналов" : out.toString();
    }

    String statusText(int pendingCount) {
        String mega = isMegaEnabled()
                ? (hasMegaTarget() ? "MEGA: папка " + megaFolder() : "MEGA: включено, не настроено")
                : "MEGA: выключено";
        String tg = isTgFinderEnabled()
                ? (hasTgFinderTarget() ? "TGFinder: " + tgFolderName() : "TGFinder: включен, не настроен")
                : "TGFinder: выключен";
        return mega + "\n" + tg + "\nОжидают отправки: " + pendingCount;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanTelegramUserId(String value) {
        String out = clean(value);
        if ("177442660".equals(out)) {
            return "8767377252";
        }
        return out;
    }
}
