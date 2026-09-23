package com.github.catvod.spider;

public final class HideUtils {

    private HideUtils() {
    }

    public static String encrypt(String text) {
        return SpiderCrypto.encrypt(text);
    }

    public static String decrypt(String text) {
        return SpiderCrypto.decrypt(text);
    }

    public static int[] calcResult(int[] input) {
        return SpiderCrypto.calcResult(input);
    }

    public static String tingMd5(String text) {
        return SpiderCrypto.native_ting_md5(text);
    }
}
