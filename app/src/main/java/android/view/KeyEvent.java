package android.view;

public class KeyEvent {
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_MENU = 82;
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MULTIPLE = 2;

    private final int action;
    private final int code;

    public KeyEvent(int action, int code) {
        this.action = action;
        this.code = code;
    }

    public KeyEvent(int code) {
        this(ACTION_DOWN, code);
    }

    public int getAction() {
        return action;
    }

    public int getKeyCode() {
        return code;
    }

    public int getActionMasked() {
        return action;
    }

    public boolean isCanceled() {
        return false;
    }
}
