package android.os;

public class SystemClock {
    public static long uptimeMillis() {
        return System.currentTimeMillis();
    }

    public static long elapsedRealtime() {
        return System.currentTimeMillis();
    }

    public static long elapsedRealtimeNanos() {
        return System.nanoTime();
    }

    public static long sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
        return ms;
    }
}
