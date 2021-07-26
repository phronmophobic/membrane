#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`"

auth="Authorization: token ${GITHUB_TOKEN}"
accept="Accept: application/vnd.github.v3+json"

if ! curl --fail --location --silent --show-error --header "${auth}" --header "${accept}" https://api.github.com/repos/phronmophobic/membrane/releases/tags/${GITHUB_SHA} > release.json ; then
  echo "> Creating release ${GITHUB_SHA}"
  curl --fail --location --silent --show-error --header "${auth}" --header "${accept}" --request POST \
    --data "{\"tag_name\":\"${GITHUB_SHA}\",\"name\":\"${GITHUB_SHA}\"}" \
    https://api.github.com/repos/phronmophobic/membrane/releases > release.json
else
  echo "> Release ${GITHUB_SHA} exists"
  cat release.json
fi

[[ $(cat release.json | grep '"upload_url"') =~ https://.*/assets ]]
upload_url="${BASH_REMATCH[0]}?name=${artifact_name}"
rm release.json

echo "Uploading ${artifact_name} to ${upload_url}"
curl --fail --location --silent --show-error --header "${auth}" --header "${accept}" --header "Content-Type: application/octet-stream" --request POST --data-binary "@${artifact_name}" ${upload_url}
