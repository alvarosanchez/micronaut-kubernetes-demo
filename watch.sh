#!/usr/bin/env sh

watch -n 1 kubectl -n abcdb get abcdbs,configmap,deployment,service,pod -l 'app.kubernetes.io/name=my-abc-db'

