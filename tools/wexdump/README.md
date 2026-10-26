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

### 5.5 重建 Gson 的泛型签名

R8 默认不带 `-keepattributes Signature`，所以载荷里 **13 个 `TypeToken` 匿名子类**全部丢了
`<List<X>>` 这一段。每个子类被构造时 `getGenericSuperclass()` 只能拿到裸 `TypeToken`，
直接抛 `IllegalStateException: TypeToken must be created with a type argument`。

实测在一个 99 源的配置里，**34 个源**栽在这一处 —— 是单一最大病因（超过 DNS 失效和超时之和）。

**怎么把类型参数找回来**：载荷是多个开源 CatVod spider 合并的，这类匿名类只被一个
bean 的 `arrayFrom` 一类的方法用，上游写的就是 `TypeToken.getParameterized(List.class, Item.class)`。
交叉验证靠三件事：

| 手段 | 用法 |
| --- | --- |
| 常量池找使用方 | 谁有该子类的 `Class` 常量 → 谁构造它（`bytecode.members_of_type` 定位到具体方法） |
| 找 `checkcast` | 反序列化结果被 cast 成什么 → 元素类型（如 `AList` cast 到 `merge/l/b`） |
| 找同类字段读 | `l0/d.f()` 把 `l0/d.i()` 返回的列表元素 cast 到 `merge/l0/g` |

**两个必须一起补的地方**（只补前者会在 `defineClass` 之后炸在反射层）：

1. 子类的类级 `Signature`：`Lcom/.../f1/a<Ljava/util/List<Lcom/.../X;>;>;`
2. **`f1.a` 自己**的类级 `Signature`：`<T:Ljava/lang/Object;>Ljava/lang/Object;`
   —— 它的 `<T>` 也被剥了，只补子类会得到
   `MalformedParameterizedTypeException: 0 formal argument(s) 1 actual argument(s)`。

容易踩的细节：`Signature` 属性的 `attribute_length` 恒为 **2**，属性体只放一个
指向常量池 Utf8 的 u2 索引；把签名字符串直接写进属性体会得到
`ClassFormatError: Wrong Signature attribute length`。

```bash
python gsonparams.py <aligned.jar> <out.jar>        # 幂等，跑两次结果一致
java SigProbe.java <out.jar> com.github.catvod.spider.merge.k.f   # 反射层自检
```

签名表 `SIGNATURES` 里每一条都带证据注释。**载荷换代后必须重跑**：类名是 R8 分配的，
新载荷里 `merge/k/f` 完全可能是别的东西，映射不通用。

### 5.6 重建 Gson 的 `@SerializedName`

5.5 修完之后还有一层，而且这一层才是"数据明明取到了、界面却是空的"的真正原因。

R8 会同时做两件事：把 bean 字段名改成 `a`/`b`/`c`，以及把 `@SerializedName` 注解整个删掉
（全 jar 的 `RuntimeVisibleAnnotations` 属性数为 **0**）。于是 Gson 的
`ReflectiveTypeAdapterFactory` 走 `annotation == null` 分支，退化成 `field.getName()`，
序列化出来就是：

```
{"a":[{"a":"1","b":"电影"}],"b":[{"b":"147594","c":"嫁金钗","d":"...","e":"(18/30)"}]}
```

播放器只认 `class` / `list` / `vod_id` / `vod_name`，所以这些源数据齐全却渲染为空。
这也解释了"搜索只有 B 站能出结果"——只有那几个手写 JSON 的源是标准键。

**注解类本身还在**：`merge/z0/b`（`value() -> String`，`alternate() -> String[]`），
`merge/b1/z`（`ReflectiveTypeAdapterFactory`）也还在调 `field.getAnnotation()`。
被删掉的只是*使用点*，这正是 `gsonfields.py` 补回去的东西。

**两个会让修复静默失败的坑**，都必须处理：

