// Battle of Talingchan desktop shell
// เปิดหน้า Artifact ของเกม (ออนไลน์ใช้ห้องของ Artifact) + ระบบอัพเดทตัวแอปผ่าน GitHub Releases
const { app, BrowserWindow, dialog, shell, Menu } = require('electron');
const { autoUpdater } = require('electron-updater');

const GAME_URL = 'https://claude.ai/artifact/HXLGUyMzRccDRD6kgM4Uh4';
let win;

function createWindow() {
  win = new BrowserWindow({
    width: 1440, height: 900, minWidth: 1000, minHeight: 650,
    backgroundColor: '#050b1a', title: 'Battle of Talingchan', autoHideMenuBar: true,
    webPreferences: { partition: 'persist:talingchan', contextIsolation: true, sandbox: true }
  });
  Menu.setApplicationMenu(null);
  win.loadURL(GAME_URL);
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://claude.ai') || url.startsWith('https://accounts.google.com')) return { action: 'allow' };
    shell.openExternal(url); return { action: 'deny' };
  });
  win.webContents.on('did-fail-load', () => {
    win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(
      '<body style="background:#050b1a;color:#9fe;font:20px sans-serif;text-align:center;padding-top:20vh">' +
      'เชื่อมต่ออินเทอร์เน็ตไม่ได้<br><br><button onclick="location.href=\'' + GAME_URL + '\'" style="font-size:18px;padding:8px 20px">ลองใหม่</button></body>'));
  });
  win.webContents.on('before-input-event', (e, i) => {
    if (i.key === 'F11' && i.type === 'keyDown') win.setFullScreen(!win.isFullScreen());
    if (i.key === 'F5' && i.type === 'keyDown') win.webContents.reload();
  });
}

function setupUpdater() {
  autoUpdater.autoDownload = false;
  autoUpdater.on('update-available', async (info) => {
    const r = await dialog.showMessageBox(win, { type: 'info', buttons: ['อัพเดทเลย', 'ไว้ทีหลัง'], defaultId: 0,
      title: 'มีเวอร์ชันใหม่', message: 'มีเวอร์ชันใหม่ ' + info.version, detail: 'ดาวน์โหลดและอัพเดทได้ทันที ไม่ต้องติดตั้งใหม่' });
    if (r.response === 0) autoUpdater.downloadUpdate();
  });
  autoUpdater.on('update-downloaded', async () => {
    const r = await dialog.showMessageBox(win, { type: 'info', buttons: ['รีสตาร์ทตอนนี้', 'ภายหลัง'], defaultId: 0,
      title: 'พร้อมอัพเดท', message: 'ดาวน์โหลดเสร็จแล้ว', detail: 'รีสตาร์ทเพื่อใช้เวอร์ชันใหม่' });
    if (r.response === 0) autoUpdater.quitAndInstall();
  });
  autoUpdater.on('error', () => {});
  autoUpdater.checkForUpdates().catch(() => {});
}

app.whenReady().then(() => { createWindow(); if (app.isPackaged) setupUpdater(); });
app.on('window-all-closed', () => app.quit());
