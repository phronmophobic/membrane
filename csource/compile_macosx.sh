#!/bin/bash

set -e
set -x

export SDKROOT="/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk"

gcc -I ./libs/freetype/include \
    -I './libs/libSOIL/' \
    -L './libs/libSOIL' \
    -framework OpenGL \
    -framework Cocoa \
    -framework IOKit \
    -framework CoreFoundation \
    -framework CoreVideo \
    -framework AppKit \
    -framework CoreGraphics \
    -framework CoreServices \
    -framework Foundation \
    -lSOIL \
    -mmacosx-version-min=10.10 \
    -dynamiclib \
    ./libs/zlib-1.2.11/libz.a \
    ./libs/libpng-1.6.37/.libs/libpng16.a \
    ./libs/bzip2-1.0.8/libbz2.a \
    ./libs/freetype-2.10.1/objs/.libs/libfreetype.a \
    -o libmembrane.dylib shader_utils.cpp platform.c vertex-attribute.c vector.c vertex-buffer.c texture-font.c utf8-utils.c distance-field.c edtaa3func.c texture-atlas.c mat4.c shader.c text.cpp image.cpp

# install_name_tool -add_rpath '@executable_path/' libwebsheet.dylib
# install_name_tool -add_rpath '@loader_path/' libwebsheet.dylib
# install_name_tool -change /opt/local/lib/libfreetype.6.dylib '@rpath/libfreetype.dylib' libwebsheet.dylib
# install_name_tool -change '@rpath/libglfw.3.dylib' '@rpath/libglfw.dylib' libwebsheet.dylib

# test
gcc -I ~/workspace/freetype-2.10.1/include \
    -I ./libs/glfw-3.3.bin.MACOS/include \
    -mmacosx-version-min=10.10 \
    -framework OpenGL \
    -framework Cocoa \
    -framework IOKit \
    -framework CoreFoundation \
    -framework CoreVideo \
    -framework AppKit \
    -framework CoreGraphics \
    -framework CoreServices \
    -framework Foundation \
    -DTESTING \
    ./libs/glfw-3.3.bin.MACOS/lib-macos/libglfw3.a \
    ./libs/zlib-1.2.11/libz.a \
    ./libs/libpng-1.6.37/.libs/libpng16.a \
    ./libs/bzip2-1.0.8/libbz2.a \
    ./libs/freetype-2.10.1/objs/.libs/libfreetype.a \
    -o testtext \
    shader_utils.cpp platform.c vertex-attribute.c vector.c vertex-buffer.c texture-font.c utf8-utils.c distance-field.c edtaa3func.c texture-atlas.c mat4.c shader.c text.cpp testtext.cpp

clang++ \
    -I ./libs/glfw-3.3.bin.MACOS/include \
    -I ./libs/skia \
    -I ./libs/skia/include/gpu \
    -I ./libs/skia/include/gpu/gl \
    -I ./libs/skia/include/core \
    -I ./libs/skia/include/utils \
    -I ./libs/skia/include/private \
    -framework OpenGL \
    -framework Cocoa \
    -framework IOKit \
    -framework CoreFoundation \
    -framework CoreVideo \
    -framework AppKit \
    -framework CoreGraphics \
    -framework CoreServices \
    -framework Foundation \
    -mmacosx-version-min=10.10 \
    -dynamiclib \
    ./libs/skia/out/Static/libskia.a \
    -std=c++17 \
    -o libmembraneskia.dylib \
    skia.cpp


# test
clang++ -I ./libs/glfw-3.3.bin.MACOS/include \
    -I ./libs/skia \
    -I ./libs/skia/include/gpu \
    -I ./libs/skia/include/gpu/gl \
    -I ./libs/skia/include/core \
    -I ./libs/skia/include/utils \
    -I ./libs/skia/include/private \
    -framework OpenGL \
    -framework Cocoa \
    -framework IOKit \
    -framework CoreFoundation \
    -framework CoreVideo \
    -framework AppKit \
    -framework CoreGraphics \
    -framework CoreServices \
    -framework Foundation \
    -mmacosx-version-min=10.10 \
    -DTESTING \
    ./libs/glfw-3.3.bin.MACOS/lib-macos/libglfw3.a \
    ./libs/skia/out/Static/libskia.a \
    -std=c++17 \
    -o testglfw \
    testglfw.cpp skia.cpp



cp libmembrane.dylib ../resources/darwin
cp libmembraneskia.dylib ../resources/darwin

echo 'done'
