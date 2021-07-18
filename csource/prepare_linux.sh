#!/bin/bash
set -o errexit -o nounset -o pipefail

apt-get update -y
apt-get install fontconfig libfontconfig1-dev curl zip -y

apt-get install gcc-9 g++-9 -y
update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-9 60 --slave /usr/bin/g++ g++ /usr/bin/g++-9
update-alternatives --config gcc

