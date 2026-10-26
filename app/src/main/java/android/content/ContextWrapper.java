package android.content;

import java.io.File;

/**
 * Minimal {@code ContextWrapper} for desktop execution.
 *
 * <p>Protected spider jars carry this type not only as a parameter of UI helpers
 * ({@code merge.z.w0.E(ContextWrapper, ...)}) but as the <em>superclass</em> of their own
 * {@code Activity}/{@code Application} shims. Java resolves it while verifying those classes, so a
 * missing entry aborts the whole jar with {@code NoClassDefFoundError} — which the loader then
 * reports as "Android native only" and drops the config spider.
 *
 * <p>Every override delegates to {@link #getBaseContext()} and falls back to the inherited shim
 * behaviour when there is no base (the normal case here: {@code Activity} and {@code Application}
 * are constructed without one). The fallback also keeps {@code Application} from recursing into
 * itself through {@link #getApplicationContext()}.
 */
public class ContextWrapper extends Context {

    private Context mBase;

    /** For shim subclasses ({@code Activity}/{@code Application}) that are built without a base. */
    protected ContextWrapper() {
    }

    public ContextWrapper(Context base) {
        mBase = base;
    }

    protected void attachBaseContext(Context base) {
        mBase = base;
    }

    public Context getBaseContext() {
        return mBase;
    }

    @Override
    public Context getApplicationContext() {
        return mBase != null ? mBase.getApplicationContext() : super.getApplicationContext();
    }

    @Override
    public String getPackageName() {
        return mBase != null ? mBase.getPackageName() : super.getPackageName();
    }

    @Override
    public File getCacheDir() {
        return mBase != null ? mBase.getCacheDir() : super.getCacheDir();
    }

    @Override
    public File getFilesDir() {
        return mBase != null ? mBase.getFilesDir() : super.getFilesDir();
    }

    @Override
    public File getDir(String name, int mode) {
        return mBase != null ? mBase.getDir(name, mode) : super.getDir(name, mode);
    }

    @Override
    public File getExternalFilesDir(String type) {
        return mBase != null ? mBase.getExternalFilesDir(type) : super.getExternalFilesDir(type);
    }

    @Override
    public File getExternalCacheDir() {
        return mBase != null ? mBase.getExternalCacheDir() : super.getExternalCacheDir();
    }

    @Override
    public File getDatabasePath(String name) {
        return mBase != null ? mBase.getDatabasePath(name) : super.getDatabasePath(name);
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return mBase != null ? mBase.getSharedPreferences(name, mode) : super.getSharedPreferences(name, mode);
    }

    @Override
    public Object getSystemService(String name) {
        return mBase != null ? mBase.getSystemService(name) : super.getSystemService(name);
    }

    @Override
    public ContentResolver getContentResolver() {
        return mBase != null ? mBase.getContentResolver() : super.getContentResolver();
    }

    @Override
    public android.content.pm.PackageManager getPackageManager() {
        return mBase != null ? mBase.getPackageManager() : super.getPackageManager();
    }

    @Override
    public android.content.res.Resources getResources() {
        return mBase != null ? mBase.getResources() : super.getResources();
    }

    @Override
    public android.content.res.AssetManager getAssets() {
        return mBase != null ? mBase.getAssets() : super.getAssets();
    }

    @Override
    public android.content.pm.ApplicationInfo getApplicationInfo() {
        return mBase != null ? mBase.getApplicationInfo() : super.getApplicationInfo();
    }

    @Override
    public Intent getIntent() {
        return mBase != null ? mBase.getIntent() : super.getIntent();
    }

    @Override
    public void sendBroadcast(Intent intent) {
        if (mBase != null) mBase.sendBroadcast(intent);
        else super.sendBroadcast(intent);
    }

    @Override
    public Intent registerReceiver(android.content.BroadcastReceiver receiver, IntentFilter filter) {
        return mBase != null ? mBase.registerReceiver(receiver, filter) : super.registerReceiver(receiver, filter);
    }

    @Override
    public void unregisterReceiver(android.content.BroadcastReceiver receiver) {
        if (mBase != null) mBase.unregisterReceiver(receiver);
        else super.unregisterReceiver(receiver);
    }

    @Override
    public ClassLoader getClassLoader() {
        return mBase != null ? mBase.getClassLoader() : super.getClassLoader();
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        return mBase != null ? mBase.checkCallingOrSelfPermission(permission) : super.checkCallingOrSelfPermission(permission);
    }

    @Override
    public android.graphics.drawable.Drawable getDrawable(int id) {
        return mBase != null ? mBase.getDrawable(id) : super.getDrawable(id);
    }
}
