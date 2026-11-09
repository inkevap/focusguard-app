#!/usr/bin/env bash
# Crea (una sola vez) la clave con la que se firman los APK de FocusGuard. Guárdala fuera del repositorio y haz copia:
# si la pierdes, no podrás actualizar la app instalada (habría que desinstalarla y perder sus datos).
set -euo pipefail
destino="${1:-$HOME/focusguard-release.jks}"
[ -e "$destino" ] && { echo "Ya existe $destino: no lo piso." >&2; exit 1; }
read -r -s -p "Contraseña para la clave (mínimo 8 caracteres): " clave; echo
keytool -genkeypair -v -keystore "$destino" -alias focusguard -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$clave" -keypass "$clave" -dname "CN=FocusGuard, O=Uso personal, C=ES"
echo "Hecho. Para firmar: FG_KEYSTORE_FILE=$destino FG_KEYSTORE_PASSWORD=... ./gradlew :app:assembleRelease -PserverUrl=https://tu-dominio"
