#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

mkdir -p libs

pushd libs



URL="https://github.com/phronmophobic/skia-build/releases/download/m92-a77a4620d2/Skia-m92-a77a4620d2-3-${platform}-Release-x86_64.zip"

curl -L -o skia.zip "${URL}"

unzip -d skia skia.zip

popd
