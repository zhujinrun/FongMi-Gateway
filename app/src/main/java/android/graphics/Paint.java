package android.graphics;

public class Paint {
    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    public enum Cap { BUTT, ROUND, SQUARE }

    public enum Join { MITER, ROUND, BEVEL }

    public enum Align { LEFT, CENTER, RIGHT }

    /** Mirrors android.graphics.Paint.FontMetrics (offset baseline相对值). */
    public static class FontMetrics {
        public float top;
        public float ascent;
        public float descent;
        public float bottom;
        public float leading;
    }

    private Shader shader;

    public Paint() {}

    public Paint(int flags) {}

    public void setColor(int color) {}

    public void setStyle(Style style) {}

    public void setStrokeWidth(float width) {}

    public void setAntiAlias(boolean aa) {}

    public void setDither(boolean dither) {}

    public void setFilterBitmap(boolean filter) {}

    public Typeface setTypeface(Typeface typeface) { return typeface; }

    public Typeface getTypeface() { return Typeface.DEFAULT; }

    /** Android returns the previously installed shader, not the new one. */
    public Shader setShader(Shader shader) {
        Shader previous = this.shader;
        this.shader = shader;
        return previous;
    }

    public Shader getShader() { return shader; }

    public void setStrokeCap(Cap cap) {}

    public void setStrokeJoin(Join join) {}

    public void setTextAlign(Align align) {}

    public void setTextSize(float textSize) {}

    public float getTextSize() { return 12f; }

    public float measureText(String text) { return text == null ? 0f : text.length() * 12f; }

    public FontMetrics getFontMetrics() { return new FontMetrics(); }

    public void getTextBounds(java.lang.String p0, int p1, int p2, android.graphics.Rect p3) {}
}
