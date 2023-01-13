#!/bin/bash

set -e
set -x

# platform=windows
# arch=x86_64
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

mkdir -p libs

pushd libs


RELEASE="4d519a8ba97ab32c3f310f2417e122b8ea354345-3"
URL="https://github.com/phronmophobic/skia-build/releases/download/${RELEASE}/Skia-${RELEASE}-${platform}-Release-${arch}.zip"

curl -L -o skia.zip "${URL}"

unzip -d skia skia.zip

popd
