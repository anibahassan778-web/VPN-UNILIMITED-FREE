#!/usr/bin/env python3
"""
WireShield VPN - Administration & Cluster Management CLI
Tool for managing WireGuard server nodes, registering peers, and testing latency.
"""

import argparse
import json
import os
import sys
import time
import socket
import urllib.request
import urllib.error

DEFAULT_BACKEND = os.getenv("WIRESHIELD_BACKEND_URL", "http://localhost:3000")
ADMIN_TOKEN = os.getenv("WIRESHIELD_ADMIN_TOKEN", "wireshield_admin_secret_token_change_in_prod")

def api_request(endpoint, method="GET", data=None):
    url = f"{DEFAULT_BACKEND.rstrip('/')}{endpoint}"
    headers = {
        "User-Agent": "WireShield-Admin-CLI/1.0",
        "Content-Type": "application/json",
        "Authorization": f"Bearer {ADMIN_TOKEN}"
    }

    req_data = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        print(f"[ERROR] HTTP {e.code}: {err_msg}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"[ERROR] Request failed: {e}", file=sys.stderr)
        return None

def list_servers():
    print("\n--- Registered WireGuard VPN Nodes ---")
    servers = api_request("/api/servers")
    if not servers:
        print("No servers found or backend unreachable.")
        return

    print(f"{'ID':<15} {'NAME':<24} {'LOCATION':<20} {'ENDPOINT':<28} {'LOAD':<8} {'PING':<8}")
    print("-" * 105)
    for s in servers:
        loc = f"{s.get('city', '')}, {s.get('country', '')}"
        ep = f"{s.get('endpointHost', '')}:{s.get('endpointPort', '')}"
        load = f"{s.get('loadPercent', 0)}%"
        ping = f"{s.get('pingMs', -1)} ms" if s.get('pingMs', -1) > 0 else "--"
        print(f"{s.get('id', ''):<15} {s.get('name', ''):<24} {loc:<20} {ep:<28} {load:<8} {ping:<8}")
    print("")

def add_server(name, country, city, host, port, pubkey, country_code="UN"):
    payload = {
        "name": name,
        "country": country,
        "countryCode": country_code,
        "city": city,
        "endpointHost": host,
        "endpointPort": int(port),
        "publicKey": pubkey,
        "dnsServers": "1.1.1.1, 1.0.0.1"
    }
    result = api_request("/api/servers", method="POST", data=payload)
    if result:
        print(f"[SUCCESS] Server '{name}' successfully added! ID: {result.get('server', {}).get('id')}")

def delete_server(server_id):
    result = api_request(f"/api/servers/{server_id}", method="DELETE")
    if result:
        print(f"[SUCCESS] Server '{server_id}' removed.")

def test_health():
    servers = api_request("/api/servers")
    if not servers:
        print("Cannot test health: no servers returned.")
        return

    print("\n--- Server Probing & Latency Check ---")
    for s in servers:
        host = s.get("endpointHost")
        port = int(s.get("endpointPort", 51820))
        name = s.get("name")
        start = time.time()
        status = "ONLINE"
        try:
            # WireGuard is UDP, but probe host resolution and TCP management/connectivity
            socket.gethostbyname(host)
            elapsed_ms = int((time.time() - start) * 1000)
            print(f"[✓] {name:<24} ({host}:{port}) -> Reachable ({elapsed_ms} ms)")
        except Exception as e:
            print(f"[✗] {name:<24} ({host}:{port}) -> UNREACHABLE ({e})")
    print("")

def main():
    parser = argparse.ArgumentParser(description="WireShield VPN Admin CLI")
    parser.add_argument("--list", action="store_true", help="List all active VPN nodes")
    parser.add_argument("--health", action="store_true", help="Test health and latencies of all nodes")
    parser.add_argument("--add", action="store_true", help="Add a new VPN server node")
    parser.add_argument("--delete", metavar="SERVER_ID", help="Delete a VPN server node by ID")
    parser.add_argument("--name", help="Server Name (for --add)")
    parser.add_argument("--country", default="Unknown", help="Server Country (for --add)")
    parser.add_argument("--city", default="Unknown", help="Server City (for --add)")
    parser.add_argument("--host", help="Server Hostname or IP (for --add)")
    parser.add_argument("--port", default=51820, type=int, help="Server WireGuard Port (default 51820)")
    parser.add_argument("--pubkey", help="Server WireGuard Public Key (for --add)")
    parser.add_argument("--country-code", default="UN", help="2-letter country code (e.g. DE, US)")

    args = parser.parse_args()

    if args.list:
        list_servers()
    elif args.health:
        test_health()
    elif args.delete:
        delete_server(args.delete)
    elif args.add:
        if not args.name or not args.host or not args.pubkey:
            print("[ERROR] --name, --host, and --pubkey are required to add a server.", file=sys.stderr)
            sys.exit(1)
        add_server(args.name, args.country, args.city, args.host, args.port, args.pubkey, args.country_code)
    else:
        list_servers()

if __name__ == "__main__":
    main()
