package android.content.res;

public class ColorStateList {
    private final int color;

    public ColorStateList(int[][] states, int[] colors) {
        this.color = colors != null && colors.length > 0 ? colors[0] : 0;
    }

    public static ColorStateList valueOf(int color) {
        return new ColorStateList(new int[][]{{}}, new int[]{color});
    }

    public int getDefaultColor() {
        return color;
    }

    public int getColorForState(int[] stateSet, int defaultColor) {
        return color;
    }
}
