package android.widget;

public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    public static Toast makeText(android.content.Context context, CharSequence text, int duration) {
        System.err.println("[ui] Toast.makeText: " + text);
        return new Toast();
    }

    public static Toast makeText(android.content.Context context, int resId, int duration) {
        System.err.println("[ui] Toast.makeText resId=" + resId);
        return new Toast();
    }

    public void setView(android.view.View view) {
        System.err.println("[ui] Toast.setView: " + view);
    }

    public void show() {
        System.err.println("[ui] Toast.show");
    }

    public void cancel() {
    }
}
