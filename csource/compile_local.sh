#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

export arch=arm64

./compile_macosx.sh

cp libmembraneskia-${arch}.dylib ./macos-aarch64/resources/darwin-aarch64/libmembraneskia.dylib


