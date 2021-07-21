Steps to Deploy:

1. Make sure libglfw.so is already in the resources/darwin folder
2. Download the release version of libmembraneskia.so from github to resources/linux-x86-64 folder
3. Updated the pom with the matching version.
4. `clojure -X:jar`
5. `clojure -M:deploy`
