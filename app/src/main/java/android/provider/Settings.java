package android.provider;

public class Settings {
    public static final String ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION = "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION";
    public static final String ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS";
    public static final String ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES";

    public static class Secure {
        public static String getString(android.content.ContentResolver resolver, String name) {
            return null;
        }

        public static String getString(android.content.ContentResolver resolver, String name, String def) {
            return def;
        }

        public static final String ANDROID_ID = "android_id";
    }

    public static class System {
        public static String getString(android.content.ContentResolver resolver, String name) {
            return null;
        }
    }
}
