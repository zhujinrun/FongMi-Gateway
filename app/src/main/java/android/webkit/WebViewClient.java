package android.webkit;
public class WebViewClient {
    public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
    public void onPageFinished(WebView view, String url) {}
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {}
}
