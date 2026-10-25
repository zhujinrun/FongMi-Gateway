# wexguard 加固壳 spider 逆向手册

面向「配置里的 `spider` 字段是一个被 wexguard 加固过的 jar」这种情况：记录怎么把里面的
dex 拿到、怎么翻译成桌面 JVM 能跑的 class、以及哪些部分在桌面端**根本不可能**跑起来。

配套脚本在同一目录，全部是可重跑的，不依赖本次会话的临时文件。

---

## 1. 结论先说

| 问题 | 结论 |
| --- | --- |
| 纯静态逆 dex 有收益吗 | 没有。`classes.dex` 只有 39KB，104 个 `*Guard` 类的方法体是 `invoke-static BaseSpiderGuard.<init>()V; return-void` 两条指令，业务逻辑一行都不在 |
| 能在桌面端跑起来吗 | 能，但要看具体源。不含 native 解码路径的源（如 `csp_AiNewGuanYing` 观影）实测可返回真实数据 |
| 用 dex2jar 翻可以吗 | **不行**。d2j 会把 `new` 类型和 `<init>` owner 一起改坏，见第 5 节 |
| 用 enjarify 翻可以吗 | 可以。1163 类 0 错误，再叠 `InitAlign` 修 R8 构造器上提即可 |
| 玩偶源为什么无数据 | 上游域名 `wogg.xxooo.cf` 已解析不出（本机 DNS 与 8.8.8.8 均超时），站内也提示「所有玩偶哥哥域名均不可用」。另有 Gson `TypeToken` 泛型签名被 R8 剥离导致反射失败。**与播放器/网关无关** |

---

## 2. 壳的结构

```
classes.dex                      39 KB   104 个 *Guard 空壳类
assets/wexguard_v7.so            ARM32
assets/wexguard_v8.so            AArch64
assets/wexshinidie.guard         943142 B  流加密载荷
```

`DexNative` 只暴露 `getLoader / getSpider / proxyInvoke` 三个 native 方法，解密全在 so 里。

加载路径：

1. `Init.classLoader()` → `getResourceAsStream("assets/wexguard_v8.so")`
2. 复制到 `cacheDir/.wexfnw*` → `System.load`
3. native 解出明文 jar，落到 `code_cache/sharedb/config.db`（**没有 `.dex` 后缀**）
4. 用 `DexClassLoader` 加载它

产物 `config.db` 实际是一个 943KB 的 jar：`classes.dex`(2,050,128 B / 1163 类 / 7863 方法)
+ `assets/libwex_v7.so` + `assets/libwex_v8.so` + `META-INF/MANIFEST.MF`。

---

## 3. 环境

- Android SDK 自带 `emulator.exe` + `adb`（**不要用 MuMu**：它会占 5555，且是另一套运行时）
- AVD：`android-34` / `x86_64` / `google_apis`，启用 `libndk_translation`
- `frida-server` 必须与客户端**同版本**（frida 17 移除了内置 Java bridge，用 16.7.19）
- `adb root` 可用

踩过的坑，按出现顺序：

| 现象 | 原因 / 解法 |
| --- | --- |
| `SecurityException: Writable dex file ... is not allowed` | Android 14 限制。jar 放进 app 私有 `cache/` 并 `chmod 555` |
| `UnsatisfiedLinkError: ... is for EM_AARCH64 instead of EM_X86_64` | `libndk_translation` 只对 app namespace 内经 `loadLibrary` 的路径转译，绝对路径 `System.load` 不走桥接 |
| `/data/app/.../lib/arm64` 不可写 | 自建宿主 APK，manifest 加 `extractNativeLibs="true"`，so 才会被解压出来 |
| `loadLibrary: library "libwexguard_v8.so" not found` | 在壳 jar 内加桥接类 `com.fm.bridge.Loader`（`classes2.dex`），走 `loadLibrary` |
| 壳的 `DexClassLoader` 报 `couldn't find "libwexguard_v8.so"` | 必须把 `ctx.getApplicationInfo().nativeLibraryDir.value` 显式作为 `librarySearchPath` 传进去 |
| `config.db` 进程退出后被清空 | 在 `DexClassLoader.$init` hook 里立刻把产物 ship 出去 |
| `adb push` 路径被 Git Bash 改写 | `export MSYS_NO_PATHCONV=1` |

