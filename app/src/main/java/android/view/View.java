package android.view;

public class View {
    public interface OnClickListener {
        void onClick(View v);
    }

    public interface OnLongClickListener {
        boolean onLongClick(View v);
    }

    public interface OnFocusChangeListener {
        void onFocusChange(View v, boolean hasFocus);
    }

    public interface OnKeyListener {
        boolean onKey(View v, int keyCode, KeyEvent event);
    }

    public interface OnTouchListener {
        boolean onTouch(View v, MotionEvent event);
    }

    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;

    public void setOnClickListener(OnClickListener l) {
    }

    public void setOnLongClickListener(OnLongClickListener l) {
    }

    public void setOnFocusChangeListener(OnFocusChangeListener l) {
    }

    public void setOnKeyListener(OnKeyListener l) {
    }

    public void setOnTouchListener(OnTouchListener l) {
    }

    public void setVisibility(int visibility) {
    }

    public int getVisibility() {
        return VISIBLE;
    }

    public void setEnabled(boolean enabled) {
    }

    public boolean isEnabled() {
        return true;
    }

    public void setFocusable(boolean focusable) {
    }

    public boolean requestFocus() {
        return true;
    }

    public void addOnAttachStateChangeListener(android.view.View$OnAttachStateChangeListener p0) {}
    public android.view.View findViewById(int p0) { return null; }
    public static int generateViewId() { return 0; }
    public android.graphics.drawable.Drawable getBackground() { return null; }
    public android.content.Context getContext() { return null; }
    public int getHeight() { return 0; }
    public int getId() { return 0; }
    public android.view.ViewGroup.LayoutParams getLayoutParams() { return null; }
    public int getPaddingBottom() { return 0; }
    public int getPaddingLeft() { return 0; }
    public int getPaddingRight() { return 0; }
    public int getPaddingTop() { return 0; }
    public android.view.ViewParent getParent() { return null; }
    public android.view.ViewTreeObserver getViewTreeObserver() { return null; }
    public int getWidth() { return 0; }
    public android.os.IBinder getWindowToken() { return null; }
    public float getX() { return 0f; }
    public float getY() { return 0f; }
    public void removeOnAttachStateChangeListener(android.view.View$OnAttachStateChangeListener p0) {}
    public void requestLayout() {}
    public void setBackground(android.graphics.drawable.Drawable p0) {}
    public void setBackgroundColor(int p0) {}
    public void setBackgroundResource(int p0) {}
    public void setFocusableInTouchMode(boolean p0) {}
    public void setId(int p0) {}
    public void setLayoutParams(android.view.ViewGroup.LayoutParams p0) {}
    public void setPadding(int p0, int p1, int p2, int p3) {}
    public void setX(float p0) {}
    public void setY(float p0) {}
}
