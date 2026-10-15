package android.widget;
public class EditText extends TextView {
public EditText() {
    }

    public EditText(android.content.Context context) {
        super(context);
    }

    public EditText(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }


    public android.text.Editable getText() { return null; }
    public void setSelection(int p0) {}
}
