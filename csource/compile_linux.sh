#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

c++ \
    -fPIC \
    -I ./libs/skia \
    -I ./libs/skia/include/gpu \
    -I ./libs/skia/include/gpu/gl \
    -I ./libs/skia/include/core \
    -I ./libs/skia/include/utils \
    -I ./libs/skia/include/private \
    -L ./libs/skia/out/Release-${arch} \
    -Wl,--whole-archive \
    ./libs/skia/out/Release-${arch}/libskia.a \
    ./libs/skia/out/Release-${arch}/libskparagraph.a \
    -Wl,--no-whole-archive \
    -shared \
    -std=c++17 \
    -o libmembraneskia-${arch}.so \
    -DSK_GL=1 \
    -lGL \
    -lfontconfig \
    -lskunicode \
    -lskshaper \
    skia.cpp



echo 'done'
