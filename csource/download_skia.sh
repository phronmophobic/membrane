#!/bin/bash

set -e
set -x

# platform=windows
# arch=x86_64
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

mkdir -p libs

pushd libs


RELEASE="dbfd72770bdd2e7b82d493df8cdda6338fdf7f7c-1"
URL="https://github.com/phronmophobic/skia-build/releases/download/${RELEASE}/Skia-${RELEASE}-${platform}-Release-${arch}.zip"

curl -L -o skia.zip "${URL}"

unzip -d skia skia.zip

popd
