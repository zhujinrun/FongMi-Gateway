package android.app;

import android.content.Context;
import android.content.DialogInterface;

public class Dialog implements DialogInterface {

    private final Context context;

    public Dialog(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public void show() {
    }

    public void hide() {
    }

    public void dismiss() {
    }

    public void cancel() {
    }

    public boolean isShowing() {
        return false;
    }

    public void setContentView(int layoutResId) {
    }

    public void setContentView(android.view.View view) {
    }

    public <T extends android.view.View> T findViewById(int id) {
        return null;
    }

    public void setTitle(CharSequence title) {
    }

    public void setCancelable(boolean flag) {
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
    }

    public void setOnShowListener(DialogInterface.OnShowListener listener) {
    }

    public void show(android.view.View v) {
    }
}
