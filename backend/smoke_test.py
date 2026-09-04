#!/usr/bin/env python3
import json, os, pathlib, socket, subprocess, tempfile, time, urllib.request, urllib.error
ROOT=pathlib.Path(__file__).resolve().parent.parent
DATA=tempfile.mkdtemp(prefix='appblocker-smoke-')
env=os.environ.copy();env['APPBLOCKER_DATA']=DATA
subprocess.check_call(['python3',str(ROOT/'backend/server.py'),'--set-password','SmokePass123!'],env=env,stdout=subprocess.DEVNULL)
p=subprocess.Popen(['python3',str(ROOT/'backend/server.py'),'--host','127.0.0.1','--port','18877'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
B='http://127.0.0.1:18877'
def call(path,data=None,headers=None):
    body=None if data is None else json.dumps(data).encode()
    req=urllib.request.Request(B+path,body,headers={'Content-Type':'application/json',**(headers or {})},method='POST' if data is not None else 'GET')
    with urllib.request.urlopen(req,timeout=4) as r:return json.load(r)
try:
    for _ in range(30):
        try: call('/api/health');break
        except Exception: time.sleep(.1)
    admin=call('/api/admin/login',{'password':'SmokePass123!'})['token']; A={'Authorization':'Bearer '+admin}
    pair=call('/api/device/pairing/start',{'device_id':'smoke-device','name':'Xiaomi Test','manufacturer':'Xiaomi','model':'Test','android_version':'15'})
    call('/api/admin/pair/approve',{'code':pair['pairing_code']},A)
    status=call('/api/device/pairing/status/'+pair['pairing_code'].replace('-',''))
    D={'Authorization':'Bearer '+status['device_token'],'X-Device-Id':'smoke-device'}
    call('/api/device/inventory',{'apps':[{'package_name':'com.example.video','app_name':'Video','system_app':False,'icon_b64':''},{'package_name':'com.android.hidden','app_name':'System Hidden','system_app':True,'icon_b64':''}]},D)
    call('/api/admin/device/rename',{'device_id':'smoke-device','name':'Bedroom Phone'},A)
    call('/api/admin/policy',{'device_id':'smoke-device','package_name':'com.example.video','mode':'TIME_LIMITED','allowance_minutes':10,'window_minutes':60},A)
    policy=call('/api/device/policy',headers=D)
    assert policy['policies'][0]['allowance_minutes']==10 and policy['policies'][0]['window_minutes']==60
    call('/api/device/usage',{'usage':[{'package_name':'com.example.video','bucket_start':int(time.time())//3600*3600,'seconds':120}]},D)
    call('/api/device/status',{'protection_status':'healthy','tamper':[]},D)
    print('SMOKE TEST OK: login, pairing, rename, inventory, policy, usage, health sync')
finally:
    p.terminate();
    try:p.wait(2)
    except: p.kill()
