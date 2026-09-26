package android.graphics.drawable;
public class Drawable {
    public interface Callback {
        void invalidateDrawable(Drawable who);
        void scheduleDrawable(Drawable who, Runnable what, long when);
        void unscheduleDrawable(Drawable who, Runnable what);
    }

    public static class ConstantState {
    public android.graphics.drawable.Drawable newDrawable() { return null; }
}
    public void setColorFilter(android.graphics.ColorFilter cf) {}
    public void setAlpha(int alpha) {}
    public void setCallback(Callback cb) {}

    public void draw(android.graphics.Canvas p0) {}
    public android.graphics.drawable.Drawable.ConstantState getConstantState() { return null; }
    public void setBounds(android.graphics.Rect p0) {}
}
