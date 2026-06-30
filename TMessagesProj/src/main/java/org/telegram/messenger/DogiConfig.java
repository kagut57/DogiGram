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

    // NagramX-style extras (all off / default-of by design).
    public static boolean disableNumberRounding;
    public static boolean hideKeyboardOnScroll;
    public static boolean autoPauseVideo;
    public static int maxRecentStickers;

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
        disableNumberRounding = prefs.getBoolean("disable_number_rounding", false);
        hideKeyboardOnScroll = prefs.getBoolean("hide_keyboard_on_scroll", false);
        autoPauseVideo = prefs.getBoolean("auto_pause_video", false);
        maxRecentStickers = prefs.getInt("max_recent_stickers", 0);
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

    // --- Disable number rounding (4.8K -> 4777) ------------------------------------------------

    public static boolean isDisableNumberRounding() {
        loadIfNeeded();
        return disableNumberRounding;
    }

    public static void toggleDisableNumberRounding() {
        loadIfNeeded();
        disableNumberRounding = !disableNumberRounding;
        prefs().edit().putBoolean("disable_number_rounding", disableNumberRounding).apply();
    }

    // --- Hide keyboard on scroll ---------------------------------------------------------------

    public static boolean isHideKeyboardOnScroll() {
        loadIfNeeded();
        return hideKeyboardOnScroll;
    }

    public static void toggleHideKeyboardOnScroll() {
        loadIfNeeded();
        hideKeyboardOnScroll = !hideKeyboardOnScroll;
        prefs().edit().putBoolean("hide_keyboard_on_scroll", hideKeyboardOnScroll).apply();
    }

    // --- Auto pause video on lock screen / background ------------------------------------------

    public static boolean isAutoPauseVideo() {
        loadIfNeeded();
        return autoPauseVideo;
    }

    public static void toggleAutoPauseVideo() {
        loadIfNeeded();
        autoPauseVideo = !autoPauseVideo;
        prefs().edit().putBoolean("auto_pause_video", autoPauseVideo).apply();
    }

    // --- Max recent stickers -------------------------------------------------------------------
    // 0 means "use the value Telegram provides"; any positive value (capped at 200) overrides it.

    public static final int MAX_RECENT_STICKERS_LIMIT = 200;

    public static int getMaxRecentStickers() {
        loadIfNeeded();
        return maxRecentStickers;
    }

    public static void setMaxRecentStickers(int value) {
        loadIfNeeded();
        maxRecentStickers = Math.max(0, Math.min(MAX_RECENT_STICKERS_LIMIT, value));
        prefs().edit().putInt("max_recent_stickers", maxRecentStickers).apply();
    }

    // Returns the configured limit, or the supplied server default when unset (0).
    public static int applyMaxRecentStickers(int serverDefault) {
        loadIfNeeded();
        return maxRecentStickers > 0 ? maxRecentStickers : serverDefault;
    }
}
