#!/bin/bash
# Supervisor: runs container-agent plus N applications in one container.
# PID 1 is tini (see Dockerfile ENTRYPOINT); it reaps zombies and forwards
# SIGTERM/SIGINT to this script.
set -u

# name=command entries. Add as many as you have applications. The agent is just
# another entry. Commands are word-split on purpose (Java flags contain spaces).
SERVICES=(
	"agent=/usr/local/bin/agent"
	"web=java -XX:MaxRAMPercentage=75 -jar /apps/web.jar"
)

pids=()
names=()
got_signal=0

kill_all() {
	local p
	for p in "${pids[@]}"; do
		kill -TERM "$p" 2>/dev/null
	done
	return 0
}
on_signal() {
	got_signal=1
	kill_all
}
trap on_signal TERM INT

for entry in "${SERVICES[@]}"; do
	name="${entry%%=*}"
	cmd="${entry#*=}"
	# shellcheck disable=SC2086
	$cmd &
	pid=$!
	pids+=("$pid")
	names+=("$name")
	echo "supervisor: started $name (pid $pid): $cmd"
done

# Block until the first service exits. An interrupted `wait -n -p` can leave the
# target variable unset, so every reference is null-safe and `set -u` stays on.
dead_pid=""
wait -n -p dead_pid
code=$?

dead_name="?"
for i in "${!pids[@]}"; do
	if [[ "${pids[$i]}" == "${dead_pid:-}" ]]; then
		dead_name="${names[$i]}"
	fi
done

if [[ "$got_signal" -eq 0 ]]; then
	echo "supervisor: $dead_name (pid ${dead_pid:-?}) exited with $code; stopping the rest"
	kill_all
fi

wait
echo "supervisor: all stopped (signal=$got_signal first=$dead_name code=$code)"

# A stop signal is the graceful path -> exit 0, like the agent alone.
# A service dying on its own -> propagate its exit code, so `restart:` works.
if [[ "$got_signal" -eq 1 ]]; then
	exit 0
fi
exit "$code"