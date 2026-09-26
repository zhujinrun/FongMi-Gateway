package android.graphics;

public class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888 }

    public int getWidth() {
        return 0;
    }

    public int getHeight() {
        return 0;
    }

    public void recycle() {
    }

    public boolean isRecycled() {
        return false;
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap();
    }

    public static android.graphics.Bitmap createScaledBitmap(android.graphics.Bitmap p0, int p1, int p2, boolean p3) { return null; }
    public int getAllocationByteCount() { return 0; }
    public void prepareToDraw() {}
}
