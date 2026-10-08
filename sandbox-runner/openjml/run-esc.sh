#!/usr/bin/env bash
# Run OpenJML's extended static checker on Java files, sandboxed.
#
#   sandbox-runner/openjml/run-esc.sh relay-formal/examples/openjml/Discount.java
#
# No network, read-only source mount, CPU and memory capped, hard wall-clock limit: the same
# constraints every Relay verifier runs under (sandbox-runner/README.md).
set -euo pipefail

IMAGE="${OPENJML_IMAGE:-relay/openjml:21.0.28}"
TIMEOUT_SECONDS="${OPENJML_TIMEOUT:-120}"

if [[ $# -eq 0 ]]; then
  echo "usage: $0 FILE.java [FILE.java ...]" >&2
  exit 2
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  docker build -t "$IMAGE" "$(dirname "$0")"
fi

dir="$(cd "$(dirname "$1")" && pwd)"
files=()
for f in "$@"; do
  files+=("$(basename "$f")")
done

exec timeout "$TIMEOUT_SECONDS" docker run --rm \
  --network none \
  --read-only --tmpfs /tmp \
  --cpus 2 --memory 2g \
  -v "$dir:/work:ro" \
  "$IMAGE" --esc "${files[@]}"
