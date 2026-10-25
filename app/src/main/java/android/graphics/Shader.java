package android.graphics;

/** Desktop stub: CatVod 的封面绘制工具（merge/x0/a）会构造渐变，需要一个 Shader 基类。 */
public class Shader {

    /** Mirrors android.graphics.Shader.TileMode. */
    public enum TileMode { CLAMP, REPEAT, MIRROR }

    public Shader() {}

    protected Shader(TileMode tile) {}
}
