package android.app;

import android.content.Context;
import android.content.DialogInterface;

public class Dialog implements DialogInterface {

    private final Context context;
    private android.view.View stubRoot;
    private DialogInterface.OnDismissListener onDismissListener;
    private DialogInterface.OnCancelListener onCancelListener;
    private DialogInterface.OnShowListener onShowListener;
    private boolean showing;

    private static final java.util.List<Dialog> SHOWN = new java.util.ArrayList<>();

    public Dialog(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public void show() {
        System.err.println("[ui] Dialog.show ctx=" + (context == null ? null : context.getClass().getName()));
        android.app.AlertDialog.register(stubRoot);
        if (!showing) {
            showing = true;
            SHOWN.add(this);
        }
        if (onShowListener != null) {
            try {
                onShowListener.onShow(this);
            } catch (Throwable t) {
                System.err.println("[ui] onShow fail: " + t);
            }
        }
    }

    public void setView(android.view.View view) {
        this.stubRoot = view;
    }

    android.view.View peekStubRoot() {
        return stubRoot;
    }

    public void hide() {
        teardown();
    }

    public void dismiss() {
        teardown();
    }

    public void cancel() {
        if (!showing) return;
        if (onCancelListener != null) {
            try {
                onCancelListener.onCancel(this);
            } catch (Throwable t) {
                System.err.println("[ui] onCancel fail: " + t);
            }
        }
        teardown();
    }

    private void teardown() {
        if (!showing) return;
        showing = false;
        SHOWN.remove(this);
        System.err.println("[ui] dismiss " + getClass().getName()
                + " listener=" + (onDismissListener != null ? onDismissListener.getClass().getName() : "none"));
        if (onDismissListener != null) {
            try {
                onDismissListener.onDismiss(this);
            } catch (Throwable t) {
                System.err.println("[ui] onDismiss fail: " + t);
            }
        }
    }

    public boolean isShowing() {
        return showing;
    }

    /** stub introspection hook (desktop gateway): dismiss every dialog we showed */
    public static void dismissAllShown() {
        System.err.println("[ui] dismissAll size=" + SHOWN.size());
        for (Dialog d : new java.util.ArrayList<>(SHOWN)) {
            try {
                d.dismiss();
            } catch (Throwable t) {
                System.err.println("[ui] dismiss fail: " + t);
            }
        }
        SHOWN.clear();
    }

    public static int shownCount() {
        return SHOWN.size();
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
        this.onCancelListener = listener;
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        this.onDismissListener = listener;
        System.err.println("[ui] setOnDismissListener " + getClass().getName() + " -> "
                + (listener == null ? "null" : listener.getClass().getName()));
    }

    public void setOnShowListener(DialogInterface.OnShowListener listener) {
        this.onShowListener = listener;
    }

    public void show(android.view.View v) {
        setView(v);
        show();
    }

    public android.view.Window getWindow() { return null; }
}