1. **注解类的 `@Retention(RUNTIME)` 也被剥了**。字段上明明写着格式正确的
   `RuntimeVisibleAnnotations`，`javap -v` 也能看见，但 JVM 把注解视为 CLASS-retained，
   `Field.getDeclaredAnnotations()` 直接返回**空数组**——`getAnnotation()` 得到 `null`，
   Gson 照旧用字段名。这是最难发现的一种失败：字节层面完全正确，运行时完全无效。
   所以 `merge/z0/b` 自己也要补 `@Retention(RUNTIME)` 和 `@Target({FIELD, METHOD})`。
2. **`alternate()` 的 `AnnotationDefault` 也被剥了**。只补 `value` 的话，Gson 一读
   `annotation.alternate()` 就抛 `IncompleteAnnotationException: missing element alternate`。
   注入时显式写一个空数组元素 `alternate = {}`。

**字段语义怎么定**：不看上游源码去猜顺序（顺序对不上——`merge/k/k` 的构造器跳过了 `a`，
所以序列化结果从 `b` 开始），而是读**构造对象的字节码**：构造器把第几个参数写进哪个字段，
调用点又用 `optString("vodId")` 这类键取的值。两边一对，语义就锁死了。例如：

```java
// merge/k/k.<init>(String,String,String,String)
this.b = arg1;  this.c = arg2;  this.k(arg3);  this.e = arg4;
// 调用点 WexAiWenCai.c(JSONArray)
new k.k(o.optString("vodId"), o.optString("vodName"), o.optString("vodPic"), o.optString("vodRemarks"))
// ⇒ b=vod_id, c=vod_name, d=vod_pic（d 由 setter k() 写，k() 是图片 URL 的 Referer 修正）, e=vod_remarks
```

```bash
python gsonfields.py <in.jar> <out.jar>
java -cp "<gateway.jar>;." AnnoDump  <out.jar> com.github.catvod.spider.merge.z0.b com.github.catvod.spider.merge.k.b
java -cp "<gateway.jar>;." FieldProbe <out.jar> com.github.catvod.spider.merge.z0.b com.github.catvod.spider.merge.y0.m "com.github.catvod.spider.merge.k.b=id1,x,1"
```

`AnnoDump` 查注解是否真被 JVM 认可（坑 1 只有它能暴露），`FieldProbe` 用载荷自带的 Gson
真的序列化一次（坑 2 只有它能暴露）。两个都得跑，只做字节检查会两次都是"看起来成功"。

---

## 6. 已知边界

- **`libwex_v8.so` 的 `LoadNiMa.decode` 路径在桌面端不可能跑**：那是 native 解密/签名逻辑，
  没有 x86_64 实现。走到这条路径的源注定失败，`JarLoader` 会把它归为
  「requires Android native/Dex」。
- **R8 剥离泛型签名，但可以重建**：整个 jar 里 `Signature` 属性为 **0 个**，
  Gson 的 `TypeToken` 靠 `getGenericSuperclass()` 拿类型参数，签名一没就抛
  `TypeToken must be created with a type argument`。修复见第 5.5 节。
- **载荷会滚动更新**：配置里的 `spider` 带日期与 md5
  （`.../2026/10/23/<hash>.jpg;md5;<hash>`）。新版本要重跑第 4、5 节。
- 网关**不会**在运行时做这套翻译：`Dex2Jar` 仍走 d2j，加固壳 jar 会被判为「无法运行」并提示
  为不支持，而不是静默出「无数据」。要让它真跑起来，走第 7 节的预置产物。

---

## 7. 预翻译产物（prebuilt translation）

桌面端无法自举翻译，所以把第 5.4 节产出的 jar 放到仓库的 `jar/prebuilt/`，
与 `fty_spider.jar` / `wex_spider.jar` 走**同一条 gh-proxy 分发通道**：

```bash
# 1. 算【源 jar】的 sha256 —— 配置里那个伪装成 jpg 的壳包，不是解密产物
sha256sum b1a69d9c....jpg

# 2. 产物按指纹命名放进来（不要覆盖旧的，别的配置可能还在用）
cp aligned.jar jar/prebuilt/<sha256>.jvm.jar

# 3. 确认真的入库了 —— .gitignore 对 *.jvm.jar 有全局忽略，靠例外放行
git check-ignore -v jar/prebuilt/<sha256>.jvm.jar   # 期望无输出
```

