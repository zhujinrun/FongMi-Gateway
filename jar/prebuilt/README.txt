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

完整性校验（强制，2026-10-26）：
    每个产物旁必须挂 <sha256>.jvm.jar.md5（32 位小写 hex + CRLF，与
    fty_spider.jar.md5 / wex_spider.jar.md5 同格式）。Prebuilt.install 会先取
    这个 .md5，再比对下载到的字节：

        md5 匹配   → 落盘安装
        md5 不匹配 → 拒绝，记 [jar] prebuilt md5 mismatch
        .md5 缺失/非法 → 拒绝，记 [jar] prebuilt md5 missing or invalid

    后两种都回落 d2j 并提示「需 Android native」，绝不会把校验不过的字节装进 JVM。
    这是硬门禁不是建议 —— 防的是镜像/CDN 投毒、缓存被换、半截下载。
    注意 .md5 与产物走同一通道，挡不住能同时篡改两端的中间人；真正的防篡改
    靠「谁有权推仓库」。指纹（sha256）是【源】jar 的，md5 是【产物】的，
    两者校验的是不同东西，不能互相替代。

    ⚠ 新增产物时必须同时生成 .md5，否则运行时会拒绝安装（安全降级，不是报错）：
        certutil -hashfile <sha256>.jvm.jar MD5
        → 把 32 位 hex 写进 <sha256>.jvm.jar.md5（CRLF 结尾，一行）

