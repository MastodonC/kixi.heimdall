#!/usr/bin/env bash

/root/download-secrets.sh

set -o errexit
set -o nounset
set -o xtrace

SANDBOX=${MESOS_SANDBOX:-"."}

exec java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$SANDBOX -XX:ErrorFile=$SANDBOX/hs_err_pid_%p.log -XX:+UseG1GC -Xloggc:$SANDBOX/gc.log -XX:+PrintGCCause -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=3 -XX:GCLogFileSize=2 ${JAVA_OPTS:-} -jar /srv/kixi.heimdall.jar --profile $PROFILE
