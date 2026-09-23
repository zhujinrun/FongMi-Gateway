package android.graphics;
public class Typeface {
    public static final Typeface DEFAULT = new Typeface();
    public static Typeface create(String familyName, int style) { return DEFAULT; }
    public static Typeface createFromAsset(android.content.res.AssetManager mgr, String path) { return DEFAULT; }
}
