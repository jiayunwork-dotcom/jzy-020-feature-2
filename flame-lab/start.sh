#!/usr/bin/env bash
# One command: build the jar, build the container image, start the service.
#   ./start.sh
# Requires: JDK 17 + Maven on the host (for the jar build) and Docker with a
# local JDK/JRE 17 image (see BASE_IMAGE in the Dockerfile).
set -euo pipefail
cd "$(dirname "$0")"

mvn -q -DskipTests package
docker build -t flame-lab .
exec docker run --rm -p "${PORT:-8080}:8080" flame-lab
