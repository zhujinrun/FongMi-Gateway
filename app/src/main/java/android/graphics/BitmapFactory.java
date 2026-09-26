package android.graphics;

public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int outWidth;
        public int outHeight;
        public boolean inScaled;
        public Bitmap.Config inPreferredConfig;
    
    public boolean inInputShareable = false;
    public boolean inPurgeable = false;
    public int inSampleSize = 0;
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

    public static android.graphics.Bitmap decodeResource(android.content.res.Resources p0, int p1, android.graphics.BitmapFactory.Options p2) { return null; }
}
