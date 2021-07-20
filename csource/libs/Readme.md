Freetype


```
wget https://download.savannah.gnu.org/releases/freetype/freetype-2.10.1.tar.gz
tar xvf freetype-2.10.1.tar.gz 
export MACOSX_DEPLOYMENT_TARGET=10.10
./configure --without-harfbuzz
make
```

Glfw



```

wget https://github.com/glfw/glfw/releases/download/3.3/glfw-3.3.bin.MACOS.zip
unzip glfw-3.3.bin.MACOS.zip 
```


zlib

```

wget https://www.zlib.net/zlib1211.zip
cd zlib-1.2.11/


export MACOSX_DEPLOYMENT_TARGET=10.10
./configure
make
```


png

```


wget 'http://prdownloads.sourceforge.net/libpng/libpng-1.6.37.tar.xz?download'
tar xvf libpng-1.6.37.tar.xz\?download 
export MACOSX_DEPLOYMENT_TARGET=10.10
./configure
make



```


bz2

```

wget 'https://sourceware.org/pub/bzip2/bzip2-1.0.8.tar.gz'
tar xvf bzip2-1.0.8.tar.gz 
export MACOSX_DEPLOYMENT_TARGET=10.10
make

```

Soil
 
```

git clone git@github.com:phronmophobic/libSOIL.git
export MACOSX_DEPLOYMENT_TARGET=10.10
make

```

Skia

```sh

# install depot tools

export MACOSX_DEPLOYMENT_TARGET=10.10

export IOS_MIN_TARGET=14.1
git clone 'https://chromium.googlesource.com/chromium/tools/depot_tools.git'
export PATH="${PWD}/depot_tools:${PATH}"

fetch skia
cd skia
python2 tools/git-sync-deps

# on linux
# tools/install_dependencies.sh

# mac/linux
bin/gn gen out/Static --args='target_cpu="x86_64" is_official_build=true skia_use_system_libwebp=false skia_use_system_libjpeg_turbo=false skia_use_system_libpng=false skia_use_system_harfbuzz=false skia_use_system_icu=false skia_use_system_expat=false skia_use_system_zlib=false cc="clang" cxx="clang++"'

#ios sim
# bin/gn gen out/ios64  --args='target_os="ios" is_official_build=true skia_use_system_expat=false skia_use_system_libjpeg_turbo=false skia_use_system_libpng=false skia_use_system_libwebp=false skia_use_system_zlib=false'

# ios device

#  add is_component_build=true for shared
# shared library doesn't export all symbols needed, but ios app needs shared library. need to build both
# may need to comment out ios_min app in BUILD.gn
bin/gn gen out/ios64Static --args='target_os="ios" is_official_build=true skia_use_system_expat=false skia_use_system_harfbuzz=false skia_use_system_icu=false skia_use_system_libjpeg_turbo=false skia_use_system_libpng=false skia_use_system_libwebp=false skia_use_system_zlib=false ios_min_target="14.1" skia_enable_skottie=false skia_use_metal=true'

bin/gn gen out/ios64Shared --args='target_os="ios" is_official_build=true is_component_build=true skia_use_system_expat=false skia_use_system_harfbuzz=false skia_use_system_icu=false skia_use_system_libjpeg_turbo=false skia_use_system_libpng=false skia_use_system_libwebp=false skia_use_system_zlib=false ios_min_target="14.1" skia_enable_skottie=false skia_use_metal=true '





ninja -C out/Static/

```


Linux Glad


Generated from http://glad.dav1d.de/#profile=compatibility&specification=gl&api=gl%3D3.3&api=gles1%3Dnone&api=gles2%3Dnone&api=glsc2%3Dnone&language=c&loader=on
Included in libs/glad


Linux glfw

``


wget https://github.com/glfw/glfw/releases/download/3.3.2/glfw-3.3.2.zip

cmake -DBUILD_SHARED_LIBS=ON .
make


```

