package com.github.catvod.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Dns;

public class OkDns implements Dns {

    private final List<String> hosts = new ArrayList<>();

    public void add(String host) {
        if (host != null && !host.isEmpty() && !hosts.contains(host)) hosts.add(host);
    }

    public void addAll(List<String> items) {
        if (items != null) for (String h : items) add(h);
    }

    public void clear() {
        hosts.clear();
    }

    public List<String> getAll() {
        return hosts;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        for (String entry : hosts) {
            int idx = entry.indexOf('=');
            if (idx <= 0) continue;
            String from = entry.substring(0, idx).trim();
            String to = entry.substring(idx + 1).trim();
            if (from.equalsIgnoreCase(hostname)) {
                try {
                    return Dns.SYSTEM.lookup(to);
                } catch (UnknownHostException ignored) {
                }
            }
        }
        return Dns.SYSTEM.lookup(hostname);
    }
}
