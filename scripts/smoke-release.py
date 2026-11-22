"""Cold-install the signed APK only on the two disposable project AVDs."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ADB = ROOT / 'tools/android-sdk/platform-tools/adb.exe'


def smoke(pair):
    api, port = pair
    serial = f'emulator-{port}'
    folder = ROOT / f'dist/checks/Release{api}'
    folder.mkdir(parents=True, exist_ok=True)

    def adb(*args, binary=False):
        p = subprocess.run([str(ADB), '-s', serial, *args], capture_output=True, check=True, timeout=45)
        return p.stdout if binary else p.stdout.decode('utf-8', errors='replace')

    if adb('emu', 'avd', 'name').splitlines()[0].strip() != f'TongjiTap{api}':
        raise RuntimeError('Refusing to modify a non-project AVD')
    if not (ROOT / f'tools/avds/TongjiTap{api}.avd').exists():
        raise RuntimeError('Project AVD definition missing')
    subprocess.run([str(ADB), '-s', serial, 'uninstall', 'org.hachile.tongjitap'], capture_output=True)
    assert 'Success' in adb('install', str(ROOT / 'dist/tongjitap-0.2.0.apk'))
    adb('shell', 'am', 'start', '-W', '-n', 'org.hachile.tongjitap/.MainActivity')
    time.sleep(.5)

    def tree():
        adb('shell', 'uiautomator', 'dump', '/sdcard/tongjitap-release.xml')
        return ET.fromstring(adb('shell', 'cat', '/sdcard/tongjitap-release.xml'))

    def find(root, text):
        return next((n for n in root.iter('node') if n.get('text') == text), None)

    def tap(node):
        assert node is not None, 'Missing control'
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(.3)

    ui = tree()
    agree = find(ui, '同意并进入')
    assert agree is not None and agree.get('enabled') == 'false'
    (folder / '01-consent.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    checkbox = None
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
    assert find(tree(), '打开悬浮控制面板') is not None
    (folder / '02-home.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    (folder / 'release-result.txt').write_text(
        f'PASS: signed release cold install, explicit consent and launch on API {api}\n', encoding='utf-8')
    print(f'Release API {api}: install, consent and main screen PASS', flush=True)


if __name__ == '__main__':
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(smoke, [(26, 5556), (36, 5558)]))
    p = subprocess.run([str(ROOT / 'tools/android-sdk/build-tools/35.0.0/aapt.exe'), 'dump', 'permissions',
                        str(ROOT / 'dist/tongjitap-0.2.0.apk')], capture_output=True, check=True, text=True)
    assert 'uses-permission:' not in p.stdout
    (ROOT / 'dist/checks/release-permissions.txt').write_text(p.stdout, encoding='utf-8')
    print('Release requested permissions: none', flush=True)
