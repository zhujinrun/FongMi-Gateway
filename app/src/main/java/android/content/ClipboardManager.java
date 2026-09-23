package android.content;

public class ClipboardManager {
    private ClipData primaryClip;

    public void setPrimaryClip(ClipData clip) {
        this.primaryClip = clip;
    }

    public ClipData getPrimaryClip() {
        return primaryClip;
    }

    public CharSequence getText() {
        ClipData clip = primaryClip;
        if (clip == null || clip.getItemCount() == 0) return null;
        return clip.getItemAt(0).getText();
    }

    public void setText(CharSequence text) {
        setPrimaryClip(ClipData.newPlainText(null, text));
    }

    public void clearPrimaryClip() {
        primaryClip = null;
    }

    public boolean hasPrimaryClip() {
        return primaryClip != null;
    }
}
