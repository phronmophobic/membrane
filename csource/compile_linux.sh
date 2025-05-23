#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

skia_root="./libs/skia"

c++ \
    -fPIC \
    -I "$skia_root" \
    -I "$skia_root"/include/gpu \
    -I "$skia_root"/include/gpu/gl \
    -I "$skia_root"/include/core \
    -I "$skia_root"/include/utils \
    -I "$skia_root"/include/private \
    -I "$skia_root"/include/codec \
    -L "$skia_root"/out/Release-${arch} \
    -Wl,--whole-archive \
    "$skia_root"/out/Release-${arch}/libskia.a \
    "$skia_root"/out/Release-${arch}/libskparagraph.a \
    "$skia_root"/out/Release-${arch}/libsvg.a \
    -Wl,--no-whole-archive \
    -shared \
    -std=c++17 \
    -o libmembraneskia-${arch}.so \
    -DSK_GL=1 \
    -lGL \
    -lfontconfig \
    -lskunicode_core \
    -lskunicode_icu \
    -lskshaper \
    -lsvg \
    skia.cpp

cp libmembraneskia-x86_64.so ./linux-x86-64/resources/linux-x86-64/libmembraneskia.so

echo 'done'
