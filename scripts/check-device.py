"""Run destructive test setup only on this project's disposable TongjiTap AVDs."""
from pathlib import Path
import argparse
import os
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
ADB = ROOT / 'tools/android-sdk/platform-tools/adb.exe'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--suite', choices=('regression', 'formal', 'integration'), default='regression')
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        raise SystemExit('Refusing to run on a physical device.')
    env = os.environ.copy()
    env['ANDROID_USER_HOME'] = str(ROOT / 'tools/android-user')

    def adb(*command, check=True, binary=False, timeout=240):
        result = subprocess.run([str(ADB), '-s', args.serial, *command], cwd=ROOT, env=env,
                                capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')

    avd = adb('emu', 'avd', 'name').splitlines()[0].strip()
    if avd not in ('TongjiTap26', 'TongjiTap36'):
        raise SystemExit(f'Refusing to modify non-project AVD: {avd}')
    expected_path = ROOT / 'tools/avds' / (avd + '.avd')
    if not expected_path.exists():
        raise SystemExit('Project AVD definition missing.')
    avd_property = adb('shell', 'getprop', 'ro.boot.qemu.avd_name').strip()
    if avd_property and avd_property != avd:
        raise SystemExit('AVD identity mismatch.')
    folder = ROOT / 'dist/checks' / avd / '1.0.0' / args.suite
    folder.mkdir(parents=True, exist_ok=True)
    for package in ('org.hachile.tongjitap.test', 'org.hachile.tongjitap', 'org.hachile.tongjitap.fixture'):
        adb('uninstall', package, check=False)
    for apk in ('app/build/outputs/apk/debug/app-debug.apk',
                'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
                'fixture/build/outputs/apk/debug/fixture-debug.apk'):
        print(adb('install', '-r', str(ROOT / apk)).strip(), flush=True)
    selector = ['-e', 'only', 'v1'] if args.suite == 'formal' else []
    adb('shell', 'cmd', 'statusbar', 'collapse', check=False)
    if args.suite == 'integration':
        selector = ['-e', 'only', 'integration']
    runner = 'DeviceChecks'
    output = adb('shell', 'am', 'instrument', '-w', '-r', *selector, 'org.hachile.tongjitap.test/org.hachile.tongjitap.' + runner, timeout=420)
    (folder / 'device-result.txt').write_text(output, encoding='utf-8')
    print(output, flush=True)
    filenames = adb('exec-out', 'run-as', 'org.hachile.tongjitap', 'ls', '-1', 'files/checks', check=False).splitlines()
    for name in filenames:
        if re.fullmatch(r'[\w.-]+\.(png|jsonl)', name):
            (folder / name).write_bytes(adb('exec-out', 'run-as', 'org.hachile.tongjitap', 'cat', 'files/checks/' + name, binary=True))
    (folder / 'logcat.txt').write_text(adb('logcat', '-d', '-t', '1500'), encoding='utf-8')
    expected = (9 if avd == 'TongjiTap26' else 11) if args.suite == 'formal' else (15 if avd == 'TongjiTap26' else 24)
    label = 'formal UI scenarios' if args.suite == 'formal' else 'device scenarios'
    if args.suite == 'integration':
        expected = 3 if avd == 'TongjiTap26' else 6
        label = 'integration scenarios'
    if f'PASS: {expected} {label}' not in output or 'FAIL:' in output:
        raise SystemExit('Device checks failed. See ' + str(folder))
    (folder / 'failure.png').unlink(missing_ok=True)
    print('Device checks passed: ' + avd, flush=True)


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    main()
