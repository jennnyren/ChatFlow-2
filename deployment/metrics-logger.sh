#!/bin/bash
# metrics-logger.sh
# Logs CPU, memory, and network I/O on this instance at regular intervals

INTERVAL="${INTERVAL:-5}"           # seconds between snapshots
LOG_FILE="system-metrics.log"
# run
# ip link show
# or
# ifconfig
# to decide if it is eth0 or ens5 for following value
NETWORK_IFACE="${NETWORK_IFACE:-eth0}"

echo "Timestamp,CPU%,MemUsedMB,MemTotalMB,NetRxKB,NetTxKB" | tee "$LOG_FILE"

# Capture initial network counters
read_net() {
  grep "$NETWORK_IFACE" /proc/net/dev | awk '{print $2, $10}'
}

PREV=$(read_net)
PREV_RX=$(echo "$PREV" | awk '{print $1}')
PREV_TX=$(echo "$PREV" | awk '{print $2}')

while true; do
  sleep "$INTERVAL"
  TIMESTAMP=$(date '+%Y-%m-%dT%H:%M:%S')

  # CPU: idle % from /proc/stat, convert to usage %
  CPU_LINE=$(grep '^cpu ' /proc/stat)
  IDLE=$(echo "$CPU_LINE" | awk '{print $5}')
  TOTAL=$(echo "$CPU_LINE" | awk '{s=0; for(i=2;i<=NF;i++) s+=$i; print s}')
  CPU_PCT=$(awk "BEGIN {printf \"%.1f\", (1 - $IDLE/$TOTAL) * 100}")

  # Memory (MB)
  MEM_TOTAL=$(grep MemTotal /proc/meminfo | awk '{print int($2/1024)}')
  MEM_FREE=$(grep MemAvailable /proc/meminfo | awk '{print int($2/1024)}')
  MEM_USED=$((MEM_TOTAL - MEM_FREE))

  # Network delta (KB since last interval)
  CURR=$(read_net)
  CURR_RX=$(echo "$CURR" | awk '{print $1}')
  CURR_TX=$(echo "$CURR" | awk '{print $2}')
  NET_RX=$(( (CURR_RX - PREV_RX) / 1024 ))
  NET_TX=$(( (CURR_TX - PREV_TX) / 1024 ))
  PREV_RX=$CURR_RX
  PREV_TX=$CURR_TX

  echo "$TIMESTAMP,$CPU_PCT,$MEM_USED,$MEM_TOTAL,$NET_RX,$NET_TX" | tee -a "$LOG_FILE"
done
