const fs = require('fs');
const { JSDOM, VirtualConsole } = require('jsdom');

const SRC = fs.readFileSync('/home/user/MyApps/Yad2App/app/src/main/assets/yad2_dislike.js', 'utf8');

let pass = 0, fail = 0;
function ok(name, cond, extra) {
  if (cond) { pass++; console.log('  ✓ ' + name); }
  else { fail++; console.log('  ✗ ' + name + (extra !== undefined ? '  → got: ' + JSON.stringify(extra) : '')); }
}
function eq(name, got, want) { ok(name + ' = ' + JSON.stringify(want), JSON.stringify(got) === JSON.stringify(want), got); }

function makeDom(html, url) {
  const vc = new VirtualConsole(); // swallow CSS parse noise
  const dom = new JSDOM(html, { url, runScripts: 'outside-only', pretendToBeVisual: true, virtualConsole: vc });
  const w = dom.window;
  // jsdom returns all-zero rects; give every element a plausible box so the
  // overlay/visibility logic has something real to work with.
  w.Element.prototype.getBoundingClientRect = function () {
    const h = Number(this.getAttribute('data-h') || 0);
    const height = h || (this.tagName === 'BODY' ? 4000 : 220);
    return { x: 10, y: 50, left: 10, top: 50, right: 340, bottom: 50 + height,
             width: 330, height, toJSON() { return this; } };
  };
  w.eval(SRC);
  return w;
}

/* ------------------------------------------------------------------ */
console.log('\n[1] חילוץ מזהה מודעה מכל צורות ה-URL של יד 2');
const w0 = makeDom('<body></body>', 'https://www.yad2.co.il/');
const API = w0.__YAD2_DISLIKE__;
const id = (u) => { const r = API.idFromUrl(u); return r ? r.key + '|' + r.cat : null; };

eq('נדל"ן מכירה',        id('https://www.yad2.co.il/realestate/item/abc12345'), 'abc12345|realestate');
eq('נדל"ן יחסי',          id('/realestate/item/h9k2m4p1'),                      'h9k2m4p1|realestate');
eq('רכב',                 id('/vehicles/item/xy77zz01?opened=1'),               'xy77zz01|vehicles');
eq('יד שנייה',            id('https://www.yad2.co.il/products/item/q1w2e3r4'),  'q1w2e3r4|products');
eq('פיד items',           id('/items/item/aa11bb22'),                           'aa11bb22|items');
eq('גנרי /item/',         id('https://www.yad2.co.il/item/zz99yy88'),           'zz99yy88|');
eq('עם UTM',              id('/realestate/item/tok55555?utm_source=fb&x=1'),    'tok55555|realestate');
eq('query ישן opendoc',   id('/realestate/forsale?opendoc=123456789'),          '123456789|realestate');
eq('query ישן item',      id('https://www.yad2.co.il/vehicles/cars?item=98765'),'98765|vehicles');
eq('אותיות גדולות',       id('/RealEstate/Item/AbC12345'),                      'abc12345|realestate');

eq('דף חיפוש אינו מודעה', id('https://www.yad2.co.il/realestate/forsale'),      null);
eq('דף בית אינו מודעה',   id('https://www.yad2.co.il/'),                        null);
eq('עוגן אינו מודעה',     id('#'),                                              null);
eq('javascript: נדחה',    id('javascript:void(0)'),                             null);
eq('/item/new נדחה',      id('/realestate/item/new'),                           null);
eq('קטגוריה אינה מודעה',  id('/vehicles/cars'),                                 null);

console.log('\n[2] אותה מודעה = אותו מזהה בכל סוגי העמודים');
const same = [
  'https://www.yad2.co.il/realestate/item/kt83nd12',           // עמוד המודעה
  '/realestate/item/kt83nd12',                                  // כרטיס בפיד
  '/realestate/item/kt83nd12?utm_medium=share',                 // שיתוף
  'https://www.yad2.co.il/realestate/item/kt83nd12#gallery',    // מהמפה
  '/realestate/item/KT83ND12'                                   // וריאציית אותיות
].map(u => API.idFromUrl(u).key);
ok('חמש צורות → מזהה אחד (' + same[0] + ')', new Set(same).size === 1, same);

