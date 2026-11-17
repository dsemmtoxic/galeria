#!/usr/bin/env bash
# Restaura a chave de TESTES inclusa como texto no repositorio.
# Nao use esta chave publica para assinaturas de producao.
set -euo pipefail
cd "$(dirname "$0")/.."

chave="signing/galeria-teste.jks"
fonte="signing/galeria-teste.jks.b64"
sha_esperado="2dae1961b94a0f20ceb64ee7e907c5caabb495f5093ba40f08b40676b659c31d"

if [[ ! -s "$fonte" ]]; then
  echo '::error::Arquivo signing/galeria-teste.jks.b64 ausente. Envie-o ao repositorio.'
  exit 1
fi

mkdir -p signing
if [[ ! -s "$chave" ]]; then
  base64 --decode "$fonte" > "$chave.tmp"
  mv "$chave.tmp" "$chave"
  echo 'Chave de testes reconstruida a partir do arquivo .b64.'
else
  echo 'Chave de testes ja presente. Conferindo integridade.'
fi

if ! echo "$sha_esperado  $chave" | sha256sum --check --status; then
  echo '::error::A chave de testes nao corresponde a esta versao do projeto.'
  exit 1
fi

if ! keytool -list -keystore "$chave" -storepass 'galeria-teste-2026' -alias 'galeria' >/dev/null; then
  echo '::error::Keystore presente, mas senha ou alias invalidos.'
  exit 1
fi

echo 'Chave de testes verificada; assinatura Gradle pronta.'
