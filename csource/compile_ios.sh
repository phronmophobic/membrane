#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

export SDKROOT="/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk"

clang++ \
    -I ./libs/skia.bak \
    -I ./libs/skia.bak/include/gpu \
    -I ./libs/skia.bak/include/gpu/gl \
    -I ./libs/skia.bak/include/core \
    -I ./libs/skia.bak/include/utils \
    -I ./libs/skia.bak/include/private \
    -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk \
    -DTARGET_OS_IOS=1 \
    -DSK_METAL=1 \
    -DSK_SUPPORT_GPU=1 \
    -target arm64-apple-ios14.1 \
    -c \
    -std=c++17 \
    -arch arm64 \
    -o libmembraneiosskia.o \
    skia.cpp


# install_name_tool -change @rpath/libskia.so @loader_path/libskia.so libmembraneiosskia.so

echo 'done'

