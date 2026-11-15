// Nizay relay: друзья, группы, присутствие и позиции игроков. Без зависимостей: node server.js
// Запуск на VPS: PORT=8787 node server.js   (за nginx/https для внешнего доступа)
const http = require('http'), fs = require('fs'), path = require('path'), crypto = require('crypto');
const PORT = +process.env.PORT || 8787, FILE = path.join(__dirname, 'data.json');
let db = { users: {} };           // key = nick.toLowerCase()
try { db = JSON.parse(fs.readFileSync(FILE, 'utf8')); } catch {}
const mem = {};                   // lastSeen/pos/invites/group — только в памяти
const save = () => fs.writeFile(FILE, JSON.stringify(db), () => {});
const M = (k) => (mem[k] ||= { seen: 0, pos: {}, invites: [], group: null });
const groups = {};                // id -> Set(key)
const byToken = (t) => Object.values(db.users).find((u) => u.token === t);
const byIdOrNick = (s) => { s = String(s || '').trim(); return Object.values(db.users).find((u) => u.id === s.replace('#', '') || u.nick.toLowerCase() === s.toLowerCase()); };
const online = (u) => Date.now() - M(u.nick.toLowerCase()).seen < 15000;
const genId = () => { let id; do id = String(Math.floor(100000 + Math.random() * 900000)); while (Object.values(db.users).some((u) => u.id === id)); return id; };

function view(u) {
  const k = u.nick.toLowerCase(), m = M(k);
  const friends = (u.friends || []).map((id) => Object.values(db.users).find((x) => x.id === id)).filter(Boolean)
    .map((f) => ({ nick: f.nick, id: f.id, online: online(f) }));
  let group = null;
  if (m.group && groups[m.group]) group = { id: m.group, members: [...groups[m.group]].map((mk) => {
    const x = db.users[mk]; if (!x) return null; const p = M(mk).pos;
    return { nick: x.nick, id: x.id, online: online(x), x: p.x, y: p.y, z: p.z, dim: p.dim, srv: p.srv };
  }).filter(Boolean) };
  const onl = Object.values(db.users).filter(online).map((x) => x.nick);
  return { me: { nick: u.nick, id: u.id }, friends, invites: m.invites.map((from) => ({ from })), group, online: onl };
}
const leave = (u) => { const m = M(u.nick.toLowerCase()); if (m.group && groups[m.group]) { groups[m.group].delete(u.nick.toLowerCase()); if (groups[m.group].size < 2) { for (const k of groups[m.group]) M(k).group = null; delete groups[m.group]; } } m.group = null; };

const routes = {
  '/register': (b) => {
    const nick = String(b.nick || '').trim(); if (!/^[A-Za-z0-9_]{3,16}$/.test(nick)) return [400, { error: 'bad nick' }];
    const k = nick.toLowerCase(), ex = db.users[k];
    if (ex) { if (b.token && ex.token === b.token) return [200, { id: ex.id, token: ex.token, nick: ex.nick }]; return [409, { error: 'nick taken' }]; }
    const u = (db.users[k] = { nick, id: genId(), token: crypto.randomBytes(16).toString('hex'), friends: [] }); save();
    return [200, { id: u.id, token: u.token, nick: u.nick }];
  },
  '/state': (b, u) => [200, view(u)],
  '/sync': (b, u) => { const m = M(u.nick.toLowerCase()); m.seen = Date.now(); if (typeof b.x === 'number') m.pos = { x: b.x, y: b.y, z: b.z, dim: b.dim, srv: b.srv }; return [200, view(u)]; },
  '/friend/add': (b, u) => {
    const t = byIdOrNick(b.target); if (!t) return [404, { error: 'not found' }]; if (t.id === u.id) return [400, { error: 'self' }];
    u.friends = [...new Set([...(u.friends || []), t.id])]; t.friends = [...new Set([...(t.friends || []), u.id])]; save(); return [200, { ok: true, nick: t.nick }];
  },
  '/friend/remove': (b, u) => { const t = byIdOrNick(b.target); if (!t) return [404, { error: 'not found' }]; u.friends = (u.friends || []).filter((i) => i !== t.id); t.friends = (t.friends || []).filter((i) => i !== u.id); save(); return [200, { ok: true }]; },
  '/group/invite': (b, u) => {
    const t = byIdOrNick(b.target); if (!t || !(u.friends || []).includes(t.id)) return [404, { error: 'not a friend' }];
    const tm = M(t.nick.toLowerCase()); if (!tm.invites.includes(u.nick)) tm.invites.push(u.nick);
    const um = M(u.nick.toLowerCase()); if (!um.group) { const id = crypto.randomBytes(4).toString('hex'); groups[id] = new Set([u.nick.toLowerCase()]); um.group = id; }
    return [200, { ok: true }];
  },
  '/group/accept': (b, u) => {
    const m = M(u.nick.toLowerCase()), from = String(b.from || ''); const f = db.users[from.toLowerCase()];
    if (!f || !m.invites.includes(f.nick)) return [404, { error: 'no invite' }];
    m.invites = m.invites.filter((x) => x !== f.nick); const fm = M(f.nick.toLowerCase());
    if (!fm.group || !groups[fm.group]) { const id = crypto.randomBytes(4).toString('hex'); groups[id] = new Set([f.nick.toLowerCase()]); fm.group = id; }
    leave(u); groups[fm.group].add(u.nick.toLowerCase()); m.group = fm.group; return [200, { ok: true }];
  },
  '/group/leave': (b, u) => { leave(u); return [200, { ok: true }]; },
};

http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*'); res.setHeader('Access-Control-Allow-Headers', 'content-type');
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
  let data = ''; req.on('data', (c) => { data += c; if (data.length > 1e5) req.destroy(); });
  req.on('end', () => {
    let b = {}; try { b = JSON.parse(data || '{}'); } catch {}
    const h = routes[req.url.split('?')[0]]; if (!h || req.method !== 'POST') { res.writeHead(404); return res.end('{}'); }
    let u = null; if (req.url !== '/register') { u = byToken(b.token); if (!u) { res.writeHead(401); return res.end('{"error":"auth"}'); } }
    const [code, out] = h(b, u); res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(out));
  });
}).listen(PORT, () => console.log('Nizay relay on :' + PORT));