/* ------------------------------------------------------------------ */
console.log('\n[3] איתור כרטיסי מודעה בפיד (בלי להסתמך על שמות class)');

const FEED = `<body>
 <header><a href="/realestate/forsale">נדל"ן</a></header>
 <main>
  <div class="sc-hash-1">
    <ul>
      <li class="xy_9f2"><article data-h="240">
         <a href="/realestate/item/aaa11111"><img src="https://img.yad2.co.il/p/1.jpg"><h2>דירה 4 חדרים, תל אביב</h2></a>
         <span>2,450,000 ₪</span>
         <button aria-label="favorite">♥</button>
      </article></li>
      <li class="xy_9f2"><article data-h="240">
         <a href="/realestate/item/bbb22222"><img src="https://img.yad2.co.il/p/2.jpg"><h2>פנטהאוז, רמת גן</h2></a>
         <span>4,100,000 ₪</span>
      </article></li>
      <li class="xy_9f2"><article data-h="240">
         <a href="/realestate/item/ccc33333"><h2>גן ילדים למכירה</h2></a>
         <span>890,000 ₪</span>
      </article></li>
    </ul>
  </div>
  <section aria-label="מודעות דומות">
     <a href="/realestate/item/aaa11111" data-h="150"><h3>דירה 4 חדרים, תל אביב</h3></a>
     <a href="/realestate/item/ddd44444" data-h="150"><h3>דירת גן, גבעתיים</h3></a>
  </section>
 </main>
</body>`;

const w1 = makeDom(FEED, 'https://www.yad2.co.il/realestate/forsale');
const marked = [...w1.document.querySelectorAll('[data-y2d-key]')];
const keys = marked.map(e => e.getAttribute('data-y2d-key'));
eq('מספר כרטיסים שזוהו', marked.length, 5);
eq('המזהים שזוהו', [...new Set(keys)].sort(), ['aaa11111','bbb22222','ccc33333','ddd44444']);
ok('כרטיס = <article> ולא כל הרשימה', marked.slice(0,3).every(e => e.tagName === 'ARTICLE' || e.tagName === 'LI'),
   marked.slice(0,3).map(e=>e.tagName));
ok('aaa11111 סומן פעמיים (פיד + קרוסלה)', keys.filter(k => k === 'aaa11111').length === 2);
ok('הקישור בהדר לא נחשב מודעה', !w1.document.querySelector('header [data-y2d-key]'));

/* ------------------------------------------------------------------ */
console.log('\n[4] דיסלייק + הערה נשמרים ומסומנים');

const rec = { key: 'bbb22222', kind: 'token', cat: 'realestate',
              url: 'https://www.yad2.co.il/realestate/item/bbb22222',
              title: 'פנטהאוז, רמת גן', price: '4,100,000 ₪', note: 'יקר מדי, מתווך' };
w1.eval('window.localStorage.setItem("yad2.dislikes.v1", ' + JSON.stringify(JSON.stringify({ bbb22222: rec })) + ')');

// טעינה מחדש של אותו עמוד — כמו כניסה חוזרת לאתר
const w2 = makeDom(FEED, 'https://www.yad2.co.il/realestate/forsale');
w2.localStorage.setItem('yad2.dislikes.v1', JSON.stringify({ bbb22222: rec }));
w2.eval('delete window.__YAD2_DISLIKE__;');
w2.eval(SRC);

const bad = w2.document.querySelector('[data-y2d-key="bbb22222"]');
eq('הכרטיס קיבל data-y2d-state', bad && bad.getAttribute('data-y2d-state'), 'disliked');
const good = w2.document.querySelector('[data-y2d-key="aaa11111"]');
eq('כרטיס שלא סומן נשאר נקי', good && good.getAttribute('data-y2d-state'), null);
ok('נוצרה שכבת ממשק נפרדת', !!w2.document.getElementById('y2d-layer'));
ok('הממשק לא הוזרק לתוך הכרטיס', bad.querySelectorAll('.y2d-btn,.y2d-tag').length === 0);
const tags = [...w2.document.querySelectorAll('#y2d-layer .y2d-tag')].map(t => t.textContent);
ok('ההערה מוצגת על המודעה', tags.some(t => t.indexOf('יקר מדי, מתווך') >= 0), tags);
eq('מונה ה-FAB', w2.document.querySelector('#y2d-fab .c').textContent, '1');
eq('__YAD2_DISLIKE__.count()', w2.__YAD2_DISLIKE__.count(), 1);

