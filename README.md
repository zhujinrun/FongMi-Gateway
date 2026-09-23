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

非本机绑定时需带 `X-Gateway-Token`。

## 示例

```powershell
curl "http://127.0.0.1:9979/config?url=http://www.%E9%A5%AD%E5%A4%AA%E7%A1%AC.cc/tv"
curl "http://127.0.0.1:9979/sites"
curl "http://127.0.0.1:9979/home?site=Bili"
```
