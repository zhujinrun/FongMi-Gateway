package android.text;

public interface Editable extends CharSequence, android.graphics.drawable.Drawable.Callback {
    Editable append(CharSequence text);

    Editable replace(int st, int en, CharSequence text);

    void clear();

    void clearSpans();
}
