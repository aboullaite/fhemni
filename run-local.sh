#!/usr/bin/env sh
set -eu

if [ -x /opt/homebrew/opt/openjdk/bin/java ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
    export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
fi

exec ./mvnw spring-boot:run "$@"
