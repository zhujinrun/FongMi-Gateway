package android.view;

import java.util.ArrayList;
import java.util.List;

public class ViewGroup extends View {
    private final List<View> children = new ArrayList<>();

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width;
        public int height;

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public LayoutParams(LayoutParams source) {
            this.width = source.width;
            this.height = source.height;
        }
    }

    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin;
        public int topMargin;
        public int rightMargin;
        public int bottomMargin;

        public MarginLayoutParams(int width, int height) {
            super(width, height);
        }

        public MarginLayoutParams(LayoutParams source) {
            super(source);
        }

        public MarginLayoutParams(MarginLayoutParams source) {
            super(source);
            this.leftMargin = source.leftMargin;
            this.topMargin = source.topMargin;
            this.rightMargin = source.rightMargin;
            this.bottomMargin = source.bottomMargin;
        }
    }

    public void addView(View child) {
        if (child != null) children.add(child);
    }

    public void addView(View child, LayoutParams params) {
        addView(child);
    }

    public void removeView(View child) {
        children.remove(child);
    }

    public void removeAllViews() {
        children.clear();
    }

    public void setLayoutParams(LayoutParams params) {
    }

    public int getChildCount() {
        return children.size();
    }

    public View getChildAt(int index) {
        if (index < 0 || index >= children.size()) return null;
        return children.get(index);
    }
}
