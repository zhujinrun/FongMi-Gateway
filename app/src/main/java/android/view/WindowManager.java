package android.view;

public class WindowManager {
    public static class LayoutParams {
        public static final int FLAG_FULLSCREEN = 0x00000400;
        public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
        public static final int FLAG_NOT_FOCUSABLE = 0x00000008;
        public static final int FLAG_LAYOUT_IN_SCREEN = 0x00000100;
        public static final int TYPE_APPLICATION = 2;
        public static final int TYPE_SYSTEM_ALERT = 2003;
        public int width;
        public int height;
        public int flags;
        public int type;
        public float alpha = 1f;
    }

    public void addView(View view, LayoutParams params) {
    }

    public void updateViewLayout(View view, LayoutParams params) {
    }

    public void removeView(View view) {
    }
}
