package android.os;

import java.io.File;
import java.util.Locale;

public class Environment {

    private static File externalStorage = new File(System.getProperty("user.home"), "gateway-storage");

    public static File getExternalStorageDirectory() {
        if (!externalStorage.exists()) externalStorage.mkdirs();
        return externalStorage;
    }

    public static String getExternalStorageState() {
        return "mounted";
    }

    public static File getExternalStoragePublicDirectory(String type) {
        File f = new File(getExternalStorageDirectory(), type == null ? "" : type);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static boolean isExternalStorageEmulated() {
        return true;
    }

    public static boolean isExternalStorageRemovable() {
        return false;
    }
}
