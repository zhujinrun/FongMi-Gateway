package android.content;

public class ComponentName {
    private final String pkg;
    private final String cls;

    public ComponentName(String pkg, String cls) {
        this.pkg = pkg;
        this.cls = cls;
    }

    public ComponentName(Class<?> cls) {
        this.pkg = cls.getPackage() != null ? cls.getPackage().getName() : "";
        this.cls = cls.getName();
    }

    public String getPackageName() {
        return pkg;
    }

    public String getClassName() {
        return cls;
    }

    public String flattenToString() {
        return pkg + "/" + cls;
    }

    public static ComponentName unflattenFromString(String str) {
        if (str == null) return null;
        int idx = str.indexOf('/');
        if (idx < 0) return null;
        return new ComponentName(str.substring(0, idx), str.substring(idx + 1));
    }
}
