package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.AttributeSet;

public class TextView extends android.view.View {
    private CharSequence text = "";
    private float textSize = 14f;
    private ColorStateList colors;
    private Typeface typeface;

    public TextView() {
    }

    public TextView(Context context) {
    }

    public TextView(Context context, AttributeSet attrs) {
    }

    public CharSequence getText() {
        return text;
    }

    public void setText(CharSequence text) {
        this.text = text == null ? "" : text;
    }

    public void setText(int resId) {
    }

    public float getTextSize() {
        return textSize;
    }

    public void setTextSize(float size) {
        this.textSize = size;
    }

    public void setTextSize(int unit, float size) {
        this.textSize = size;
    }

    public ColorStateList getTextColors() {
        if (colors == null) colors = ColorStateList.valueOf(-1);
        return colors;
    }

    public void setTextColors(ColorStateList colors) {
        this.colors = colors;
    }

    public int getCurrentTextColor() {
        return getTextColors().getDefaultColor();
    }

    public void setTextColor(int color) {
        this.colors = ColorStateList.valueOf(color);
    }

    public void setTextColor(ColorStateList colors) {
        this.colors = colors;
    }

    public Typeface getTypeface() {
        return typeface;
    }

    public void setTypeface(Typeface tf) {
        this.typeface = tf;
    }

    public void setTypeface(Typeface tf, int style) {
        this.typeface = tf;
    }

    public void computeScroll() {}
    public android.text.TextPaint getPaint() { return null; }
    public void setEllipsize(android.text.TextUtils.TruncateAt p0) {}
    public void setGravity(int p0) {}
    public void setHint(java.lang.CharSequence p0) {}
    public void setHintTextColor(int p0) {}
    public void setHorizontallyScrolling(boolean p0) {}
    public void setInputType(int p0) {}
    public void setLineSpacing(float p0, float p1) {}
    public void setMaxLines(int p0) {}
    public void setMaxWidth(int p0) {}
    public void setPadding(int p0, int p1, int p2, int p3) {}
    public void setScroller(android.widget.Scroller p0) {}
    public void setSingleLine() {}
    public void setSingleLine(boolean p0) {}
}
