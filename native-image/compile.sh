#!/bin/bash

set -e
set -x

which java
java -version

clojure -T:build compile-native-image

native-image \
    -cp "$(clojure -A:native-image -Spath):target/classes" \
    -H:Name=todo \
    -Djava.awt.headless=false \
    -H:ConfigurationFileDirectories=native-image/config \
    -H:+ReportExceptionStackTraces \
    -H:+AddAllCharsets \
    -J-Dclojure.spec.skip-macros=true \
    -J-Dclojure.compiler.direct-linking=true \
    -J-Dtech.v3.datatype.graal-native=true \
    --features=clj_easy.graal_build_time.InitClojureClasses \
    --verbose \
    --no-fallback \
    com.phronemophobic.native_image.main



