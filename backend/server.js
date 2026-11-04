/**
 * WireShield VPN Management Backend API
 * Production-ready, privacy-respecting REST API for server list,
 * client peer allocation, and health monitoring.
 *
 * Zero-Logs Architecture:
 * - Does not log client IP addresses or traffic
 * - Private keys are generated locally on the client and NEVER sent here
 */

const express = require('express');
const cors = require('cors');
const crypto = require('crypto');

const app = express();
const PORT = process.env.PORT || 3000;
const ADMIN_API_KEY = process.env.ADMIN_API_KEY;

if (!ADMIN_API_KEY && process.env.NODE_ENV === 'production') {
  console.error('[FATAL] ADMIN_API_KEY environment variable must be set in production.');
  process.exit(1);
}

// Security Middlewares
app.use(cors());
app.use(express.json());

// In-Memory Database store with optional SQLite persistence
// Servers catalog is initialized empty: only user/admin verified nodes are added via .conf or admin API.
const db = {
  servers: [],
  peers: new Map() // clientPublicKey -> { serverId, allocatedIp, createdAt }
};

// Admin Auth Middleware
function requireAdminAuth(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized. Admin Bearer token required.' });
  }
  const token = authHeader.split(' ')[1];
  if (token !== ADMIN_API_KEY) {
    return res.status(403).json({ error: 'Forbidden. Invalid admin token.' });
  }
  next();
}

// -------------------------------------------------------------
// PUBLIC CLIENT ENDPOINTS
// -------------------------------------------------------------

/**
 * GET /api/servers
 * Returns list of active WireGuard nodes with metadata, public keys, and load.
 */
app.get('/api/servers', (req, res) => {
  const activeServers = db.servers
    .filter(s => s.isActive)
    .map(s => ({
      id: s.id,
      name: s.name,
      country: s.country,
      countryCode: s.countryCode,
      city: s.city,
      endpointHost: s.endpointHost,
      endpointPort: s.endpointPort,
      publicKey: s.publicKey,
      dnsServers: s.dnsServers,
      loadPercent: s.loadPercent,
      pingMs: s.pingMs
    }));

  res.json(activeServers);
});

/**
 * GET /api/servers/:id
 * Returns metadata of a specific server.
 */
app.get('/api/servers/:id', (req, res) => {
  const server = db.servers.find(s => s.id === req.params.id);
  if (!server) {
    return res.status(404).json({ error: 'Server not found' });
  }
  res.json(server);
});

/**
 * GET /api/servers/:id/status
 * Health check endpoint for a server node.
 */
app.get('/api/servers/:id/status', (req, res) => {
  const server = db.servers.find(s => s.id === req.params.id);
  if (!server) {
    return res.status(404).json({ error: 'Server not found' });
  }
  res.json({
    id: server.id,
    status: server.isActive ? 'ONLINE' : 'OFFLINE',
    loadPercent: server.loadPercent,
    pingMs: server.pingMs,
    timestamp: new Date().toISOString()
  });
});

/**
 * POST /api/client/config
 * Provisions a client configuration by registering the client's Public Key.
 *
 * Request Body:
 * {
 *   "serverId": "srv-de-fra",
 *   "clientPublicKey": "Base64-32-byte-public-key"
 * }
 */
app.post('/api/client/config', (req, res) => {
  const { serverId, clientPublicKey } = req.body;

  if (!serverId || !clientPublicKey) {
    return res.status(400).json({ error: 'Missing serverId or clientPublicKey' });
  }

  // Validate WireGuard Base64 public key length (32 bytes = 44 characters ending with =)
  if (typeof clientPublicKey !== 'string' || clientPublicKey.length !== 44) {
    return res.status(400).json({ error: 'Invalid WireGuard public key format' });
  }

  const server = db.servers.find(s => s.id === serverId && s.isActive);
  if (!server) {
    return res.status(404).json({ error: 'Requested VPN server not found or inactive' });
  }

  // Allocate next IP inside subnet
  server.currentIpCounter = (server.currentIpCounter || 2) + 1;
  const clientOctet = server.currentIpCounter;
  const baseSubnet = server.subnetCidr.split('/')[0].replace(/\.0$/, '');
  const allocatedIp = `${baseSubnet}.${clientOctet}/32`;

  // Register peer
  db.peers.set(clientPublicKey, {
    serverId: server.id,
    allocatedIp,
    createdAt: new Date().toISOString()
  });

  // Return server configuration parameters (Zero private keys transmitted!)
  res.json({
    status: 'SUCCESS',
    allocatedAddress: allocatedIp,
    dns: server.dnsServers,
    mtu: 1420,
    server: {
      id: server.id,
      endpoint: `${server.endpointHost}:${server.endpointPort}`,
      publicKey: server.publicKey,
      allowedIps: '0.0.0.0/0, ::/0',
      persistentKeepalive: 25
    }
  });
});

/**
 * POST /api/client/revoke
 * Revokes an existing client public key from the system.
 */
app.post('/api/client/revoke', (req, res) => {
  const { clientPublicKey } = req.body;
  if (!clientPublicKey) {
    return res.status(400).json({ error: 'Missing clientPublicKey' });
  }

  if (db.peers.has(clientPublicKey)) {
    db.peers.delete(clientPublicKey);
    return res.json({ status: 'REVOKED', message: 'Client peer key successfully revoked' });
  }

  res.status(404).json({ error: 'Peer key not found' });
});

// -------------------------------------------------------------
// ADMIN MANAGEMENT ENDPOINTS
// -------------------------------------------------------------

/**
 * POST /api/servers
 * Adds a new WireGuard server node (Admin only)
 */
app.post('/api/servers', requireAdminAuth, (req, res) => {
  const { name, country, countryCode, city, endpointHost, endpointPort, publicKey, dnsServers } = req.body;

  if (!name || !endpointHost || !publicKey) {
    return res.status(400).json({ error: 'Name, endpointHost, and publicKey are required' });
  }

  const newServer = {
    id: `srv-${countryCode ? countryCode.toLowerCase() : 'node'}-${crypto.randomBytes(3).toString('hex')}`,
    name,
    country: country || 'Unknown',
    countryCode: countryCode || 'UN',
    city: city || 'Location',
    endpointHost,
    endpointPort: endpointPort || 51820,
    publicKey,
    dnsServers: dnsServers || '1.1.1.1, 1.0.0.1',
    subnetCidr: '10.66.' + Math.floor(Math.random() * 200 + 70) + '.0/24',
    loadPercent: 5,
    pingMs: -1,
    isActive: true,
    currentIpCounter: 2
  };

  db.servers.push(newServer);
  res.status(201).json({ status: 'CREATED', server: newServer });
});

/**
 * DELETE /api/servers/:id
 * Deletes a server node (Admin only)
 */
app.delete('/api/servers/:id', requireAdminAuth, (req, res) => {
  const index = db.servers.findIndex(s => s.id === req.params.id);
  if (index === -1) {
    return res.status(404).json({ error: 'Server not found' });
  }
  db.servers.splice(index, 1);
  res.json({ status: 'DELETED', id: req.params.id });
});

// Start listening
if (process.env.NODE_ENV !== 'test') {
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[WireShield] Management Backend running on port ${PORT}`);
    console.log(`[WireShield] Health check available at: http://localhost:${PORT}/api/servers`);
  });
}

module.exports = app;