关键点：JNI 的 `FindClass` 用的是**调用方**的 ClassLoader，所以桥接类必须放在壳 jar 里，
`JNI_OnLoad` 才能看到 `com.github.catvod.spider.*`。

---

## 4. dump

```bash
emulator -avd GuardDump34 -port 5556 &
adb -s emulator-5556 root
adb -s emulator-5556 push frida-server /data/local/tmp/ && adb shell "/data/local/tmp/frida-server &"
python run_dump.py            # spawn 宿主 APK，attach，加载 driver.js，落盘产物
```

产物落在 `out/config.db`，改名为 `real.jar` 后 `unzip` 出 `classes.dex`（本文称 `real.dex`）。

---

## 5. 翻译：为什么 d2j 不行

### 5.1 R8 的构造器上提（dex 本身就不合法于 JVM）

```text
0039: new-instance v1, L.../merge/k/k;
003b: invoke-direct {v1}, Ljava/lang/Object;.<init>:()V     ← owner 不是 k/k
0044: invoke-virtual {v1, v3}, L.../merge/k/k;.i(...)V      ← 用法说明它就是 k/k
```

ART 允许对「未初始化对象」调用祖先类（甚至 `Object`）的构造器；JVM 校验器要求
`<init>` 的 owner **必须等于**被 `new` 的类型。R8 会把匿名子类的构造器上提到父类甚至 `Object`，
所以这类 dex 必须改写才能过 JVM 校验：

- 把 `<init>` owner 改回真实类型
- 给该类型补一个委托父类的桥接构造器（多级继承要**逐级**补，否则还剩 `Call to wrong <init> method`）
- 用 `COMPUTE_FRAMES` 重算 `StackMapTable`（d2j 产物压根没有帧）

`InitAlign.java` 就是干这个的，用 ASM 数据流分析（自定义 `Interpreter`，把 `new` 的产物标记成
`Uninit`）在 `invokespecial <init>` 处回溯操作数栈判定接收者真实类型。

> `getCommonSuperClass` 必须真的沿继承链找**最近**公共祖先。直接返回 `java/lang/Object` 会生成过宽
> 的栈帧，调用点再校验就炸 `Type 'java/lang/Object' is not assignable to ...`。

### 5.2 d2j 的额外破坏

d2j 为了「让输出可校验」，会把 `new X` 改成 `new Object`（配 `Object.<init>`），类型信息彻底丢失；
有些位置连 `<init>` 的描述符都对不上。实测：

| 检查项 | d2j 产物 | enjarify 产物 |
| --- | --- | --- |
| `InitAlign` 里无法判定的构造器点（`rejected`） | 548 | **0** |
| 指令顺序与 dex 一致 | 否（基本块被重排） | 是 |

按顺序把 dex 的 `new` 类型 zip 回去会把无关方法改坏（`rejected` 从 0 涨到 548）。用 LCS 对齐
+ 同时恢复 `<init>` owner 能把 d2j 压到 `rejected=0`，但仍有 9 个类卡在
`Bad type on operand stack`（d2j 把描述符也改错了），属于无底洞。

**所以基线翻译器选 enjarify。**

### 5.3 流水线

```bash
# 1) enjarify: dex -> class
python -m enjarify real.dex -o enj/
# 2) 对齐 R8 构造器上提 + 重算帧
javac -cp asm-9.9.jar:asm-tree-9.9.jar:asm-analysis-9.9.jar -d out InitAlign.java
java  -cp out:asm-9.9.jar:asm-tree-9.9.jar:asm-analysis-9.9.jar \
      InitAlign enj/ aligned.jar
# 3) 叠上壳 jar 里的 assets（native 依赖仍在时保留）
mkdir -p pk/assets && cp <壳 jar 里的>assets/libwex_v*.so pk/assets/
(cd pk && jar cf ../spider.jar .)
```

`InitAlign` 正常输出形如：

```text
classes=1163
analyzed=5908 failed=0 rewritten=796 rejected=0 bridges=211
bridge ctors added=201
```

`rejected` 必须为 0；非 0 说明基线翻译器有问题，不要往下走。

### 5.4 校验

```bash
javac -d out VerifyAll.java
java -cp out:gateway.jar VerifyAll aligned.jar
```

期望：`verifyError=0`。`missing` 是 Android SDK 类缺失，属于预期（网关只桩了用到的那些）。

