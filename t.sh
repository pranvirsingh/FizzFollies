#!/bin/bash
cd /home/claude/fizz
OUT=${OUT:-build/jvm}
rm -rf $OUT
SRC=$(ls src/com/pranvir/fizz/*.kt | grep -v -e MainActivity -e Audio.kt)
./kc.sh $OUT "" $SRC jvmtest/shim/*.kt jvmtest/*.kt 2>&1 | grep -E "error|warning: unre" | head -40
for m in "$@"; do java -Xmx3g -Djava.awt.headless=true -cp $OUT:/home/claude/tc/kotlin-stdlib-2.3.10-RC.jar $m 2>&1 | grep -v JAVA_TOOL | tail -60; done
