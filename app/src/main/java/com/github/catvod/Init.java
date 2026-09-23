package com.github.catvod;

import android.content.Context;

public class Init {

    private static Context context;

    public static void init(Context ctx) {
        context = ctx;
    }

    public static Context context() {
        if (context == null) context = new Context();
        return context;
    }
}
