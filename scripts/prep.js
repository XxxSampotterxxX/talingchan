// คัดลอก PeerJS จาก node_modules มาไว้ในโฟลเดอร์เกม (รันอัตโนมัติหลัง npm install)
const fs = require('fs'), path = require('path');
const src = path.join(__dirname, '..', 'node_modules', 'peerjs', 'dist', 'peerjs.min.js');
const dst = path.join(__dirname, '..', 'game', 'peerjs.min.js');
if (!fs.existsSync(src)) { console.error('peerjs not found'); process.exit(1); }
fs.copyFileSync(src, dst); console.log('copied peerjs');