维护：
    上游改载荷 → sha256 变 → 旧文件自动失效且不会被误用。重跑 tools/wexdump
    流水线，把产物按新指纹命名追加即可（不要覆盖旧的，旧配置可能还在用）。
    失效的旧文件可以删，每个约 2MB。

    ⚠ .gitignore 里对 *.jvm.jar 有全局忽略，这里靠 !jar/prebuilt/*.jvm.jar 例外
    放行。新增文件后务必 `git ls-files jar/prebuilt/` 确认真的入库了，否则
    构建出来的 jar 里没有它、运行时永远拉 404。

站点域名配置 siteconfig.json（2026-10-27 新增，同日扩到全量 16 键）
==============================================================
    本目录里唯一的非 class 产物：一份 {站点 key: [域名...]} 映射，由
    SiteDomains.sync() 在启动时下载，落到 <data>/files/AiWex/siteconfig。

    为什么需要：加固壳里有一批 spider 不在字节码里写死站址，它们的 init() 先调
    merge.x0.e.J(key) 读这个文件里的域名数组，逐个探测可用主机（6s 超时取最快），
    探测不到才退回代码里的兜底域名。安卓壳由配置中心下发；桌面网关原先没有这条
    链路，files/AiWex 一直是空的。

    后果：这些源全撞在兜底域名上。玩偶的兜底是 wogg.xxooo.cf（DNS 已不存在），
    请求全失败后 spider 返回空骨架 {"list":[],"m":0,"n":0,"q":72,...}，
    与「上游全站停更」的形态完全一样 —— 2026-10-26 曾据此误判玩偶是上游真空。

    ★ 官方下发链路（2026-10-27 挖出，以后重抓照这个走）
      入口是字节码里硬编码的一串 OSS 地址，都在
      com/github/catvod/spider/merge/a0/c 的 static{} 里：

          http://upload.baicanuc.cn/ossfiles/1768320816/item.txt
          http://ossduo.baicanuc.cn/ossfiles/1768320816/item.txt
          http://ossone.baicanuc.cn/ossfiles/1768320816/item.txt
          （同级还有 api.txt / aigo.txt / baidu.txt / guangya.txt）

      item.txt 是一个装饰用的 JSON（vod_Name/notice/gg 等），其中 config_site
      字段指向真正的映射文件，伪装成 .jpg：

          config_site: http://oss4liview.moji.com/thd_file/<日期>/<hash>.jpg

      那个 jpg 就是 1232 字节的纯 JSON，16 个键，正是安卓壳下发的原件。

      一条命令重抓（换新日期只需重跑，item.txt 的路径是固定的）：
          curl -s http://ossduo.baicanuc.cn/ossfiles/1768320816/item.txt \
            | python -c "import json,sys;print(json.load(sys.stdin)['config_site'])" \
            | xargs curl -s | python -m json.tool > jar/prebuilt/siteconfig.json

      注意 api.txt 那个 44 字节 base64（解出来正好 32 字节）是 AES-256 密钥，
      与 siteconfig 无关，不要拿它去解这份 JSON。

    ★ 判据不是「返回 200」就行
      这些 spider 解析的是 MacCMS 网页，要能取到 /index.php/vodshow/... 且页面
      含 mac_total。2026-10-27 实测有两类假活：
        - guanying 的 6 个域名，5 个返回 1076 B 的「浏览器安全验证」PoW 挑战页
          （static.1234cdn.com 的 powSolve），无 JS 环境永远过不去；
        - dj92.com 之类是域名停放页（Cowboy server，470 B）。
      反过来，「域名死」也不等于站点死：大站点普遍有多个镜像，探测会自动选活的。

    ★ 覆盖的 16 个键（对应 99 站里 15 个，erxiao/zhizhen 不在该配置内）
      wogg=玩偶  huajuan=花卷  muou=木偶  guanying=观影  duoduo=多多
      huban=胡巴  leijing 123pan shayang jutou=桔子  qiwei=七味
      libvio panku=盘库  kuaiying=快影  labi=蜡笔  shandian=闪电
      另有 woniu4k(蜗牛) 与 pianku(盘库别名) 是两个类私有的键，官方表里没有，
      它们靠兜底域名 wn4k.com 活着，所以不写。

    ★ 已知写进去也救不回来的（2026-10-27 实测，不必再试）
      guanying  5/6 域名是 PoW 挑战页，只有分类骨架、列表恒空
      huban     官方表里唯一 IP 43.248.128.118 已死，兜底 103.217.192.130 返回 404
      WexAiJianPian(賤賤)  不走 siteconfig：查 swrdsfeiujo25sw.cc 的 DNS TXT 拿
                域名列表（cqxkn.com,hzlfm.com,whhyn.com,cqlzn.com,zxfmj.com），
                这 5 个域名在 Cloudflare 上都没有 A 记录，下发链路本身已空
      MusicAiLunHui(轮回音乐)  https://www.dj92.cc 写死在字节码，DNS 失效；
                dj92.com 是停放页，其余后缀无记录

    容错：远程缺失 / 不是合法 {key:[url]} 映射 / 值含非法字符 → 整份丢弃并保持
    本地现状；本地与远程一致时不重写。绝不拿半截下载或 404 页面覆盖。
    某个键的域名全死时不影响启动：探测失败 → 抛异常 → 回落到该类的兜底域名。

当前内容：
    9e82d9058843e897e3d75054604d4371949006a297e176e8c7ce668b50d2cad1
        源: http://oss4liview.moji.com/thd_file/2026/10/23/b1a69d9c535fd309388e390b0b1e0749.jpg
            (md5 3c264c629e02273b9ad61e769218c508, 1006882 B)
        产物: 1163 类 / 7863 方法，enjarify + InitAlign（rewritten=796 rejected=0）
        校验: <sha256>.jvm.jar.md5 = 6505c284c6ed1703c23203dcfc34588d
        验证: 真实配置 99 个 site → coverage 99/99；观影源 /home 返回 3 分类 8 筛选维度 39 类型
              Player 端到端：film/class + film/search + film/detail 三项全通
        注意: 该配置的 99 个源里，公共池（fty+wex）只能覆盖 10 个，其余 89 个靠这份产物

    f35f5ae50ffd07f4ff9fb63a2ecf0bde6dfcf4dcfd210e6df08c107d72edd9c5
        源: http://oss4liview.moji.com/thd_file/2026/10/26/837a77b77fc98aba6b97856944e94d7c.jpg
            (md5 722d5c1bdcb1b38fc62874941e060d6b, 1010202 B)
        产物: 【指纹别名】与 9e82d905 同一份字节（同 md5 6505c284…），非重跑流水线
        来由: 2026-10-26 上游只重打包了壳（sha256 换成 f35f5ae5…），Prebuilt 按
              源 jar 的 sha256 查表，旧键查不到就回落 d2j → 89 站 ClassNotFound。
              实测新旧 guard 载荷差约 3KB（946288 vs 943142 B）但旧译码产物仍兼容，
              故复制一份挂新指纹即可，成本约 1 分钟。
        校验: <sha256>.jvm.jar.md5 = 6505c284c6ed1703c23203dcfc34588d（同上，字节相同）
        验证: 2026-10-26 换代后实测：coverage 99/99；homeContent 58/99 出数、
              58/58 全标准键；AiNewLaBi search「斗罗」→ detail 683KB 五条网盘线路
        注意: 别名是权宜——新载荷那 ~3KB 差异没有跟进译码。下次换代若 API 契约变了
              会静默出空数据，此时才需要重跑 tools/wexdump（约半小时）。

    siteconfig.json
        站点域名映射（本目录唯一的非 class 产物），来历与维护见上一节。
        当前内容: 安卓壳官方下发的原件，16 键 39 条域名（2026-10-27 抓取，
                  来源 http://oss4liview.moji.com/thd_file/2026/10/20/
                       89f63e6bf75d3c5da2bb2e94022c5a98.jpg）
        实测: 38 条域名里 34 条活；huban 唯一 IP 已死，guanying 5 条是 PoW 页
        验证: 空 data 目录冷启动 → [siteconfig] 16 键全量落盘 → 99 站回归
              可用 47 → 50（新增 玩偶 / AiNewDuoDuo / SoAiHaiYin，无一片掉落）：
                玩偶         home 7 分类，category 70 条「末世行者」
                AiNewDuoDuo  home 6 分类，category 72 条「空枪」  ← 修前是空骨架
                花卷/木偶/盘库/桔子/七味/快影/闪电/蜡笔/蜗牛 全部照常出片
        注意: AiNewDuoDuo 的兜底 tv.yydsys.top 与玩偶同款死法（DNS 无记录），
              但它拿到官方表后立刻恢复 —— 证明「空骨架 + q 恒定」不能直接判定
              上游停更，先查 siteconfig 有没有这个键。
