#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

export SDKROOT="/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk"

skia_root="./libs/skia"

clang++ \
    -I "$skia_root" \
    -I "$skia_root"/include/gpu \
    -I "$skia_root"/include/gpu/gl \
    -I "$skia_root"/include/core \
    -I "$skia_root"/include/utils \
    -I "$skia_root"/include/private \
    -framework OpenGL \
    -framework Cocoa \
    -framework IOKit \
    -framework CoreFoundation \
    -framework CoreVideo \
    -framework AppKit \
    -framework CoreGraphics \
    -framework CoreServices \
    -framework Foundation \
    -framework Metal \
    -mmacosx-version-min=10.13 \
    "$skia_root"/out/Release-x64/libskia.a \
    -DSK_GL=1 \
    -arch x86_64 \
    -dynamiclib \
    -std=c++17 \
    -o libmembraneskia.dylib \
    skia.cpp

# cd ./libs/libtmt && ./compile.sh
# cd -

# test
# clang++ -I ./libs/glfw-3.3.bin.MACOS/include \
#     -I ./libs/skia \
#     -I ./libs/skia/include/gpu \
#     -I ./libs/skia/include/gpu/gl \
#     -I ./libs/skia/include/core \
#     -I ./libs/skia/include/utils \
#     -I ./libs/skia/include/private \
#     -I ./libs/libtmt \
#     -framework OpenGL \
#     -framework Cocoa \
#     -framework IOKit \
#     -framework CoreFoundation \
#     -framework CoreVideo \
#     -framework AppKit \
#     -framework CoreGraphics \
#     -framework CoreServices \
#     -framework Foundation \
#     -framework Metal \
#     -mmacosx-version-min=10.13 \
#     -DTESTING \
#     ./libs/glfw-3.3.bin.MACOS/lib-macos/libglfw3.a \
#     /Users/adrian/Downloads/Skia-m92-f46c37ba85-2-macos-Release-x64/out/Release-x64/libskia.a \
#     ./libs/libtmt/tmt.o \
#     -DSK_GL=1 \
#     -std=c++17 \
#     -arch x86_64 \
#     -o testglfw \
#     testglfw.cpp skia.cpp

cp libmembraneskia.dylib ../resources/darwin

echo 'done'
