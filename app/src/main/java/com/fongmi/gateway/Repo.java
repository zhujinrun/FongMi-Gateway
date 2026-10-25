package com.fongmi.gateway;

/**
 * 分发通道的统一入口。
 *
 * <p>池文件、预置翻译产物都从同一个 GitHub 仓库的 {@code jar/} 目录取，前面挂 gh-proxy 镜像前缀；
 * {@code JarLoader.expand()} 会据此再展开出其余镜像做重试。换仓库只需要改这里。
 */
public final class Repo {

    private Repo() {
    }

    public static final String BASE =
            "https://gh-proxy.org/https://github.com/zhujinrun/FongMi-Gateway/raw/refs/heads/fongmi/jar/";
}
