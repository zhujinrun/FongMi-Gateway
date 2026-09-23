package android.app;

import android.content.Context;

public class Application extends Context {
    private static Application instance = new Application();

    public static Application get() {
        return instance;
    }

    public void onCreate() {
    }

    public void registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) {
    }

    public void unregisterReceiver(android.content.BroadcastReceiver receiver) {
    }
}
