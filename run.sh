#!/usr/bin/env bash
# One command to rebuild everything from the current data.
#   ./run.sh                      -> re-analyse what is already in the database
#   ./run.sh path/to/export.json  -> import that file first, then analyse
set -e
cd "$(dirname "$0")"

PGBIN=/opt/homebrew/opt/postgresql@14/bin
export PGPORT=5433 PGHOST=/tmp PGDATABASE=wifi_survey

# Port 5432 is taken by another Postgres on this Mac, so ours runs on 5433.
if ! $PGBIN/pg_isready -p 5433 -h /tmp >/dev/null 2>&1; then
  echo "starting postgres on 5433..."
  $PGBIN/pg_ctl -D /opt/homebrew/var/postgresql@14 -o "-p 5433 -k /tmp" \
                -l .pg/server.log start
  sleep 3
fi

if [ -n "$1" ]; then
  echo "importing $1"
  .venv/bin/python scripts/import_session.py "$1"
fi

.venv/bin/python analysis/pipeline.py
echo
echo "opening the dashboard..."
open dashboard/index.html
