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
}
