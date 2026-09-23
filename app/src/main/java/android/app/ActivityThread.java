package android.app;

import android.content.Intent;

import java.util.HashMap;
import java.util.Map;

public final class ActivityThread {
    private static ActivityThread sCurrent = new ActivityThread();
    private final Map<Object, ActivityClientRecord> mActivities = new HashMap<>();

    public static ActivityThread currentActivityThread() {
        return sCurrent;
    }

    public static boolean isSystem() {
        return false;
    }

    public Application getApplication() {
        return Application.get();
    }

    public static final class ActivityClientRecord {
        public boolean paused;
        public boolean stopped;
        public boolean finishes;
        public Activity activity;
        public Intent activityInfoIntent;
        public Object token;
    }

    public ActivityClientRecord putActivity(Object token, Activity activity) {
        ActivityClientRecord r = new ActivityClientRecord();
        r.activity = activity;
        r.token = token;
        mActivities.put(token == null ? activity : token, r);
        return r;
    }
}
