package android.view;

public class Window {
    private final ViewGroup decor = new ViewGroup();

    public View getDecorView() {
        return decor;
    }

    public void setFlags(int flags, int mask) {
    }

    public void addFlags(int flags) {
    }

    public void clearFlags(int flags) {
    }

    public void setStatusBarColor(int color) {
    }

    public void setNavigationBarColor(int color) {
    }

    public android.view.WindowManager.LayoutParams getAttributes() { return null; }
    public void setAttributes(android.view.WindowManager.LayoutParams p0) {}
    public void setBackgroundDrawable(android.graphics.drawable.Drawable p0) {}
    public void setLayout(int p0, int p1) {}
}
