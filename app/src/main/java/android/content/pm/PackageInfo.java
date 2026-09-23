package android.content.pm;

public class PackageInfo {
    public String packageName;
    public String versionName;
    public int versionCode;
    public Signature[] signatures;
    public ApplicationInfo applicationInfo;
    public int firstInstallTime;
    public int lastUpdateTime;
    public String[] requestedPermissions;
}
