#!/usr/bin/env bash
# Compiles everything in src/ to out/, failing on any warning.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -Xlint:all -Werror --release 17 -d out src/jongrai/*.java
echo "Built out/ - run: java -cp out jongrai.WebServer"
