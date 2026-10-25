package android.graphics.drawable;

/** Desktop stub: CatVod 的图标构建 helper 会用 LayerDrawable 叠图层。 */
public class LayerDrawable extends Drawable {
    private final Drawable[] layers;
    private final int[] ids;

    public LayerDrawable(Drawable[] layers) {
        this.layers = layers == null ? new Drawable[0] : layers;
        this.ids = new int[this.layers.length];
    }

    public void setId(int index, int id) {
        if (index >= 0 && index < ids.length) ids[index] = id;
    }

    public int getId(int index) {
        return index >= 0 && index < ids.length ? ids[index] : -1;
    }

    public int getNumberOfLayers() {
        return layers.length;
    }

    public Drawable getDrawable(int index) {
        return index >= 0 && index < layers.length ? layers[index] : null;
    }

    public void setDrawable(int index, Drawable drawable) {
        if (index >= 0 && index < layers.length) layers[index] = drawable;
    }

    public void setLayerInset(int index, int l, int t, int r, int b) {
    }

    public void setLayerWidth(int index, int w) {
    }

    public void setLayerHeight(int index, int h) {
    }
}
