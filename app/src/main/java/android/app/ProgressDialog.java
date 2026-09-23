package android.app;

import android.content.Context;

public class ProgressDialog extends AlertDialog {

    public ProgressDialog(Context context) {
        super(context);
    }

    public void setMessage(CharSequence message) {
    }

    public void setProgress(int progress) {
    }

    public void setMax(int max) {
    }

    public void setIndeterminate(boolean indeterminate) {
    }

    public static ProgressDialog show(Context context, CharSequence title, CharSequence message) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.show();
        return dialog;
    }

    public static ProgressDialog show(Context context, CharSequence title, CharSequence message, boolean indeterminate) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setIndeterminate(indeterminate);
        dialog.show();
        return dialog;
    }
}
