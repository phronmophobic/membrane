#!/bin/bash

set -e
set -x


clang++ \
    -fPIC \
    -I ./libs/skia \
    -I ./libs/skia/include/gpu \
    -I ./libs/skia/include/gpu/gl \
    -I ./libs/skia/include/core \
    -I ./libs/skia/include/utils \
    -I ./libs/skia/include/private \
    -L ./libs/skia/out/Static \
    -shared \
    -std=c++17 \
    -o libmembraneskia.so \
    skia.cpp \
    -lskia \
    -lGL \
    -lfontconfig  


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
