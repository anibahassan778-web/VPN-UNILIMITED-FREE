# WireShield Backend REST API Specification

Base URL: `http://localhost:3000` or `https://vpn.example.com`

---

### 1. `GET /api/servers`
Retrieve list of active VPN exit nodes.
* **Auth**: None (Public)
* **Response** `200 OK`:
```json
[
  {
    "id": "srv-de-fra",
    "name": "Frankfurt Edge 01",
    "country": "Germany",
    "countryCode": "DE",
    "city": "Frankfurt",
    "endpointHost": "de-fra.wireshield-relay.net",
    "endpointPort": 51820,
    "publicKey": "0a5dY+T0w6O3kU2sW1V5a8b7C9d0E2f4G6h8I0j2K4M=",
    "dnsServers": "1.1.1.1, 1.0.0.1",
    "loadPercent": 32,
    "pingMs": 24
  }
]
```

---

### 2. `GET /api/servers/:id/status`
Health check and latency for an individual node.
* **Auth**: None (Public)
* **Response** `200 OK`:
```json
{
  "id": "srv-de-fra",
  "status": "ONLINE",
  "loadPercent": 32,
  "pingMs": 24,
  "timestamp": "2026-10-07T18:40:00.000Z"
}
```

---

### 3. `POST /api/client/config`
Provisions an internal VPN IP address for an authorized client public key.
* **Auth**: None / Rate Limited
* **Request Body**:
```json
{
  "serverId": "srv-de-fra",
  "clientPublicKey": "Base64-32-byte-public-key-here============"
}
```
* **Response** `200 OK`:
```json
{
  "status": "SUCCESS",
  "allocatedAddress": "10.66.66.5/32",
  "dns": "1.1.1.1, 1.0.0.1",
  "mtu": 1420,
  "server": {
    "id": "srv-de-fra",
    "endpoint": "de-fra.wireshield-relay.net:51820",
    "publicKey": "0a5dY+T0w6O3kU2sW1V5a8b7C9d0E2f4G6h8I0j2K4M=",
    "allowedIps": "0.0.0.0/0, ::/0",
    "persistentKeepalive": 25
  }
}
```

---

### 4. `POST /api/client/revoke`
Revokes an allocated client peer key.
* **Auth**: None / Client Token
* **Request Body**:
```json
{
  "clientPublicKey": "Base64-32-byte-public-key-here============"
}
```
* **Response** `200 OK`:
```json
{
  "status": "REVOKED",
  "message": "Client peer key successfully revoked"
}
```

---

### 5. `POST /api/servers`
Adds a new VPN node to the cluster.
* **Auth**: `Authorization: Bearer <ADMIN_TOKEN>`
* **Request Body**:
```json
{
  "name": "Tokyo Express",
  "country": "Japan",
  "countryCode": "JP",
  "city": "Tokyo",
  "endpointHost": "jp-tyo.wireshield-relay.net",
  "endpointPort": 51820,
  "publicKey": "1a3cE5g7I9k1M3o5Q7s9U1w3Y5a7C9e1G3i5K7m9O1Q=",
  "dnsServers": "1.1.1.1, 1.0.0.1"
}
```
* **Response** `201 Created`
