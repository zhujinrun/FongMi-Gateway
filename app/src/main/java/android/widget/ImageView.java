package android.widget;
public class ImageView extends android.view.View {
    public enum ScaleType { CENTER, CENTER_CROP, CENTER_INSIDE, FIT_CENTER, FIT_XY, FIT_START, FIT_END, MATRIX }
    public void setImageResource(int resId) {}
    public void setImageBitmap(android.graphics.Bitmap bmp) {}
    public void setScaleType(ScaleType scaleType) {}

    public android.graphics.drawable.Drawable getDrawable() { return null; }
    public void setAdjustViewBounds(boolean p0) {}
    public void setImageDrawable(android.graphics.drawable.Drawable p0) {}
}
