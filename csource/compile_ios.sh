#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

export SDKROOT="/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk"

clang++ \
    -I ./libs/skia \
    -I ./libs/skia/include/gpu \
    -I ./libs/skia/include/gpu/gl \
    -I ./libs/skia/include/core \
    -I ./libs/skia/include/utils \
    -I ./libs/skia/include/private \
    -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk \
    -framework CoreFoundation \
    -framework ImageIO \
    -framework MobileCoreServices \
    -framework CoreGraphics \
    -framework CoreText \
    -framework UIKit \
    -framework Metal \
    -framework Foundation \
    -framework CoreServices \
    -DTARGET_OS_IOS=1 \
    -DSK_METAL=1 \
    -DSK_SUPPORT_GPU=1 \
    -target arm64-apple-ios14.1 \
    -dynamiclib \
    -L ./libs/skia/out/ios64Static/ \
    -lskia \
    -lskparagraph \
    -lskshaper \
    -lsktext \
    -std=c++17 \
    -install_name @rpath/libmembraneiosskia.so \
    -arch arm64 \
    -o libmembraneiosskia.so \
    skia.cpp


# install_name_tool -change @rpath/libskia.so @loader_path/libskia.so libmembraneiosskia.so

echo 'done'

