package android.util;

public class TypedValue {
    public static final int TYPE_NULL = 0x00;
    public static final int TYPE_REFERENCE = 0x01;
    public static final int TYPE_STRING = 0x03;
    public static final int TYPE_FLOAT = 0x04;
    public static final int TYPE_DIMENSION = 0x05;
    public static final int TYPE_FRACTION = 0x06;
    public static final int TYPE_INT_DEC = 0x10;
    public static final int TYPE_INT_HEX = 0x11;
    public static final int TYPE_INT_BOOLEAN = 0x12;

    public static final int COMPLEX_UNIT_PX = 0;
    public static final int COMPLEX_UNIT_DIP = 1;
    public static final int COMPLEX_UNIT_SP = 2;
    public static final int COMPLEX_UNIT_PT = 3;
    public static final int COMPLEX_UNIT_IN = 4;
    public static final int COMPLEX_UNIT_MM = 5;

    public int type;
    public int data;
    public String string;

    public static float complexToFloat(int complex) {
        return (complex & 0xFFFFFF00) * RADIX_MULTS[(complex >> 4) & 3];
    }

    private static final float[] RADIX_MULTS = {0.00390625f, 3.0517578E-5f, 1.1920929E-7f, 4.656613E-10f};

    public static int complexToDimensionPixelSize(int complex, DisplayMetrics metrics) {
        return (int) (complexToFloat(complex) * metrics.density + 0.5f);
    }

    public static float getDimension(DisplayMetrics metrics) {
        return metrics.density;
    }

    public static float applyDimension(int p0, float p1, android.util.DisplayMetrics p2) { return 0f; }
}
