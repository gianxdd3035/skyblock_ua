#!/bin/sh
# Компіляція й запуск тесту рушія (потрібен JDK 21+). Аргументи: [шлях до NEU items]
set -e
cd "$(dirname "$0")/.."
rm -rf build/enginetest
javac -encoding UTF-8 -d build/enginetest src/main/java/ua/kyrylo/skyblockua/core/*.java tools/EngineTest.java
java -Dstdout.encoding=UTF-8 -cp build/enginetest EngineTest src/main/resources/assets/skyblockua/dict "$@"
