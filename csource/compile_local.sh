#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

export arch=arm64

## deleting the file seems to help workaround apple quarantining?
rm -f libmembraneskia-${arch}.dylib
./compile_macosx.sh

## deleting the file seems to help workaround apple quarantining?
rm ./macos-aarch64/resources/darwin-aarch64/libmembraneskia.dylib
cp libmembraneskia-${arch}.dylib ./macos-aarch64/resources/darwin-aarch64/libmembraneskia.dylib


