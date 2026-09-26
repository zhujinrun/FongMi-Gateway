package android.view.animation;

public class LinearInterpolator implements android.view.animation.Interpolator {
    public LinearInterpolator() {}

    public float getInterpolation(float input) {
        return input;
    }
}
