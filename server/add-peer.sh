#!/usr/bin/env bash
# ==============================================================================
# WireShield VPN - Add Client Peer Script
# Generates a new client WireGuard peer on the server, updates wg0, and outputs .conf
# ==============================================================================

set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage: sudo ./add-peer.sh <client-identifier> [optional-client-ip]"
    echo "Example: sudo ./add-peer.sh phone-alice 10.66.66.5"
    exit 1
fi

CLIENT_NAME=$1
WG_CONF="/etc/wireguard/wg0.conf"
SERVER_PUB_KEY=$(cat /etc/wireguard/server.public)
ENDPOINT_HOST=$(curl -s -4 https://ifconfig.me || echo "127.0.0.1")
ENDPOINT_PORT="51820"
DNS="1.1.1.1, 1.0.0.1"

# Determine next available IP if not provided
if [[ $# -ge 2 ]]; then
    CLIENT_IP="$2/32"
else
    # Find highest assigned IP in wg0.conf
    LAST_OCTET=$(grep -oP 'AllowedIPs\s*=\s*10\.66\.66\.\K[0-9]+' "$WG_CONF" | sort -n | tail -n1 || echo 1)
    NEXT_OCTET=$((LAST_OCTET + 1))
    CLIENT_IP="10.66.66.${NEXT_OCTET}/32"
fi

# Generate client key pair & optional preshared key
CLIENT_PRIV_KEY=$(wg genkey)
CLIENT_PUB_KEY=$(echo "$CLIENT_PRIV_KEY" | wg pubkey)
PRESHARED_KEY=$(wg genpsk)

# Append peer to live WireGuard interface without downtime
wg set wg0 peer "$CLIENT_PUB_KEY" preshared-key <(echo "$PRESHARED_KEY") allowed-ips "$CLIENT_IP"

# Append peer to wg0.conf persistent storage
cat << EOF >> "$WG_CONF"

# Peer: $CLIENT_NAME
[Peer]
PublicKey = $CLIENT_PUB_KEY
PresharedKey = $PRESHARED_KEY
AllowedIPs = $CLIENT_IP
EOF

# Output client configuration file
CLIENT_CONF="${CLIENT_NAME}.conf"
cat << EOF > "$CLIENT_CONF"
[Interface]
PrivateKey = $CLIENT_PRIV_KEY
Address = $CLIENT_IP
DNS = $DNS
MTU = 1420

[Peer]
PublicKey = $SERVER_PUB_KEY
PresharedKey = $PRESHARED_KEY
Endpoint = $ENDPOINT_HOST:$ENDPOINT_PORT
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
EOF

chmod 600 "$CLIENT_CONF"

echo "========================================================"
echo " Client Peer '$CLIENT_NAME' Created Successfully!      "
echo "========================================================"
echo "Allocated IP : $CLIENT_IP"
echo "Public Key   : $CLIENT_PUB_KEY"
echo "Config File  : $CLIENT_CONF"
echo ""
echo "QR Code (scan directly in WireShield app):"
if command -v qrencode &> /dev/null; then
    qrencode -t ansiutf8 < "$CLIENT_CONF"
fi
echo "========================================================"
