package com.fongmi.gateway.loader;

import com.fongmi.gateway.config.Site;
import com.fongmi.gateway.spider.drpy.DrpySpider;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Prefers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BaseLoader {

    private static final BaseLoader INSTANCE = new BaseLoader();

    private final JarLoader jarLoader = new JarLoader();
    private final ConcurrentHashMap<String, Spider> drpySpiders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> drpyErrors = new ConcurrentHashMap<>();

    public static BaseLoader get() {
        return INSTANCE;
    }

    public void clear() {
        jarLoader.clear();
        drpySpiders.values().forEach(Spider::destroy);
        drpySpiders.clear();
        drpyErrors.clear();
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        if (api == null || api.isEmpty()) return new SpiderNull();
        if (isJs(api)) {
            return jsSpider(key, ext);
        }
        if (isPy(api)) {
            return new SpiderNull();
        }
        if (isCsp(api)) {
            return jarLoader.getSpider(key, api, ext, jar);
        }
        return new SpiderNull();
    }

    /**
     * drpy source: {@code api} is the framework bundle, {@code ext} the site's own rule file.
     * Only the rule is read; the framework is not needed, see {@link DrpySpider}.
     */
    private Spider jsSpider(String key, String ext) {
        String spKey = key + "|" + ext;
        Spider cached = drpySpiders.get(spKey);
        if (cached != null) return cached;
        try {
            DrpySpider spider = new DrpySpider();
            spider.siteKey = key;
            spider.init(android.app.Application.get(), ext);
            drpySpiders.put(spKey, spider);
            drpyErrors.remove(key);
            return spider;
        } catch (Throwable e) {
            // never cache the failure: a rule file that timed out once should retry on the next
            // request instead of turning the source into a permanent SpiderNull
            System.err.println("[drpy] spider fail key=" + key + " ext=" + ext + " -> " + e);
            drpyErrors.put(key, e.getClass().getSimpleName() + ": " + e.getMessage());
            SpiderNull nullSpider = new SpiderNull();
            nullSpider.siteKey = key;
            return nullSpider;
        }
    }

    public Spider getSpider(Site site) {
        if (site == null || site.isEmpty()) return new SpiderNull();
        return getSpider(site.getKey(), site.getApi(), site.getExt(), site.getJar());
    }

    public void parseJar(String jar, boolean recent) {
        if (jar == null || jar.isEmpty()) return;
        jarLoader.parseJar(Crypto.md5(jar), jar);
        if (recent) jarLoader.setRecent(Crypto.md5(jar));
    }

    public void setRecent(String key, String api, String jar) {
        if (isCsp(api)) jarLoader.setRecent(Crypto.md5(jar));
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        return jarLoader.proxy(params);
    }

    public String getError(String key, String jar) {
        // site key first; jar md5 only for parse/load failures, not single-class failures
        String e = drpyErrors.getOrDefault(key, "");
        if (e.isEmpty()) e = jarLoader.getError(key);
        if (e == null || e.isEmpty()) {
            String jaKey = Crypto.md5(String.valueOf(jar));
            e = jarLoader.getError(jaKey);
        }
        return e == null ? "" : e;
    }

    public boolean hasNativeError() {
        return false;
    }

    /** True if a spider class for this csp api is resolvable from the currently loaded jar pool. */
    public boolean hasSpider(String api) {
        if (api == null || api.isEmpty()) return true;
        if (!isCsp(api)) return true;
        return jarLoader.hasSpiderClass(api);
    }

    public String recent() {
        return jarLoader.getRecent();
    }

    private static boolean isJs(String api) {
        return api.contains(".js");
    }

    private static boolean isPy(String api) {
        return api.contains(".py");
    }

    private static boolean isCsp(String api) {
        return api.startsWith("csp_");
    }
}
