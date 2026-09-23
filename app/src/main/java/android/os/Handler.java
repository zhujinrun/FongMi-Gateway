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
}
