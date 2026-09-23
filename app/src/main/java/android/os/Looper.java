package android.os;

public class Looper {
    private static final Looper MAIN = new Looper();
    private static Looper current = MAIN;

    public static Looper getMainLooper() {
        return MAIN;
    }

    public static Looper myLooper() {
        return current;
    }

    public static void prepare() {
    }

    public static void prepareMainLooper() {
    }

    public static void loop() {
    }
}
