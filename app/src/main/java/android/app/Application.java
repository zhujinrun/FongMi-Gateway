package android.app;

import android.content.ContextWrapper;

/** Platform-faithful: {@code Application extends ContextWrapper}. */
public class Application extends ContextWrapper {
    private static Application instance = new Application();

    public static Application get() {
        return instance;
    }

    public void onCreate() {
    }

    public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) {
        return null;
    }

    public void unregisterReceiver(android.content.BroadcastReceiver receiver) {
    }

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
    }

    /** Desktop stub: some spiders (e.g. wexguard 壳的 InitOrigin$1) implement this. */
    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState);

        void onActivityStarted(Activity activity);

        void onActivityResumed(Activity activity);

        void onActivityPaused(Activity activity);

        void onActivityStopped(Activity activity);

        void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState);

        void onActivityDestroyed(Activity activity);
    }
}
