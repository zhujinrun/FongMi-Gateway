package android.webkit;
public class WebView extends android.view.ViewGroup {
    public WebView(android.content.Context context) {}
    public void loadUrl(String url) {}
    public void postUrl(String url, byte[] data) {}
    public void evaluateJavascript(String script, Object callback) {}
    public void setWebViewClient(WebViewClient client) {}
    public void setWebChromeClient(WebChromeClient client) {}
    public WebSettings getSettings() { return new WebSettings(); }
    public void destroy() {}
    public void stopLoading() {}
    public void reload() {}
    public boolean canGoBack() { return false; }
    public void goBack() {}
    public void addJavascriptInterface(Object object, String name) {}

    public void clearCache(boolean p0) {}
    public void clearFormData() {}
    public void clearHistory() {}
    public void setBackgroundColor(int p0) {}
    public void setLayoutParams(android.view.ViewGroup.LayoutParams p0) {}
}
