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
}
