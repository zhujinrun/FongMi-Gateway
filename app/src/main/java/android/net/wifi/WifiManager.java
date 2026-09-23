package android.net.wifi;

public class WifiManager {
    public static final int WIFI_STATE_ENABLED = 3;
    public static final int WIFI_STATE_DISABLED = 1;

    public WifiInfo getConnectionInfo() {
        return new WifiInfo();
    }

    public boolean isWifiEnabled() {
        return false;
    }

    public boolean setWifiEnabled(boolean enabled) {
        return false;
    }

    public int getWifiState() {
        return WIFI_STATE_DISABLED;
    }
}
