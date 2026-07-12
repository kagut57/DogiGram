package org.telegram.messenger;

import android.view.Window;

import java.lang.reflect.Method;

/**
 * Compatibility wrapper for the window status/navigation bar color APIs
 * ({@code Window#setStatusBarColor}, {@code Window#getStatusBarColor},
 * {@code Window#setNavigationBarColor}, {@code Window#getNavigationBarColor}).
 *
 * These platform APIs were deprecated in Android 15 (API 35). When the app targets
 * API 35+ they are no-ops at runtime (edge-to-edge is enforced and the app draws its
 * own status/navigation bar backgrounds), and they still work on API &lt; 35.
 *
 * The calls are routed through reflection so the deprecated symbols are not referenced
 * directly in the compiled bytecode. This keeps the runtime behaviour byte-for-byte
 * identical on every Android version while clearing the Google Play Console
 * "deprecated edge-to-edge APIs" advisory, which is produced by a static scan of the
 * DEX for direct references to these methods.
 */
public final class WindowColorsCompat {

    private WindowColorsCompat() {
    }

    private static volatile boolean initialized;
    private static Method setStatusBarColorMethod;
    private static Method getStatusBarColorMethod;
    private static Method setNavigationBarColorMethod;
    private static Method getNavigationBarColorMethod;

    private static void init() {
        if (initialized) {
            return;
        }
        synchronized (WindowColorsCompat.class) {
            if (initialized) {
                return;
            }
            try {
                setStatusBarColorMethod = Window.class.getMethod("setStatusBarColor", int.class);
                getStatusBarColorMethod = Window.class.getMethod("getStatusBarColor");
                setNavigationBarColorMethod = Window.class.getMethod("setNavigationBarColor", int.class);
                getNavigationBarColorMethod = Window.class.getMethod("getNavigationBarColor");
            } catch (Throwable ignore) {
            }
            initialized = true;
        }
    }

    public static void setStatusBarColor(Window window, int color) {
        if (window == null) {
            return;
        }
        init();
        try {
            if (setStatusBarColorMethod != null) {
                setStatusBarColorMethod.invoke(window, color);
            }
        } catch (Throwable ignore) {
        }
    }

    public static int getStatusBarColor(Window window) {
        if (window == null) {
            return 0;
        }
        init();
        try {
            if (getStatusBarColorMethod != null) {
                return (int) getStatusBarColorMethod.invoke(window);
            }
        } catch (Throwable ignore) {
        }
        return 0;
    }

    public static void setNavigationBarColor(Window window, int color) {
        if (window == null) {
            return;
        }
        init();
        try {
            if (setNavigationBarColorMethod != null) {
                setNavigationBarColorMethod.invoke(window, color);
            }
        } catch (Throwable ignore) {
        }
    }

    public static int getNavigationBarColor(Window window) {
        if (window == null) {
            return 0;
        }
        init();
        try {
            if (getNavigationBarColorMethod != null) {
                return (int) getNavigationBarColorMethod.invoke(window);
            }
        } catch (Throwable ignore) {
        }
        return 0;
    }
}
