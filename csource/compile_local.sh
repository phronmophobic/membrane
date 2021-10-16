#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

export arch=arm64

./compile_macosx.sh

mv libmembraneskia-${arch}.dylib ./macosx-aarch64/resources/darwin-aarch64/libmembraneskia.dylib


