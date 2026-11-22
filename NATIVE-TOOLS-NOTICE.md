# Native tools notice

This build downloads Android native executables at build time, checksum pinned in `.github/scripts/prepare-native-tools.py`.

- Platform tools 35.0.2: https://github.com/lzhiyong/android-sdk-tools/tree/35.0.2 (AOSP-based build, upstream sources and component licenses are identified in that project). Includes adb, fastboot, sqlite3, etc1tool, hprof-conv, e2fsdroid, sload_f2fs, mke2fs, make_f2fs and make_f2fs_casefold.
- curl 8.18.0 Android tool: https://github.com/vvb2060/curl-android/tree/v8.18.0 (curl and third-party components; consult upstream licenses).

These are third-party builds, not Google-distributed Android APK binaries. USB and fastboot access may be blocked by Android permissions or require root.