运行时链路：`JarLoader.ensureJvmClasses()` → `Prebuilt.install()` 判壳（含
`assets/*.guard` 才继续）→ 算源 jar 的 sha256 → 按 `<repo>/prebuilt/<sha256>.jvm.jar`
经镜像链拉取 → 落到缓存里的 `<源 jar 名>.jvm.jar` 直接交给 `URLClassLoader`，跳过 d2j。
**每台机器只下载一次**，之后按 mtime 复用。

**为什么走远程而不是打进 gateway.jar**：这个配置的载荷带日期（几天一换），上游一更新就得
重跑流水线。打进包意味着每次都要重新发一版播放器；放仓库则推一次提交，所有用户下次启动
自动拿到，包体也不用每人多背 2MB。

**为什么用 sha256 而不是配置里的 `;md5;`**：配置可能根本不写 md5，上游也可能重新打包导致
整包 md5 变化而内容等价；内容指纹能同时覆盖这两种情况。反过来，载荷一旦真变，指纹跟着变 →
拉不到 → 回落 d2j 并保持「requires Android native/Dex」提示，
**绝不会把旧版本的类塞给新载荷**。

**为什么不是公共池**：`jar/wex_spider.jar` 那种池是所有配置无条件挂载的；本产物是
**某个配置私有**的（类名与业务逻辑绑定那一份载荷），只在对应指纹出现时才拉。实测该配置的
99 个源里，公共池（fty + wex）只能覆盖 10 个。

实测（2026-10-25，源 md5 `3c264c62...` / sha256 `9e82d905...`，`-Dgateway.repo` 指向本地服务模拟通道）：

| 场景 | 结果 |
| --- | --- |
| 指纹已收录 | `prebuilt translation fetched` → `spider coverage: 99/99` |
| 同一缓冲区二次启动 | 无 `fetched`，直接复用缓存 → `99/99` |
| 壳包重新打包（字节等价、sha256 不同） | 拉不到 → 走 d2j → 正确降级为 `10/99` + 不支持提示 |
| 公共池 `wex_spider.jar`（也是 dex-only） | 被 `looksGuarded` 拦掉，不产生无谓请求 |

功能验证（真实配置、真实 `csp_AiNewGuanYingGuard` 类名）：

```
GET /home?site=AiNewGuanYing
→ 200, classes=[mv,tv,ac], filters=8 个维度, 类型选项 39 个
```

注意配置里的 `csp_XxxGuard` 会自动回退到 `csp_Xxx`（`JarLoader.loadSpiderClass`），
因为解密后的 dex 里只有不带 `Guard` 后缀的真实类名。

**镜像覆盖**：`-Dgateway.repo=<url>` 可以把 `jar/` 通道指到自建镜像或本地目录
（测试时就是这样把 `Repo.BASE` 指到 `http://127.0.0.1:8123/` 的）。

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
| `classfile.py` | 极简 class 文件读写：往返字节一致，可追加类级 `Signature` 属性 |
| `bytecode.py` | 指令长度表 + 线性扫描，定位「哪个方法 `new` 了某个类」「谁 `checkcast` 成什么」 |
| `gsonparams.py` | 按证据表给 `TypeToken` 匿名子类补 `<类型参数>`，并给 `f1.a` 自己补 `<T>`（第 5.5 节） |
| `SigProbe.java` | 反射层自检：直接打印 `getGenericSuperclass()` 的真实返回，字节对不代表反射可用 |

`gsonparams.py` 是**数据级修复**：产物仍然放在 `jar/prebuilt/<源 sha256>.jvm.jar`，
网关和播放器都不用改代码、不用重发版本。重新生成时同名覆盖即可（指纹取自源 jar，
上游载荷不变就还是同一个文件名）。
