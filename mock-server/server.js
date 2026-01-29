const express = require('express');
const path = require('path');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

// In-memory mock data
let users = [
  { id: 1, name: '张三', role: '管理员' },
  { id: 2, name: '李四', role: '普通用户' }
];

let transactions = [
  { id: 1, desc: '初始化余额', amount: 1000, time: new Date().toISOString() }
];

let warnings = [
  { id: 1, rule: '高温', severity: '高', status: '未处理', triggered_at: new Date().toISOString() }
];

// API endpoints
app.get('/api/', (req, res) => {
  res.type('text').send('Village 管理系统 - 模拟后端');
});

app.get('/api/users', (req, res) => {
  res.json(users);
});

app.post('/api/users', (req, res) => {
  const body = req.body || {};
  const id = users.length ? Math.max(...users.map(u => u.id)) + 1 : 1;
  const user = { id, name: body.name || `用户${id}`, role: body.role || '普通用户' };
  users.push(user);
  res.status(201).json(user);
});

// Auth: simple login for dev
app.post('/api/auth/login', (req, res) => {
  const { username, password } = req.body || {};
  // very simple mock auth: accept any non-empty username
  if (!username) return res.status(400).json({ error: 'username required' });
  const user = users.find(u => u.name === username) || users[0];
  return res.json({ token: 'mock-token-123', user });
});

// User CRUD: get by id, update, delete
app.get('/api/users/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const u = users.find(x => x.id === id);
  if (!u) return res.status(404).json({ error: 'not found' });
  res.json(u);
});

app.put('/api/users/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const idx = users.findIndex(x => x.id === id);
  if (idx === -1) return res.status(404).json({ error: 'not found' });
  const body = req.body || {};
  users[idx] = { ...users[idx], name: body.name || users[idx].name, role: body.role || users[idx].role };
  res.json(users[idx]);
});

app.delete('/api/users/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const idx = users.findIndex(x => x.id === id);
  if (idx === -1) return res.status(404).json({ error: 'not found' });
  users.splice(idx, 1);
  res.status(204).send();
});

app.get('/api/finance/transactions', (req, res) => {
  // return transactions in shape expected by frontend: {id, time, desc, amount}
  res.json(transactions.map(t => ({ id: t.id, time: t.time || t.created_at, desc: t.desc || t.description || '-', amount: t.amount })));
});

app.post('/api/finance/transactions', (req, res) => {
  const body = req.body || {};
  const id = transactions.length ? Math.max(...transactions.map(t => t.id)) + 1 : 1;
  const tx = { id, desc: body.desc || body.description || '交易', amount: Number(body.amount || 0), time: new Date().toISOString() };
  transactions.push(tx);
  res.status(201).json(tx);
});

app.get('/api/warnings/events', (req, res) => {
  res.json(warnings);
});

app.post('/api/warnings/events', (req, res) => {
  const body = req.body || {};
  const id = warnings.length ? Math.max(...warnings.map(w => w.id)) + 1 : 1;
  const w = { id, title: body.title || body.rule || '未命名', msg: body.msg || body.description || '', severity: body.severity || '中', status: body.status || '未处理', triggered_at: new Date().toISOString() };
  warnings.push(w);
  res.status(201).json(w);
});

// Warning rules management (village.md)
app.post('/api/warnings/rules', (req, res) => {
  const body = req.body || {};
  const id = (warnings.length ? Math.max(...warnings.map(w => w.id)) : 0) + 1;
  const rule = { id, rule: body.rule || '规则'+id, condition: body.condition || {}, created_at: new Date().toISOString() };
  // store as a warning entry for simplicity in mock
  warnings.push({ id: rule.id, title: rule.rule, msg: JSON.stringify(rule.condition), severity: '中', status: '未生效', triggered_at: rule.created_at });
  res.status(201).json(rule);
});

// Serve frontend static files
const staticDir = path.join(__dirname, '..', 'frontend');
app.use(express.static(staticDir));

// Fallback to index.html for SPA routing
app.get('*', (req, res) => {
  res.sendFile(path.join(staticDir, 'index.html'));
});

const PORT = process.env.PORT || 8082;
app.listen(PORT, () => {
  console.log(`Village mock server running at http://localhost:${PORT}`);
});