/* ------------------------------------------------------------------ */
console.log('\n[5] מצבי תצוגה');
w2.localStorage.setItem('yad2.dislikes.prefs.v1', JSON.stringify({ mode: 'hide' }));
const w3 = makeDom(FEED, 'https://www.yad2.co.il/realestate/forsale');
w3.localStorage.setItem('yad2.dislikes.v1', JSON.stringify({ bbb22222: rec }));
w3.localStorage.setItem('yad2.dislikes.prefs.v1', JSON.stringify({ mode: 'hide' }));
w3.eval('delete window.__YAD2_DISLIKE__;');
w3.eval(SRC);
eq('body data-y2d-mode', w3.document.body.getAttribute('data-y2d-mode'), 'hide');
ok('CSS מכיל כלל הסתרה',
   w3.document.getElementById('y2d-css').textContent.indexOf('body[data-y2d-mode="hide"] [data-y2d-state="disliked"]{display:none') >= 0);

/* ------------------------------------------------------------------ */
console.log('\n[6] עמוד מודעה בודדת');
const ITEM = `<body><main><h1>דירה 4 חדרים, תל אביב</h1>
  <div>2,450,000 ₪</div>
  <section><a href="/realestate/item/bbb22222" data-h="150"><h3>פנטהאוז, רמת גן</h3></a></section>
</main></body>`;
const w4 = makeDom(ITEM, 'https://www.yad2.co.il/realestate/item/aaa11111');
w4.localStorage.setItem('yad2.dislikes.v1', JSON.stringify({ aaa11111: Object.assign({}, rec, { key:'aaa11111', note:'רחוק מהעבודה' }) }));
w4.eval('delete window.__YAD2_DISLIKE__;');
w4.eval(SRC);
eq('זוהתה מודעת העמוד', w4.__YAD2_DISLIKE__.currentPageAd().key, 'aaa11111');
const bar = [...w4.document.querySelectorAll('#y2d-layer .y2d-tag')].map(t => t.textContent).join(' | ');
ok('פס עליון מציג את ההערה של המודעה הנוכחית', bar.indexOf('רחוק מהעבודה') >= 0, bar);
ok('גם מודעה שקושרה בעמוד סומנה', !!w4.document.querySelector('[data-y2d-key="bbb22222"]'));

/* ------------------------------------------------------------------ */
console.log('\n[7] גשר אנדרואיד (Yad2Store) מקבל עדיפות על localStorage');
const w5 = makeDom(FEED, 'https://www.yad2.co.il/realestate/forsale');
const written = {};
w5.Yad2Store = {
  getAll: () => JSON.stringify({ ccc33333: Object.assign({}, rec, { key:'ccc33333', note:'מהגשר' }) }),
  getSettings: () => JSON.stringify({ mode: 'mark' }),
  put: (k, v) => { written[k] = v; },
  remove: (k) => { delete written[k]; },
  clear: () => {},
  putSettings: () => {},
  toast: () => {}
};
w5.eval('delete window.__YAD2_DISLIKE__;');
w5.eval(SRC);
eq('נטען מהגשר ולא מהאחסון המקומי', w5.__YAD2_DISLIKE__.count(), 1);
eq('הכרטיס מהגשר סומן',
   w5.document.querySelector('[data-y2d-key="ccc33333"]').getAttribute('data-y2d-state'), 'disliked');

