package android.content;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Context {

    private static File rootDir;

    private final java.util.concurrent.ConcurrentHashMap<String, SharedPreferences> prefs = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setRootDir(File dir) {
        rootDir = dir;
        if (rootDir != null && !rootDir.exists()) rootDir.mkdirs();
    }

    public static File getRootDir() {
        if (rootDir == null) {
            rootDir = new File(System.getProperty("user.home"), ".gateway");
        }
        return rootDir;
    }

    public File getCacheDir() {
        return ensure(new File(getRootDir(), "cache"));
    }

    public File getFilesDir() {
        return ensure(new File(getRootDir(), "files"));
    }

    public File getDir(String name, int mode) {
        return ensure(new File(getRootDir(), name));
    }

    public Context getApplicationContext() {
        if (this instanceof android.app.Application) return this;
        return android.app.Application.get();
    }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        SharedPreferences p = prefs.get(name);
        if (p == null) {
            p = new SharedPreferences.Impl(new File(getRootDir(), "prefs"), name);
            SharedPreferences prev = prefs.putIfAbsent(name, p);
            if (prev != null) p = prev;
        }
        return p;
    }

    public Object getSystemService(String name) {
        return null;
    }

    public String getPackageName() {
        return "com.fongmi.gateway";
    }

    public File getExternalFilesDir(String type) {
        return ensure(new File(getRootDir(), "external/" + (type == null ? "" : type)));
    }

    public File getExternalCacheDir() {
        return ensure(new File(getRootDir(), "external/cache"));
    }

    public File getDatabasePath(String name) {
        return ensure(new File(getRootDir(), "databases")).toPath().resolve(name == null ? "db" : name).toFile();
    }

    public ContentResolver getContentResolver() {
        return new ContentResolver();
    }

    public android.content.pm.PackageManager getPackageManager() {
        return new android.content.pm.PackageManager();
    }

    public android.content.res.Resources getResources() {
        return new android.content.res.Resources();
    }

    public void sendBroadcast(Intent intent) {
    }

    public Intent getIntent() {
        return new Intent();
    }

    private static File ensure(File f) {
        if (!f.exists()) f.mkdirs();
        return f;
    }
}
