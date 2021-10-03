#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

mkdir -p libs

pushd libs


RELEASE="m92-a77a4620d2-3"
URL="https://github.com/phronmophobic/skia-build/releases/download/${RELEASE}/Skia-${RELEASE}-${platform}-Release-${arch}.zip"

curl -L -o skia.zip "${URL}"

unzip -d skia skia.zip

popd
