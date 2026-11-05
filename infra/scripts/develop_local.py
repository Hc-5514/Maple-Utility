#!/usr/bin/env python3
"""Private, project-scoped local integration environment."""
import base64, datetime, hashlib, json, os, pathlib, re, secrets, shlex, subprocess, sys
os.umask(0o077)
ROOT=pathlib.Path(__file__).resolve().parents[2]
CONFIG=ROOT/'.env.develop.local'
PROJECT='maple-develop'
DB='maple_develop'
BACKUPS=pathlib.Path.home()/'Maple-Backups'/'local-develop'

def run(args, **kw):
    return subprocess.run(args,check=True,**kw)
def output(args, **kw):
    return subprocess.check_output(args,text=True,**kw).strip()
def require(ok, message):
    if not ok: raise ValueError(message)
def stamp(): return datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
def read_env(path):
    require(not path.is_symlink(),'Environment symlink rejected')
    result={}
    for raw in path.read_text().splitlines():
        line=raw.strip()
        if not line or line.startswith('#'): continue
        key,sep,value=line.partition('=')
        require(sep and re.fullmatch(r'[A-Z][A-Z0-9_]*',key),'Invalid environment entry')
        require(key not in result,'Duplicate environment entry')
        parts=shlex.split(value,comments=True)
        require(len(parts)<=1,'Unsupported environment value')
        result[key]=parts[0] if parts else ''
    return result
def save_env(values):
    tmp=CONFIG.with_name(CONFIG.name+'.tmp')
    with tmp.open('x') as f:
        for key,value in values.items(): f.write(key+'='+shlex.quote(value)+'\n')
    tmp.chmod(0o600);tmp.replace(CONFIG)
def load():
    require(CONFIG.exists() and CONFIG.stat().st_uid==os.getuid() and CONFIG.stat().st_mode&0o777==0o600,'Run init; private configuration requires owner and mode 600')
    data=read_env(CONFIG)
    for key in ('DB_PASSWORD','JWT_SECRET','NEXON_API_KEY_SECRET','KAKAO_CLIENT_ID','KAKAO_CLIENT_SECRET'):
        require(bool(data.get(key)),'Missing required setting: '+key)
    require(len(base64.b64decode(data['NEXON_API_KEY_SECRET'],validate=True))==32,'Invalid local encryption key')
    return data
def compose(*args,capture=False):
    data=load();env=dict(os.environ)
    for key in list(env):
        if key.startswith('COMPOSE_'):env.pop(key)
    env.update(data)
    env['BUILD_SHA']=output(['git','-C',str(ROOT),'rev-parse','--short=12','HEAD'])
    cmd=['docker','compose','--project-directory',str(ROOT),'--project-name',PROJECT,'--env-file',str(CONFIG),'-f',str(ROOT/'docker-compose.develop.yml'),*args]
    return output(cmd,env=env) if capture else run(cmd,env=env)
def service(name):
    cid=compose('ps','-q',name,capture=True)
    require(bool(cid) and '\n' not in cid,'Missing project service: '+name)
    meta=json.loads(output(['docker','inspect',cid]))[0]
    labels=meta['Config'].get('Labels',{})
    require(labels.get('com.docker.compose.project')==PROJECT and labels.get('com.docker.compose.service')==name,'Wrong project/service target')
    return cid
def sql(query,db='postgres'):
    return output(['docker','exec',service('postgres'),'psql','-X','-w','-qAt','-U',DB,'-d',db,'-v','ON_ERROR_STOP=1','-c',query])
def snapshot(db):
    tables=sql("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'user_api_keys' ORDER BY tablename",db).splitlines()
    result={}
    for table in tables:
        require(re.fullmatch('[a-z_]+',table),'Unexpected table name')
        result[table]=sql(f"SELECT count(*)||':'||md5(COALESCE(string_agg(md5(row_to_json(t)::text),'' ORDER BY md5(row_to_json(t)::text)),'')) FROM public.{table} t",db)
    return result

def init():
    if CONFIG.exists():load();print('Existing local configuration preserved');return
    source=read_env(ROOT/'.env') if (ROOT/'.env').exists() else {}
    keys={k:source.get(k,'') for k in ('KAKAO_CLIENT_ID','KAKAO_CLIENT_SECRET')}
    require(all(keys.values()),'Set existing Kakao client ID/secret in private .env before init')
    keys.update(DB_PASSWORD=secrets.token_hex(32),JWT_SECRET=secrets.token_hex(64),NEXON_API_KEY_SECRET=base64.b64encode(secrets.token_bytes(32)).decode())
    save_env(keys);print('Private local configuration created; existing .env unchanged')
def fetch():
    script=(ROOT/'infra/scripts/manual-backup.sh').read_bytes()
    result=subprocess.run(['ssh','-o','BatchMode=yes','maple-utility-ec2','bash -s -- maple-postgres /home/ubuntu/maple-manual-backups/local-develop'],input=script,stdout=subprocess.PIPE,check=True).stdout.decode()
    lines=[s.removeprefix('Backup directory: ') for s in result.splitlines() if s.startswith('Backup directory: ')]
    require(len(lines)==1 and re.fullmatch('/home/ubuntu/maple-manual-backups/local-develop/[0-9TZ-]+',lines[0]),'Unexpected remote backup path')
    BACKUPS.mkdir(parents=True,exist_ok=True);BACKUPS.chmod(0o700)
    dest=BACKUPS/pathlib.Path(lines[0]).name;dest.mkdir(mode=0o700)
    run(['scp','-r','maple-utility-ec2:'+lines[0]+'/.',str(dest)+'/'])
    verify(dest);print('Backup directory: '+str(dest))
