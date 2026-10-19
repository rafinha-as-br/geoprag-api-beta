#!/usr/bin/env bash
# Recria o túnel adb reverse do celular para a API local (ver docs/ambiente-local.md).
# Uso: scripts/adb-reverse.sh [porta]   (padrão: 8080)
set -euo pipefail

porta="${1:-8080}"

if ! adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
  echo "Nenhum celular autorizado. Conecte o cabo e aceite a depuração USB (adb devices)." >&2
  exit 1
fi

adb reverse "tcp:${porta}" "tcp:${porta}"
adb reverse --list
