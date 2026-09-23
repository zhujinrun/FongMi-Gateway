package com.fongmi.gateway;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class GsonHolder {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private GsonHolder() {
    }
}
