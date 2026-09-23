package android.content.pm;
public class PackageItemInfo {
    public String name;
    public CharSequence loadLabel(PackageManager pm) { return name; }
}
