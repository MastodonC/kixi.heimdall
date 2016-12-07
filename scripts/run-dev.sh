#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o xtrace

export ENVIRONMENT=development
exec java -jar /srv/kixi.heimdall.jar --profile dev-docker
