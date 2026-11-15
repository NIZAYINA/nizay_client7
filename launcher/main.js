// Nizay launcher — main process.
// Запуск: npm install && npm start. Мод кладётся из launcher/mod/nizay-client-*.jar в <gameDir>/mods.
const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const crypto = require('crypto');
const os = require('os');
const { execFile } = require('child_process');
const { Client } = require('minecraft-launcher-core');

const MC_VERSION = '1.21.11';
const DEFAULTS = {
  ramGb: 4,
  javaPath: '',
  gameDir: '%APPDATA%\\.nizay',
  closeOnLaunch: false,
  showConsole: false,
  autoUpdate: true,
  lastLaunch: null,
  playtimeMin: 2300,
  activeAccount: 'Steve_01',
  accounts: [
    { name: 'Steve_01', type: 'Offline', uuid: '', last: 'никогда', lastTs: null },
  ],
};

// Оффлайн-UUID по алгоритму vanilla: UUID v3 от "OfflinePlayer:<ник>"
function offlineUuid(name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name, 'utf8').digest();
  h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}
const NICK_RE = /^[A-Za-z0-9_]{3,16}$/;

const stateFile = () => path.join(app.getPath('userData'), 'launcher.json');
const loadState = () => {
  let s;
  try { s = { ...DEFAULTS, ...JSON.parse(fs.readFileSync(stateFile(), 'utf8')) }; }
  catch { s = JSON.parse(JSON.stringify(DEFAULTS)); }
  for (const a of s.accounts) if (a.type === 'Offline' && !a.uuid) a.uuid = offlineUuid(a.name);
  pullActiveFromGame(s);
  return s;
};
const saveState = (s) => {
  fs.mkdirSync(path.dirname(stateFile()), { recursive: true });
  fs.writeFileSync(stateFile(), JSON.stringify(s, null, 2));
  pushAccountsToGame(s);
};

// --- Связь с модом: <gameDir>/nizay-accounts.json. Лаунчер публикует список, мод может сменить "active".
// --- Друзья: relay-сервер (см. server/). Токен хранится по нику аккаунта.
async function frCall(s, pathName, body) {
  const base = (s.fr?.server || '').replace(/\/+$/, '');
  if (!base) return { error: 'Укажите адрес сервера' };
  try {
    const r = await fetch(base + pathName, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(5000) });
    return await r.json();
  } catch (e) { return { error: 'Сервер недоступен' }; }
}
async function frIdentity(s) {
  s.fr ||= { server: '', tokens: {} };
  const nick = s.activeAccount, tok = s.fr.tokens[nick];
  const r = await frCall(s, '/register', { nick, token: tok });
  if (r.token) { s.fr.tokens[nick] = r.token; fs.writeFileSync(stateFile(), JSON.stringify(s, null, 2)); }
  return r;
}
function writeFriendsFile(s) {
  try {
    const tok = s.fr?.tokens?.[s.activeAccount];
    fs.mkdirSync(expand(s.gameDir), { recursive: true });
    fs.writeFileSync(path.join(expand(s.gameDir), 'nizay-friends.json'), JSON.stringify({ server: s.fr?.server || '', token: tok || '', nick: s.activeAccount }, null, 2));
  } catch {}
}
const accountsFile = (s) => path.join(expand(s.gameDir), 'nizay-accounts.json');
function pushAccountsToGame(s) {
  writeFriendsFile(s);
  try {
    const f = accountsFile(s);
    fs.mkdirSync(path.dirname(f), { recursive: true });
    const data = { active: s.activeAccount || '', accounts: s.accounts.map((a) => ({ id: a.name, name: a.name, type: (a.type || 'Offline').toLowerCase() })) };
    fs.writeFileSync(f, JSON.stringify(data, null, 2));
  } catch { /* папка игры может быть недоступна — не критично */ }
}
function pullActiveFromGame(s) {
  try {
    const j = JSON.parse(fs.readFileSync(accountsFile(s), 'utf8'));
    if (j.changedBy === 'game' && j.active && j.active !== s.activeAccount && s.accounts.some((a) => a.name === j.active)) {
      s.activeAccount = j.active;
      fs.writeFileSync(stateFile(), JSON.stringify(s, null, 2));
      pushAccountsToGame(s); // сбрасывает флаг changedBy
    }
  } catch { /* файла ещё нет */ }
}
const expand = (p) => p.replace(/%([^%]+)%/g, (_, k) => process.env[k] || (k === 'APPDATA' ? app.getPath('appData') : '')).replace(/\\/g, path.sep);

let win;
function createWindow() {
  win = new BrowserWindow({
    width: 1000, height: 640, frame: false, transparent: false, backgroundColor: '#0b0b0d',
    resizable: false, icon: path.join(__dirname, 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false },
  });
  win.loadFile('index.html');
}
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());

