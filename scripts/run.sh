#!/usr/bin/env bash

/root/download-secrets.sh

set -o errexit
set -o nounset
set -o xtrace

exec java -jar /srv/kixi.heimdall.jar --profile production
