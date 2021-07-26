#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`"

auth="Authorization: token ${GITHUB_TOKEN}"
accept="Accept: application/vnd.github.v3+json"

if ! curl --fail --location --silent --show-error --header "${auth}" --header "${accept}" https://api.github.com/repos/phronmophobic/membrane/releases/tags/${GITHUB_SHA} > release.json ; then
  exit 0
fi

if grep -q "$artifact_name" release.json; then
    echo "> Artifact exists: $artifact_name, stopping"
    rm release.json
    exit 1
fi

rm release.json