/* ------------------------------------------------------------------ */
console.log('\n[8] עמידות: הזרקה כפולה, DOM שמשתנה, אתר לא מוכר');
const w6 = makeDom(FEED, 'https://www.yad2.co.il/realestate/forsale');
w6.eval(SRC); w6.eval(SRC);   // הזרקה חוזרת כמו ב-onPageFinished
eq('שכבה אחת בלבד', w6.document.querySelectorAll('#y2d-layer').length, 1);
eq('גיליון סגנון אחד בלבד', w6.document.querySelectorAll('#y2d-css').length, 1);
eq('כפתור צף אחד בלבד', w6.document.querySelectorAll('#y2d-fab').length, 1);

// React מחליף את הכרטיס — הסריקה החוזרת חייבת לסמן מחדש
const li = w6.document.querySelector('[data-y2d-key="aaa11111"]');
li.removeAttribute('data-y2d-key');
w6.__YAD2_DISLIKE__.rescan();
eq('סימון מחדש אחרי החלפת DOM', li.getAttribute('data-y2d-key'), 'aaa11111');

const w7 = makeDom('<body><a href="/some/page">שלום</a><div>אין כאן מודעות</div></body>', 'https://www.yad2.co.il/');
eq('עמוד בלי מודעות — 0 סימונים', w7.document.querySelectorAll('[data-y2d-key]').length, 0);
ok('ובכל זאת נטען בלי לזרוק שגיאה', !!w7.__YAD2_DISLIKE__);


/* ------------------------------------------------------------------ */
console.log('\n[9] אינטגרציה: ה-bootstrap של אנדרואיד + nonce');

// משחזר בדיוק את מה ש-MainActivity.injectPlugin מריץ
const NONCE = 'a1b2c3d4e5f6';
const BOOTSTRAP = `
(function () {
  var N = ${JSON.stringify(NONCE)};
  var B = window.Yad2Bridge;
  if (B && !window.Yad2Store) {
    window.Yad2Store = {
      getAll:      function ()      { return B.getAll(N); },
      getSettings: function ()      { return B.getSettings(N); },
      put:         function (k, v)  { B.put(N, k, v); },
      remove:      function (k)     { B.remove(N, k); },
      clear:       function ()      { B.clear(N); },
      putSettings: function (s)     { B.putSettings(N, s); },
      toast:       function (m)     { B.toast(N, m); }
    };
  }
})();
`;

// חיקוי של Yad2Bridge.kt: כל קריאה בלי ה-nonce הנכון נדחית
function fakeAndroidBridge(seed) {
  const db = Object.assign({}, seed);
  let settings = '{}';
  const calls = { rejected: 0 };
  return {
    calls, db,
    getAll: (n) => (n === NONCE ? JSON.stringify(db) : (calls.rejected++, '{}')),
    getSettings: (n) => (n === NONCE ? settings : (calls.rejected++, '{}')),
    put: (n, k, v) => { if (n !== NONCE) { calls.rejected++; return; } db[k] = JSON.parse(v); },
    remove: (n, k) => { if (n !== NONCE) { calls.rejected++; return; } delete db[k]; },
    clear: (n) => { if (n !== NONCE) { calls.rejected++; return; } for (const k in db) delete db[k]; },
    putSettings: (n, s) => { if (n !== NONCE) { calls.rejected++; return; } settings = s; },
    toast: (n) => { if (n !== NONCE) calls.rejected++; }
  };
}

const w8 = makeDom(FEED, 'https://www.yad2.co.il/realestate/forsale');
const androidBridge = fakeAndroidBridge({ aaa11111: { key: 'aaa11111', cat: 'realestate', title: 'דירה', note: 'קומה גבוהה בלי מעלית' } });
w8.Yad2Bridge = androidBridge;
w8.eval('delete window.__YAD2_DISLIKE__;');
w8.eval(BOOTSTRAP);
w8.eval(SRC);

eq('נטען דרך הגשר של אנדרואיד', w8.__YAD2_DISLIKE__.count(), 1);
eq('הכרטיס סומן מהאחסון של האפליקציה',
   w8.document.querySelector('[data-y2d-key="aaa11111"]').getAttribute('data-y2d-state'), 'disliked');
ok('ההערה מהאפליקציה מוצגת',
   [...w8.document.querySelectorAll('#y2d-layer .y2d-tag')].some(t => t.textContent.indexOf('קומה גבוהה בלי מעלית') >= 0));