---

## 6. 已知边界

- **`libwex_v8.so` 的 `LoadNiMa.decode` 路径在桌面端不可能跑**：那是 native 解密/签名逻辑，
  没有 x86_64 实现。走到这条路径的源注定失败，`JarLoader` 会把它归为
  「requires Android native/Dex」。
- **R8 剥离泛型签名**：`grep -aoc "<T:" real.dex` = 1（本该有大量 `Signature` 属性）。
  Gson 的 `TypeToken` 靠 `getGenericSuperclass()` 拿类型参数，签名一没就抛
  `TypeToken must be created with a type argument`。这个**无法在桌面端修补**。
- **载荷会滚动更新**：配置里的 `spider` 带日期与 md5
  （`.../2026/10/23/<hash>.jpg;md5;<hash>`）。新版本要重跑第 4、5 节。
- 网关**不会**在运行时做这套翻译：`Dex2Jar` 仍走 d2j，加固壳 jar 会被判为「无法运行」并提示
  为不支持，而不是静默出「无数据」。要让它真跑起来，走第 7 节的预置产物。

---

## 7. 预置翻译产物（bundled translation）

桌面端无法自举翻译，所以把第 5.4 节产出的 jar **随网关分发**，运行时按内容指纹命中：

```bash
# 1. 算【源 jar】的 sha256 —— 配置里那个伪装成 jpg 的壳包，不是解密产物
sha256sum b1a69d9c....jpg

# 2. 产物按指纹命名放进来
cp aligned.jar app/src/main/resources/prebuilt/<sha256>.jvm.jar

# 3. 重新打包
./gradlew fatJar
```

运行时链路：`JarLoader.ensureJvmClasses()` → `Prebuilt.install()` 算源 jar 的 sha256 →
命中则释放成缓存里的 `<源 jar 名>.jvm.jar` 并直接交给 `URLClassLoader`，完全跳过 d2j。

**为什么用 sha256 而不是配置里的 `;md5;`**：配置可能根本不写 md5，上游也可能重新打包导致
整包 md5 变化而内容等价；内容指纹能同时覆盖这两种情况。反过来，上游一改载荷指纹就变 → 命中不了
→ 回到「requires Android native/Dex」提示，**绝不会把旧版本的类塞给新载荷**。

实测（2026-10-25，源 md5 `3c264c62...` / sha256 `9e82d905...`）：

| 场景 | 结果 |
| --- | --- |
| 原始配置（99 个 site，带 `;md5;`） | `prebuilt translation hit` → `spider coverage: 99/99` |
| 同一文件但配置去掉 `;md5;` | 同样命中 → `99/99`（证明不依赖配置声明） |
| 壳包重新打包（字节等价、sha256 不同） | 不命中 → 走 d2j → 正确降级为 `10/99` + 不支持提示 |

功能验证（真实配置、真实 `csp_AiNewGuanYingGuard` 类名）：

```
GET /home?site=AiNewGuanYing
→ 200, classes=[mv,tv,ac], filters=8 个维度, 类型选项 39 个
```

注意配置里的 `csp_XxxGuard` 会自动回退到 `csp_Xxx`（`JarLoader.loadSpiderClass`），
因为解密后的 dex 里只有不带 `Guard` 后缀的真实类名。

**分发代价**：每个预置产物让 `gateway.jar` 增加约 2MB。产物失效后（上游升级）不会自动消失，
需要人工替换或删除 `prebuilt/` 下的对应文件 —— 这是刻意的，避免未经 dump 验证的翻译被误用。

---

## 8. 脚本清单

| 文件 | 作用 |
| --- | --- |
| `driver.js` | frida 脚本：宿主进程里用 `DexClassLoader` 加载壳 jar、触发解密、截获产物 |
| `run_dump.py` | host 侧：spawn / attach / 落盘 `{t:'artifact'}` 二进制 |
| `dex_pairs.py` | 从 dex 提取每个方法的 `(new-instance 类型, <init> owner)` 有序列表 |
| `fixalign.py` | 用 dex 真值 + LCS 对齐修 d2j 产物（仅在第 5.2 节最后那条死路上需要） |
| `InitAlign.java` | 对齐 R8 构造器上提并重算 StackMapTable（**必跑**） |
| `VerifyAll.java` | 强制 link 全部类，统计 `VerifyError` |
