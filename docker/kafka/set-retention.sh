#!/bin/bash
# Keeps the changes of catalog.changed (and the ones parked on catalog.changed.DLT) for 30 days, so sync-service can be
# offline that long and still catch up. The broker default in docker-compose.yml only applies to topics created after
# it, so this sets it on the topics that already exist. Safe to run again.
CONTAINER=${KAFKA_CONTAINER:-pcshop-kafka}
BOOTSTRAP=${KAFKA_BOOTSTRAP:-localhost:9092}
RETENTION_MS=${RETENTION_MS:-2592000000}
for topic in catalog.changed catalog.changed.DLT; do
  MSYS_NO_PATHCONV=1 docker exec "$CONTAINER" /opt/kafka/bin/kafka-configs.sh --bootstrap-server "$BOOTSTRAP" \
    --alter --entity-type topics --entity-name "$topic" --add-config "retention.ms=$RETENTION_MS"
done
