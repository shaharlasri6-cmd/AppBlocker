#!/usr/bin/env python3
import argparse, base64, hashlib, hmac, json, mimetypes, os, secrets, sqlite3, time, urllib.parse
from http import HTTPStatus
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
WEB=ROOT/'web'
DATA=Path(os.environ.get('APPBLOCKER_DATA', str(ROOT/'data')))
DB=DATA/'appblocker.db'
SECRET_FILE=DATA/'server.secret'
SESSION_TTL=12*3600
PAIR_TTL=600

def now(): return int(time.time())
def b64u(b): return base64.urlsafe_b64encode(b).decode().rstrip('=')
def ub64(s): return base64.urlsafe_b64decode(s+'='*((4-len(s)%4)%4))
def db():
    c=sqlite3.connect(DB); c.row_factory=sqlite3.Row; c.execute('PRAGMA foreign_keys=ON'); c.execute('PRAGMA journal_mode=WAL'); return c

def init_db():
    DATA.mkdir(parents=True, exist_ok=True)
    if not SECRET_FILE.exists(): SECRET_FILE.write_bytes(secrets.token_bytes(32)); os.chmod(SECRET_FILE,0o600)
    with db() as c:
        c.executescript('''
        CREATE TABLE IF NOT EXISTS admin(id INTEGER PRIMARY KEY CHECK(id=1), pass_salt TEXT NOT NULL, pass_hash TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, name TEXT NOT NULL, manufacturer TEXT, model TEXT, android_version TEXT, token_hash TEXT, paired_at INTEGER, last_seen INTEGER, protection_status TEXT DEFAULT 'unknown', policy_version INTEGER DEFAULT 0, tamper_json TEXT DEFAULT '[]');
        CREATE TABLE IF NOT EXISTS pairings(code_hash TEXT PRIMARY KEY, device_id TEXT NOT NULL, device_name TEXT NOT NULL, manufacturer TEXT, model TEXT, android_version TEXT, expires_at INTEGER NOT NULL, approved INTEGER DEFAULT 0, device_token TEXT);
        CREATE TABLE IF NOT EXISTS apps(device_id TEXT NOT NULL, package_name TEXT NOT NULL, app_name TEXT NOT NULL, icon_b64 TEXT, system_app INTEGER DEFAULT 0, PRIMARY KEY(device_id,package_name), FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS policies(device_id TEXT NOT NULL, package_name TEXT NOT NULL, mode TEXT NOT NULL DEFAULT 'UNRESTRICTED', allowance_minutes INTEGER, window_minutes INTEGER, window_anchor TEXT DEFAULT 'FIXED', PRIMARY KEY(device_id,package_name), FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS settings(device_id TEXT PRIMARY KEY, general_image TEXT, time_image TEXT, notification_thresholds TEXT DEFAULT '[15,10,5,1]', FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS usage(device_id TEXT NOT NULL, package_name TEXT NOT NULL, bucket_start INTEGER NOT NULL, seconds INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(device_id,package_name,bucket_start));
        ''')

def secret(): return SECRET_FILE.read_bytes()
def hash_password(p,s): return hashlib.pbkdf2_hmac('sha256',p.encode('utf-8'),bytes.fromhex(s),240000).hex()
def verify_password(p):
    with db() as c:
        r=c.execute('SELECT pass_salt,pass_hash FROM admin WHERE id=1').fetchone()
    return bool(r and hmac.compare_digest(hash_password(str(p),r['pass_salt']),r['pass_hash']))
def token_hash(t): return hashlib.sha256(t.encode()).hexdigest()
def make_session():
    payload=json.dumps({'exp':now()+SESSION_TTL,'nonce':secrets.token_hex(8)},separators=(',',':')).encode(); sig=hmac.new(secret(),payload,hashlib.sha256).digest(); return b64u(payload)+'.'+b64u(sig)
def valid_session(t):
    try:
        a,b=t.split('.',1); payload=ub64(a); return hmac.compare_digest(hmac.new(secret(),payload,hashlib.sha256).digest(),ub64(b)) and json.loads(payload)['exp']>=now()
    except: return False

def json_body(h):
    n=int(h.headers.get('Content-Length','0')); return json.loads(h.rfile.read(n) or b'{}')
def protected_package(pkg):
    return pkg in {'android','com.android.systemui','com.android.settings','com.android.phone','com.google.android.dialer','com.sec.android.app.launcher','com.miui.home','com.android.launcher3'} or pkg.startswith('com.android.permissioncontroller')

