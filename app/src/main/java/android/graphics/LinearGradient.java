package android.graphics;

/** Desktop stub:封面渐变背景。 */
public class LinearGradient extends Shader {

    public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1, TileMode tile) {
        super(tile);
    }

    public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions, TileMode tile) {
        super(tile);
    }
}
