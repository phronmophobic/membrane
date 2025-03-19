#!/bin/bash

set -e
set -x

# platform=windows
# arch=x86_64
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

mkdir -p libs

pushd libs


RELEASE="f5fefe5245098be43cb608eace5e14d67cdc09e6-2"
URL="https://github.com/phronmophobic/skia-build/releases/download/${RELEASE}/Skia-${RELEASE}-${platform}-Release-${arch}.zip"

curl -L -o skia.zip "${URL}"

unzip -d skia skia.zip

popd
