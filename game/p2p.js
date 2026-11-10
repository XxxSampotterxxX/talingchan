// ระบบห้องออนไลน์แบบเครื่องต่อเครื่อง (WebRTC ผ่าน PeerJS) ใช้แทนห้องของ Artifact
// หน้าตา API เหมือน window.claude.use('room') เพื่อไม่ต้องแก้โค้ดเกม
(function () {
  if (window.claude && window.claude.use) return;
  const PREFIX = 'tlchan-v1-';
  const CFG = {
    debug: 0,
    config: {
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:global.stun.twilio.com:3478' },
        { urls: ['turn:openrelay.metered.ca:80', 'turn:openrelay.metered.ca:443', 'turn:openrelay.metered.ca:443?transport=tcp'], username: 'openrelayproject', credential: 'openrelayproject' }
      ]
    }
  };
  const wantRole = () => (typeof OL !== 'undefined' && OL.role) || null;

  function mkRoom(name) {
    const id = PREFIX + String(name).toLowerCase().replace(/[^a-z0-9-]/g, '');
    const handlers = {};
    const peersCb = [];
    const me = { isMe: true }, other = { isMe: false };
    let conn = null, peer = null, done = false;

    const notify = (joined, left) => {
      const ps = conn && conn.open ? [me, other] : [me];
      peersCb.slice().forEach(f => { try { f({ peers: ps, joined: joined || [], left: left || [] }); } catch (e) {} });
    };
    function wire(c) {
      if (conn && conn !== c) { try { conn.close(); } catch (e) {} }
      conn = c;
      c.on('data', d => {
        if (!d || !d.t) return;
        (handlers[d.t] || []).slice().forEach(f => { try { f({ isMe: false, data: d.d, topic: d.t }); } catch (e) { console.error(e); } });
      });
      c.on('open', () => notify([other], []));
      c.on('close', () => { if (conn === c) { conn = null; notify([], [other]); } });
      c.on('error', () => {});
      if (c.open) notify([other], []);
    }
    const api = {
      on(t, f) { (handlers[t] = handlers[t] || []).push(f); return () => { handlers[t] = (handlers[t] || []).filter(x => x !== f); }; },
      emit(t, d) { try { if (conn && conn.open) conn.send({ t, d }); } catch (e) {} return Promise.resolve(); },
      onPeers(f) { peersCb.push(f); return () => { const i = peersCb.indexOf(f); if (i >= 0) peersCb.splice(i, 1); }; },
      presence() { return Promise.resolve(); },
      leave() { done = true; try { conn && conn.close(); } catch (e) {} try { peer && peer.destroy(); } catch (e) {} conn = null; peer = null; return Promise.resolve(); }
    };

    return new Promise((resolve, reject) => {
      if (typeof Peer === 'undefined') return reject(new Error('ไม่พบ PeerJS'));
      let settled = false;
      const ok = () => { if (!settled) { settled = true; clearTimeout(timer); resolve(api); } };
      const fail = m => { if (!settled) { settled = true; clearTimeout(timer); try { peer && peer.destroy(); } catch (e) {} reject(new Error(m)); } };
      const timer = setTimeout(() => fail('หมดเวลา เชื่อมต่อเซิร์ฟเวอร์จับคู่ไม่ได้ (ตรวจอินเทอร์เน็ต/ไฟร์วอลล์)'), 20000);

      function asHost() {
        peer = new Peer(id, CFG);
        peer.on('open', ok);
        peer.on('connection', c => { if (!done) wire(c); });
        peer.on('disconnected', () => { if (!done) { try { peer.reconnect(); } catch (e) {} } });
        peer.on('error', e => {
          if (e.type === 'unavailable-id') fail('รหัสห้องนี้ถูกใช้อยู่แล้ว ลองสร้างใหม่');
          else if (!settled) fail('ต่อเซิร์ฟเวอร์ไม่ได้: ' + (e.type || e.message || e));
        });
      }
      function asGuest() {
        peer = new Peer(undefined, CFG);
        peer.on('error', e => {
          if (e.type === 'peer-unavailable') fail('ไม่พบห้องรหัสนี้ (เช็กรหัส และให้โฮสต์สร้างห้องก่อน)');
          else if (!settled) fail('ต่อเซิร์ฟเวอร์ไม่ได้: ' + (e.type || e.message || e));
        });
        peer.on('open', () => {
          const c = peer.connect(id, { reliable: true, serialization: 'json' });
          wire(c);
          c.on('open', ok);
        });
        peer.on('disconnected', () => { if (!done) { try { peer.reconnect(); } catch (e) {} } });
      }
      (wantRole() === 'guest' ? asGuest : asHost)();
    });
  }
  window.claude = { use: async n => (n === 'room' ? { join: async name => mkRoom(name) } : null) };
})();
