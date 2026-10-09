/* 从 docs/prototype/情侣备忘录-原型.html 提取种子数据 → SeedData.kt
   用法: node tools/gen_seed.js */
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../docs/prototype/情侣备忘录-原型.html'), 'utf8');

function extract(startMarker, endMarker) {
  const i = html.indexOf(startMarker);
  if (i < 0) throw new Error('not found: ' + startMarker);
  const from = i + startMarker.length;
  const j = html.indexOf(endMarker, from);
  if (j < 0) throw new Error('end not found: ' + endMarker);
  let s = html.slice(from, j).trim();
  if (s.endsWith(';')) s = s.slice(0, -1); // 只去语句分号，保留数组/对象自身的闭合
  return s;
}
function parseJS(literal) {
  return new Function('return (' + literal + ')')();
}
const kStr = (s) => JSON.stringify(s).replace(/\\\u0027/g, "'"); // JSON 转义是 Kotlin 合法子集

// ── POOL_GROUPS（翻篇方式）──
const POOL_GROUPS = parseJS(extract('const POOL_GROUPS=', '\nconst ACT_GROUPS'));
// ── ACT_GROUPS（互动）──
const ACT_GROUPS = parseJS(extract('const ACT_GROUPS=', '\nconst ACT=ACT_GROUPS'));
// ── RSEED（分寸种子）──
const RSEED = parseJS(extract('const RSEED=', '\nlet ruleCat'));
// ── QS（吵架样例）──
const QS = parseJS(extract('const QS=', '\nconst POOL_GROUPS'));

// 校验
const poolCount = POOL_GROUPS.reduce((a, g) => a + g.list.length, 0);
const actCount = ACT_GROUPS.reduce((a, g) => a + g.list.length, 0);
console.log('POOL 条数 =', poolCount, ' ACT 条数 =', actCount, ' RSEED =', RSEED.length, ' QS =', QS.length);
const lvBase = { 表达: 1, 作息: 1, 趣味: 1, 云陪: 2, 跑腿: 2, 投喂: 2, 手作: 3, 承诺: 3, 寄送: 4, 见面时: 5 };
const lvOff = [0, 1, 0, -1, 0, 1, -1, 0];
const lvDist = {};
POOL_GROUPS.forEach(g => g.list.forEach((t, i) => {
  let lv = (lvBase[g.c] || 2) + lvOff[i % 8];
  if (g.m && lv < 3) lv = 3;
  lv = Math.min(5, Math.max(1, lv));
  lvDist[lv] = (lvDist[lv] || 0) + 1;
}));
console.log('等级分布 =', JSON.stringify(lvDist));

let out = [];
out.push('package com.xiaoman.memo.data');
out.push('');
out.push('/* 由 tools/gen_seed.js 从「docs/prototype/情侣备忘录-原型.html」自动生成，请勿手改。 */');
out.push('');
out.push('data class PoolGroup(val c: String, val m: Boolean, val list: List<String>)');
out.push('data class ActGroup(val c: String, val list: List<String>)');
out.push('');
out.push('object Seed {');
out.push('    val POOL_GROUPS = listOf(');
POOL_GROUPS.forEach((g, gi) => {
  const parts = g.list.map(kStr).join(',\n        ');
  out.push(`        PoolGroup(${kStr(g.c)}, ${g.m ? 1 : 0} == 1, listOf(`);
  out.push('        ' + parts);
  out.push(gi === POOL_GROUPS.length - 1 ? '        ))' : '        )),');
});
out.push('    )');
out.push('');
out.push('    val ACT_GROUPS = listOf(');
ACT_GROUPS.forEach((g, gi) => {
  const parts = g.list.map(kStr).join(',\n        ');
  out.push(`        ActGroup(${kStr(g.c)}, listOf(`);
  out.push('        ' + parts);
  out.push(gi === ACT_GROUPS.length - 1 ? '        ))' : '        )),');
});
out.push('    )');
out.push('');
out.push('    /* cat,who,text,line,agreed */');
out.push('    val RSEED = listOf(');
RSEED.forEach((r, i) => {
  out.push(`        RuleSeed(${kStr(r.cat)}, ${kStr(r.who)}, ${kStr(r.t)}, ${!!r.line}, ${!!r.agreed})${i === RSEED.length - 1 ? '' : ','}`);
});
out.push('    )');
out.push('');
out.push('    data class RuleSeed(val cat: String, val who: String, val text: String, val line: Boolean, val agreed: Boolean)');
out.push('}');

fs.writeFileSync(path.join(__dirname, '../android/app/src/main/java/com/xiaoman/memo/data/SeedData.kt'), out.join('\n'));
console.log('written SeedData.kt, lines =', out.length);
