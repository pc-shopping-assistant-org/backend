#!/bin/bash
# Registers (or updates) the Debezium connector that feeds the product search from catalog_db.
# Run it once after `docker compose up -d`; it is safe to run again.
CONNECT=${CONNECT_URL:-http://localhost:18083}
DIR=$(dirname "$0")
until curl -fs "$CONNECT/connectors" > /dev/null; do echo "waiting for Kafka Connect..."; sleep 3; done
curl -s -X PUT "$CONNECT/connectors/catalog-connector/config" -H 'Content-Type: application/json' \
  -d "$(python3 -c "import json,sys; print(json.dumps(json.load(open(sys.argv[1]))['config']))" "$DIR/catalog-connector.json")"
echo
curl -s "$CONNECT/connectors/catalog-connector/status"
echo
