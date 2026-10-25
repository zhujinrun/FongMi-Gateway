/*
 * wexguard 壳 driver v3（最小宿主 com.fm.guarddump）
 *
 * 环境结论：
 *   - x86_64 镜像下 primaryCpuAbi=arm64-v8a，native bridge 只接 loadLibrary 路径
 *   - 所以宿主 APK 必须 extractNativeLibs=true，把 arm64 so 解压到 lib/arm64
 *   - 壳自身用 System.load(绝对路径)，hook 里重定向成 loadLibrary("wexguard_v8")
 */
'use strict';

const TAG = '[fm]';
const JAR = '/data/user/0/com.fm.guarddump/cache/shell.jar';
const GUARD_CLASS = 'com.github.catvod.spider.AiNewWoggGuard';

const seen = new Set();
let globalCF = null;
function log(s) { console.log(TAG + ' ' + s); }

function ship(path, why) {
  try {
    if (seen.has(path)) return;
    const data = File.readAllBytes(path);
    if (data.length < 1024) return;
    seen.add(path);
    const name = String(path).split('/').pop();
    log('SHIP(' + why + ') ' + path + ' ' + data.length + 'B');
    send({ t: 'artifact', name: name, path: path, size: data.length, why: why }, data);
  } catch (e) { log('ship fail ' + e); }
}

function dexPathsOf(loader) {
  const out = [];
  try {
    const B = Java.use('dalvik.system.BaseDexClassLoader');
    const cl = Java.cast(loader, B);
    const f = B.class.getDeclaredField('pathList');
    f.setAccessible(true);
    const pl = f.get(cl);
    if (!pl) return out;
    const m = pl.getClass().getDeclaredMethod('getDexPaths');
    m.setAccessible(true);
    const list = m.invoke(pl, Java.array('java.lang.Object', []));
    for (let i = 0; i < list.size(); i++) out.push(String(list.get(i)));
  } catch (e) { log('dexPathsOf fail ' + e); }
  return out;
}

function hookMisc() {
  try {
    const CL = Java.use('java.lang.ClassLoader');
    CL.getResourceAsStream.overload('java.lang.String').implementation = function (n) {
      const r = this.getResourceAsStream(n);
      if (n && (n.indexOf('assets') >= 0 || n.indexOf('wex') >= 0)) {
        log('getResourceAsStream("' + n + '") -> ' + (r === null ? 'NULL' : 'OK'));
      }
      return r;
    };
  } catch (e) { log('hook res fail ' + e); }

  try {
    const S = Java.use('java.lang.System');
    S.load.overload('java.lang.String').implementation = function (p) {
      const ps = String(p);
      log('System.load(' + ps + ')');
      if (ps.indexOf('.wexfnw') >= 0) {
        try {
          log('  -> shell Loader.loadLib(wexguard_v8)');
          const L = globalCF.use('com.fm.bridge.Loader');
          L.loadLib('wexguard_v8');
          log('  shell loadLib OK');
          return;
        } catch (e) { log('  shell loadLib FAIL: ' + e); }
      }
      return this.load(p);
    };
    log('hooked System.load');
  } catch (e) { log('hook sys.load fail ' + e); }

  try {
    const D = Java.use('dalvik.system.DexClassLoader');
    D.$init.overloads.forEach(function (ov) {
      ov.implementation = function () {
        const a = Array.prototype.slice.call(arguments);
        const r = ov.call(this, ...a);
        const dp = String(a[0]);
        log('DexClassLoader(' + dp + ')');
        if (dp.indexOf('shell.jar') < 0) {
          try {
            ship(dp, 'dexloader');
            // 同时试同目录下的伴生文件
            const dir = dp.substring(0, dp.lastIndexOf('/'));
            const f = Java.use('java.io.File').$new(dir);
            const ls = f.listFiles();
            if (ls) for (let i = 0; i < ls.length; i++) {
              const q = String(ls[i].getAbsolutePath());
              if (q !== dp && ls[i].length() > 4096) ship(q, 'dexloader-sibling');
            }
          } catch (e) { log('  ship dex fail ' + e); }
        }
        return r;
      };
    });
  } catch (e) {}
}

function scanCache(ctx, tag) {
  try {
    const list = ctx.getCacheDir().listFiles();
    if (!list) return;
    for (let i = 0; i < list.length; i++) {
      const f = list[i];
      try {
        if (f.isDirectory()) continue;
        const nm = String(f.getName()), p = String(f.getAbsolutePath()), len = f.length();
        if (len < 2048) continue;
        if (nm.indexOf('.wexfnw') >= 0) {
          if (len !== 48680 && len !== 48364) { log('[' + tag + '] CANDIDATE ' + p + ' ' + len + 'B'); ship(p, tag); }
          else log('[' + tag + '] (so copy) ' + nm + ' ' + len);
        } else if (/\.(dex|jar)$/i.test(nm)) {
          log('[' + tag + '] ' + p + ' ' + len + 'B');
        }
      } catch (e) {}
    }
  } catch (e) { log('scanCache fail ' + e); }
}

