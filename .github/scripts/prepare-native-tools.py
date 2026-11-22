#!/usr/bin/env python3
"""Download a pinned Android curl executable for each supported ABI."""
import hashlib, io, lzma, pathlib, urllib.request, zipfile
url = 'https://github.com/vvb2060/curl-android/releases/download/v8.18.0/tool-release.apk.xz'
expected = '0aa32165eb4a9cb8a56e13053cd3bc80cccb0c761b4daf9688c3e3667522bb4b'
req = urllib.request.Request(url, headers={'User-Agent':'LibreCode-build'})
with urllib.request.urlopen(req, timeout=120) as response:
    data = response.read(20_000_001)
if len(data) > 20_000_000 or hashlib.sha256(data).hexdigest() != expected:
    raise RuntimeError('Checksum mismatch for Android curl')
archive = zipfile.ZipFile(io.BytesIO(lzma.decompress(data)))
for abi in ('arm64-v8a','armeabi-v7a','x86','x86_64'):
    data = archive.read('lib/'+abi+'/libcurl.so')
    if not data.startswith(b'\x7fELF'): raise RuntimeError('Invalid ELF')
    dest = pathlib.Path('app/src/main/jniLibs') / abi
    dest.mkdir(parents=True, exist_ok=True)
    (dest / 'libcurl.so').write_bytes(data)
    print('Prepared curl for', abi)
