#!/usr/bin/env bash
# ==============================================================================
# WireShield Open-Source VPN - Linux WireGuard Server Automated Installer
# Target OS: Ubuntu 22.04/24.04 LTS, Debian 12, CentOS/Rocky 9
# ==============================================================================

set -euo pipefail

echo "========================================================"
echo "    WireShield Secure WireGuard Server Installer       "
echo "========================================================"

if [[ $EUID -ne 0 ]]; then
   echo "[ERROR] This script must be run as root (sudo)." 1>&2
   exit 1
fi

WG_PORT=${1:-51820}
SERVER_SUBNET="10.66.66.1/24"
DNS_SERVERS="1.1.1.1, 1.0.0.1"

# 1. Detect default network interface
DEFAULT_IFACE=$(ip -4 route ls | grep default | grep -Po '(?<=dev )(\S+)' | head -n1)
echo "[INFO] Detected primary outbound network interface: $DEFAULT_IFACE"

# 2. Install WireGuard and network tools
echo "[INFO] Updating repositories and installing wireguard, iptables, qrencode..."
if command -v apt-get &> /dev/null; then
    apt-get update -q
    apt-get install -y -q wireguard iptables qrencode curl ufw
elif command -v dnf &> /dev/null; then
    dnf install -y epel-release elrepo-release || true
    dnf install -y wireguard-tools iptables qrencode curl
fi

# 3. Enable IP Forwarding and kernel hardening
echo "[INFO] Applying kernel networking configuration..."
cat << 'EOF' > /etc/sysctl.d/99-wireguard-hardening.conf
net.ipv4.ip_forward = 1
net.ipv6.conf.all.forwarding = 1
net.ipv4.conf.all.rp_filter = 1
net.ipv4.conf.default.rp_filter = 1
net.ipv4.tcp_syncookies = 1
net.ipv4.conf.all.accept_redirects = 0
net.ipv4.conf.all.send_redirects = 0
net.ipv4.conf.all.accept_source_route = 0
net.ipv6.conf.all.accept_redirects = 0
EOF
sysctl -p /etc/sysctl.d/99-wireguard-hardening.conf > /dev/null

# 4. Generate Server Keys
mkdir -p /etc/wireguard
chmod 700 /etc/wireguard

SERVER_PRIV_KEY=$(wg genkey)
SERVER_PUB_KEY=$(echo "$SERVER_PRIV_KEY" | wg pubkey)

echo "$SERVER_PRIV_KEY" > /etc/wireguard/server.private
echo "$SERVER_PUB_KEY" > /etc/wireguard/server.public
chmod 600 /etc/wireguard/server.private

# 5. Write WireGuard wg0.conf
echo "[INFO] Generating /etc/wireguard/wg0.conf..."
cat << EOF > /etc/wireguard/wg0.conf
[Interface]
Address = $SERVER_SUBNET
ListenPort = $WG_PORT
PrivateKey = $SERVER_PRIV_KEY
SaveConfig = false

# PostUp NAT Forwarding rules
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o $DEFAULT_IFACE -j MASQUERADE; ip6tables -A FORWARD -i wg0 -j ACCEPT; ip6tables -t nat -A POSTROUTING -o $DEFAULT_IFACE -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o $DEFAULT_IFACE -j MASQUERADE; ip6tables -D FORWARD -i wg0 -j ACCEPT; ip6tables -t nat -D POSTROUTING -o $DEFAULT_IFACE -j MASQUERADE

# Client peers are appended below or dynamically added via add-peer.sh
EOF

chmod 600 /etc/wireguard/wg0.conf

# 6. Enable and Start wireguard service
echo "[INFO] Enabling systemd wg-quick@wg0 service..."
systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0

# 7. Configure Firewall
if command -v ufw &> /dev/null && ufw status | grep -q "active"; then
    echo "[INFO] Configuring UFW firewall rules..."
    ufw allow "$WG_PORT"/udp comment 'WireShield WireGuard'
    ufw reload
fi

PUBLIC_IP=$(curl -s -4 https://ifconfig.me || curl -s -4 https://icanhazip.com || echo "YOUR_SERVER_IP")

echo "========================================================"
echo "    WireShield Server Installation Completed!          "
echo "========================================================"
echo "Server Endpoint : $PUBLIC_IP:$WG_PORT"
echo "Server Public Key: $SERVER_PUB_KEY"
echo "WireGuard Port  : $WG_PORT/UDP"
echo "Interface Subnet: $SERVER_SUBNET"
echo ""
echo "To generate a new client peer profile, run: ./add-peer.sh <client-name>"
echo "========================================================"
