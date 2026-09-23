package android.content;

public class Intent {
    public Intent() {
    }

    public Intent(String action) {
    }

    public Intent putExtra(String name, String value) {
        return this;
    }

    public Intent putExtra(String name, boolean value) {
        return this;
    }

    public Intent putExtra(String name, int value) {
        return this;
    }

    public String getStringExtra(String name) {
        return null;
    }

    public boolean getBooleanExtra(String name, boolean def) {
        return def;
    }

    public int getIntExtra(String name, int def) {
        return def;
    }

    public String getAction() {
        return null;
    }

    public android.net.Uri getData() {
        return null;
    }
}
