#!/usr/bin/env bash
# ==============================================================================
# WireShield VPN - Revoke Client Peer Script
# Safely removes a client peer by Public Key from runtime wg0 and wg0.conf
# ==============================================================================

set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage: sudo ./revoke-peer.sh <client-public-key-or-client-name>"
    exit 1
fi

TARGET=$1
WG_CONF="/etc/wireguard/wg0.conf"

# Remove from running kernel interface immediately
if [[ "$TARGET" =~ = ]]; then
    PUB_KEY="$TARGET"
else
    # Find public key matching client comment in wg0.conf
    PUB_KEY=$(awk -v name="$TARGET" '
        $0 ~ "# Peer: " name { found=1; next }
        found && $1 == "PublicKey" { print $3; exit }
    ' "$WG_CONF")
fi

if [[ -n "${PUB_KEY:-}" ]]; then
    echo "[INFO] Removing peer $PUB_KEY from active wg0 tunnel..."
    wg set wg0 peer "$PUB_KEY" remove || true
fi

# Remove from wg0.conf file
echo "[INFO] Cleaning up $WG_CONF..."
python3 -c "
import sys, re

conf_path = '$WG_CONF'
target = '$TARGET'

with open(conf_path, 'r') as f:
    content = f.read()

# Pattern matching peer block
pattern = r'(# Peer:\s*' + re.escape(target) + r'\s*\n)?\[Peer\][^\[]+PublicKey\s*=\s*' + re.escape(target if '=' in target else '.*') + r'[^\[]*'
cleaned = re.sub(pattern, '', content, flags=re.MULTILINE)

with open(conf_path, 'w') as f:
    f.write(cleaned.strip() + '\n')
" 2>/dev/null || true

echo "[SUCCESS] Peer revoked successfully."
