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

未指定 `--spider` 时，自动预下载（含 md5 校验）：

```text
https://gh-proxy.org/https://github.com/zhujinrun/FongMi-Gateway/raw/refs/heads/fongmi/jar/spider_real.jar
  ;md5;
https://gh-proxy.org/https://github.com/zhujinrun/FongMi-Gateway/raw/refs/heads/fongmi/jar/spider_real.jar.md5
```

配置自带的远程 jar 若是 Android 原生壳，会自动回退到 `--spider` / 上述默认 jar；**下载失败只打日志，网关进程不退出**。

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
  --spider "file:///E:/code/AndroidProjects/FengMi/Gateway/spider_real.jar"
```

### 场景 4：自定义网络 spider + md5

```powershell
java -jar app\build\libs\gateway.jar --port 9979 `
  --config "http://fty.xxooo.cf/tv" `
  --spider "https://example.com/spider.jar;md5;https://example.com/spider.jar.md5"
```

`--spider` 支持 `file:///`、`http(s)://`、`;md5;<32位hex>`、`;md5;<md5文件URL>`。

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
