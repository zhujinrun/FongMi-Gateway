# CatVod Gateway

纯 JVM 电视源网关：解码 TVBox/影视 配置、加载 Spider（DEX→JVM）、提供 HTTP RPC。

## 构建

```powershell
.\gradlew.bat :app:fatJar
```

产物：`app\build\libs\gateway.jar`

## 运行

```powershell
java -jar app\build\libs\gateway.jar [选项]
```

默认监听 `http://127.0.0.1:9979`，**不带任何参数即可启动**（HTTP 立即可用；spider 在后台预下载，失败不影响进程）。

### 常用选项

| 选项 | 说明 |
|------|------|
| `--port <n>` | 端口，默认 `9979` |
| `--host <ip>` | 绑定地址，默认 `127.0.0.1` |
| `--config <url>` | 启动时加载 TVBox 配置（站点列表） |
| `--spider <url>` | spider jar（缺省用下方默认网络 jar） |
| `--token <t>` | 非本机绑定时的 `X-Gateway-Token`（可自动生成） |
| `--data <dir>` | 数据目录，默认 `~/.gateway` |
| `--quiet` | 减少日志 |

### 默认 spider

未指定 `--spider` 时，自动预下载**两个** jar（含 md5 校验，`|` 连成池）：

```text
https://gh-proxy.org/https://github.com/zhujinrun/FongMi-Gateway/raw/refs/heads/fongmi/jar/fty_spider.jar
  ;md5; .../jar/fty_spider.jar.md5
|https://gh-proxy.org/https://github.com/zhujinrun/FongMi-Gateway/raw/refs/heads/fongmi/jar/wex_spider.jar
  ;md5; .../jar/wex_spider.jar.md5
```

- **fty 池**：饭太硬等 FongMi/JVM 类 spider（原 `spider_real.jar` 的后继，仓库 `jar/fty_spider.jar`）。
- **wex 池**：王二小/WEXGuard 解壳 spider（仓库 `jar/wex_spider.jar`）。
- 池内两个 jar 都会下载并常驻；站点 `csp_XxxGuard` 类按池顺序在各 jar 中查找（含 Guard 后缀回退），**任一配置源裸启动即可用**，无需手工 `--spider`。

配置自带的远程 jar 若是 Android 原生壳，会自动回退到 `--spider` / 上述默认 jar；**下载失败只打日志，网关进程不退出**。

github / gh-proxy 链接自动多镜像回退：按「原始链接 → gh-proxy.org → ghfast.top → ghproxy.net → gh-proxy.com → mirror.ghproxy.com → gitdl.cn → github.moeyy.xyz → github 直连」顺序逐个尝试，md5 文件同样走镜像；命中非首个镜像时日志输出 `[jar] loaded via mirror:`。

### drpy（`.js`）源

站点 `api` 是 drpy 框架（如 `drpy2.min.js`），`ext` 是站点自己的规则文件时，走 `com.fongmi.gateway.spider.drpy`：

- **不执行 drpy2.min.js**。那份框架靠 `import "assets://js/lib/cheerio.min.js"` 拉一整套模块，桌面端没有这套 assets，也不需要：这些规则文件都只是 `var rule = {...}`，字段是数据而非代码。网关用 Rhino 把对象字面量求值成 JSON 读一次，其余全部用 Java 解释。
- **只对 `js:` 片段用 Rhino**（`lazy`、`一级`、`推荐`、`搜索` 里的钩子），宿主函数手写在 `DrpyJs`：`request`/`fetch`/`post`、`setResult`、`jsp.pdfh|pd|pdfa`、`md5`、`base64Encode/Decode`、`joinUrl`、`*_UA` 常量、`fetch_params`。
- 选 Rhino 而非 QuickJS/GraalJS：纯 Java，fat jar 在 win/mac/linux 都是单文件（QuickJS 要按平台带 `.dll/.dylib/.so`，GraalJS 要多 ~50MB）。代价是 jar +1.7MB。

已实现（按 drpy2.min.js 的语义逐条对齐）：`class_name`/`class_url`、`filter`/`filter_url`/`filter_def` 与 `{{fl.x}}` 渲染、`一级`/`搜索` 的三种取值（选择器 / `json:` / `js:`）、`double` 双层列表、`二级` 的 `"*"`（合成 `嗅探播放$url`，id 带 `@@name@@pic`）、`js:` 与对象式 `二级`、`play_parse` + `lazy`。

未实现（现有规则都没用到，降级为「无数据」而不是报错）：`class_parse`、gzip 字符串形式的 `filter`、`play_json` 正则表、xpath 选择器。

