#!/bin/bash
# Real Kotlin compile + unit-test run WITHOUT Gradle (sandbox check). NOT a replacement for GitHub Actions CI.
# Needs: java 17+, internet to github.com. Downloads kotlinc 1.9.24 and an android-34 android.jar, then compiles
# app/src/main + app/src/test against small stubs for vosk / androidx / junit (tools/local_stubs).
set -e
W=${1:-/tmp/nova_check}; mkdir -p $W; cd $W
[ -d kotlinc ] || { curl -sL -o k.zip https://github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip && unzip -q k.zip; }
[ -f android.jar ] || curl -sL -o android.jar https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-34/android.jar   # if raw.githubusercontent.com is blocked: git clone --depth 1 --filter=blob:none --sparse https://github.com/Reginer/aosp-android-jar.git and copy android-34/android.jar here
R=$(cd "$(dirname "$0")/.." && pwd); S=$R/tools/local_stubs
export JAVA_OPTS="-Xmx2500m -Xss16m"; K=$W/kotlinc/lib/kotlin-stdlib.jar
rm -rf stubsrc mainsrc; mkdir -p stubsrc && cp -r $S/org stubsrc/ 2>/dev/null || true; cp $S/*.kt $S/extra/*.kt stubsrc/
# LlamaBrain.kt needs jars that are not available offline -> harness uses extra/LlamaBrainStub.kt instead
cp -r $R/app/src/main/java mainsrc; rm -f mainsrc/com/nova/assistant/LlamaBrain.kt
$W/kotlinc/bin/kotlinc -jvm-target 17 -no-stdlib -cp $W/android.jar:$K -d out_main mainsrc stubsrc
# JSON_JAR=/path/org.json.jar puts the real org.json before android.jar (android.jar only has throwing stubs; Gradle uses testImplementation org.json:json).
# Prefer the REAL JUnit 4.13.2 (bundled in /opt/gradle*/lib or JUNIT_JAR/HAMCREST_JAR); fall back to the small stub.
JU=${JUNIT_JAR:-$(ls /opt/gradle*/lib/junit-4.13.2.jar 2>/dev/null | head -1)}; HC=${HAMCREST_JAR:-$(ls /opt/gradle*/lib/hamcrest-core-1.3.jar 2>/dev/null | head -1)}
if [ -n "$JU" ] && [ -n "$HC" ]; then
  $W/kotlinc/bin/kotlinc -jvm-target 17 -no-stdlib -Xfriend-paths=$W/out_main -cp $W/android.jar:$K:out_main:$JU -d out_test $R/app/src/test/java
  CLASSES=$(cd out_test && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | sort | tr '\n' ' ')
  cd $R/app && java -cp $W/out_test:$W/out_main:${JSON_JAR:+$JSON_JAR:}$W/android.jar:$K:$JU:$HC org.junit.runner.JUnitCore $CLASSES
else
  $W/kotlinc/bin/kotlinc -jvm-target 17 -no-stdlib -Xfriend-paths=$W/out_main -cp $W/android.jar:$K:out_main -d out_test $R/app/src/test/java $S/junit
  mkdir -p out_junit && java -m jdk.compiler/com.sun.tools.javac.Main -d out_junit $S/junit/org/junit/*.java $S/Run.java
  cd $R/app && java -cp $W/out_junit:$W/out_test:$W/out_main:$W/android.jar:$K Run $W/out_test
fi
