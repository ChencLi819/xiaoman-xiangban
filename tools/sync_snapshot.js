// 小满同步快照的加解密小工具（与 App 内 data/SyncTransport.kt 完全一致的算法）
//   key = SHA-256("xiaoman-sync|" + 配对码)；AES-256-GCM，12 字节随机 IV，
//   文件内容 = base64(iv) + "." + base64(ciphertext||tag)
//
// 用法:
//   node tools/sync_snapshot.js dec <加密文件> <配对码> [输出.json]   # 解密查看（默认打印）
//   node tools/sync_snapshot.js enc <明文.json> <配对码> <输出文件>   # 加密（伪造对端快照用）
//   node tools/sync_snapshot.js memos <加密文件> <配对码>            # 只列出 memos 的 text/privateOnly
const fs = require('fs');
const crypto = require('crypto');

const keyOf = (code) => crypto.createHash('sha256').update('xiaoman-sync|' + code, 'utf8').digest();

function decrypt(payload, code) {
  const [ivB64, ctB64] = payload.trim().split('.');
  const iv = Buffer.from(ivB64, 'base64');
  const ct = Buffer.from(ctB64, 'base64');
  const tag = ct.subarray(ct.length - 16);
  const body = ct.subarray(0, ct.length - 16);
  const d = crypto.createDecipheriv('aes-256-gcm', keyOf(code), iv);
  d.setAuthTag(tag);
  return Buffer.concat([d.update(body), d.final()]).toString('utf8');
}

function encrypt(plain, code) {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', keyOf(code), iv);
  const body = Buffer.concat([c.update(plain, 'utf8'), c.final()]);
  return iv.toString('base64') + '.' + Buffer.concat([body, c.getAuthTag()]).toString('base64');
}

const [cmd, a, b, c] = process.argv.slice(2);
if (cmd === 'dec') {
  const out = decrypt(fs.readFileSync(a, 'utf8'), b);
  if (c) { fs.writeFileSync(c, out); console.log('WROTE ' + c); } else console.log(out);
} else if (cmd === 'enc') {
  fs.writeFileSync(c, encrypt(fs.readFileSync(a, 'utf8'), b));
  console.log('WROTE ' + c);
} else if (cmd === 'memos') {
  const root = JSON.parse(decrypt(fs.readFileSync(a, 'utf8'), b));
  const ms = root.memos || [];
  console.log(`memos=${ms.length}`);
  ms.forEach((m) => console.log(`  privateOnly=${!!m.privateOnly} deleted=${!!m.deleted} text=${JSON.stringify(m.text)}`));
} else if (cmd === 'list') {
  // list <加密文件> <配对码> <表名> [字段...]，默认打印 deleted 与几个常用文本字段
  const root = JSON.parse(decrypt(fs.readFileSync(a, 'utf8'), b));
  const rows = root[c] || [];
  const fields = process.argv.slice(6, 20);
  const pick = fields.length ? fields : ['uuid', 'deleted', 'updatedAt'];
  console.log(`${c}=${rows.length}`);
  rows.forEach((r) => console.log('  ' + pick.map((k) => `${k}=${JSON.stringify(r[k])}`).join(' ')));
} else {
  console.log('usage: dec <file> <code> [out] | enc <json> <code> <out> | memos <file> <code> | list <file> <code> <table> [fields...]');
  process.exit(2);
}
