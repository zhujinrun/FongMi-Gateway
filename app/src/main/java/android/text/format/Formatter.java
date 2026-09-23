package android.text.format;

public class Formatter {
    public static String formatFileSize(android.content.Context context, long sizeBytes) {
        return formatShortFileSize(context, sizeBytes);
    }

    public static String formatShortFileSize(android.content.Context context, long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        double kb = sizeBytes / 1024.0;
        if (kb < 1024) return String.format("%.1fK", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1fM", mb);
        double gb = mb / 1024.0;
        return String.format("%.2fG", gb);
    }

    public static String formatShortFileSize(android.content.Context context, long sizeBytes, int precision) {
        return formatShortFileSize(context, sizeBytes);
    }
}