两处**刻意的偏离**：

1. `js:` 钩子返回的列表也补 `@@name@@pic` 后缀 —— drpy 参考实现只在一级选择器路径补，导致钩子源（虎牙/斗鱼）详情页没有标题。详情解析会先切掉后缀，所以不影响 id。
2. `play` 结果若是 `.m3u8/.mp4/.flv` 等直链则强制 `parse:0`。drpy 在规则没有 `play_json` 时一律写 `parse:1`，直链再走嗅探会被打断。

实测（饭太硬 49 源，同一配置、同一轮预热）：可用 29 → 31，三个 `.js` 站（虎牙/斗鱼/兔小贝）从 `home-fail` 变为分类出片；兔小贝的 `lazy` 能直接解出 mp4 直链，虎牙/斗鱼是直播页，交给 Player 嗅探。

### 场景 1：只起网关，稍后在 Player 里同步配置

```powershell
java -jar app\build\libs\gateway.jar --port 9979
```

Player 同步时配置地址可留空（用已加载源），或填 TVBox URL 让网关现场 `POST /config`。

### 场景 2：启动即加载配置（推荐日常使用）

```powershell
java -jar app\build\libs\gateway.jar --port 9979 --config "http://fty.xxooo.cf/tv"
```

spider 用默认网络 jar；配置自带 spider 失败时自动兜底。

### 场景 3：指定本地 spider（开发/离线）

```powershell
java -jar app\build\libs\gateway.jar --port 9979 `
  --config "http://fty.xxooo.cf/tv" `
  --spider "file:///E:/code/AndroidProjects/FengMi/fty_spider_real.jar"
```

### 场景 4：自定义网络 spider + md5

```powershell
java -jar app\build\libs\gateway.jar --port 9979 `
  --config "http://fty.xxooo.cf/tv" `
  --spider "https://example.com/spider.jar;md5;https://example.com/spider.jar.md5"
```

`--spider` 支持 `file:///`、`http(s)://`、`;md5;<32位hex>`、`;md5;<md5文件URL>`、`|` 连接的多 jar 池（全部尝试加载，站点类按序在各 jar 中查找）：

```powershell
java -jar app\build\libs\gateway.jar --port 9979 `
  --spider "file:///E:/code/AndroidProjects/FengMi/fty_spider_real.jar|file:///E:/code/AndroidProjects/FengMi/wex_spider_decoded.jar"
```

### 运行后自检

```powershell
curl http://127.0.0.1:9979/health
curl http://127.0.0.1:9979/sites
```

## 接口

| 方法 | 路径 | 参数 |
|------|------|------|
| GET | `/health` | - |
| GET/POST | `/config` | `url` |
| GET | `/sites` | - |
| GET | `/home` | `site` |
| GET | `/category` | `site`,`tid`,`pg`,`filter`,`ext` |
| GET | `/detail` | `site`,`id` |
| GET | `/search` | `site`,`key`,`pg`,`quick` |
| GET | `/player` | `site`,`flag`,`id` |
| POST | `/rpc` | JSON body 见 `method` |
| POST | `/{siteKey}/init` | Player catvod 空 body → `{}` |
| POST | `/{siteKey}/home` | → `{class,list}` 裸 JSON |
| POST | `/{siteKey}/category` | body `{id,page,filters}` → `{list}` |
| POST | `/{siteKey}/detail` | body `{id}` → `{list}` |
| POST | `/{siteKey}/search` | body `{wd,pg}` → `{list}` |
| POST | `/{siteKey}/play` | body `{flag,id}` → `{url,...}` |

非本机绑定时需带 `X-Gateway-Token`。

## 接入 FongMi Player（catvod type 8）

1. 启动网关并加载配置：`java -jar gateway.jar --port 9979 --config <TVBox配置URL>`
2. Player → 设置 → 影视配置 → 添加站点：
   - 类型：`catvod[nodejs]`
   - api：`http://127.0.0.1:9979/{siteKey}`（`siteKey` 取网关 `/sites` 里的 key，勿带斜杠）
3. 走 renderer `cms.ts` 的 type8：`POST {api}/init|home|category|detail|search|play`，**无 `{code,data}` 信封**。

## 示例

```powershell
curl "http://127.0.0.1:9979/config?url=http://www.%E9%A5%AD%E5%A4%AA%E7%A1%AC.cc/tv"
curl "http://127.0.0.1:9979/sites"
curl "http://127.0.0.1:9979/home?site=Bili"
```
