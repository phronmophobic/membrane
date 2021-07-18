#!/bin/bash
set -o errexit -o nounset -o pipefail

apt-get update -y
apt-get install fontconfig libfontconfig1-dev curl zip wget -y

bash -c "$(wget -O - https://apt.llvm.org/llvm.sh)"

ls /usr/bin/clang*

# update-alternatives --install /usr/bin/cc cc /usr/bin/clang-3.5 100
# update-alternatives --install /usr/bin/c++ c++ /usr/bin/clang++-3.5 100
