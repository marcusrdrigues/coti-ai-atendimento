#!/usr/bin/env bash
# Remove um conhecimento da base: o registro em "documento" e todos os trechos dele na "vector_store".
#
# A remoção de documento por API está fora do MVP (RF13); este é o caminho administrativo. Usa o papel dono do
# esquema (CONHECIMENTO_FLYWAY_USER / CONHECIMENTO_FLYWAY_PASSWORD, lidos do .env), porque a aplicação só tem DML
# e o VACUUM exige ser dono da tabela. O VACUUM final é necessário: o índice HNSW guarda entradas de linhas
# apagadas até ele rodar, e isso degrada a busca (specs/04-plan.md, spike T037).
#
# Uso:   scripts/remover-conhecimento.sh <id-do-documento> [--sim]
# Sem --sim, mostra o que será removido e pede confirmação. A remoção não tem volta.
set -euo pipefail

ID="${1:-}"
CONFIRMAR="${2:-}"
if [[ ! "$ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
  echo "Uso: $0 <id-do-documento (UUID)> [--sim]" >&2
  exit 2
fi

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "Arquivo .env não encontrado na raiz do projeto." >&2; exit 2; }

# Lê só as duas variáveis necessárias, sem executar o conteúdo do .env e sem imprimir valores.
valor() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | tr -d '\r'; }
USUARIO="$(valor CONHECIMENTO_FLYWAY_USER)"
export PGPASSWORD="$(valor CONHECIMENTO_FLYWAY_PASSWORD)"
[[ -n "$USUARIO" && -n "$PGPASSWORD" ]] || { echo "Defina CONHECIMENTO_FLYWAY_USER e CONHECIMENTO_FLYWAY_PASSWORD no .env." >&2; exit 2; }

# O id vai como variável do psql (:'id'), nunca concatenado no SQL.
psql_banco() { docker compose exec -T -e PGPASSWORD postgres psql -v ON_ERROR_STOP=1 -U "$USUARIO" -d coti_conhecimento "$@"; }

echo "Documento a remover:"
psql_banco -v id="$ID" <<'SQL'
SELECT d.titulo, d.status, d.quantidade_chunks AS trechos_declarados,
       (SELECT count(*) FROM vector_store v WHERE v.metadata ->> 'documentoId' = :'id') AS trechos_na_base
  FROM documento d WHERE d.id = CAST(:'id' AS uuid);
SQL

if [[ "$CONFIRMAR" != "--sim" ]]; then
  read -r -p "Remover este documento e os seus trechos, sem volta? (digite sim) " resposta
  [[ "$resposta" == "sim" ]] || { echo "Cancelado."; exit 1; }
fi

psql_banco -v id="$ID" <<'SQL'
BEGIN;
WITH t AS (DELETE FROM vector_store WHERE metadata ->> 'documentoId' = :'id' RETURNING 1)
  SELECT count(*) AS trechos_removidos FROM t;
WITH d AS (DELETE FROM documento WHERE id = CAST(:'id' AS uuid) RETURNING 1)
  SELECT count(*) AS documentos_removidos FROM d;
COMMIT;
SQL

# VACUUM não roda dentro de transação.
psql_banco -c 'VACUUM (ANALYZE) vector_store'
echo "Concluído."
