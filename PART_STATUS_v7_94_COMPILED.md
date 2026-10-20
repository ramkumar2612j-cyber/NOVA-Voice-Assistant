# v7.94 - real compile check status (no feature code changed)
RUN (sandbox, kotlinc 1.9.24 + android-34 jar, tools/local_compile_check.sh steps): app/src/main compiled with 0 errors. JUnit run with the small stub harness: pass=1450 fail=4.
KNOWN FAIL (seen): WebBrowsersTest.breakerSkipsADeadBrowser (WebBrowsersTest.kt:84). The other 3 failing test names were NOT looked at. Not yet known if they are real bugs or stub/harness gaps.
CHANGED: only tools/local_stubs/junit/org/junit/Assert.java (added assertNotEquals, assertArrayEquals for int[]/long[]) so tests compile locally. No app code changed.
Thread.sleep: re-checked the 4 bare ones (torch x2, mic loop x2): all covered by try/catch, no change needed.
NOVA SERVICE SPLIT: NOT done (7293 lines). Do it only after the 4 failures are understood and a green GitHub Actions build exists.
NOT RUN: Gradle build, GitHub Actions, phone test.
NEXT ACCOUNT: 1) run the local compile + tests again and print the names of all failing tests; 2) for each, decide real bug vs harness gap and fix only real bugs; 3) then split NovaService in small pieces, compiling after each; 4) run GitHub Actions "Build and Test APK".
