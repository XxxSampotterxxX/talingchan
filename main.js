// Battle of Talingchan desktop (v2): เกมอยู่ในแอป ไม่ต้องล็อกอิน / ออนไลน์เครื่องต่อเครื่อง
const { app, BrowserWindow, dialog, shell, Menu } = require('electron');
const { autoUpdater } = require('electron-updater');
const path = require('path');
let win;

function createWindow() {
  win = new BrowserWindow({
    width: 1440, height: 900, minWidth: 1000, minHeight: 650,
    backgroundColor: '#050b1a', title: 'Battle of Talingchan', autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, sandbox: true }
  });
  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'game', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (/^https?:/.test(url)) { e.preventDefault(); shell.openExternal(url); } });
  win.webContents.on('before-input-event', (e, i) => {
    if (i.type !== 'keyDown') return;
    if (i.key === 'F11') win.setFullScreen(!win.isFullScreen());
    if (i.key === 'F5') win.webContents.reload();
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
