package android.widget;
public class ImageView extends android.view.View {
    private android.graphics.Bitmap bitmap;

    public ImageView() {
    }

    public ImageView(android.content.Context context) {
        super(context);
    }

    public ImageView(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public enum ScaleType { CENTER, CENTER_CROP, CENTER_INSIDE, FIT_CENTER, FIT_XY, FIT_START, FIT_END, MATRIX }
    public void setImageResource(int resId) {}
    public void setImageBitmap(android.graphics.Bitmap bmp) {
        this.bitmap = bmp;
    }

    /** stub introspection hook (desktop gateway) */
    public android.graphics.Bitmap peekBitmap() { return bitmap; }
    public void setScaleType(ScaleType scaleType) {}

    public android.graphics.drawable.Drawable getDrawable() { return null; }
    public void setAdjustViewBounds(boolean p0) {}
    public void setImageDrawable(android.graphics.drawable.Drawable p0) {}
}