// כתיבה: דיסלייק חדש חייב להגיע ל-SharedPreferences דרך הגשר
w8.__YAD2_DISLIKE__.set({ key: 'ccc33333', kind: 'token', cat: 'realestate',
                         url: '/realestate/item/ccc33333', title: 'גן ילדים למכירה', note: 'לא מעניין' });
ok('כתיבה נשמרה בצד אנדרואיד', !!androidBridge.db.ccc33333, Object.keys(androidBridge.db));
eq('ההערה שנשמרה', androidBridge.db.ccc33333.note, 'לא מעניין');
ok('נוסף חותם זמן', typeof androidBridge.db.ccc33333.updated === 'number');
eq('הכרטיס סומן מיד',
   w8.document.querySelector('[data-y2d-key="ccc33333"]').getAttribute('data-y2d-state'), 'disliked');
eq('מונה ה-FAB התעדכן', w8.document.querySelector('#y2d-fab .c').textContent, '2');

w8.__YAD2_DISLIKE__.unset('ccc33333');
ok('הסרה מחקה מהאחסון של אנדרואיד', !androidBridge.db.ccc33333);
eq('והסימון ירד מהכרטיס',
   w8.document.querySelector('[data-y2d-key="ccc33333"]').getAttribute('data-y2d-state'), null);

// iframe של צד שלישי: יש לו את Yad2Bridge אבל אין לו את ה-nonce
eq('קריאה בלי nonce מוחזרת ריקה', androidBridge.getAll('nonce-שגוי'), '{}');
eq('קריאה עם null מוחזרת ריקה', androidBridge.getAll(null), '{}');
ok('הניסיונות נדחו', androidBridge.calls.rejected >= 2, androidBridge.calls.rejected);
androidBridge.remove('לא-נכון', 'aaa11111');
ok('מחיקה בלי nonce לא השפיעה', !!androidBridge.db.aaa11111);

console.log('\n[10] כפתור "חזור" של אנדרואיד סוגר קודם חלונית של התוסף');
eq('בלי חלונית פתוחה — לא בולע את החזרה', w8.__YAD2_DISLIKE__.handleBack(), false);
w8.__YAD2_DISLIKE__.openPanel();
ok('החלונית נפתחה', !!w8.document.getElementById('y2d-scrim'));
eq('עם חלונית פתוחה — בולע את החזרה', w8.__YAD2_DISLIKE__.handleBack(), true);
ok('החלונית נסגרה', !w8.document.getElementById('y2d-scrim'));


/* ------------------------------------------------------------------ */
console.log('\n[11] פיד גדול: נכונות + עלות סריקה חוזרת');

let big = '<body><main><ul>';
for (let i = 0; i < 80; i++) {
  big += `<li><article data-h="220">
    <a href="/vehicles/item/car${String(i).padStart(5,'0')}"><img src="https://img.yad2.co.il/c/${i}.jpg">
    <h2>מאזדה 3 שנת 201${i % 10}</h2></a><span>${60 + i},000 ₪</span></article></li>`;
}
big += '</ul></main></body>';

const t0 = Date.now();
const w9 = makeDom(big, 'https://www.yad2.co.il/vehicles/cars');
const firstScan = Date.now() - t0;
eq('כל 80 המודעות זוהו', w9.document.querySelectorAll('[data-y2d-key]').length, 80);
eq('מזהה ראשון', w9.document.querySelector('[data-y2d-key]').getAttribute('data-y2d-key'), 'car00000');

const t1 = Date.now();
for (let i = 0; i < 20; i++) w9.__YAD2_DISLIKE__.rescan();
const twenty = Date.now() - t1;
console.log('    (סריקה ראשונה ' + firstScan + 'ms, עוד 20 סריקות ' + twenty + 'ms)');
ok('20 סריקות חוזרות על 80 מודעות מתחת ל-3 שניות', twenty < 3000, twenty + 'ms');

console.log('\n──────────────────────────────');
console.log(pass + ' עברו, ' + fail + ' נכשלו');
process.exit(fail ? 1 : 0);
