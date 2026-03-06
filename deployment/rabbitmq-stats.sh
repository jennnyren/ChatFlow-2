#!/bin/bash
# rabbitmq-stats.sh
# Polls RabbitMQ management API and logs queue metrics

RABBIT_HOST="${RABBIT_HOST:-localhost}"
RABBIT_PORT="${RABBIT_PORT:-15672}"
RABBIT_USER="${RABBIT_USER:-guest}"
RABBIT_PASS="${RABBIT_PASS:-guest}"
INTERVAL="${INTERVAL:-5}"           # seconds between polls
LOG_FILE="rabbitmq-metrics.log"

echo "Timestamp,Queue,Depth,PublishRate,DeliverRate,Consumers" | tee "$LOG_FILE"

while true; do
  TIMESTAMP=$(date '+%Y-%m-%dT%H:%M:%S')

  # Fetch all queue stats
  RESPONSE=$(curl -s -u "$RABBIT_USER:$RABBIT_PASS" \
    "http://$RABBIT_HOST:$RABBIT_PORT/api/queues")

  if [ -z "$RESPONSE" ] || [ "$RESPONSE" = "null" ]; then
    echo "$TIMESTAMP,ERROR: could not reach RabbitMQ" | tee -a "$LOG_FILE"
    sleep "$INTERVAL"
    continue
  fi

  # Parse each queue: name, depth, publish rate, deliver rate, consumer count
  echo "$RESPONSE" | python3 -c "
import sys, json
data = json.load(sys.stdin)
timestamp = '$TIMESTAMP'
for q in data:
    name         = q.get('name', '')
    depth        = q.get('messages', 0)
    pub_rate     = q.get('message_stats', {}).get('publish_details', {}).get('rate', 0.0)
    deliver_rate = q.get('message_stats', {}).get('deliver_details', {}).get('rate', 0.0)
    consumers    = q.get('consumers', 0)
    print(f'{timestamp},{name},{depth},{pub_rate:.2f},{deliver_rate:.2f},{consumers}')
" | tee -a "$LOG_FILE"

  sleep "$INTERVAL"
done
