#!/bin/sh
# Launch N dev clients side by side (default 2) for duel testing.
# Each is a full RuneLite client with the plugin sideloaded; log a
# different account into each. Logs land in build/client-N.log.
set -e
cd "$(dirname "$0")/.."
N="${1:-2}"
./gradlew -q clientScript
for i in $(seq 1 "$N"); do
	nohup build/run-client.sh > "build/client-$i.log" 2>&1 &
	echo "client $i started (pid $!, log build/client-$i.log)"
	sleep 2
done
