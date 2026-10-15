#!/usr/bin/env bash
# Prints an access token for one of the demo users, for curl and for MCP clients.
#
#   scripts/sentinel-token.sh alex.analyst alex
#   export SENTINEL_TOKEN=$(scripts/sentinel-token.sh sam.senior sam)
#   curl -H "Authorization: Bearer $SENTINEL_TOKEN" http://localhost:8083/api/alerts
#
# It uses the password grant of the sentinel-cli client, which exists in the demo realm only
# (db/keycloak/sentinel-realm.json); a real deployment would use the device or browser flow.
# The token lasts 8 hours - long enough for an MCP session - and carries the user's roles, so
# whatever the client does is checked, masked and audited as that person.
set -euo pipefail

user=${1:?usage: sentinel-token.sh <username> <password>}
password=${2:?usage: sentinel-token.sh <username> <password>}
issuer=${SENTINEL_ISSUER_URI:-http://localhost:8080/realms/sentinel}

response=$(curl -sS --fail-with-body \
  -d grant_type=password -d client_id=sentinel-cli \
  --data-urlencode "username=$user" --data-urlencode "password=$password" \
  "$issuer/protocol/openid-connect/token") || { echo "$response" >&2; exit 1; }

echo "$response" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
