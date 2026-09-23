package com.fongmi.gateway.loader;

import com.fongmi.gateway.config.Site;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Prefers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BaseLoader {

    private static final BaseLoader INSTANCE = new BaseLoader();

    private final JarLoader jarLoader = new JarLoader();

    public static BaseLoader get() {
        return INSTANCE;
    }

    public void clear() {
        jarLoader.clear();
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        if (api == null || api.isEmpty()) return new SpiderNull();
        if (isJs(api)) {
            // JS spiders not implemented in MVP
            return new SpiderNull();
        }
        if (isPy(api)) {
            return new SpiderNull();
        }
        if (isCsp(api)) {
            return jarLoader.getSpider(key, api, ext, jar);
        }
        return new SpiderNull();
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
        String jaKey = Crypto.md5(String.valueOf(jar));
        String e = jarLoader.getError(jaKey);
        if (e == null || e.isEmpty()) e = jarLoader.getError(key);
        return e == null ? "" : e;
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