class H(BaseHTTPRequestHandler):
    server_version='AppBlocker/1.0'
    def log_message(self,fmt,*args): print('[HTTP]',fmt%args)
    def send_json(self,obj,status=200):
        b=json.dumps(obj,separators=(',',':')).encode(); self.send_response(status); self.send_header('Content-Type','application/json'); self.send_header('Cache-Control','no-store'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)
    def fail(self,msg,status=400): self.send_json({'error':msg},status)
    def admin(self):
        a=self.headers.get('Authorization',''); return a.startswith('Bearer ') and valid_session(a[7:])
    def device(self):
        a=self.headers.get('Authorization',''); did=self.headers.get('X-Device-Id','')
        if not a.startswith('Bearer ') or not did:return None
        with db() as c:r=c.execute('SELECT token_hash FROM devices WHERE id=?',(did,)).fetchone()
        return did if r and r['token_hash'] and hmac.compare_digest(r['token_hash'],token_hash(a[7:])) else None
    def req_path(self): return urllib.parse.urlparse(self.path).path
    def do_GET(self):
        path=self.req_path()
        if path=='/api/health': return self.send_json({'ok':True,'time':now()})
        if path=='/api/admin/state':
            if not self.admin(): return self.fail('unauthorized',401)
            return self.admin_state()
        if path.startswith('/api/device/pairing/status/'):
            code=path.rsplit('/',1)[-1].upper().replace('-',''); ch=token_hash(code)
            with db() as c:r=c.execute('SELECT approved,device_token,expires_at,device_id FROM pairings WHERE code_hash=?',(ch,)).fetchone()
            if not r or r['expires_at']<now(): return self.fail('expired or unknown pairing',404)
            return self.send_json({'approved':bool(r['approved']),'device_token':r['device_token'] if r['approved'] else None,'device_id':r['device_id']})
        if path=='/api/device/policy':
            did=self.device()
            if not did:return self.fail('unauthorized',401)
            with db() as c:
                d=c.execute('SELECT policy_version FROM devices WHERE id=?',(did,)).fetchone(); rows=c.execute('SELECT package_name,mode,allowance_minutes,window_minutes,window_anchor FROM policies WHERE device_id=?',(did,)).fetchall(); s=c.execute('SELECT * FROM settings WHERE device_id=?',(did,)).fetchone()
                c.execute('UPDATE devices SET last_seen=? WHERE id=?',(now(),did))
            return self.send_json({'version':d['policy_version'],'policies':[dict(r) for r in rows],'settings':dict(s) if s else {'notification_thresholds':'[15,10,5,1]'},'server_time':now()})
        if path.startswith('/media/'):
            f=(DATA/path.removeprefix('/')).resolve()
            if not str(f).startswith(str(DATA.resolve())) or not f.is_file(): return self.fail('not found',404)
            b=f.read_bytes(); self.send_response(200); self.send_header('Content-Type',mimetypes.guess_type(f.name)[0] or 'application/octet-stream'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b); return
        return self.static(path)
    def do_POST(self):
        path=self.req_path()
        try:data=json_body(self)
        except:return self.fail('invalid json')
        if path=='/api/admin/login':
            password=data.get('password','')
            if not isinstance(password,str) or not verify_password(password): return self.fail('invalid credentials',401)
            return self.send_json({'token':make_session()})
        if path=='/api/device/pairing/start':
            did=str(data.get('device_id') or secrets.token_hex(16)); code=''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZ23456789') for _ in range(8)); ch=token_hash(code)
            with db() as c:
                c.execute('DELETE FROM pairings WHERE expires_at<?',(now(),)); c.execute('INSERT INTO pairings(code_hash,device_id,device_name,manufacturer,model,android_version,expires_at) VALUES(?,?,?,?,?,?,?)',(ch,did,str(data.get('name','Android device')),str(data.get('manufacturer','')),str(data.get('model','')),str(data.get('android_version','')),now()+PAIR_TTL))
            return self.send_json({'pairing_code':code[:4]+'-'+code[4:],'expires_at':now()+PAIR_TTL,'device_id':did})
        if path=='/api/admin/pair/approve':
            if not self.admin():return self.fail('unauthorized',401)
            code=str(data.get('code','')).upper().replace('-',''); ch=token_hash(code)
            with db() as c:
                r=c.execute('SELECT * FROM pairings WHERE code_hash=?',(ch,)).fetchone()
                if not r or r['expires_at']<now() or r['approved']:return self.fail('invalid/expired pairing')
                tok=secrets.token_urlsafe(32)
                c.execute('INSERT OR REPLACE INTO devices(id,name,manufacturer,model,android_version,token_hash,paired_at,last_seen,policy_version) VALUES(?,?,?,?,?,?,?,?,COALESCE((SELECT policy_version FROM devices WHERE id=?),0))',(r['device_id'],r['device_name'],r['manufacturer'],r['model'],r['android_version'],token_hash(tok),now(),now(),r['device_id']))
                c.execute('INSERT OR IGNORE INTO settings(device_id) VALUES(?)',(r['device_id'],)); c.execute('UPDATE pairings SET approved=1,device_token=? WHERE code_hash=?',(tok,ch))
            return self.send_json({'ok':True,'device_id':r['device_id']})
        if path=='/api/admin/device/rename':
            if not self.admin():return self.fail('unauthorized',401)
            did=str(data.get('device_id','')); name=str(data.get('name','')).strip()
            if not did or len(name)<1 or len(name)>80:return self.fail('invalid device name')
            with db() as c:
                if not c.execute('SELECT 1 FROM devices WHERE id=?',(did,)).fetchone():return self.fail('unknown device',404)
                c.execute('UPDATE devices SET name=? WHERE id=?',(name,did))
            return self.send_json({'ok':True,'name':name})
        if path=='/api/admin/policy':
            if not self.admin():return self.fail('unauthorized',401)
            did,pkg,mode=str(data.get('device_id','')),str(data.get('package_name','')),str(data.get('mode','UNRESTRICTED'))
            if protected_package(pkg) and mode!='UNRESTRICTED': return self.fail('system-critical package cannot be blocked')
            if mode not in {'UNRESTRICTED','BLOCKED','TIME_LIMITED'}:return self.fail('invalid mode')
            allowance=data.get('allowance_minutes'); window=data.get('window_minutes')
            if mode=='TIME_LIMITED' and (not isinstance(allowance,int) or not isinstance(window,int) or allowance<=0 or window<=0 or allowance>window):return self.fail('invalid time limit')
            with db() as c:
                c.execute('INSERT INTO policies(device_id,package_name,mode,allowance_minutes,window_minutes,window_anchor) VALUES(?,?,?,?,?,?) ON CONFLICT(device_id,package_name) DO UPDATE SET mode=excluded.mode,allowance_minutes=excluded.allowance_minutes,window_minutes=excluded.window_minutes,window_anchor=excluded.window_anchor',(did,pkg,mode,allowance,window,str(data.get('window_anchor','FIXED')))); c.execute('UPDATE devices SET policy_version=policy_version+1 WHERE id=?',(did,))
            return self.send_json({'ok':True})
        if path=='/api/admin/settings':
            if not self.admin():return self.fail('unauthorized',401)
            did=str(data.get('device_id','')); th=data.get('notification_thresholds',[15,10,5,1]);
            if not isinstance(th,list) or any(not isinstance(x,int) or x<0 for x in th):return self.fail('invalid thresholds')
            with db() as c:c.execute('INSERT INTO settings(device_id,notification_thresholds) VALUES(?,?) ON CONFLICT(device_id) DO UPDATE SET notification_thresholds=excluded.notification_thresholds',(did,json.dumps(sorted(set(th),reverse=True)))); c.execute('UPDATE devices SET policy_version=policy_version+1 WHERE id=?',(did,))
            return self.send_json({'ok':True})
        if path=='/api/admin/image':
            if not self.admin():return self.fail('unauthorized',401)
            did,kind=str(data.get('device_id','')),str(data.get('kind','')); raw=str(data.get('data_url',''))
            if kind not in {'general','time'} or ',' not in raw:return self.fail('invalid image')
            head,b64=raw.split(',',1)
            try:blob=base64.b64decode(b64,validate=True)
            except:return self.fail('invalid image data')
            if len(blob)>3*1024*1024:return self.fail('image too large (3MB max)')
            ext='.png' if 'png' in head else '.jpg'; rel=f'media/{did}-{kind}{ext}'; f=DATA/rel; f.parent.mkdir(exist_ok=True); f.write_bytes(blob); url='/'+rel
            col='general_image' if kind=='general' else 'time_image'
            with db() as c:c.execute(f'INSERT INTO settings(device_id,{col}) VALUES(?,?) ON CONFLICT(device_id) DO UPDATE SET {col}=excluded.{col}',(did,url)); c.execute('UPDATE devices SET policy_version=policy_version+1 WHERE id=?',(did,))
            return self.send_json({'ok':True,'url':url})
        if path=='/api/device/inventory':
            did=self.device()
            if not did:return self.fail('unauthorized',401)
            apps=data.get('apps',[])
            with db() as c:
                for a in apps[:1000]: c.execute('INSERT INTO apps(device_id,package_name,app_name,icon_b64,system_app) VALUES(?,?,?,?,?) ON CONFLICT(device_id,package_name) DO UPDATE SET app_name=excluded.app_name,icon_b64=excluded.icon_b64,system_app=excluded.system_app',(did,str(a.get('package_name','')),str(a.get('app_name','')),str(a.get('icon_b64') or '')[:300000],1 if a.get('system_app') else 0))
                c.execute('UPDATE devices SET last_seen=? WHERE id=?',(now(),did))
            return self.send_json({'ok':True})
        if path=='/api/device/usage':
            did=self.device()
            if not did:return self.fail('unauthorized',401)
            events=data.get('usage',[])
            with db() as c:
                for e in events[:2000]:c.execute('INSERT INTO usage(device_id,package_name,bucket_start,seconds) VALUES(?,?,?,?) ON CONFLICT(device_id,package_name,bucket_start) DO UPDATE SET seconds=MAX(seconds,excluded.seconds)',(did,str(e.get('package_name','')),int(e.get('bucket_start',0)),int(e.get('seconds',0))))
                c.execute('UPDATE devices SET last_seen=? WHERE id=?',(now(),did))
            return self.send_json({'ok':True})
        if path=='/api/device/status':
            did=self.device()
            if not did:return self.fail('unauthorized',401)
            with db() as c:c.execute('UPDATE devices SET last_seen=?, protection_status=?, tamper_json=? WHERE id=?',(now(),str(data.get('protection_status','unknown')),json.dumps(data.get('tamper',[]))[:10000],did))
            return self.send_json({'ok':True})
        return self.fail('not found',404)
    def admin_state(self):
        with db() as c:
            ds=[dict(r) for r in c.execute('SELECT id,name,manufacturer,model,android_version,last_seen,protection_status,policy_version,tamper_json FROM devices ORDER BY paired_at DESC')]
            for d in ds:
                d['tamper']=json.loads(d.pop('tamper_json') or '[]'); d['apps']=[dict(r) for r in c.execute('SELECT a.package_name,a.app_name,a.icon_b64,a.system_app,COALESCE(p.mode,"UNRESTRICTED") mode,p.allowance_minutes,p.window_minutes,p.window_anchor FROM apps a LEFT JOIN policies p ON p.device_id=a.device_id AND p.package_name=a.package_name WHERE a.device_id=? ORDER BY lower(a.app_name)',(d['id'],))]; s=c.execute('SELECT * FROM settings WHERE device_id=?',(d['id'],)).fetchone(); raw_settings=dict(s) if s else {}; raw_settings.pop('uninstall_pass_salt',None); raw_settings.pop('uninstall_pass_hash',None); d['settings']=raw_settings; d['usage']=[dict(r) for r in c.execute('SELECT package_name,SUM(seconds) seconds FROM usage WHERE device_id=? AND bucket_start>=? GROUP BY package_name',(d['id'],now()-86400))]
        return self.send_json({'devices':ds,'server_time':now()})
    def static(self,path):
        if path=='/':path='/index.html'
        f=(WEB/path.lstrip('/')).resolve()
        if not str(f).startswith(str(WEB.resolve())) or not f.is_file():f=WEB/'index.html'
        b=f.read_bytes(); self.send_response(200); self.send_header('Content-Type',mimetypes.guess_type(f.name)[0] or 'text/plain'); self.send_header('Cache-Control','no-cache'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)

