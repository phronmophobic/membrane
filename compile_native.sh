native-image --report-unsupported-elements-at-runtime \
             --initialize-at-build-time \
             --no-server \
             -jar ./target/membrane-0.9.8-beta-standalone.jar \
             -H:Name=./target/todoapp
