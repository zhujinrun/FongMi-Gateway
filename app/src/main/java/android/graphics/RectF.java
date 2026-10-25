package android.graphics;

/** Desktop stub: float 版矩形，封面绘制用它算圆角背景和文字基线。 */
public class RectF {

    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {}

    public RectF(float left, float top, float right, float bottom) {
        set(left, top, right, bottom);
    }

    public RectF(Rect r) {
        set(r.left, r.top, r.right, r.bottom);
    }

    public void set(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void setEmpty() {
        set(0f, 0f, 0f, 0f);
    }

    public final float width() { return right - left; }

    public final float height() { return bottom - top; }

    public final float centerX() { return (left + right) * 0.5f; }

    public final float centerY() { return (top + bottom) * 0.5f; }

    public final boolean isEmpty() { return left >= right || top >= bottom; }

    public boolean contains(float x, float y) { return x >= left && x < right && y >= top && y < bottom; }

    public void offset(float dx, float dy) {
        left += dx;
        top += dy;
        right += dx;
        bottom += dy;
    }

    public void inset(float dx, float dy) {
        left += dx;
        top += dy;
        right -= dx;
        bottom -= dy;
    }

    @Override
    public String toString() {
        return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")";
    }
}