ipcMain.handle('state:load', () => loadState());
ipcMain.handle('state:save', (_, s) => { saveState(s); return true; });
ipcMain.handle('acc:addOffline', (_, rawName) => {
  const name = String(rawName || '').trim();
  if (!NICK_RE.test(name)) return { error: 'Ник: 3–16 символов, латиница, цифры и _' };
  const s = loadState();
  if (s.accounts.some((a) => a.name.toLowerCase() === name.toLowerCase())) return { error: 'Такой аккаунт уже есть' };
  s.accounts.push({ name, type: 'Offline', uuid: offlineUuid(name), last: 'никогда', lastTs: null });
  if (!s.activeAccount) s.activeAccount = name;
  saveState(s);
  return { state: s };
});
ipcMain.handle('acc:remove', (_, name) => {
  const s = loadState();
  s.accounts = s.accounts.filter((a) => a.name !== name);
  if (s.activeAccount === name) s.activeAccount = (s.accounts[0] || {}).name || '';
  saveState(s);
  return s;
});
ipcMain.handle('acc:select', (_, name) => {
  const s = loadState();
  if (s.accounts.some((a) => a.name === name)) s.activeAccount = name;
  saveState(s);
  return s;
});
ipcMain.on('win:min', () => win.minimize());
ipcMain.on('win:close', () => win.close());
ipcMain.handle('link:open', (_, url) => { if (/^https:\/\//.test(url)) shell.openExternal(url); });
ipcMain.handle('game:folder', () => {
  const dir = expand(loadState().gameDir); fs.mkdirSync(dir, { recursive: true }); shell.openPath(dir);
});

// ---------- сеть ----------
const UA = { 'User-Agent': 'nizay-launcher' };
function request(url, cb, rej, hops = 0) {
  if (hops > 8) return rej(new Error('Слишком много редиректов: ' + url));
  https.get(url, { headers: UA }, (r) => {
    if (r.statusCode >= 300 && r.statusCode < 400 && r.headers.location) {
      r.resume();
      return request(new URL(r.headers.location, url).toString(), cb, rej, hops + 1);
    }
    if (r.statusCode !== 200) { r.resume(); return rej(new Error(`HTTP ${r.statusCode}: ${url}`)); }
    cb(r);
  }).on('error', rej);
}
const getJson = (url) => new Promise((res, rej) => request(url, (r) => {
  let d = ''; r.on('data', (c) => (d += c));
  r.on('end', () => { try { res(JSON.parse(d)); } catch (e) { rej(e); } });
}, rej));
const download = (url, file, onProgress) => new Promise((res, rej) => request(url, (r) => {
  const total = Number(r.headers['content-length']) || 0; let got = 0, last = 0;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = file + '.part';
  const out = fs.createWriteStream(tmp);
  r.on('data', (c) => { got += c.length; const now = Date.now(); if (onProgress && now - last > 250) { last = now; onProgress(got, total); } });
  r.pipe(out);
  out.on('finish', () => { fs.renameSync(tmp, file); res(); });
  out.on('error', rej); r.on('error', rej);
}, rej));
const run = (cmd, args) => new Promise((res, rej) => execFile(cmd, args, { windowsHide: true, maxBuffer: 1 << 26 }, (e, so, se) => (e ? rej(new Error(`${cmd}: ${se || e.message}`)) : res(so))));

// ---------- Java 21 (скачиваем Temurin, если своей нет) ----------
const javaBin = (home) => path.join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
function findJavaHome(dir) {
  if (!fs.existsSync(dir)) return null;
  if (fs.existsSync(javaBin(dir))) return dir;
  for (const n of fs.readdirSync(dir)) {
    const sub = path.join(dir, n);
    if (fs.statSync(sub).isDirectory()) { const f = findJavaHome(sub); if (f) return f; }
  }
  return null;
}
async function javaMajor(bin) {
  try {
    const out = await new Promise((res, rej) => execFile(bin, ['-version'], { windowsHide: true }, (e, so, se) => (e ? rej(e) : res(String(se || so)))));
    const m = /version "(\d+)(?:\.(\d+))?/.exec(out); if (!m) return 0;
    return m[1] === '1' ? Number(m[2]) : Number(m[1]);
  } catch { return 0; }
}
async function ensureJava(root, s, status) {
  // 1) путь из настроек (если он реально существует и это Java 21+)
  if (s.javaPath) {
    const p = expand(s.javaPath);
    const bin = /\.exe$|[\\/]java$/i.test(p) ? p : javaBin(p);
    if (fs.existsSync(bin) && (await javaMajor(bin)) >= 21) return bin;
    status('Указанная Java не подошла (нужна 21+), скачиваю свою…');
  }
  // 2) уже скачанная
  const jdir = path.join(root, 'runtime', 'java21');
  let home = findJavaHome(jdir);
  if (home && (await javaMajor(javaBin(home))) >= 21) return javaBin(home);
  // 3) качаем Temurin JRE 21
  const plat = { win32: 'windows', darwin: 'mac', linux: 'linux' }[process.platform];
  const arch = process.arch === 'arm64' ? 'aarch64' : 'x64';
  const url = `https://api.adoptium.net/v3/binary/latest/21/ga/${plat}/${arch}/jre/hotspot/normal/eclipse`;
  const archive = path.join(root, 'runtime', process.platform === 'win32' ? 'java21.zip' : 'java21.tar.gz');
  fs.rmSync(jdir, { recursive: true, force: true }); fs.mkdirSync(jdir, { recursive: true });
  await download(url, archive, (g, t) => status(`Скачиваю Java 21… ${(g / 1048576).toFixed(0)}${t ? ' / ' + (t / 1048576).toFixed(0) : ''} МБ`));
  status('Распаковка Java 21…');
  try { await run('tar', ['-xf', archive, '-C', jdir]); }
  catch (e) {
    if (process.platform !== 'win32') throw e;
    await run('powershell', ['-NoProfile', '-Command', `Expand-Archive -LiteralPath '${archive}' -DestinationPath '${jdir}' -Force`]);
  }
  fs.rmSync(archive, { force: true });
  home = findJavaHome(jdir);
  if (!home) throw new Error('Не удалось распаковать Java 21');
  if (process.platform !== 'win32') fs.chmodSync(javaBin(home), 0o755);
  return javaBin(home);
}

// ---------- Fabric: ставим профиль через meta-API (без инсталлятора) ----------
async function ensureFabric(root) {
  const loaders = await getJson(`https://meta.fabricmc.net/v2/versions/loader/${MC_VERSION}`);
  if (!loaders.length) throw new Error('Fabric пока не поддерживает Minecraft ' + MC_VERSION);
  const loader = (loaders.find((l) => l.loader.stable) || loaders[0]).loader.version;
  const id = `fabric-loader-${loader}-${MC_VERSION}`;
  const dir = path.join(root, 'versions', id);
  if (!fs.existsSync(path.join(dir, `${id}.json`))) {
    const profile = await getJson(`https://meta.fabricmc.net/v2/versions/loader/${MC_VERSION}/${loader}/profile/json`);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, `${id}.json`), JSON.stringify(profile));
  }
  return id;
}

function ensureMod(root, log) {
  const modsDir = path.join(root, 'mods'); fs.mkdirSync(modsDir, { recursive: true });
  const src = path.join(__dirname, 'mod');
  const jars = fs.existsSync(src) ? fs.readdirSync(src).filter((n) => /^nizay-client.*\.jar$/.test(n)) : [];
  if (!jars.length) { log('ВНИМАНИЕ: jar мода не найден в лаунчере — игра запустится без Nizay-мода'); return; }
  for (const old of fs.readdirSync(modsDir).filter((n) => /^nizay-client.*\.jar$/.test(n))) fs.rmSync(path.join(modsDir, old), { force: true });
  for (const f of jars) fs.copyFileSync(path.join(src, f), path.join(modsDir, f));
}

// Fabric API нужен моду: тянем с Modrinth, если ещё нет
async function ensureFabricApi(root, status) {
  const modsDir = path.join(root, 'mods');
  if (fs.readdirSync(modsDir).some((n) => /^fabric-api/i.test(n))) return;
  status('Скачиваю Fabric API…');
  const vs = await getJson(`https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22${MC_VERSION}%22%5D&loaders=%5B%22fabric%22%5D`);
  const file = vs[0] && (vs[0].files.find((f) => f.primary) || vs[0].files[0]);
  if (!file) throw new Error('Fabric API для ' + MC_VERSION + ' не найден на Modrinth');
  await download(file.url, path.join(modsDir, file.filename));
}

// ---------- проверка целостности jar (битая/недокачанная библиотека ломает запуск) ----------
function isValidZip(file) {
  try {
    const st = fs.statSync(file); if (st.size < 22) return false;
    const len = Math.min(st.size, 65557), buf = Buffer.alloc(len), fd = fs.openSync(file, 'r');
    try { fs.readSync(fd, buf, 0, len, st.size - len); } finally { fs.closeSync(fd); }
    const i = buf.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
    return i !== -1 && i + 22 <= len && i + 22 + buf.readUInt16LE(i + 20) === len;
  } catch { return false; }
}
function purgeBrokenJars(root, log) {
  let n = 0;
  const walk = (dir) => {
    if (!fs.existsSync(dir)) return;
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const f = path.join(dir, e.name);
      if (e.isDirectory()) walk(f);
      else if (/\.jar$/i.test(e.name) && !isValidZip(f)) { fs.rmSync(f, { force: true }); n++; log('Удалён повреждённый файл: ' + f); }
    }
  };
  walk(path.join(root, 'libraries')); walk(path.join(root, 'versions'));
  return n;
}

