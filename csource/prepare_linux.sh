#!/bin/bash
set -o errexit -o nounset -o pipefail

apt-get update -y
apt-get install fontconfig libfontconfig1-dev curl zip -y
