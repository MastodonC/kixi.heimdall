#!/usr/bin/env bash

/root/download-secrets.sh

set -o errexit
set -o nounset
set -o xtrace

exec java ${JAVA_OPTS:-} -jar /srv/kixi.heimdall.jar --profile $PROFILE
