package android.os;

public class Bundle {
    public void putString(String key, String value) {
    }

    public String getString(String key) {
        return null;
    }

    public void putInt(String key, int value) {
    }

    public int getInt(String key) {
        return 0;
    }

    public void putBoolean(String key, boolean value) {
    }

    public boolean getBoolean(String key) {
        return false;
    }

    public boolean containsKey(String key) {
        return false;
    }

    public java.util.Set<String> keySet() {
        return new java.util.HashSet<>();
    }
}
