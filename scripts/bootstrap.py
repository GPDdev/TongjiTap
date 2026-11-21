"""Install the pinned build tools inside tools/, without changing system settings."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
TOOLS.mkdir(exist_ok=True)


def download(name, url, sha1=None):
    dest = TOOLS / name
    if not dest.exists():
        partial = dest.with_suffix(dest.suffix + '.partial')
        with urllib.request.urlopen(url, timeout=120) as response, partial.open('wb') as out:
            shutil.copyfileobj(response, out)
        partial.replace(dest)
    if sha1 and hashlib.sha1(dest.read_bytes()).hexdigest() != sha1:
        raise RuntimeError(f'Checksum mismatch: {dest}')
    print(f'Ready: {name}', flush=True)
    return dest


def install_sdk(spec):
    name, folder, sha1 = spec
    target = TOOLS / 'android-sdk' / folder
    if target.exists():
        return
    archive = download(name, 'https://dl.google.com/android/repository/' + name, sha1)
    staging = TOOLS / ('unpack-' + name)
    staging.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        z.extractall(staging)
    entries = list(staging.iterdir())
    if len(entries) != 1 or not entries[0].is_dir():
        raise RuntimeError(f'Unexpected SDK archive layout: {name}')
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(entries[0]), target)
    staging.rmdir()


if __name__ == '__main__':
    sdk = [
        ('platform-36_r02.zip', 'platforms/android-36', '2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576'),
        ('build-tools_r35_windows.zip', 'build-tools/35.0.0', 'af059bb67cf7786f45ee0db85e2d24985df1b4b6'),
        ('platform-tools_r37.0.3-win.zip', 'platform-tools', '7fea85bc18d0480bc81d3603952d666f49496e5a'),
    ]
    with ThreadPoolExecutor(max_workers=5) as pool:
        jobs = [pool.submit(install_sdk, s) for s in sdk]
        jobs.append(pool.submit(download, 'gradle-8.13-bin.zip', 'https://downloads.gradle.org/distributions/gradle-8.13-bin.zip'))
        jobs.append(pool.submit(download, 'junit-4.13.2.jar', 'https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar'))
        jobs.append(pool.submit(download, 'hamcrest-core-1.3.jar', 'https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar'))
        for job in jobs:
            job.result()
    gradle = TOOLS / 'gradle-8.13-bin.zip'
    expected = urllib.request.urlopen('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip.sha256', timeout=30).read().decode().strip()
    if hashlib.sha256(gradle.read_bytes()).hexdigest() != expected:
        raise RuntimeError('Gradle checksum mismatch')
    if not (TOOLS / 'gradle-8.13').exists():
        with zipfile.ZipFile(gradle) as z:
            z.extractall(TOOLS)
    (ROOT / 'local.properties').write_text('sdk.dir=tools/android-sdk\n', encoding='utf-8')
    print('SDK and Gradle installed. See README.md for build commands.', flush=True)
