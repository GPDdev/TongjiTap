"""Download official AOSP test images and create isolated, disposable AVDs."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
SDK = TOOLS / 'android-sdk'
AVDS = TOOLS / 'avds'


def install(name, url, size, checksum, destination):
    if destination.exists():
        return
    archive = TOOLS / name
    current = archive.stat().st_size if archive.exists() else 0
    if current < size:
        request = urllib.request.Request(url, headers={'Range': f'bytes={current}-'} if current else {})
        with urllib.request.urlopen(request, timeout=120) as response:
            mode = 'ab' if response.status == 206 and current else 'wb'
            with archive.open(mode) as out:
                shutil.copyfileobj(response, out)
    if archive.stat().st_size != size or hashlib.sha1(archive.read_bytes()).hexdigest() != checksum:
        raise RuntimeError(f'Checksum mismatch: {name}')
    staging = TOOLS / ('unpack-' + name)
    staging.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        z.extractall(staging)
    entries = list(staging.iterdir())
    if len(entries) != 1 or not entries[0].is_dir():
        raise RuntimeError(f'Unexpected archive layout: {name}')
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(entries[0]), destination)
    staging.rmdir()
    print(f'Installed: {destination.relative_to(ROOT)}', flush=True)


if __name__ == '__main__':
    specs = [
        ('emulator.zip', 'https://dl.google.com/android/repository/emulator-windows_x64-16916650.zip', 460845901,
         '2cd8beaf83472f12e99a0554a2db3626db81f4ba', SDK / 'emulator'),
        ('x86_64-26_r01.zip', 'https://dl.google.com/android/repository/sys-img/android/x86_64-26_r01.zip', 474178332,
         '432f149c048bffce7f9de526ec65b336daf7a0a3', SDK / 'system-images/android-26/default/x86_64'),
        ('x86_64-36_r02.zip', 'https://dl.google.com/android/repository/sys-img/android/x86_64-36_r02.zip', 844217077,
         '829c076e8ff448a336097ae25a355b495ba36e2c', SDK / 'system-images/android-36/default/x86_64'),
    ]
    with ThreadPoolExecutor(max_workers=3) as pool:
        jobs = [pool.submit(install, *s) for s in specs]
        for job in jobs:
            job.result()
    AVDS.mkdir(exist_ok=True)
    for api in (26, 36):
        name = f'TongjiTap{api}'
        folder = AVDS / (name + '.avd')
        folder.mkdir(exist_ok=True)
        (AVDS / (name + '.ini')).write_text(f'avd.ini.encoding=UTF-8\npath={folder}\ntarget=android-{api}\n')
        config = {
            'AvdId': name, 'avd.ini.displayname': name, 'abi.type': 'x86_64', 'tag.id': 'default',
            'hw.cpu.arch': 'x86_64', 'hw.cpu.ncore': '2', 'hw.ramSize': '2048', 'vm.heapSize': '256',
            'hw.lcd.width': '1080', 'hw.lcd.height': '1920', 'hw.lcd.density': '420', 'hw.keyboard': 'yes',
            'hw.mainKeys': 'no', 'hw.battery': 'yes', 'hw.gpu.enabled': 'yes', 'hw.gpu.mode': 'swiftshader_indirect',
            'hw.audioInput': 'no', 'hw.sdCard': 'no', 'disk.dataPartition.size': '2G',
            'image.sysdir.1': f'system-images/android-{api}/default/x86_64/',
            'fastboot.forceColdBoot': 'yes', 'fastboot.forceFastBoot': 'no', 'showDeviceFrame': 'no',
        }
        (folder / 'config.ini').write_text(''.join(f'{k}={v}\n' for k, v in config.items()))
    print('AVDs ready: TongjiTap26, TongjiTap36', flush=True)
