package com.github.catvod.crawler;

import android.util.Log;

public class SpiderDebug {

    private static final String TAG = "Spider";

    public static void log(Throwable th) {
        if (th != null) Log.d(TAG, android.util.Log.getStackTraceString(th));
    }

    public static void log(String msg) {
        Log.d(TAG, msg == null ? "" : msg);
    }

    public static void log(String format, Object... args) {
        try {
            Log.d(TAG, String.format(format, args));
        } catch (Exception e) {
            Log.d(TAG, String.valueOf(args));
        }
    }

    public static void log(String tag, String msg) {
        Log.d(tag, msg == null ? "" : msg);
    }
}
