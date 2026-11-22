"""Create a local release key once. Never print or commit its password."""
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parents[1]
folder = root / '.signing'
folder.mkdir(exist_ok=True)
key = folder / 'tongjitap.jks'
properties = folder / 'tongjitap.properties'
if key.exists() != properties.exists():
    raise SystemExit('Signing files are incomplete. Restore the matching key and properties; no files were replaced.')
if not key.exists():
    password = secrets.token_hex(24)
    temporary = folder / '.keytool-password'
    temporary.write_text(password, encoding='utf-8')
    try:
        subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'JKS',
                        '-alias', 'tongjitap', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                        '-dname', 'CN=TongjiTap Personal Build', '-storepass:file', str(temporary),
                        '-keypass:file', str(temporary)], check=True)
        properties.write_text('storePassword=' + password + '\n', encoding='ascii')
    finally:
        temporary.unlink(missing_ok=True)
print('Local release signing key ready. Back up .signing privately for future updates.')
