package android.view;

public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;

    private final int action;

    public MotionEvent(int action) {
        this.action = action;
    }

    public int getAction() {
        return action;
    }

    public int getActionMasked() {
        return action;
    }

    public float getX() {
        return 0;
    }

    public float getY() {
        return 0;
    }

    public float getRawX() { return 0f; }
    public float getRawY() { return 0f; }
}
