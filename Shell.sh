#!/bin/sh
# Navigate to the directory containing this script and run the JAR
exec java --enable-preview -jar "$(dirname "$0")/target/mini-shell.jar" "$@"
