package android.net;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Uri {

    private final String raw;
    private String scheme;
    private String ssp;
    private String authority;
    private String path;
    private String query;
    private String fragment;
    private String schemeSpecificPart;

    private Uri(String uri) {
        this.raw = uri == null ? "" : uri;
        parse();
    }

    public static Uri parse(String uriString) {
        return new Uri(uriString);
    }

    public static Uri fromFile(File file) {
        return new Uri("file://" + file.getAbsolutePath());
    }

    public static String encode(String s) {
        return encode(s, null);
    }

    public static String encode(String s, String allow) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            char c = (char) (b & 0xff);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '!' || c == '~' || c == '*' || c == '\'' || c == '(' || c == ')'
                    || (allow != null && allow.indexOf(c) >= 0)) {
                sb.append(c);
            } else {
                sb.append('%');
                String hex = Integer.toHexString(b & 0xff).toUpperCase();
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
        }
        return sb.toString();
    }

    public static String decode(String s) {
        if (s == null) return null;
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private void parse() {
        int fragment = raw.indexOf('#');
        String noFrag = fragment >= 0 ? raw.substring(0, fragment) : raw;
        this.fragment = fragment >= 0 ? raw.substring(fragment + 1) : null;

        int schemeSep = noFrag.indexOf(':');
        if (schemeSep > 0) {
            this.scheme = noFrag.substring(0, schemeSep).toLowerCase();
            this.schemeSpecificPart = noFrag.substring(schemeSep + 1);
        } else {
            this.schemeSpecificPart = noFrag;
        }

        String ssp = schemeSpecificPart == null ? "" : schemeSpecificPart;
        if (ssp.startsWith("//")) {
            int pathStart = ssp.indexOf('/', 2);
            int query = ssp.indexOf('?');
            String authEnd;
            if (pathStart < 0 && query < 0) {
                this.authority = ssp.substring(2);
                this.path = "";
            } else {
                int end = -1;
                if (pathStart >= 0 && query >= 0) end = Math.min(pathStart, query);
                else if (pathStart >= 0) end = pathStart;
                else end = query;
                this.authority = ssp.substring(2, end);
                if (pathStart >= 0) {
                    int q = ssp.indexOf('?', pathStart);
                    this.path = q >= 0 ? ssp.substring(pathStart, q) : ssp.substring(pathStart);
                    this.query = q >= 0 ? ssp.substring(q + 1, ssp.length() - (fragment >= 0 ? 0 : 0)) : null;
                    if (this.query != null && this.fragment != null) {
                        // query already limited if we cut fragment earlier from noFrag
                    }
                    if (q >= 0) {
                        String qPart = ssp.substring(q + 1);
                        int f = qPart.indexOf('#');
                        this.query = f >= 0 ? qPart.substring(0, f) : qPart;
                    }
                } else {
                    this.path = "";
                    if (query >= 0) {
                        this.query = ssp.substring(query + 1);
                    }
                }
            }
            if (query >= 0 && pathStart >= 0 && pathStart > query) {
                this.path = "";
                this.query = ssp.substring(query + 1);
            }
        } else {
            this.path = ssp;
            int q = ssp.indexOf('?');
            if (q >= 0) {
                this.path = ssp.substring(0, q);
                this.query = ssp.substring(q + 1);
            }
        }
        this.ssp = schemeSpecificPart;
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        if (authority == null) return null;
        int port = authority.lastIndexOf(':');
        String host = port > 0 ? authority.substring(0, port) : authority;
        int at = host.lastIndexOf('@');
        return at >= 0 ? host.substring(at + 1) : host;
    }

    public int getPort() {
        if (authority == null) return -1;
        int port = authority.lastIndexOf(':');
        if (port > 0) {
            try {
                return Integer.parseInt(authority.substring(port + 1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    public String getPath() {
        return path;
    }

    public String getLastPathSegment() {
        if (path == null || path.isEmpty()) return null;
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    public String getQuery() {
        return query;
    }

    public String getFragment() {
        return fragment;
    }

    public String getEncodedPath() {
        return path;
    }

    public String getEncodedQuery() {
        return query;
    }

    public Set<String> getQueryParameterNames() {
        Map<String, String> map = getQueryParameters();
        return map.keySet();
    }

    public Map<String, String> getQueryParameters() {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            else if (!pair.isEmpty()) map.put(decode(pair), "");
        }
        return map;
    }

    public String getQueryParameter(String key) {
        return getQueryParameters().get(key);
    }

    public Uri buildUpon() {
        return this;
    }

    public boolean isAbsolute() {
        return scheme != null && !scheme.isEmpty();
    }

    @Override
    public String toString() {
        return raw;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Uri u)) return false;
        return raw.equals(u.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }
}
