package android.content;

public class ContentResolver {
    public static final String SCHEME_CONTENT = "content";
    public static final String SCHEME_FILE = "file";
    public static final String SCHEME_ANDROID_RESOURCE = "android.resource";

    public java.io.InputStream openInputStream(android.net.Uri uri) throws java.io.FileNotFoundException {
        throw new java.io.FileNotFoundException("not supported: " + uri);
    }

    public java.lang.String getType(android.net.Uri p0) { return null; }
}
