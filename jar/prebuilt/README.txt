jar/prebuilt/ —— 加固壳 spider 的预翻译产物
============================================

这里的每个文件都是「桌面 JVM 能加载的 class jar」，文件名是【源 jar 的 sha256】：

    <sha256-of-source-jar>.jvm.jar

源 jar 指配置里 `spider` 字段指向的那个包（可能伪装成 .jpg），不是解密后的产物。

为什么需要预翻译：
    wexguard 一类加固壳的 jar 在 JVM 上无法自举 —— Init.init 需要 dalvik
    DexClassLoader + ARM .so，而壳自己的 classes.dex 只有 104 个空 *Guard 类。
    唯一办法是在 ARM 环境（模拟器 + frida）里让壳自己解密 payload，再用
    enjarify + InitAlign 翻成 class。完整流程见 tools/wexdump/README.md。

和 fty_spider.jar / wex_spider.jar 的区别：
    那两个是【公共池】，所有配置共用，由 DEFAULT_SPIDER 无条件挂载；
    这里的文件是【某个配置私有】的，类名和业务逻辑绑定那一份加密载荷，
    只在对应指纹出现时才下载，不同配置/不同版本的产物互不干扰。

运行时行为（JarLoader.ensureJvmClasses → Prebuilt.install）：
    算出源 jar 的 sha256，按 <repo>/prebuilt/<sha256>.jvm.jar 去取（走 gh-proxy
    镜像链，与公共池同一通道），成功就落成缓存里的 <源 jar 名>.jvm.jar 直接加载，
    跳过 dex2jar；每台机器只会下载一次。
    取不到（指纹未收录 / 上游已换版本）→ 照旧走 d2j → 该源被判为
    「requires Android native/Dex」并提示不支持，不会静默出「无数据」。
    所以这里缺文件是安全降级，不是错误。

维护：
    上游改载荷 → sha256 变 → 旧文件自动失效且不会被误用。重跑 tools/wexdump
    流水线，把产物按新指纹命名追加即可（不要覆盖旧的，旧配置可能还在用）。
    失效的旧文件可以删，每个约 2MB。

    ⚠ .gitignore 里对 *.jvm.jar 有全局忽略，这里靠 !jar/prebuilt/*.jvm.jar 例外
    放行。新增文件后务必 `git ls-files jar/prebuilt/` 确认真的入库了，否则
    构建出来的 jar 里没有它、运行时永远拉 404。

当前内容：
    9e82d9058843e897e3d75054604d4371949006a297e176e8c7ce668b50d2cad1
        源: http://oss4liview.moji.com/thd_file/2026/10/23/b1a69d9c535fd309388e390b0b1e0749.jpg
            (md5 3c264c629e02273b9ad61e769218c508, 1006882 B)
        产物: 1163 类 / 7863 方法，enjarify + InitAlign（rewritten=796 rejected=0）
        验证: 真实配置 99 个 site → coverage 99/99；观影源 /home 返回 3 分类 8 筛选维度 39 类型
        注意: 该配置的 99 个源里，公共池（fty+wex）只能覆盖 10 个，其余 89 个靠这份产物
