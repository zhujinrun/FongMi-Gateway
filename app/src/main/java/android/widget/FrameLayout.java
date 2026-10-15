package android.widget;
public class FrameLayout extends android.view.ViewGroup {
    public FrameLayout() {
    }

    public FrameLayout(android.content.Context context) {
        super(context);
    }

    public FrameLayout(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(android.view.ViewGroup.LayoutParams source) { super(source); }
        public LayoutParams(android.view.ViewGroup.LayoutParams source, int w, int h) { super(source); }
    
    public int gravity = 0;
    public int topMargin = 0;
}
}
