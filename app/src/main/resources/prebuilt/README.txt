prebuilt/ —— 加固壳 spider 的预翻译产物
==========================================

这里的每个文件都是「桌面 JVM 能加载的 class jar」，文件名是【源 jar 的 sha256】：

    <sha256-of-source-jar>.jvm.jar

源 jar 指配置里 `spider` 字段指向的那个包（可能伪装成 .jpg），不是解密后的产物。

为什么需要预置：
    wexguard 一类加固壳的 jar 在 JVM 上无法自举 —— Init.init 需要 dalvik
    DexClassLoader + ARM .so。唯一办法是在 ARM 环境（模拟器 + frida）里让壳
    自己解密 payload，再用 enjarify + InitAlign 翻成 class。完整流程见
    Gateway/tools/wexdump/README.md。

运行时行为（JarLoader.ensureJvmClasses → Prebuilt.install）：
    算出源 jar 的 sha256，命中这里的文件就释放到缓存并直接加载，跳过 dex2jar。
    命中不了就照旧走 d2j —— 加固壳会被判为「requires Android native/Dex」并
    提示为不支持，而不是静默出「无数据」。所以这里缺文件是安全降级，不是错误。

维护：
    上游改载荷 → sha256 变 → 旧文件失效但不会误用。重跑 tools/wexdump 流水线，
    把产物按新指纹重命名追加即可。失效的旧文件可以删（每个约 2MB）。

当前内容：
    9e82d9058843e897e3d75054604d4371949006a297e176e8c7ce668b50d2cad1
        源: http://oss4liview.moji.com/thd_file/2026/10/23/b1a69d9c535fd309388e390b0b1e0749.jpg
            (md5 3c264c629e02273b9ad61e769218c508, 1006882 B)
        产物: 1163 类 / 7863 方法，enjarify + InitAlign（rewritten=796 rejected=0）
        验证: 真实配置 99 个 site → coverage 99/99；观影源 /home 返回 3 分类 8 筛选维度 39 类型
