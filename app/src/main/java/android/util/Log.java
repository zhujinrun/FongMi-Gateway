package android.util;

public class Log {
    public static int v(String tag, String msg) {
        return println("V", tag, msg, null);
    }

    public static int d(String tag, String msg) {
        return println("D", tag, msg, null);
    }

    public static int i(String tag, String msg) {
        return println("I", tag, msg, null);
    }

    public static int w(String tag, String msg) {
        return println("W", tag, msg, null);
    }

    public static int e(String tag, String msg) {
        return println("E", tag, msg, null);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return println("E", tag, msg, tr);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return println("W", tag, msg, tr);
    }

    public static int d(String tag, String msg, Throwable tr) {
        return println("D", tag, msg, tr);
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        tr.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    public static int println(int priority, String tag, String msg) {
        return println(String.valueOf((char) priority), tag, msg, null);
    }

    private static int println(String level, String tag, String msg, Throwable tr) {
        if (Boolean.getBoolean("gateway.quiet")) return 0;
        System.err.println(level + "/" + tag + ": " + msg);
        if (tr != null) tr.printStackTrace(System.err);
        return 0;
    }
}
