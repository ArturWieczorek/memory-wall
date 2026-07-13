#!/usr/bin/env bash
# Run the Memory Wall backend for a real (preprod) deployment.
#
# The ONLY thing you must supply is your Blockfrost project id, as an environment variable, so the
# secret never lives in a committed file:
#
#     export WALL_BACKEND_PROJECT_ID=preprod...     # your key (get one free at blockfrost.io)
#     ./infra/run-backend.sh
#
# Everything else has a sensible default for the public preprod wall; override any of them by
# exporting the matching variable before running. The server binds to localhost only - expose it to
# the internet with a tunnel (see infra/HOSTING.md), never by opening a port on your router.
#
# OPTIONAL fee/pin tier (off by default). Export these before running to turn it on:
#   WALL_FEE_ADDRESS=addr_test1...   a PUBLIC address you own; tips land there (turns the tier on)
#   WALL_MIN_FEE_LOVELACE=2000000    minimum tip to post (lovelace; 1 ADA = 1,000,000)
#   WALL_PIN_FEE_LOVELACE=5000000    tip at/above which a post is pinned
#   WALL_MAX_PINNED=3                pin slots (optional)   WALL_PIN_DURATION_SECONDS=604800  (7 days)
# See infra/HOSTING.md ("Optional: turn on the fee / pin tier") for what to expect.
set -euo pipefail

# --- required: the provider key (a secret; never commit it) ---------------------------------------
if [ -z "${WALL_BACKEND_PROJECT_ID:-}" ]; then
  cat >&2 <<'EOF'
ERROR: WALL_BACKEND_PROJECT_ID is not set.

This is your Blockfrost project id (a secret). Get a free one:
  1. Sign up at https://blockfrost.io
  2. Add a project with network = Cardano Preprod
  3. Copy the project id (it starts with "preprod")

Then set it and re-run (do NOT paste it into a committed file):
  export WALL_BACKEND_PROJECT_ID=preprod...
  ./infra/run-backend.sh
EOF
  exit 1
fi

# --- overridable defaults for the public preprod wall ---------------------------------------------
export WALL_BACKEND_URL="${WALL_BACKEND_URL:-https://cardano-preprod.blockfrost.io/api/v0/}"
# CORS: the browser origin of the hosted UI. An origin is scheme + host only (NO path). The default
# is this repo's GitHub Pages site; change it if you host the UI elsewhere.
export WALL_CORS_ORIGINS="${WALL_CORS_ORIGINS:-https://arturwieczorek.github.io}"

# --- go -------------------------------------------------------------------------------------------
cd "$(dirname "$0")/.."
echo "Starting Memory Wall backend:"
echo "  provider url : ${WALL_BACKEND_URL}"
echo "  project id   : set (hidden)"
echo "  cors origins : ${WALL_CORS_ORIGINS}"
echo "  bind : port  : ${WALL_BIND:-127.0.0.1} : ${WALL_PORT:-8090}  (localhost only; expose via a tunnel)"
echo "  health check : curl http://127.0.0.1:${WALL_PORT:-8090}/api/health"
echo
exec ./gradlew run
