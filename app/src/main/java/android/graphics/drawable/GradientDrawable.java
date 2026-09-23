package android.graphics.drawable;
public class GradientDrawable extends Drawable {
    public enum Orientation { TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR }
    public GradientDrawable() {}
    public GradientDrawable(Orientation orientation, int[] colors) {}
    public void setCornerRadius(float radius) {}
    public void setColor(int color) {}
}
