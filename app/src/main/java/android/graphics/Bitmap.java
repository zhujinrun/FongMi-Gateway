package android.graphics;

public class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888 }

    private int width;
    private int height;
    private int[] pixels;

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** stub introspection hook (desktop gateway): pixel buffer, may be null */
    public int[] peekPixels() {
        return pixels;
    }

    public void recycle() {
    }

    public boolean isRecycled() {
        return false;
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        Bitmap b = new Bitmap();
        b.width = width;
        b.height = height;
        b.pixels = new int[Math.max(0, width * height)];
        return b;
    }

    public static android.graphics.Bitmap createScaledBitmap(android.graphics.Bitmap p0, int p1, int p2, boolean p3) { return null; }
    public int getAllocationByteCount() { return 0; }
    public void prepareToDraw() {}
    public void setPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        if (this.pixels == null || pixels == null) return;
        for (int row = 0; row < height; row++) {
            int src = offset + row * stride;
            int dst = (y + row) * this.width + x;
            for (int col = 0; col < width; col++) {
                if (dst + col >= 0 && dst + col < this.pixels.length && src + col < pixels.length) {
                    this.pixels[dst + col] = pixels[src + col];
                }
            }
        }
    }

    public void setPixel(int x, int y, int color) {
        if (pixels == null) return;
        int i = y * width + x;
        if (i >= 0 && i < pixels.length) pixels[i] = color;
    }

    public int getPixel(int x, int y) {
        if (pixels == null) return 0;
        int i = y * width + x;
        return (i >= 0 && i < pixels.length) ? pixels[i] : 0;
    }

    public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        if (this.pixels == null || pixels == null) return;
        for (int row = 0; row < height; row++) {
            int dst = offset + row * stride;
            int src = (y + row) * this.width + x;
            for (int col = 0; col < width; col++) {
                if (dst + col < pixels.length && src + col < this.pixels.length) {
                    pixels[dst + col] = this.pixels[src + col];
                }
            }
        }
    }
}
