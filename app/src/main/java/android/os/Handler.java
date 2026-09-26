package android.os;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Handler {
    private static final ScheduledExecutorService MAIN = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "android-main");
        t.setDaemon(true);
        return t;
    });

    public Handler() {
    }

    public Handler(Looper looper) {
    }

    public boolean post(Runnable r) {
        MAIN.execute(r);
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        MAIN.schedule(r, delayMillis, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean postAtTime(Runnable r, long uptimeMillis) {
        return post(r);
    }

    public void removeCallbacks(Runnable r) {
    }

    public final boolean sendMessage(android.os.Message msg) {
        return true;
    }

    public void handleMessage(android.os.Message p0) {}
    public boolean hasMessages(int p0) { return false; }
    public android.os.Message obtainMessage() { return null; }
    public android.os.Message obtainMessage(int p0, int p1, int p2) { return null; }
    public android.os.Message obtainMessage(int p0, java.lang.Object p1) { return null; }
    public void removeCallbacksAndMessages(java.lang.Object p0) {}
    public boolean sendEmptyMessage(int p0) { return false; }
    public boolean sendEmptyMessageDelayed(int p0, long p1) { return false; }
    public boolean sendMessageDelayed(android.os.Message p0, long p1) { return false; }
}