function waitApp(cb) {
  let n = 0;
  const t = setInterval(function () {
    try {
      const app = Java.use('android.app.ActivityThread').currentApplication();
      if (app) { clearInterval(t); cb(app); return; }
    } catch (e) {}
    if (++n > 200) { clearInterval(t); log('no app'); }
  }, 150);
}

function main() {
  waitApp(function (app) {
    const ctx = app.getApplicationContext();
    log('ctx=' + ctx.getPackageName());

    const DCL = Java.use('dalvik.system.DexClassLoader');
    const libDir = String(ctx.getApplicationInfo().nativeLibraryDir.value);
    let loader;
    try {
      log('librarySearchPath=' + libDir);
      loader = DCL.$new(JAR, String(ctx.getCodeCacheDir().getAbsolutePath()), libDir, ctx.getClassLoader());
      log('shell loader OK');
    } catch (e) { log('FAIL loader: ' + e); return; }

    let CF;
    try { CF = Java.ClassFactory.get(loader); globalCF = CF; log('ClassFactory OK'); }
    catch (e) { log('FAIL ClassFactory: ' + e); return; }

    let I;
    try { I = CF.use('com.github.catvod.spider.Init'); log('Init OK'); }
    catch (e) { log('FAIL Init: ' + e); return; }

    try { I.init(ctx); log('Init.init OK'); }
    catch (e) { log('FAIL init: ' + e); }

    scanCache(ctx, 'after-init');

    let ld = null;
    try { ld = I.loader(); log('loader() -> ' + ld); }
    catch (e) { log('FAIL loader(): ' + e); }
    if (ld) {
      const ps = dexPathsOf(ld);
      log('dexPaths=' + JSON.stringify(ps));
      ps.forEach(function (p) { ship(p, 'loader'); });
    }

    scanCache(ctx, 'after-loader');

    let spider = null;
    try {
      {
        const realJar = '/data/user/0/com.fm.guarddump/cache/real2.dex';
        try {
          const DF = Java.use('dalvik.system.DexFile');
          const df = DF.$new(realJar);
          const en = df.entries();
          let cnt = 0, found = false; const sample = [];
          while (en.hasMoreElements()) {
            const n = String(en.nextElement());
            if (cnt < 3) sample.push(n);
            if (n.indexOf('AiNewWogg') >= 0) found = true;
            cnt++;
          }
          log('DexFile entries=' + cnt + ' hasWogg=' + found + ' sample=' + JSON.stringify(sample));
        } catch (e) { log('DexFile enum fail: ' + e); }
        const rl = DCL.$new(realJar, String(ctx.getCodeCacheDir().getAbsolutePath()), libDir, ctx.getClassLoader());
        log('real loader = ' + rl);
        try {
          const data = File.readAllBytes(realJar);
          const arr = Java.array('byte', new Uint8Array(data));
          const buf = Java.nio.ByteBuffer.wrap(arr);
          const IDCL = Java.use('dalvik.system.InMemoryDexClassLoader');
          const il = IDCL.$new(buf, ctx.getClassLoader());
          const c = il.loadClass('com.github.catvod.spider.AiNewWogg');
          log('InMemory loadClass OK: ' + c);
        } catch (e) { log('InMemory fail: ' + e); }
        try {
          const B = Java.use('dalvik.system.BaseDexClassLoader');
          const plf = B.class.getSuperclass().getDeclaredField('pathList');
          plf.setAccessible(true);
          const pl = plf.get(Java.cast(rl, B));
          const ef = pl.getClass().getDeclaredField('dexElements');
          ef.setAccessible(true);
          const arr = ef.get(pl);
          log('dexElements len=' + java.lang.reflect.Array.getLength(arr));
        } catch (e) { log('dexElements read fail: ' + e); }
        const CF2 = Java.ClassFactory.get(rl);
        const W = CF2.use('com.github.catvod.spider.AiNewWogg');
        try { W.init(ctx); log('AiNewWogg.init OK'); } catch (e) { log('init fail ' + e); }
        const home = String(W.homeContent(true));
        log('homeContent len=' + home.length + ' :: ' + home.slice(0, 300));
        send({ t: 'home', body: home.slice(0, 200000) });
        const sr = String(W.searchContent('嫁金钗', false));
        log('searchContent len=' + sr.length + ' :: ' + sr.slice(0, 400));
        send({ t: 'search', body: sr.slice(0, 200000) });
      }
    } catch (e) { log('spider call fail ' + e); }

    setTimeout(function () { scanCache(ctx, 'final'); log('DONE'); }, 4000);
  });
}

Java.perform(function () {
  log('attached pid=' + Process.id + ' arch=' + Process.arch);
  hookMisc();
  main();
});