def verify(folder):
    require(folder.is_dir(),'Backup directory required')
    parts=(folder/'SHA256SUMS').read_text().split()
    require(len(parts)==2 and parts[1]=='maple.dump' and re.fullmatch('[a-f0-9]{64}',parts[0]),'Invalid checksum manifest')
    require(hashlib.sha256((folder/'maple.dump').read_bytes()).hexdigest()==parts[0],'Backup checksum mismatch')
    require((folder/'pg_dump.log').stat().st_size==0,'Review pg_dump warnings before restore')
    return parts[0]
def restore(folder):
    digest=verify(folder)
    compose('up','-d','--wait','postgres','redis')
    require(sql('SELECT current_database()',DB)==DB,'Wrong database')
    compose('stop','frontend','app')
    existing=sql("SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema='public')",DB)=='t'
    if existing:
        BACKUPS.mkdir(parents=True,exist_ok=True);BACKUPS.chmod(0o700)
        run(['bash',str(ROOT/'infra/scripts/manual-backup.sh'),service('postgres'),str(BACKUPS/'before-restore')])
    temp='restore_'+stamp().lower()+'_'+secrets.token_hex(3)
    sql('CREATE DATABASE '+temp+' TEMPLATE template0')
    swapped=False
    try:
        with (folder/'maple.dump').open('rb') as f:
            run(['docker','exec','-i',service('postgres'),'pg_restore','-U',DB,'-d',temp,'--no-owner','--no-privileges','--exit-on-error','--single-transaction'],stdin=f)
        before=snapshot(temp)
        require('flyway_schema_history' in before and 'characters' in before,'Incomplete application dump')
        sql('DELETE FROM public.user_api_keys',temp)
        require(sql('SELECT count(*) FROM public.user_api_keys',temp)=='0','Credentials remain')
        require(before==snapshot(temp),'Business data or history changed during credential cleanup')
        require(sql("SELECT count(*) FROM pg_stat_activity WHERE datname='"+DB+"'")=='0','Database still in use')
        retained='previous_'+stamp().lower()+'_'+secrets.token_hex(3)
        sql('BEGIN; ALTER DATABASE '+DB+' RENAME TO '+retained+'; ALTER DATABASE '+temp+' RENAME TO '+DB+'; COMMIT;')
        swapped=True
        data=load();data['JWT_SECRET']=secrets.token_hex(64);save_env(data)
        lua="local c='0' repeat local p=redis.call('SCAN',c,'COUNT',100) c=p[1] for _,k in ipairs(p[2]) do redis.call('UNLINK',k) end until c=='0' return 1"
        run(['docker','exec',service('redis'),'redis-cli','EVAL',lua,'0'],stdout=subprocess.DEVNULL)
        state=ROOT/'.local-develop';state.mkdir(mode=0o700,exist_ok=True)
        (state/'restore.json').write_text(json.dumps({'backup_sha256':digest,'backup_directory':str(folder),'retained_database':retained,'restored_at':stamp(),'credentials_removed':True},indent=2)+'\n')
        print('Restore verified; business records preserved, old credentials removed; previous database retained: '+retained)
    finally:
        if not swapped:sql('DROP DATABASE IF EXISTS '+temp)
def main():
    require(len(sys.argv)>=2,'Usage: develop-local.sh init|fetch-backup|restore BACKUP_DIR|up|down|status')
    cmd=sys.argv[1]
    if cmd=='init':require(len(sys.argv)==2,'Unexpected arguments');init();return
    if cmd=='fetch-backup':require(len(sys.argv)==2,'Unexpected arguments');fetch();return
    load()
    if cmd=='restore':
        require(len(sys.argv)==3,'restore requires backup directory');restore(pathlib.Path(sys.argv[2]).expanduser().resolve())
    elif cmd=='up':
        require(len(sys.argv)==2 and (ROOT/'.local-develop/restore.json').exists(),'Restore a verified backup before up')
        print('Source: '+output(['git','-C',str(ROOT),'branch','--show-current'])+' '+output(['git','-C',str(ROOT),'rev-parse','HEAD']),flush=True)
        compose('up','-d','--build','--wait','--wait-timeout','240')
        print('Open http://localhost:3000; sign in and register the development application key')
    elif cmd=='down':require(len(sys.argv)==2,'Unexpected arguments');compose('down')
    elif cmd=='status':require(len(sys.argv)==2,'Unexpected arguments');compose('ps');print('http://localhost:3000')
    else:raise ValueError('Unknown command')
if __name__=='__main__':
    try:main()
    except (ValueError,OSError,subprocess.CalledProcessError) as error:
        print('Local environment operation failed: '+str(error),file=sys.stderr);sys.exit(1)
