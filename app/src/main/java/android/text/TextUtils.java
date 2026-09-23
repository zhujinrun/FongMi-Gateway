package android.text;

import java.util.Iterator;

public class TextUtils {

    public enum TruncateAt {
        START, MIDDLE, END, MARQUEE
    }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean isEmpty(Object[] arr) {
        return arr == null || arr.length == 0;
    }

    public static String join(CharSequence delimiter, Iterable<?> tokens) {
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = tokens.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) sb.append(delimiter);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null || a.length() != b.length()) return false;
        if (a instanceof String && b instanceof String) return a.equals(b);
        return a.toString().contentEquals(b);
    }

    public static CharSequence getTrimmed(CharSequence str) {
        if (str == null) return null;
        String s = str.toString().trim();
        return s;
    }
}
