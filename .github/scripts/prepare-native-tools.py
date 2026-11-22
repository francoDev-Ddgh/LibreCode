#!/usr/bin/env python3
"""Reproducibly fetch pinned Android native executables for release packaging."""
import hashlib, io, lzma, pathlib, urllib.request, zipfile
root = pathlib.Path('app/src/main/jniLibs')
tools = ('adb','fastboot','sqlite3','etc1tool','hprof-conv','e2fsdroid','sload_f2fs','mke2fs','make_f2fs','make_f2fs_casefold')
archives = {
 'arm64-v8a': ('aarch64','db1cea2c4454d5f9c5a802646b2d1cf560b4ee7badbe23e51ab8e1881bb50fc2'),
 'armeabi-v7a': ('arm','17b27ad0136b8d1c3ab67be24b3ab9a81c412c39fdc4112b622d900f90b02983'),
 'x86': ('x86','254be5472243311ee105811d82f2d0fc8575976c888d30c78d9d286303791660'),
 'x86_64': ('x86_64','30110518265f0f6e8fb18bcce9ef29a7f1caf507e8d6a37ae92d26b11e44bf07')}
def fetch(url, expected):
 request = urllib.request.Request(url, headers={'User-Agent':'LibreCode-build'})
 with urllib.request.urlopen(request, timeout=120) as r: data = r.read(50_000_001)
 if len(data) > 50_000_000 or hashlib.sha256(data).hexdigest() != expected: raise RuntimeError('Native tools checksum mismatch: ' + url)
 return data
for abi, (arch, checksum) in archives.items():
 data=fetch('https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-'+arch+'.zip',checksum)
 z=zipfile.ZipFile(io.BytesIO(data)); dest=root / abi; dest.mkdir(parents=True,exist_ok=True)
 for name in tools:
  content=z.read('platform-tools/'+name)
  if not content.startswith(b'\x7fELF'): raise RuntimeError('Not an ELF executable: '+name)
  (dest / ('lib'+name.replace('-', '_')+'.so')).write_bytes(content)
 print('Prepared platform-tools for',abi)
curl=fetch('https://github.com/vvb2060/curl-android/releases/download/v8.18.0/tool-release.apk.xz','0aa32165eb4a9cb8a56e13053cd3bc80cccb0c761b4daf9688c3e3667522bb4b')
z=zipfile.ZipFile(io.BytesIO(lzma.decompress(curl)))
for abi in archives:
 data=z.read('lib/'+abi+'/libcurl.so')
 if not data.startswith(b'\x7fELF'): raise RuntimeError('Invalid curl ELF')
 (root / abi / 'libcurl.so').write_bytes(data)
 print('Prepared curl for',abi)
