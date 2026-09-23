# CatVod Gateway

纯 JVM 电视源网关：解码 TVBox/影视 配置、加载 Spider（DEX→JVM）、提供 HTTP RPC。

## 构建

```powershell
.\gradlew.bat :app:fatJar
```

产物：`app\build\libs\gateway.jar`

## 运行

```powershell
java -jar app\build\libs\gateway.jar --port 9979
# 预加载配置
java -jar app\build\libs\gateway.jar --config "http://www.饭太硬.cc/tv"
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
