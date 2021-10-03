Steps to Deploy:

1. Make sure libglfw.dylib is already in the resources/linux-x86-64 folder
2. Download the release version of libmembraneskia.dylib from github to resources/darwin folder
3. Updated the pom with the matching version.
4. `clojure -X:jar`
5. `clojure -M:deploy`
