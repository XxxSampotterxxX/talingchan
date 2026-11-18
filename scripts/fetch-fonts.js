// ดาวน์โหลดฟอนต์ Google Fonts ที่หน้าเกมใช้ มาเก็บในเครื่อง ตอน build (ให้แอปแสดงฟอนต์ได้แม้ไม่มีเน็ต)
// ใช้: node scripts/fetch-fonts.js [ไฟล์ html] [โฟลเดอร์ฟอนต์]
const fs = require('fs'), path = require('path');
const html = process.argv[2] || path.join(__dirname, '..', 'game', 'index.html');
const outDir = process.argv[3] || path.join(path.dirname(html), 'fonts');
const UA = { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36' };
(async () => {
  let src = fs.readFileSync(html, 'utf8');
  const re = /<link[^>]+href="(https:\/\/fonts\.googleapis\.com\/css2[^"]+)"[^>]*>/g;
  const links = [...src.matchAll(re)];
  if (!links.length) { console.log('no google fonts link'); return; }
  fs.mkdirSync(outDir, { recursive: true });
  let css = '', n = 0;
  for (const m of links) {
    const url = m[1].replace(/&amp;/g, '&');
    try {
      const r = await fetch(url, { headers: UA });
      if (!r.ok) throw new Error('css ' + r.status);
      let t = await r.text();
      const urls = [...new Set([...t.matchAll(/url\((https:[^)]+)\)/g)].map(x => x[1]))];
      for (const u of urls) {
        const name = 'f' + (n++) + path.extname(new URL(u).pathname);
        const fr = await fetch(u, { headers: UA });
        if (!fr.ok) continue;
        fs.writeFileSync(path.join(outDir, name), Buffer.from(await fr.arrayBuffer()));
        t = t.split(u).join(name);
      }
      css += t + '\n';
    } catch (e) { console.log('font fetch failed (keep online link):', e.message); return; }
  }
  fs.writeFileSync(path.join(outDir, 'fonts.css'), css);
  const rel = path.relative(path.dirname(html), path.join(outDir, 'fonts.css')).split(path.sep).join('/');
  src = src.replace(re, `<link rel="stylesheet" href="${rel}">`).replace(/<link rel="preconnect" href="https:\/\/fonts\.googleapis\.com">\s*/g, '');
  fs.writeFileSync(html, src);
  console.log('fonts saved', n, 'files →', outDir);
})();
