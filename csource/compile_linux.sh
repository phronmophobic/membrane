#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

skia_root="./libs/skia"

clang++ \
    -fPIC \
    -I ./libs/skia \
    -I ./libs/skia/include/gpu \
    -I ./libs/skia/include/gpu/gl \
    -I ./libs/skia/include/core \
    -I ./libs/skia/include/utils \
    -I ./libs/skia/include/private \
    -L ./libs/skia/out/Release-x64 \
    -shared \
    -std=c++17 \
    -o libmembraneskia.so \
    -DSK_GL=1 \
    -lskia \
    -lGL \
    -lfontconfig \
    skia.cpp


# c++ \
#     $(pkg-config --cflags glfw3) \
#     -o testglfw2 \
#     testglfw2.cpp \
#     $(pkg-config --static --libs glfw3) -lgl


# clang++ \
#     $(pkg-config --cflags glfw3) \
#     -I ./libs/skia \
#     -I ./libs/skia/include/gpu \
#     -I ./libs/skia/include/gpu/gl \
#     -I ./libs/skia/include/core \
#     -I ./libs/skia/include/utils \
#     -I ./libs/skia/include/private \
#     -L ./libs/skia/out/Static/ \
#     -std=c++17 \
#     -o testglfw \
#     testglfw.cpp skia.cpp $(pkg-config --static --libs glfw3) -lskia -lfontconfig -lfreetype -lGL

cp libmembraneskia.so ../resources/linux-x86-64

echo 'done'
