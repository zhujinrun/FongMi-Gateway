package android.content.pm;

public class PackageManager {
    public static final int GET_META_DATA = 128;
    public static final int GET_SIGNATURES = 64;
    public static final int GET_SIGNING_CERTIFICATES = 134217728;
    public static final int MATCH_UNINSTALLED_PACKAGES = 8192;

    public android.content.pm.PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        if (packageName == null) throw new NameNotFoundException("null");
        android.content.pm.PackageInfo info = new android.content.pm.PackageInfo();
        info.packageName = packageName;
        info.versionName = "1.0";
        info.versionCode = 1;
        info.applicationInfo = new android.content.pm.ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        info.applicationInfo.targetSdkVersion = 34;
        info.applicationInfo.minSdkVersion = 21;
        info.applicationInfo.flags = 1;
        info.applicationInfo.dataDir = System.getProperty("user.home");
        if ((flags & (GET_SIGNATURES | GET_SIGNING_CERTIFICATES)) != 0) {
            info.signatures = new android.content.pm.Signature[]{new android.content.pm.Signature()};
        } else {
            info.signatures = new android.content.pm.Signature[0];
        }
        return info;
    }

    public android.content.pm.ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        if (packageName == null) throw new NameNotFoundException("null");
        android.content.pm.ApplicationInfo info = new android.content.pm.ApplicationInfo();
        info.packageName = packageName;
        info.targetSdkVersion = 34;
        info.minSdkVersion = 21;
        info.flags = 1;
        info.dataDir = System.getProperty("user.home");
        info.sourceDir = "";
        return info;
    }

    public String[] getPackagesForUid(int uid) {
        return new String[]{"com.fongmi.gateway"};
    }

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException(String name) {
            super(name);
        }
    }
}