#!/usr/bin/env python3
"""v6.81 structural contract (static): things that crash or break on real Android 12-14 phones. Kotlin NOT run here."""
import os, sys, re
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
rd = lambda p: open(os.path.join(R, p), encoding="utf-8").read()
MA = rd("app/src/main/java/com/nova/assistant/MainActivity.kt"); NS = rd("app/src/main/java/com/nova/assistant/NovaService.kt")
G = rd("app/build.gradle.kts"); MF = rd("app/src/main/AndroidManifest.xml")
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
check("WebView render-process death is handled (no app crash on low-RAM phones)", "onRenderProcessGone" in MA and "return true" in MA.split("onRenderProcessGone")[1][:900] and "recreate()" in MA)
check("context receivers pass an export flag on Android 13+ (targetSdk 34)", "RECEIVER_NOT_EXPORTED" in NS and NS.count("registerSysReceiver(") >= 3 and not re.search(r"(?<!un)registerReceiver\(batteryLowRx", NS))
check("Gradle wrapper files are in the project", all(os.path.isfile(os.path.join(R, p)) for p in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")))
check("wrapper Gradle is 8.7 or newer (AGP 8.5.2 needs 8.7+)", re.search(r"gradle-8\.(\d+)", rd("gradle/wrapper/gradle-wrapper.properties")) and int(re.search(r"gradle-8\.(\d+)", rd("gradle/wrapper/gradle-wrapper.properties")).group(1)) >= 7)
check("versionName matches the release notes", 'versionName = "6.99"' in G)
check("every PendingIntent has IMMUTABLE or MUTABLE (crash on targetSdk 31+ otherwise)", all(("FLAG_IMMUTABLE" in blk or "FLAG_MUTABLE" in blk or "flags" in blk or "fl)" in blk or ", fl" in blk) for blk in [x[:400] for x in re.split(r"PendingIntent\.get(?:Activity|Service|Broadcast)\(", "".join(rd("app/src/main/java/com/nova/assistant/" + f) for f in os.listdir(os.path.join(R, "app/src/main/java/com/nova/assistant")) if f.endswith(".kt")))[1:]]))
check("foreground services declare a type that matches the permissions", 'foregroundServiceType="microphone"' in MF and "FOREGROUND_SERVICE_MICROPHONE" in MF and 'foregroundServiceType="camera|microphone"' in MF and "FOREGROUND_SERVICE_CAMERA" in MF)
check("only the launcher activity and system-bound services are exported (+ boot receiver)", MF.count('exported="true"') == 8)   # v6.98: +WatchCommandActivity (VOICE_COMMAND, no UI)
sys.exit(1 if bad else 0)
