package android.text;

public class SpannableStringBuilder {
    private final StringBuilder sb = new StringBuilder();

    public SpannableStringBuilder() {
    }

    public SpannableStringBuilder(CharSequence text) {
        if (text != null) sb.append(text);
    }

    public SpannableStringBuilder append(CharSequence text) {
        sb.append(text);
        return this;
    }

    public SpannableStringBuilder insert(int index, CharSequence text) {
        sb.insert(index, text);
        return this;
    }

    public SpannableStringBuilder replace(int start, int end, CharSequence text) {
        sb.replace(start, end, text == null ? "" : text.toString());
        return this;
    }

    public int length() {
        return sb.length();
    }

    public char charAt(int index) {
        return sb.charAt(index);
    }

    public CharSequence subSequence(int start, int end) {
        return sb.subSequence(start, end);
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
