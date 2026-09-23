package android.widget;
public class LinearLayout extends android.view.ViewGroup {
    public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
        public static final int HORIZONTAL = 0;
        public static final int VERTICAL = 1;
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(android.view.ViewGroup.LayoutParams source) { super(source); }
        public LayoutParams(android.view.ViewGroup.LayoutParams source, int w, int h) { super(source); }
    }
    public void setOrientation(int orientation) {}
}
