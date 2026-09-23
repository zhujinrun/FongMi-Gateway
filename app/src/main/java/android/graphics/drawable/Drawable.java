package android.graphics.drawable;
public class Drawable {
    public interface Callback {
        void invalidateDrawable(Drawable who);
        void scheduleDrawable(Drawable who, Runnable what, long when);
        void unscheduleDrawable(Drawable who, Runnable what);
    }

    public static class ConstantState {}
    public void setColorFilter(android.graphics.ColorFilter cf) {}
    public void setAlpha(int alpha) {}
    public void setCallback(Callback cb) {}
}
