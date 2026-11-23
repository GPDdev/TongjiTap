"""Cold-install the signed APK only on the two disposable project AVDs."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import subprocess
import time
import argparse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ADB = ROOT / 'tools/android-sdk/platform-tools/adb.exe'


def smoke(pair):
    api, port = pair
    serial = f'emulator-{port}'
    folder = ROOT / f'dist/checks/Release{api}/1.0.0'
    folder.mkdir(parents=True, exist_ok=True)

    def adb(*args, binary=False):
        p = subprocess.run([str(ADB), '-s', serial, *args], capture_output=True, check=True, timeout=45)
        return p.stdout if binary else p.stdout.decode('utf-8', errors='replace')

    if adb('emu', 'avd', 'name').splitlines()[0].strip() != f'TongjiTap{api}':
        raise RuntimeError('Refusing to modify a non-project AVD')
    if not (ROOT / f'tools/avds/TongjiTap{api}.avd').exists():
        raise RuntimeError('Project AVD definition missing')
    subprocess.run([str(ADB), '-s', serial, 'uninstall', 'org.hachile.tongjitap'], capture_output=True)
    upgrade = api == 36
    first_apk = 'tongjitap-0.2.1.apk' if upgrade else 'tongjitap-1.0.0.apk'
    assert 'Success' in adb('install', str(ROOT / 'dist' / first_apk))
    adb('shell', 'cmd', 'statusbar', 'collapse')
    adb('shell', 'am', 'start', '-W', '-n', 'org.hachile.tongjitap/.MainActivity')
    time.sleep(.5)

    def tree():
        for attempt in range(3):
            try:
                adb('shell', 'uiautomator', 'dump', '/sdcard/tongjitap-release.xml')
                return ET.fromstring(adb('exec-out', 'cat', '/sdcard/tongjitap-release.xml'))
            except (subprocess.CalledProcessError, ET.ParseError):
                if attempt == 2:
                    raise
                time.sleep(.5)

    def find(root, text):
        return next((n for n in root.iter('node') if n.get('text') == text), None)

    def tap(node):
        assert node is not None, 'Missing control'
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(.3)

    def consent():
        ui = tree()
        agree = find(ui, '同意并进入')
        assert agree is not None and agree.get('enabled') == 'false'
        (folder / '01-consent.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        for _ in range(18):
            checkbox = find(ui, '我已阅读并理解功能、权限和风险说明')
            if checkbox is not None:
                break
            adb('shell', 'input', 'swipe', '540', '1500', '540', '400', '250')
            ui = tree()
        assert checkbox is not None
        tap(checkbox)
        agree = find(tree(), '同意并进入')
        assert agree is not None and agree.get('enabled') == 'true'
        tap(agree)

    def app_uid():
        packages = adb('shell', 'pm', 'list', 'packages', '-U', 'org.hachile.tongjitap')
        match = re.search(r'^package:org\.hachile\.tongjitap uid:(\d+)\s*$', packages, re.M)
        assert match is not None, 'Installed app UID missing'
        return match.group(1)

    consent()
    if upgrade:
        tap(find(tree(), '设置循环次数'))
        editor = next(n for n in tree().iter('node') if n.get('class') == 'android.widget.EditText')
        tap(editor)
        adb('shell', 'input', 'keyevent', '123', *(['67'] * 8))
        adb('shell', 'input', 'text', '13')
        tap(find(tree(), '保存'))
        assert find(tree(), '整组循环 13 次') is not None
        before = app_uid()
        assert 'Success' in adb('install', '-r', str(ROOT / 'dist/tongjitap-1.0.0.apk'))
        after = app_uid()
        assert before == after
        adb('shell', 'am', 'start', '-W', '-n', 'org.hachile.tongjitap/.MainActivity')
        consent()
    for _ in range(5):
        tap(find(tree(), '下一步'))
    assert find(tree(), 'QQ 交流群：934139029') is not None
    tap(find(tree(), '开始使用'))
    assert find(tree(), '整组循环 13 次' if upgrade else '整组循环 10 次') is not None
    (folder / '02-home.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    for tab, expected in [('随步', '播放倍速'), ('跃点', '开始之前'), ('更多', '当前版本 1.0.0')]:
        tap(find(tree(), tab))
        ui = tree()
        if tab != '随步':
            assert find(ui, expected) is not None
        else:
            assert any(n.get('content-desc') == '调速设置' for n in ui.iter('node'))
        (folder / (tab + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    (folder / 'release-result.txt').write_text(
        f'PASS: signed release, explicit consent, tutorial and four tabs on API {api}; upgrade={upgrade}\n', encoding='utf-8')
    print(f'Release API {api}: consent, tutorial, four tabs; upgrade={upgrade} PASS', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--api', type=int, choices=(26,36), action='append')
    apis = parser.parse_args().api or [26,36]
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(smoke, [(api,5556 if api==26 else 5558) for api in apis]))
    p = subprocess.run([str(ROOT / 'tools/android-sdk/build-tools/35.0.0/aapt.exe'), 'dump', 'permissions',
                        str(ROOT / 'dist/tongjitap-1.0.0.apk')], capture_output=True, check=True, text=True)
    required = {'android.permission.INTERNET', 'android.permission.ACCESS_WIFI_STATE',
                'android.permission.ACCESS_NETWORK_STATE', 'android.permission.WAKE_LOCK',
                'android.permission.VIBRATE', 'android.permission.FOREGROUND_SERVICE',
                'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'}
    declared = set(re.findall(r"uses-permission: name='([^']+)'", p.stdout))
    assert required <= declared
    assert not declared.intersection({'android.permission.CHANGE_WIFI_STATE', 'android.permission.ACCESS_FINE_LOCATION',
                                     'android.permission.READ_EXTERNAL_STORAGE', 'android.permission.READ_CONTACTS'})
    (ROOT / 'dist/checks/release-permissions.txt').write_text(p.stdout, encoding='utf-8')
    print('Release permission manifest checked', flush=True)
