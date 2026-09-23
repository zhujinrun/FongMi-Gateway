package android.graphics;

public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int outWidth;
        public int outHeight;
        public boolean inScaled;
        public Bitmap.Config inPreferredConfig;
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        return null;
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) {
        return null;
    }

    public static Bitmap decodeStream(java.io.InputStream is) {
        return null;
    }

    public static Bitmap decodeStream(java.io.InputStream is, Rect outPadding, Options opts) {
        return null;
    }

    public static Bitmap decodeFile(String pathName) {
        return null;
    }

    public static Bitmap decodeFile(String pathName, Options opts) {
        return null;
    }
}
