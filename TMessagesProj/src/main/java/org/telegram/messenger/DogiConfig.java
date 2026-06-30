/*
 * DogiGram custom configuration.
 *
 * Holds the extra DogiGram-only toggles ported from NagramXF. The values are
 * persisted in the same "dogigramconfig" SharedPreferences used by
 * DogiGramSettingsActivity, so everything stays in one place.
 */
package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

public class DogiConfig {

    public static final String PREFS = "dogigramconfig";

    // Double-tap-on-message actions.
    public static final int DOUBLE_TAP_NONE = 0;
    public static final int DOUBLE_TAP_REACTION = 1;
    public static final int DOUBLE_TAP_REPLY = 2;
    public static final int DOUBLE_TAP_SAVE = 3;
    public static final int DOUBLE_TAP_EDIT = 4;
    public static final int DOUBLE_TAP_TRANSLATE = 5;

    public static boolean uploadDownloadBoost;
    public static boolean showId;
    public static boolean showDcId;
    public static int doubleTapAction;

    private static boolean loaded;

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }
        SharedPreferences prefs = prefs();
        uploadDownloadBoost = prefs.getBoolean("upload_download_boost", false);
        showId = prefs.getBoolean("show_id", true);
        showDcId = prefs.getBoolean("show_dc_id", true);
        doubleTapAction = prefs.getInt("double_tap_action", DOUBLE_TAP_REACTION);
        loaded = true;
    }

    // --- Upload & download boost ---------------------------------------------------------------

    public static boolean isUploadDownloadBoost() {
        loadIfNeeded();
        return uploadDownloadBoost;
    }

    public static void toggleUploadDownloadBoost() {
        loadIfNeeded();
        uploadDownloadBoost = !uploadDownloadBoost;
        prefs().edit().putBoolean("upload_download_boost", uploadDownloadBoost).apply();
    }

    // --- Profile ID / DC ID --------------------------------------------------------------------

    public static boolean isShowId() {
        loadIfNeeded();
        return showId;
    }

    public static void toggleShowId() {
        loadIfNeeded();
        showId = !showId;
        prefs().edit().putBoolean("show_id", showId).apply();
    }

    public static boolean isShowDcId() {
        loadIfNeeded();
        return showDcId;
    }

    public static void toggleShowDcId() {
        loadIfNeeded();
        showDcId = !showDcId;
        prefs().edit().putBoolean("show_dc_id", showDcId).apply();
    }

    // --- Double-tap action ---------------------------------------------------------------------

    public static int getDoubleTapAction() {
        loadIfNeeded();
        return doubleTapAction;
    }

    public static void setDoubleTapAction(int action) {
        loadIfNeeded();
        doubleTapAction = action;
        prefs().edit().putInt("double_tap_action", action).apply();
    }
}