let running = false;
ipcMain.handle('fr:server', (_, url) => { const s = loadState(); s.fr ||= { server: '', tokens: {} }; s.fr.server = String(url || '').trim(); saveState(s); return s; });
ipcMain.handle('fr:state', async () => {
  const s = loadState(); const id = await frIdentity(s); if (id.error) return { error: id.error, state: s };
  writeFriendsFile(s);
  const r = await frCall(s, '/state', { token: s.fr.tokens[s.activeAccount] });
  return r.error ? { error: r.error, state: s } : { data: r, state: s };
});
for (const [ch, pth, key] of [['fr:add', '/friend/add', 'target'], ['fr:remove', '/friend/remove', 'target'], ['fr:invite', '/group/invite', 'target'], ['fr:accept', '/group/accept', 'from'], ['fr:leave', '/group/leave', null]]) {
  ipcMain.handle(ch, async (_, v) => { const s = loadState(); await frIdentity(s); const b = { token: s.fr.tokens[s.activeAccount] }; if (key) b[key] = v; return frCall(s, pth, b); });
}
ipcMain.handle('game:launch', async () => {
  if (running) return false;
  const s = loadState();
  const root = expand(s.gameDir);
  const send = (ch, v) => win && !win.isDestroyed() && win.webContents.send(ch, v);
  const log = (m) => send('game:log', m);
  const status = (m) => { send('game:status', m); log(m); };
  const fail = (e) => { running = false; send('game:error', e.message); send('game:state', 'ready'); log('ERROR: ' + (e.stack || e.message)); };
  try {
    running = true; send('game:error', ''); send('game:state', 'preparing');
    fs.mkdirSync(root, { recursive: true });
    pushAccountsToGame(s);
    const acc = s.accounts.find((a) => a.name === s.activeAccount);
    if (!acc) throw new Error('Не выбран аккаунт — добавьте его во вкладке «Аккаунты»');
    if (acc.type !== 'Offline') throw new Error('Вход через Microsoft пока не подключён — используйте Offline-аккаунт');

    const javaPath = await ensureJava(root, s, status);
    status('Устанавливаю Fabric…');
    const fabricId = await ensureFabric(root);
    ensureMod(root, log);
    await ensureFabricApi(root, status);

    status('Проверяю файлы игры…');
    purgeBrokenJars(root, log);
    const launcher = new Client();
    const opts = {
      authorization: { access_token: '0', client_token: '0', uuid: acc.uuid || offlineUuid(acc.name), name: acc.name, user_properties: '{}', meta: { type: 'msa', demo: false } },
      root,
      version: { number: MC_VERSION, type: 'release', custom: fabricId },
      memory: { max: `${s.ramGb}G`, min: '1G' },
      javaPath,
    };
    let lastP = 0;
    launcher.on('debug', (m) => log(String(m)));
    launcher.on('data', (m) => log(String(m)));
    launcher.on('progress', (p) => {
      const now = Date.now(); if (now - lastP < 200 && p.task < p.total) return; lastP = now;
      const names = { assets: 'ресурсы', classes: 'библиотеки', 'classes-custom': 'библиотеки Fabric', 'assets-copy': 'копирование ресурсов' };
      status(`Скачиваю ${names[p.type] || p.type}: ${p.task} / ${p.total}`);
    });
    launcher.on('download-status', () => {});
    launcher.on('close', (code) => {
      running = false; send('game:state', 'ready'); send('game:status', '');
      if (code !== 0 && code !== null) {
        const bad = purgeBrokenJars(root, log);
        send('game:error', bad
          ? `Найдены и удалены повреждённые файлы (${bad}). Нажмите «Запуск» ещё раз — они скачаются заново.`
          : `Игра завершилась с кодом ${code}. Включите консоль в настройках, чтобы увидеть лог.`);
      }
      if (win && s.closeOnLaunch) win.show();
    });
    send('game:state', 'launching'); status('Запуск игры…');
    const proc = await launcher.launch(opts);
    if (!proc) throw new Error('Не удалось запустить игру (см. лог). Проверьте интернет и папку игры.');
    s.lastLaunch = Date.now(); acc.lastTs = s.lastLaunch; saveState(s);
    send('game:state', 'running'); send('game:status', '');
    if (s.closeOnLaunch && win) win.hide();
    return true;
  } catch (e) { fail(e); return false; }
});
