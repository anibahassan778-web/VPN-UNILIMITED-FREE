#!/usr/bin/env bash
# ==============================================================================
# WireShield VPN - Firewall, NAT & Anti-Leak Rules
# ==============================================================================

set -euo pipefail

WAN_IFACE=$(ip -4 route ls | grep default | grep -Po '(?<=dev )(\S+)' | head -n1)
WG_IFACE="wg0"
WG_PORT="51820"
VPN_SUBNET="10.66.66.0/24"

echo "[INFO] Applying Firewall & NAT routing for $WAN_IFACE <-> $WG_IFACE..."

# 1. Allow WireGuard incoming UDP traffic
iptables -A INPUT -p udp --dport "$WG_PORT" -j ACCEPT

# 2. Allow established and related connections
iptables -A INPUT -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
iptables -A FORWARD -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT

# 3. Allow traffic originating from VPN tunnel to be forwarded to WAN
iptables -A FORWARD -i "$WG_IFACE" -o "$WAN_IFACE" -s "$VPN_SUBNET" -j ACCEPT

# 4. NAT Masquerade
iptables -t nat -A POSTROUTING -o "$WAN_IFACE" -s "$VPN_SUBNET" -j MASQUERADE

# 5. Prevent DNS leaking by intercepting or allowing UDP/TCP 53
iptables -A FORWARD -i "$WG_IFACE" -p udp --dport 53 -j ACCEPT
iptables -A FORWARD -i "$WG_IFACE" -p tcp --dport 53 -j ACCEPT

# 6. IPv6 Equivalent Rules
if ip -6 route ls | grep default > /dev/null 2>&1; then
    ip6tables -A INPUT -p udp --dport "$WG_PORT" -j ACCEPT
    ip6tables -A FORWARD -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
    ip6tables -A FORWARD -i "$WG_IFACE" -o "$WAN_IFACE" -j ACCEPT
    ip6tables -t nat -A POSTROUTING -o "$WAN_IFACE" -j MASQUERADE
fi

echo "[SUCCESS] Firewall rules applied."
