#!/usr/bin/env sh

watch kubectl -n abcdb get abcdbs,configmap,deployment,service -l 'app.kubernetes.io/name=my-abc-db'

