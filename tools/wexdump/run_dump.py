import os, sys, time, json
import frida

OUT = r'E:/code/AndroidProjects/FengMi/Player/.probe/dump/out'
JS = r'E:/code/AndroidProjects/FengMi/Player/.probe/dump/driver.js'
PKG = 'com.fm.guarddump'
DURATION = int(os.environ.get('DUR', '75'))

os.makedirs(OUT, exist_ok=True)
logf = open(os.path.join(OUT, 'run.log'), 'w', encoding='utf8')

def log(s):
    print(s, flush=True)
    logf.write(str(s) + '\n')
    logf.flush()

def on_message(msg, data):
    if msg['type'] == 'send':
        p = msg.get('payload') or {}
        t = p.get('t')
        if t == 'artifact':
            name = str(p.get('name', 'artifact.bin')).replace('/', '_')
            path = os.path.join(OUT, name)
            if os.path.exists(path):
                path = path + '.%d' % int(time.time() % 100000)
            with open(path, 'wb') as f:
                f.write(data or b'')
            log('[saved] %s  %d bytes' % (path, len(data or b'')))
        elif t == 'home':
            body = p.get('body') or ''
            with open(os.path.join(OUT, 'home.json'), 'w', encoding='utf8') as f:
                f.write(body)
            log('[home] saved %d chars' % len(body))
        else:
            log('[msg] ' + json.dumps(p, ensure_ascii=False)[:300])
    elif msg['type'] == 'error':
        log('[err] ' + str(msg.get('description'))[:800])
        st = msg.get('stack')
        if st:
            log(str(st)[:1200])

def main():
    dev = frida.get_usb_device(timeout=15)
    log('device=%s' % dev)
    pid = dev.spawn([PKG])
    log('spawned pid=%d' % pid)
    session = dev.attach(pid)
    with open(JS, encoding='utf8') as f:
        src = f.read()
    script = session.create_script(src)
    script.on('message', on_message)
    script.load()
    log('script loaded, resuming...')
    dev.resume(pid)
    time.sleep(DURATION)
    log('--- done, files: %s' % sorted(os.listdir(OUT)))

if __name__ == '__main__':
    main()
