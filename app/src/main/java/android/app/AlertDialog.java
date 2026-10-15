package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

public class AlertDialog extends Dialog {

    public AlertDialog(Context context) {
        super(context);
    }

    public void setButton(int whichButton, CharSequence text, DialogInterface.OnClickListener listener) {
    }

    public void setButton(CharSequence text, DialogInterface.OnClickListener listener) {
    }

    public static class Builder {
        private final Context context;
        private View view;
        private DialogInterface.OnCancelListener cancelListener;
        private DialogInterface.OnDismissListener dismissListener;
        private DialogInterface.OnShowListener showListener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(CharSequence title) {
            return this;
        }

        public Builder setTitle(int titleId) {
            return this;
        }

        public Builder setMessage(CharSequence message) {
            System.err.println("[ui] AlertDialog.setMessage: " + message);
            return this;
        }

        public Builder setMessage(int messageId) {
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setNeutralButton(int textId, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            return this;
        }

        public Builder setView(View view) {
            this.view = view;
            System.err.println("[ui] AlertDialog.setView: " + view);
            dump(view, 0);
            return this;
        }

        private static int qrSeq = 0;

        private static void dumpBitmap(android.graphics.Bitmap b) {
            try {
                if (b == null) return;
                int w = b.getWidth(), h = b.getHeight();
                int[] px = b.peekPixels();
                if (px == null || w <= 0 || h <= 0) return;
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                img.setRGB(0, 0, w, h, px, 0, w);
                java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "gwui");
                if (!dir.mkdirs() && !dir.isDirectory()) return;
                java.io.File out = new java.io.File(dir,
                        "qr_" + System.currentTimeMillis() + "_" + (++qrSeq) + "_" + w + "x" + h + ".png");
                javax.imageio.ImageIO.write(img, "png", out);
                prune(dir, out);
                System.err.println("[ui] bitmap dumped: " + out.getAbsolutePath());
            } catch (Throwable t) {
                System.err.println("[ui] bitmap dump failed: " + t);
            }
        }

        private static void prune(java.io.File dir, java.io.File keep) {
            java.io.File[] fs = dir.listFiles((d, n) -> n != null && n.startsWith("qr_") && n.endsWith(".png"));
            if (fs == null || fs.length <= 8) return;
            java.util.Arrays.sort(fs, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
            for (int i = 0; i < fs.length - 8; i++) {
                if (!fs[i].equals(keep)) fs[i].delete();
            }
        }

        private static void dump(View v, int depth) {
            if (v == null) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) sb.append("  ");
            sb.append(v.getClass().getName());
            if (v instanceof android.widget.TextView) {
                sb.append(" text=[").append(((android.widget.TextView) v).getText()).append("]");
            }
            if (v instanceof android.widget.ImageView) {
                android.graphics.Bitmap b = ((android.widget.ImageView) v).peekBitmap();
                sb.append(" bitmap=").append(b == null ? "null" : b.getWidth() + "x" + b.getHeight());
                dumpBitmap(b);
            }
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                sb.append(" children=").append(g.getChildCount());
                System.err.println("[ui] " + sb);
                for (int i = 0; i < g.getChildCount(); i++) dump(g.getChildAt(i), depth + 1);
            } else {
                System.err.println("[ui] " + sb);
            }
        }

        public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) {
            this.cancelListener = listener;
            return this;
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) {
            this.dismissListener = listener;
            return this;
        }

        public Builder setOnShowListener(DialogInterface.OnShowListener listener) {
            this.showListener = listener;
            return this;
        }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
            return this;
        }

        public AlertDialog create() {
            AlertDialog dialog = new AlertDialog(context);
            dialog.setView(this.view);
            if (this.cancelListener != null) dialog.setOnCancelListener(this.cancelListener);
            if (this.dismissListener != null) dialog.setOnDismissListener(this.dismissListener);
            if (this.showListener != null) dialog.setOnShowListener(this.showListener);
            return dialog;
        }

        public AlertDialog show() {
            AlertDialog dialog = create();
            dialog.show();
            return dialog;
        }
    }

    public android.widget.Button getButton(int p0) { return null; }

    private static final java.util.List<View> CLICKABLE = new java.util.ArrayList<>();
    private static final java.util.List<String> LABELS = new java.util.ArrayList<>();
    private static final java.util.List<String> TEXTS = new java.util.ArrayList<>();

    /** stub introspection hook (desktop gateway): (re)build the clickable registry for a dialog */
    static void register(View root) {
        java.util.List<View> clicks = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<String> texts = new java.util.ArrayList<>();
        collect(root, clicks, labels, texts);
        // 二维码对话框里没有按钮，不要让它把登录面板的按钮列表清空
        if (clicks.isEmpty()) {
            System.err.println("[ui] registry kept: " + CLICKABLE.size() + " clickable (dialog has no buttons)");
            return;
        }
        CLICKABLE.clear();
        LABELS.clear();
        TEXTS.clear();
        CLICKABLE.addAll(clicks);
        LABELS.addAll(labels);
        TEXTS.addAll(texts);
        System.err.println("[ui] registry: " + CLICKABLE.size() + " clickable " + LABELS);
    }

    private static void collect(View v, java.util.List<View> clicks, java.util.List<String> labels, java.util.List<String> texts) {
        if (v == null) return;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), clicks, labels, texts);
        }
        if (v instanceof android.widget.TextView) {
            texts.add(String.valueOf(((android.widget.TextView) v).getText()));
        }
        if (v.peekOnClickListener() != null) {
            labels.add(v instanceof android.widget.TextView
                ? String.valueOf(((android.widget.TextView) v).getText())
                : v.getClass().getName());
            clicks.add(v);
        }
    }

    public static int clickableCount() {
        return CLICKABLE.size();
    }

    public static String clickableLabel(int i) {
        return i >= 0 && i < LABELS.size() ? LABELS.get(i) : null;
    }

    public static java.util.List<String> texts() {
        return new java.util.ArrayList<>(TEXTS);
    }

    public static boolean click(int i) {
        if (i < 0 || i >= CLICKABLE.size()) return false;
        View v = CLICKABLE.get(i);
        System.err.println("[ui] click(" + i + ") -> " + LABELS.get(i));
        try {
            v.peekOnClickListener().onClick(v);
            return true;
        } catch (Throwable t) {
            System.err.println("[ui] click(" + i + ") failed: " + t);
            return false;
        }
    }
}
