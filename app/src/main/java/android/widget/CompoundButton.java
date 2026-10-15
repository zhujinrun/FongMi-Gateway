package android.widget;

public class CompoundButton extends android.widget.Button {
public CompoundButton() {
    }

    public CompoundButton(android.content.Context context) {
        super(context);
    }

    public CompoundButton(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }


    public boolean isChecked() { return false; }
    public void setChecked(boolean p0) {}
    public void setOnCheckedChangeListener(android.widget.CompoundButton$OnCheckedChangeListener p0) {}
}
