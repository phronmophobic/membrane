#!/bin/bash
set -o errexit -o nounset -o pipefail

apt-get update -y
apt-get install fontconfig libfontconfig1-dev curl zip clang -y

# update-alternatives --install /usr/bin/cc cc /usr/bin/clang-3.5 100
# update-alternatives --install /usr/bin/c++ c++ /usr/bin/clang++-3.5 100
