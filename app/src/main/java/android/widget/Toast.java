package android.widget;
public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;
    public static Toast makeText(android.content.Context context, CharSequence text, int duration) { return new Toast(); }
    public void show() {}
    public void cancel() {}
}