def set_password(pw):
    if len(pw)<8: raise SystemExit('Password must be at least 8 characters')
    salt=secrets.token_bytes(16).hex(); ph=hash_password(pw,salt)
    with db() as c:c.execute('INSERT OR REPLACE INTO admin(id,pass_salt,pass_hash) VALUES(1,?,?)',(salt,ph))

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--host',default='0.0.0.0'); ap.add_argument('--port',type=int,default=8787); ap.add_argument('--set-password'); ap.add_argument('--verify-password'); args=ap.parse_args(); init_db()
    if args.set_password is not None:
        set_password(args.set_password); print('Admin password updated'); return
    if args.verify_password is not None:
        if verify_password(args.verify_password): print('Password verification OK'); return
        raise SystemExit('Password verification FAILED')
    with db() as c:r=c.execute('SELECT 1 FROM admin WHERE id=1').fetchone()
    if not r:
        pw=os.environ.get('APPBLOCKER_ADMIN_PASSWORD')
        if not pw: raise SystemExit('No admin password. Run: python3 backend/server.py --set-password "your-password"')
        set_password(pw)
    print(f'AppBlocker dashboard: http://{args.host}:{args.port}')
    ThreadingHTTPServer((args.host,args.port),H).serve_forever()
if __name__=='__main__':main()
