#!/bin/bash

set -e
set -x

clojure -T:build compile-native-image

# not currently needed
java -XstartOnFirstThread -agentlib:native-image-agent=config-output-dir=native-image/config -cp "$(clojure -A:native-image -Spath):target/classes" com.phronemophobic.main

clojure -T:build fix-config
