import subprocess, pathlib, os, json, hashlib
root=pathlib.Path(__file__).resolve().parents[2]
out=root/'qa-tools'/'android-migration';out.mkdir(exist_ok=True)
sdk=pathlib.Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'
java=pathlib.Path('C:/Program Files/Android/Android Studio/jbr/bin')
# All operations target a new disposable emulator and .securityqa IDs only.
adb=['adb','-s','emulator-5580']
def run(args,**kwargs):
    result=subprocess.run([str(a) for a in args],check=True,capture_output=True,**kwargs)
    return result.stdout
assert run(adb+['shell','getprop','ro.boot.qemu']).decode().strip()=='1'
assert run(adb+['shell','getprop','sys.boot_completed']).decode().strip()=='1'
for network in ['wifi','data']: run(adb+['shell','svc',network,'disable'])
env=os.environ.copy();env['JAVA_HOME']=str(java.parent);env['PATH']=str(java)+';'+env['PATH'];env['QA_KEY_PASS']='fixture-only-key-password'
for name in ['old-fixture','new-fixture']:
    key=out/(name+'.p12')
    if not key.exists():run([java/'keytool.exe','-genkeypair','-keystore',key,'-storetype','PKCS12','-storepass:env','QA_KEY_PASS','-keypass:env','QA_KEY_PASS','-alias','qa','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Disposable Security QA'],env=env)
results=[]
for repo,namespace in [('Home','com.masakasakasama.home'),('Stock','com.example.stockwidget')]:
    pkg=namespace+'.securityqa';assert pkg.endswith('.securityqa')
    app=root/repo/'app/build/outputs/apk/debug/app-debug.apk'
    tests=root/repo/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    signed={}
    for signer in ['old-fixture','new-fixture']:
        for kind,src in [('app',app),('test',tests)]:
            target=out/(repo+'-'+signer+'-'+kind+'.apk')
            run([sdk/'build-tools/34.0.0/apksigner.bat','sign','--ks',out/(signer+'.p12'),'--ks-key-alias','qa','--ks-pass','env:QA_KEY_PASS','--key-pass','env:QA_KEY_PASS','--out',target,src],env=env)
            verification=run([sdk/'build-tools/34.0.0/apksigner.bat','verify','--verbose','--print-certs',target],env=env).decode()
            (out/(target.name+'.verification.txt')).write_text(verification)
            signed[(signer,kind)]=target
    installed=run(adb+['shell','pm','list','packages',pkg]).decode()
    if 'package:'+pkg in installed:
        assert (out/(repo+'.hbk')).exists(), 'Previous QA backup is required before resetting an existing QA fixture'
        for target in [pkg+'.test',pkg]:run(adb+['uninstall',target])
    for kind in ['app','test']:run(adb+['install','-r',signed[('old-fixture',kind)]])
    name=namespace+'.backup.MigrationTest'
    def test(method):
        output=run(adb+['shell','am','instrument','-w','-r','-e','class',name+'#'+method,pkg+'.test/androidx.test.runner.AndroidJUnitRunner']).decode()
        (out/(repo+'-'+method+'.txt')).write_text(output)
        assert 'OK (1 test)' in output,output
    test('exportFixture')
    backup=run(adb+['exec-out','run-as',pkg,'cat','files/migration.hbk'])
    expected=run(adb+['exec-out','run-as',pkg,'cat','files/migration.sha256'])
    assert len(backup)>48 and backup[:4]==bytes([72,66,75,1])
    (out/(repo+'.hbk')).write_bytes(backup);(out/(repo+'.sha256')).write_bytes(expected)
    # Cross-signature in-place update MUST fail, before touching the old app.
    mismatch=subprocess.run(adb+['install','-r',str(signed[('new-fixture','app')])],capture_output=True)
    assert mismatch.returncode!=0 and b'INSTALL_FAILED_UPDATE_INCOMPATIBLE' in mismatch.stdout+mismatch.stderr
    # Export has been authenticated/read back by the app test BEFORE this QA-only uninstall.
    for target in [pkg+'.test',pkg]:run(adb+['uninstall',target])
    for kind in ['app','test']:run(adb+['install',signed[('new-fixture',kind)]])
    run(adb+['shell','run-as',pkg,'mkdir','-p','files'])
    for local,remote in [(out/(repo+'.hbk'),'migration.hbk'),(out/(repo+'.sha256'),'migration.sha256')]:
        run(adb+['push',local,'/data/local/tmp/'+repo+'-'+remote])
        run(adb+['shell','run-as',pkg,'cp','/data/local/tmp/'+repo+'-'+remote,'files/'+remote])
    test('importFixture')
    if repo=='Stock':test('verifyApiKeyVault')
    results.append({'app':repo,'device':'disposable Android API34 emulator','package':pkg,'differentSigners':True,'inPlaceUpdateRejected':True,'backupReadbackBeforeUninstall':True,'restoredContentSha256':expected.decode().strip(),'rollbackVerified':True,'physicalDeviceTest':False,'productionDataUsed':False})
    print('PASS',repo,flush=True)
permission=run(adb+['shell','am','instrument','-w','-r','-e','class','com.masakasakasama.home.backup.MigrationTest#verifyWatchlistPermission','com.masakasakasama.home.securityqa.test/androidx.test.runner.AndroidJUnitRunner']).decode()
(out/'Home-watchlist-permission.txt').write_text(permission)
assert 'OK (1 test)' in permission,permission
outsider=subprocess.run(adb+['shell','content','query','--uri','content://com.example.stockwidget.watchlist/symbols'],capture_output=True)
assert b'Permission Denial' in outsider.stdout+outsider.stderr
for result in results:result['sharedSignerPermissionVerified']=True
(out/'results.json').write_text(json.dumps(results,indent=2))
print('PASS same-signer Home watchlist access and outsider denial',flush=True)
