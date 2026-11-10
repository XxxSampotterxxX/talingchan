// Battle of Talingchan desktop (v2): เกมอยู่ในแอป ไม่ต้องล็อกอิน / ออนไลน์เครื่องต่อเครื่อง
const { app, BrowserWindow, dialog, shell, Menu, protocol, net } = require('electron');
const { pathToFileURL } = require('url');
const { autoUpdater } = require('electron-updater');
const path = require('path');
let win;
// ให้เกมโหลดไฟล์ภาพการ์ด (packs/*.webp) ผ่าน fetch ได้ (file:// ทำไม่ได้)
protocol.registerSchemesAsPrivileged([{ scheme: 'tlapp', privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true } }]);
function registerGameProtocol() {
  const root = path.join(__dirname, 'game');
  protocol.handle('tlapp', (req) => {
    const u = new URL(req.url);
    let p = decodeURIComponent(u.pathname);
    if (p === '/' || p === '') p = '/index.html';
    const f = path.normalize(path.join(root, p));
    if (!f.startsWith(root)) return new Response('forbidden', { status: 403 });
    return net.fetch(pathToFileURL(f).toString());
  });
}

function createWindow() {
  win = new BrowserWindow({
    width: 1440, height: 900, minWidth: 1000, minHeight: 650,
    backgroundColor: '#050b1a', title: 'Battle of Talingchan', autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, sandbox: true }
  });
  Menu.setApplicationMenu(null);
  win.loadURL('tlapp://game/index.html');
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (/^https?:/.test(url)) { e.preventDefault(); shell.openExternal(url); } });
  win.webContents.on('before-input-event', (e, i) => {
    if (i.type !== 'keyDown') return;
    if (i.key === 'F11') win.setFullScreen(!win.isFullScreen());
    if (i.key === 'F5') win.webContents.reload();
  });
}

// ---------- ระบบอัพเดท: แจ้ง -> ดาวน์โหลด (หน้าต่างสถานะ รอจนเสร็จ) -> รีสตาร์ท ----------
let upWin = null;
const esc = (t) => String(t || '').replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
function notesText(n) {
  if (!n) return '';
  const raw = Array.isArray(n) ? n.map((x) => x.note || '').join('\n') : String(n);
  return raw.replace(/<br\s*\/?>|<\/(p|li|h\d)>/gi, '\n').replace(/<li>/gi, '• ').replace(/<[^>]+>/g, '').replace(/\n{3,}/g, '\n\n').trim();
}
function openProgress(version) {
  upWin = new BrowserWindow({ parent: win, modal: true, width: 440, height: 250, resizable: false, minimizable: false, maximizable: false,
    closable: false, frame: false, backgroundColor: '#07122b', show: false, webPreferences: { sandbox: true } });
  const html = `<meta charset=utf-8><body style="margin:0;background:#07122b;color:#dff6ff;font-family:'Noto Sans Thai',Tahoma,sans-serif;border:2px solid #35e0ff;box-sizing:border-box;height:100vh;padding:22px">
<div style="font-size:18px;font-weight:700">กำลังอัพเดทเป็น ${esc(version)}…</div>
<div style="height:16px;border-radius:8px;background:#0d2a55;border:1px solid #2a6aa8;overflow:hidden;margin:22px 0 8px"><div id=b style="height:100%;width:0%;background:linear-gradient(90deg,#1a8cff,#35e0ff);box-shadow:0 0 10px #35e0ff;transition:width .25s"></div></div>
<div style="display:flex;justify-content:space-between;font-size:13px;color:#9fc4de"><span id=p>0%</span><span id=s>กำลังเริ่ม…</span></div>
<div style="margin-top:20px;font-size:13px;color:#ffcf4a">⚠ อย่าปิดโปรแกรมจนกว่าจะเสร็จ</div></body>
<script>window.setP=(pc,l,t,sp,eta)=>{b.style.width=pc+'%';p.textContent=pc.toFixed(0)+'% · '+l+' / '+t+' MB';s.textContent=sp+' MB/s'+(eta?' · เหลือ ~'+eta+' วินาที':'')}</script>`;
  upWin.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html));
  upWin.once('ready-to-show', () => upWin.show());
}
function closeProgress() { if (upWin) { upWin.setClosable(true); upWin.destroy(); upWin = null; } }

function setupUpdater() {
  autoUpdater.autoDownload = false;
  let total = 0;
  autoUpdater.on('update-available', async (info) => {
    total = (info.files && info.files[0] && info.files[0].size) || 0;
    const mb = total ? ' · ขนาด ' + (total / 1048576).toFixed(0) + ' MB' : '';
    const notes = notesText(info.releaseNotes);
    const r = await dialog.showMessageBox(win, { type: 'info', buttons: ['อัพเดทเลย', 'ไว้ทีหลัง'], defaultId: 0, cancelId: 1,
      title: 'มีเวอร์ชันใหม่', message: 'มีเวอร์ชันใหม่ ' + info.version,
      detail: 'เวอร์ชันปัจจุบัน ' + app.getVersion() + mb + (notes ? '\n\nสิ่งที่เปลี่ยน:\n' + notes : '') });
    if (r.response === 0) { openProgress(info.version); autoUpdater.downloadUpdate().catch(() => { closeProgress(); dialog.showErrorBox('อัพเดทไม่สำเร็จ', 'ดาวน์โหลดไม่สำเร็จ ลองใหม่ภายหลัง'); }); }
  });
  autoUpdater.on('download-progress', (p) => {
    if (!upWin) return;
    const eta = p.bytesPerSecond > 0 ? Math.ceil((p.total - p.transferred) / p.bytesPerSecond) : 0;
    upWin.webContents.executeJavaScript(`setP(${p.percent},${(p.transferred / 1048576).toFixed(0)},${(p.total / 1048576).toFixed(0)},${(p.bytesPerSecond / 1048576).toFixed(1)},${eta})`).catch(() => {});
  });
  autoUpdater.on('update-downloaded', async () => {
    closeProgress();
    const r = await dialog.showMessageBox(win, { type: 'info', buttons: ['รีสตาร์ทเลย', 'ไว้ทีหลัง'], defaultId: 0, cancelId: 1,
      title: 'ดาวน์โหลดเสร็จแล้ว', message: '✅ ดาวน์โหลดเสร็จแล้ว', detail: 'รีสตาร์ทเพื่อใช้เวอร์ชันใหม่\n(ถ้าเลือกไว้ทีหลัง จะติดตั้งเองตอนปิดโปรแกรม)' });
    if (r.response === 0) autoUpdater.quitAndInstall();
  });
  autoUpdater.on('error', () => { closeProgress(); });
  autoUpdater.checkForUpdates().catch(() => {});
}

app.whenReady().then(() => { registerGameProtocol(); createWindow(); if (app.isPackaged) setupUpdater(); });
app.on('window-all-closed', () => app.quit());
