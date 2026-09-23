package com.github.catvod.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefers {

    private static final String NAME = "gateway";
    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getSharedPreferences(NAME, 0);
    }

    private static SharedPreferences get() {
        if (prefs == null) {
            prefs = new Context().getSharedPreferences(NAME, 0);
        }
        return prefs;
    }

    public static String getString(String key) {
        return getString(key, "");
    }

    public static String getString(String key, String def) {
        return get().getString(key, def);
    }

    public static void put(String key, String value) {
        get().edit().putString(key, value).apply();
    }

    public static int getInt(String key, int def) {
        return get().getInt(key, def);
    }

    public static void put(String key, int value) {
        get().edit().putInt(key, value).apply();
    }

    public static long getLong(String key, long def) {
        return get().getLong(key, def);
    }

    public static void put(String key, long value) {
        get().edit().putLong(key, value).apply();
    }

    public static boolean getBoolean(String key, boolean def) {
        return get().getBoolean(key, def);
    }

    public static void put(String key, boolean value) {
        get().edit().putBoolean(key, value).apply();
    }

    public static void remove(String key) {
        get().edit().remove(key).apply();
    }
}
