// ดาวน์โหลดรูปการ์ดที่ยังไม่อยู่ในชุดภาพ (packs) จาก cdn.bottcg.com มาไว้ใน game/cards/ ตอน build
// ชื่อไฟล์ที่เว็บใช้: รหัส (เช่น PRMO-141) หรือ รหัส-ความหายาก (เช่น B2SA-006-SR) และอาจอยู่ในโฟลเดอร์ย่อย
const fs = require('fs'), path = require('path');
const keys = require('./missing.json');
const out = path.join(__dirname, '..', 'game', 'cards');
fs.mkdirSync(out, { recursive: true });
const FOLDERS = ['', 'thankyoupnes/', '123v1k1/', '0aslabe/'];
const HDR = { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120 Safari/537.36', Referer: 'https://bottcg.com/' };
const names = (k) => { const dash = k.replace('_', '-'); return [...new Set([k, dash, dash + '-C'])]; };
(async () => {
  let ok = 0; const bad = [];
  for (const k of keys) {
    let done = false;
    for (const n of names(k)) {
      for (const f of FOLDERS) {
        try {
          const r = await fetch('https://cdn.bottcg.com/cards/' + f + n + '.png', { headers: HDR });
          if (!r.ok) continue;
          const buf = Buffer.from(await r.arrayBuffer());
          if (buf.length < 2000 || buf[1] !== 0x50) continue; // ต้องเป็น PNG
          fs.writeFileSync(path.join(out, k + '.png'), buf); ok++; done = true; break;
        } catch (e) {}
      }
      if (done) break;
    }
    if (!done) bad.push(k);
  }
  console.log('art downloaded', ok, '/', keys.length, bad.length ? 'missing: ' + bad.join(',') : '');
})();
