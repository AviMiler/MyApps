// ==UserScript==
// @name         יד 2 — דיסלייק על מודעות + הערות
// @namespace    https://github.com/AviMiler/MyApps
// @version      1.0
// @description  סימון מודעות ביד 2 כלא רלוונטיות, עם הערה חופשית, שנשמר ומוצג בכל סוגי העמודים
// @match        https://www.yad2.co.il/*
// @match        https://yad2.co.il/*
// @match        https://m.yad2.co.il/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

// נוצר אוטומטית מ-app/src/main/assets/yad2_dislike.js — אין לערוך כאן.
// להרצה מחדש: tools/build-userscript.sh
/* ==========================================================================
 * yad2_dislike.js — "תוסף דיסלייק" ליד 2
 * --------------------------------------------------------------------------
 * מסמן מודעות ביד 2 כ"לא רלוונטי" (דיסלייק), מאפשר לכתוב הערה חופשית על כל
 * מודעה שסומנה, ומציג את הסימון + ההערה שוב ושוב בכל פעם שהמודעה מופיעה
 * באתר — בכל סוגי העמודים (פיד תוצאות, מפה, קרוסלת "מודעות דומות", מועדפים,
 * היסטוריה, ועמוד המודעה עצמו).
 *
 * הקובץ הזה עצמאי לחלוטין ורץ בשני מצבים:
 *   1. בתוך אפליקציית האנדרואיד — המידע נשמר בצד האנדרואיד דרך הגשר
 *      window.Yad2Store (שורד ניקוי עוגיות/אחסון של הדפדפן, וגם גיבוי).
 *   2. כ-userscript רגיל בדפדפן (Tampermonkey וכו') — נופל אחורה ל-localStorage.
 *
 * עקרון תכנון מרכזי: האתר בנוי React/Next.js. לכן התוסף כמעט ולא נוגע ב-DOM
 * של האתר — הוא מוסיף לכרטיס המודעה *תכונות* (data-y2d-*) בלבד, וכל ממשק
 * המשתמש (כפתורים, תוויות, חלוניות) מצויר בשכבה נפרדת משלו שמוצמדת ל-<body>
 * ומתמקמת מעל הכרטיסים לפי הקואורדינטות שלהם. כך React לא "נלחם" בנו ולא
 * מתרסק על צמתים שהוא לא יצר.
 * ========================================================================== */

(function () {
  'use strict';

  if (window.__YAD2_DISLIKE__) {           // כבר מותקן בדף הזה — רק רענון
    window.__YAD2_DISLIKE__.rescan();
    return;
  }

  /* ======================================================================
   * 1. זיהוי מודעה — המזהה של מודעה בכל סוגי העמודים
   * ----------------------------------------------------------------------
   * המזהה היציב היחיד של מודעה ביד 2 הוא הטוקן שבכתובת עמוד המודעה:
   *
   *   /realestate/item/<token>     נדל"ן (מכירה / השכרה / מסחרי)
   *   /vehicles/item/<token>       רכב (פרטי, אופנוע, משאית, ג'יפ, ימי...)
   *   /products/item/<token>       יד שנייה / מוצרים
   *   /items/item/<token>          וריאציות פיד נוספות
   *   /item/<token>                צורה גנרית / ישנה
   *   ?opendoc=<id> ?item=<id>     צורות ישנות מבוססות query-string
   *
   * אותו טוקן בדיוק מופיע ב-href של הכרטיס בפיד, ב-href בקרוסלת "מודעות
   * דומות", בכרטיס שנפתח מעל המפה, בעמוד המועדפים, וכמובן ב-location.href
   * של עמוד המודעה עצמו. לכן הטוקן הוא המפתח שמאחד את כל סוגי העמודים,
   * והוא מה שנשמר באחסון.
   *
   * לא מסתמכים על שמות מחלקות (class) — הם מתחלפים בכל build של האתר
   * (CSS Modules עם hash). לא מסתמכים גם על data-testid/data-nagish כמזהה
   * ראשי, כי הערכים שם מתארים תפקיד ולא מודעה. הם משמשים רק כגיבוי.
   * ====================================================================== */

  var PATH_ITEM_RE = /(?:^|\/)(realestate|vehicles|products|items|item|commercial|b2b|shop|pets|cars)\/item\/([A-Za-z0-9_-]{3,40})/i;
  var BARE_ITEM_RE = /(?:^|\/)item\/([A-Za-z0-9_-]{3,40})/i;
  var QUERY_KEYS   = ['item', 'itemid', 'item_id', 'opendoc', 'adnumber', 'ad_number', 'orderid', 'order_id', 'token'];
  var TOKEN_RE     = /^[A-Za-z0-9_-]{3,40}$/;

  // מילים שמופיעות ב-URL אבל אינן טוקן של מודעה
  var NOT_A_TOKEN = {
    'new': 1, 'edit': 1, 'search': 1, 'index': 1, 'null': 1, 'undefined': 1,
    'item': 1, 'items': 1, 'map': 1, 'list': 1, 'feed': 1
  };

  /** מפרק href/URL ומחזיר {key, cat} או null. */
  function idFromUrl(raw) {
    if (!raw) return null;
    var url = String(raw);
    if (url.charAt(0) === '#' || /^(javascript|mailto|tel):/i.test(url)) return null;

    var m = PATH_ITEM_RE.exec(url);
    if (m && !NOT_A_TOKEN[m[2].toLowerCase()]) {
      return { key: m[2].toLowerCase(), cat: m[1].toLowerCase(), kind: 'token' };
    }
    m = BARE_ITEM_RE.exec(url);
    if (m && !NOT_A_TOKEN[m[1].toLowerCase()]) {
      return { key: m[1].toLowerCase(), cat: '', kind: 'token' };
    }

    var q = url.indexOf('?');
    if (q >= 0) {
      var params = url.slice(q + 1).split(/[&;]/);
      for (var i = 0; i < params.length; i++) {
        var eq = params[i].indexOf('=');
        if (eq < 0) continue;
        var name = decodeURIComponent(params[i].slice(0, eq)).toLowerCase();
        var val  = decodeURIComponent(params[i].slice(eq + 1));
        if (QUERY_KEYS.indexOf(name) >= 0 && TOKEN_RE.test(val) && !NOT_A_TOKEN[val.toLowerCase()]) {
          return { key: val.toLowerCase(), cat: catFromPath(url), kind: 'token' };
        }
      }
    }
    return null;
  }

  function catFromPath(url) {
    var m = /(?:^|\/)(realestate|vehicles|products|items|commercial|b2b|shop|pets)(?:\/|\?|$)/i.exec(url);
    return m ? m[1].toLowerCase() : '';
  }

  /* גיבוי א': תכונות data שמכילות מזהה מספרי/טוקן על הכרטיס עצמו.
   * נלקח רק אם הערך *נראה* כמו מזהה (מכיל ספרה) — כדי לא לתפוס
   * ערכים תיאוריים כמו "feed-item-list-box". */
  var ID_ATTRS = ['data-item-id', 'data-itemid', 'data-ad-id', 'data-adid',
                  'data-listing-id', 'data-order-id', 'data-token', 'data-id', 'id'];

  function idFromAttrs(el) {
    for (var n = el, depth = 0; n && depth < 4; n = n.parentElement, depth++) {
      if (!n.getAttribute) continue;
      for (var i = 0; i < ID_ATTRS.length; i++) {
        var v = n.getAttribute(ID_ATTRS[i]);
        if (!v) continue;
        v = v.trim();
        if (!TOKEN_RE.test(v) || !/\d/.test(v) || NOT_A_TOKEN[v.toLowerCase()]) continue;
        return { key: v.toLowerCase(), cat: catFromPath(location.pathname), kind: 'attr' };
      }
    }
    return null;
  }

  /* גיבוי ב': טביעת אצבע של תוכן הכרטיס (כותרת + מחיר + שם קובץ התמונה).
   * משמש רק כשאין קישור ואין תכונת מזהה — כך שגם אז הסימון נשמר. */
  function idFromContent(el) {
    var txt = (el.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 160);
    var img = el.querySelector('img');
    var src = img ? (img.getAttribute('src') || img.getAttribute('data-src') || '') : '';
    src = src.split('?')[0].split('/').pop() || '';
    var basis = txt + '|' + src;
    if (basis.length < 12) return null;
    return { key: 'h' + fnv1a(basis), cat: catFromPath(location.pathname), kind: 'hash' };
  }

  function fnv1a(str) {
    var h = 0x811c9dc5;
    for (var i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = (h + ((h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24))) >>> 0;
    }
    return h.toString(36);
  }

  /** המזהה של המודעה שהעמוד הנוכחי הוא עמוד המודעה שלה (או null). */
  function currentPageAd() {
    return idFromUrl(location.href);
  }

  /* ======================================================================
   * 2. שכבת אחסון — גשר אנדרואיד, עם נפילה אחורה ל-localStorage
   * ====================================================================== */

  var LS_DATA = 'yad2.dislikes.v1';
  var LS_PREFS = 'yad2.dislikes.prefs.v1';

  var Store = (function () {
    var bridge = (typeof window.Yad2Store !== 'undefined') ? window.Yad2Store : null;
    var cache = {};
    var prefs = { mode: 'mark', showButtons: true };

    function readJson(str, fallback) {
      try { var v = JSON.parse(str); return (v && typeof v === 'object') ? v : fallback; }
      catch (e) { return fallback; }
    }

    function load() {
      if (bridge) {
        cache = readJson(bridge.getAll(), {});
        prefs = Object.assign(prefs, readJson(bridge.getSettings(), {}));
      } else {
        cache = readJson(localStorage.getItem(LS_DATA), {});
        prefs = Object.assign(prefs, readJson(localStorage.getItem(LS_PREFS), {}));
      }
    }

    function persistLocal() {
      try { localStorage.setItem(LS_DATA, JSON.stringify(cache)); } catch (e) {}
    }
    function persistPrefs() {
      if (bridge) { bridge.putSettings(JSON.stringify(prefs)); }
      else { try { localStorage.setItem(LS_PREFS, JSON.stringify(prefs)); } catch (e) {} }
    }

    load();

    return {
      has:  function (key) { return Object.prototype.hasOwnProperty.call(cache, key); },
      get:  function (key) { return cache[key] || null; },
      all:  function () { return cache; },
      count: function () { return Object.keys(cache).length; },
      put: function (rec) {
        rec.updated = Date.now();
        if (!rec.ts) rec.ts = rec.updated;
        cache[rec.key] = rec;
        if (bridge) bridge.put(rec.key, JSON.stringify(rec)); else persistLocal();
      },
      remove: function (key) {
        delete cache[key];
        if (bridge) bridge.remove(key); else persistLocal();
      },
      clear: function () {
        cache = {};
        if (bridge) bridge.clear(); else persistLocal();
      },
      replaceAll: function (obj) {
        cache = obj || {};
        if (bridge) { bridge.clear(); for (var k in cache) if (cache.hasOwnProperty(k)) bridge.put(k, JSON.stringify(cache[k])); }
        else persistLocal();
      },
      prefs: function () { return prefs; },
      setPref: function (k, v) { prefs[k] = v; persistPrefs(); },
      hasBridge: function () { return !!bridge; },
      toast: function (msg) { if (bridge && bridge.toast) bridge.toast(msg); }
    };
  })();

  /* ======================================================================
   * 3. עיצוב
   * ====================================================================== */

  var CSS = [
    /* --- מה שנוגע בכרטיס עצמו: רק אפקט ויזואלי לפי תכונה --- */
    '[data-y2d-state="disliked"]{filter:grayscale(.85) contrast(.92);opacity:.42;',
    '  outline:2px solid rgba(214,48,49,.75)!important;outline-offset:-2px;border-radius:8px;',
    '  transition:opacity .15s ease,filter .15s ease}',
    '[data-y2d-state="disliked"]:hover{opacity:.85;filter:grayscale(.2)}',
    'body[data-y2d-mode="collapse"] [data-y2d-state="disliked"]{max-height:0!important;min-height:0!important;',
    '  height:0!important;margin:0!important;padding:0!important;border:0!important;outline:0!important;',
    '  overflow:hidden!important;opacity:0!important}',
    'body[data-y2d-mode="hide"] [data-y2d-state="disliked"]{display:none!important}',

    /* --- שכבת הממשק שלנו (מחוץ לעץ של React) --- */
    '#y2d-layer{position:fixed;inset:0;z-index:2147483000;pointer-events:none;overflow:hidden;',
    '  direction:rtl;font-family:Arial,"Segoe UI",sans-serif}',
    '#y2d-layer *{box-sizing:border-box}',
    '.y2d-ov{position:absolute;pointer-events:none}',
    '.y2d-btn{position:absolute;bottom:6px;inset-inline-start:6px;pointer-events:auto;',
    '  width:34px;height:34px;border-radius:50%;border:none;cursor:pointer;',
    '  background:rgba(255,255,255,.94);box-shadow:0 2px 8px rgba(0,0,0,.28);',
    '  font-size:17px;line-height:34px;text-align:center;padding:0;color:#333;',
    '  -webkit-tap-highlight-color:transparent;transition:transform .1s ease}',
    '.y2d-btn:active{transform:scale(.88)}',
    '.y2d-btn.on{background:#d63031;color:#fff}',
    '.y2d-tag{position:absolute;top:6px;inset-inline-start:6px;inset-inline-end:6px;pointer-events:auto;',
    '  background:rgba(214,48,49,.96);color:#fff;border-radius:8px;padding:5px 9px;',
    '  font-size:12px;font-weight:700;line-height:1.35;cursor:pointer;',
    '  box-shadow:0 2px 8px rgba(0,0,0,.3);max-height:66px;overflow:hidden}',
    '.y2d-tag .n{display:block;font-weight:400;font-size:11.5px;opacity:.97;margin-top:2px;',
    '  white-space:pre-wrap;word-break:break-word}',

    /* --- כפתור צף לניהול --- */
    '#y2d-fab{position:fixed;inset-inline-start:12px;bottom:calc(12px + env(safe-area-inset-bottom,0px));',
    '  pointer-events:auto;width:46px;height:46px;border-radius:50%;border:none;cursor:pointer;',
    '  background:#2d3436;color:#fff;font-size:20px;box-shadow:0 3px 12px rgba(0,0,0,.4);opacity:.82}',
    '#y2d-fab:active{opacity:1}',
    '#y2d-fab .c{position:absolute;top:-4px;inset-inline-end:-4px;background:#d63031;color:#fff;',
    '  min-width:20px;height:20px;border-radius:10px;font-size:11px;line-height:20px;padding:0 5px;font-weight:700}',

    /* --- חלוניות --- */
    '.y2d-scrim{position:fixed;inset:0;background:rgba(0,0,0,.55);pointer-events:auto;',
    '  display:flex;align-items:flex-end;justify-content:center}',
    '.y2d-sheet{pointer-events:auto;background:#fff;color:#111;width:100%;max-width:560px;',
    '  max-height:88vh;overflow:auto;border-radius:16px 16px 0 0;padding:16px;',
    '  box-shadow:0 -4px 24px rgba(0,0,0,.4);-webkit-overflow-scrolling:touch}',
    '.y2d-sheet h3{margin:0 0 4px;font-size:17px}',
    '.y2d-sheet .sub{font-size:12.5px;color:#636e72;margin:0 0 12px;word-break:break-word}',
    '.y2d-sheet textarea{width:100%;min-height:92px;border:1px solid #dfe6e9;border-radius:10px;',
    '  padding:10px;font-size:15px;font-family:inherit;resize:vertical;direction:rtl}',
    '.y2d-chips{display:flex;flex-wrap:wrap;gap:6px;margin:10px 0}',
    '.y2d-chip{background:#f1f2f6;border:1px solid #dfe6e9;border-radius:14px;padding:5px 11px;',
    '  font-size:12.5px;cursor:pointer;color:#2d3436}',
    '.y2d-chip:active{background:#dfe6e9}',
    '.y2d-row{display:flex;gap:8px;margin-top:12px}',
    '.y2d-act{flex:1;border:none;border-radius:10px;padding:11px;font-size:14.5px;font-weight:700;',
    '  cursor:pointer;font-family:inherit}',
    '.y2d-act.p{background:#d63031;color:#fff}',
    '.y2d-act.s{background:#f1f2f6;color:#2d3436}',
    '.y2d-act.d{background:#fff;color:#d63031;border:1px solid #d63031}',
    '.y2d-list{margin-top:10px}',
    '.y2d-item{border:1px solid #ecf0f1;border-radius:10px;padding:10px;margin-bottom:8px}',
    '.y2d-item .t{font-size:13.5px;font-weight:700;color:#2d3436;word-break:break-word}',
    '.y2d-item .m{font-size:11.5px;color:#95a5a6;margin-top:2px}',
    '.y2d-item .n{font-size:13px;color:#c0392b;margin-top:5px;white-space:pre-wrap;word-break:break-word}',
    '.y2d-item .b{display:flex;gap:6px;margin-top:8px}',
    '.y2d-item .b button{flex:1;border:1px solid #dfe6e9;background:#fff;border-radius:8px;',
    '  padding:7px;font-size:12.5px;cursor:pointer;font-family:inherit;color:#2d3436}',
    '.y2d-search{width:100%;border:1px solid #dfe6e9;border-radius:10px;padding:9px;font-size:14px;',
    '  direction:rtl;font-family:inherit}',
    '.y2d-modes{display:flex;gap:6px;margin:10px 0}',
    '.y2d-modes button{flex:1;border:1px solid #dfe6e9;background:#fff;border-radius:8px;padding:8px;',
    '  font-size:12.5px;cursor:pointer;font-family:inherit;color:#2d3436}',
    '.y2d-modes button.on{background:#2d3436;color:#fff;border-color:#2d3436}',
    '.y2d-empty{text-align:center;color:#95a5a6;font-size:13px;padding:22px 0}',

    /* מצב לילה בסיסי */
    '@media (prefers-color-scheme:dark){',
    '  .y2d-sheet{background:#1e272e;color:#ecf0f1}',
    '  .y2d-sheet textarea,.y2d-search{background:#2d3436;color:#ecf0f1;border-color:#3d484d}',
    '  .y2d-chip,.y2d-act.s,.y2d-modes button,.y2d-item .b button{background:#2d3436;color:#ecf0f1;border-color:#3d484d}',
    '  .y2d-item{border-color:#3d484d}.y2d-item .t{color:#ecf0f1}',
    '}'
  ].join('\n');

  /* ======================================================================
   * 4. תשתית DOM של התוסף
   * ====================================================================== */

  var layer = null, fab = null;
  var overlays = Object.create(null);   // key -> {wrap, btn, tag, card}
  var knownCards = [];                  // [{el, id}] מהסריקה האחרונה

  function ready(fn) {
    if (document.body) { fn(); }
    else { document.addEventListener('DOMContentLoaded', fn, { once: true }); }
  }

  function injectCss() {
    if (document.getElementById('y2d-css')) return;
    var s = document.createElement('style');
    s.id = 'y2d-css';
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  function buildLayer() {
    if (layer && document.body.contains(layer)) return;
    // ניקוי שכבה שנשארה ממופע קודם של הסקריפט (הזרקה חוזרת אחרי שהמשמר אופס),
    // כדי שלא יצטברו שתי שכבות ושני כפתורים צפים על אותו עמוד.
    var stale = document.querySelectorAll('#y2d-layer');
    for (var i = 0; i < stale.length; i++) stale[i].remove();
    pageBar = null;
    layer = document.createElement('div');
    layer.id = 'y2d-layer';
    document.body.appendChild(layer);

    fab = document.createElement('button');
    fab.id = 'y2d-fab';
    fab.type = 'button';
    fab.title = 'המודעות שסימנתי בדיסלייק';
    fab.innerHTML = '📋<span class="c">0</span>';
    fab.addEventListener('click', function (e) { e.preventDefault(); e.stopPropagation(); openPanel(); });
    layer.appendChild(fab);
    overlays = Object.create(null);
    updateFab();
  }

  function updateFab() {
    if (!fab) return;
    var c = Store.count();
    fab.querySelector('.c').textContent = c > 999 ? '999+' : String(c);
  }

  /* ======================================================================
   * 5. איתור כרטיסי מודעה
   * ----------------------------------------------------------------------
   * לא לפי class. מוצאים כל <a> שמצביע לעמוד מודעה, ומטפסים כלפי מעלה עד
   * לאלמנט הגדול ביותר שעדיין מכיל *מודעה אחת בלבד* — זה הכרטיס הוויזואלי.
   * העלייה נעצרת גם לפי גובה, כדי לא "לבלוע" את כל הפיד כשיש מודעה בודדת.
   * ====================================================================== */

  var LINK_SEL = 'a[href*="item"],a[href*="Item"],a[href*="opendoc"]';
  var STOP_TAGS = { BODY: 1, MAIN: 1, HEADER: 1, FOOTER: 1, NAV: 1, ASIDE: 1, FORM: 1, HTML: 1 };

  function distinctAdIds(el) {
    var seen = Object.create(null), n = 0;
    var links = el.querySelectorAll(LINK_SEL);
    for (var i = 0; i < links.length && n < 3; i++) {
      var id = idFromUrl(links[i].getAttribute('href'));
      if (id && !seen[id.key]) { seen[id.key] = 1; n++; }
    }
    return n;
  }

  /* טיפוס העץ הוא החלק היקר בסריקה: לכל עוגן הוא בודק כמה מודעות יש בכל
   * אב-קדמון. בפיד עם עשרות מודעות, וסריקה שחוזרת כל שנייה וחצי, זה מצטבר.
   * המבנה סביב עוגן כמעט אף פעם לא משתנה, ולכן התוצאה נשמרת ב-WeakMap
   * ומחושבת מחדש רק אם ה-href השתנה או שהכרטיס נותק מהעמוד. */
  var rootCache = new WeakMap();

  function findCardRootCached(anchor, href) {
    var hit = rootCache.get(anchor);
    if (hit && hit.href === href && hit.card.isConnected) return hit.card;
    var card = findCardRoot(anchor);
    rootCache.set(anchor, { href: href, card: card });
    return card;
  }

  function findCardRoot(anchor) {
    var best = anchor, node = anchor, maxH = Math.max(window.innerHeight * 1.25, 700);
    for (var i = 0; i < 10; i++) {
      var p = node.parentElement;
      if (!p || STOP_TAGS[p.tagName]) break;
      if (distinctAdIds(p) > 1) break;
      var h = p.getBoundingClientRect().height;
      if (h > maxH) break;
      best = p;
      node = p;
    }
    return best;
  }

  function collectCards() {
    var out = [], seenEl = new Set(), byKey = Object.create(null);
    var links = document.querySelectorAll(LINK_SEL);

    for (var i = 0; i < links.length; i++) {
      var a = links[i];
      var href = a.getAttribute('href') || a.href;
      var id = idFromUrl(href);
      if (!id) continue;
      var card = findCardRootCached(a, href);
      if (seenEl.has(card)) continue;
      seenEl.add(card);
      if (!id.cat) id.cat = catFromPath(href) || catFromPath(location.pathname);
      id.url = absUrl(href);
      // אותה מודעה יכולה להופיע פעמיים בעמוד (למשל גם בפיד וגם בקרוסלה) —
      // שומרים את שתי המופעים כדי שיסומנו שניהם.
      out.push({ el: card, id: id });
      byKey[id.key] = 1;
    }

    // עמוד המודעה עצמו: מסמנים את הדף, לא כרטיס.
    return out;
  }

  function absUrl(href) {
    try { return new URL(href, location.href).href; } catch (e) { return href; }
  }

  function cardTitle(el) {
    var h = el.querySelector('h1,h2,h3,h4,[class*="title" i],[data-testid*="title" i]');
    var t = h ? (h.innerText || '') : (el.innerText || '');
    t = t.replace(/\s+/g, ' ').trim();
    return t.slice(0, 90);
  }

  function cardPrice(el) {
    var m = /([\d,]{3,})\s*(?:₪|ש"ח|שח)/.exec(el.innerText || '');
    return m ? m[0].replace(/\s+/g, ' ').trim() : '';
  }

  /* ======================================================================
   * 6. סימון + ציור השכבה
   * ====================================================================== */

  function applyMarks() {
    knownCards = collectCards();
    var mode = Store.prefs().mode || 'mark';
    document.body.setAttribute('data-y2d-mode', mode);

    for (var i = 0; i < knownCards.length; i++) {
      var c = knownCards[i];
      if (c.el.getAttribute('data-y2d-key') !== c.id.key) {
        c.el.setAttribute('data-y2d-key', c.id.key);
      }
      var disliked = Store.has(c.id.key);
      var want = disliked ? 'disliked' : null;
      if (c.el.getAttribute('data-y2d-state') !== want) {
        if (want) c.el.setAttribute('data-y2d-state', want);
        else c.el.removeAttribute('data-y2d-state');
      }
    }
    markCurrentPage();
    positionOverlays();
    updateFab();
  }

  /* עמוד מודעה בודדת: אין "כרטיס" — מציגים פס עליון קבוע עם ההערה. */
  var pageBar = null;
  function markCurrentPage() {
    var id = currentPageAd();
    if (!id) { if (pageBar) { pageBar.remove(); pageBar = null; } return; }
    if (pageBar && !layer.contains(pageBar)) pageBar = null;
    if (!pageBar) {
      pageBar = document.createElement('div');
      pageBar.className = 'y2d-ov';
      pageBar.style.cssText = 'top:0;inset-inline-start:0;inset-inline-end:0;height:0';
      layer.appendChild(pageBar);
    }
    var rec = Store.get(id.key);
    pageBar.innerHTML = '';
    var tag = document.createElement('div');
    if (rec) {
      tag.className = 'y2d-tag';
      tag.style.cssText = 'position:relative;inset:auto;max-height:none;margin:8px;';
      tag.innerHTML = '👎 ' + esc('סימנת מודעה זו כלא רלוונטית') +
        (rec.note ? '<span class="n">' + esc(rec.note) + '</span>' : '') +
        '<span class="n" style="opacity:.85">(הקש לעריכה)</span>';
    } else {
      tag.className = 'y2d-tag';
      tag.style.cssText = 'position:relative;inset:auto;max-height:none;margin:8px;background:rgba(45,52,54,.94)';
      tag.innerHTML = '👎 ' + esc('סמן מודעה זו כלא רלוונטית + הערה');
    }
    tag.addEventListener('click', function (e) {
      e.preventDefault(); e.stopPropagation();
      openNote(pageAdRecord(id));
    });
    pageBar.appendChild(tag);
  }

  function pageAdRecord(id) {
    var existing = Store.get(id.key);
    if (existing) return existing;
    var h1 = document.querySelector('h1');
    return {
      key: id.key, kind: id.kind, cat: id.cat || catFromPath(location.pathname),
      url: location.href.split('#')[0],
      title: h1 ? (h1.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 90) : document.title.slice(0, 90),
      price: '', note: ''
    };
  }

  /* --- מיקום כפתורים/תוויות מעל הכרטיסים (בלי לגעת ב-DOM של האתר) --- */
  function positionOverlays() {
    if (!layer) return;
    var vh = window.innerHeight, vw = window.innerWidth;
    var live = Object.create(null);
    var showButtons = Store.prefs().showButtons !== false;
    var mode = Store.prefs().mode || 'mark';

    for (var i = 0; i < knownCards.length; i++) {
      var c = knownCards[i], el = c.el, key = c.id.key;
      var r = el.getBoundingClientRect();
      var visible = r.width > 40 && r.height > 40 &&
                    r.bottom > -80 && r.top < vh + 80 &&
                    r.right > -80 && r.left < vw + 80;
      var disliked = Store.has(key);
      if (!visible || (disliked && mode !== 'mark') || (!disliked && !showButtons)) continue;

      var slot = key + '#' + i;
      live[slot] = 1;
      var ov = overlays[slot];
      if (!ov) {
        ov = { wrap: document.createElement('div'), btn: null, tag: null };
        ov.wrap.className = 'y2d-ov';
        ov.btn = document.createElement('button');
        ov.btn.className = 'y2d-btn';
        ov.btn.type = 'button';
        ov.wrap.appendChild(ov.btn);
        layer.appendChild(ov.wrap);
        overlays[slot] = ov;
      }
      ov.card = c;
      ov.wrap.style.left = r.left + 'px';
      ov.wrap.style.top = r.top + 'px';
      ov.wrap.style.width = r.width + 'px';
      ov.wrap.style.height = r.height + 'px';

      ov.btn.textContent = disliked ? '↺' : '👎';
      ov.btn.title = disliked ? 'ביטול הסימון / עריכת ההערה' : 'סימון דיסלייק + הערה';
      ov.btn.className = 'y2d-btn' + (disliked ? ' on' : '');
      if (!ov.btn.__bound) {
        ov.btn.__bound = true;
        ov.btn.addEventListener('click', (function (o) {
          return function (e) {
            e.preventDefault(); e.stopPropagation();
            openNote(recordFor(o.card));
          };
        })(ov), true);
      }

      var rec = disliked ? Store.get(key) : null;
      if (rec) {
        if (!ov.tag) {
          ov.tag = document.createElement('div');
          ov.tag.className = 'y2d-tag';
          ov.tag.addEventListener('click', (function (o) {
            return function (e) {
              e.preventDefault(); e.stopPropagation();
              openNote(recordFor(o.card));
            };
          })(ov), true);
          ov.wrap.appendChild(ov.tag);
        }
        ov.tag.innerHTML = '👎 ' + esc('לא רלוונטי') +
          (rec.note ? '<span class="n">' + esc(rec.note) + '</span>' : '');
      } else if (ov.tag) {
        ov.tag.remove(); ov.tag = null;
      }
    }

    for (var k in overlays) {
      if (!live[k]) { overlays[k].wrap.remove(); delete overlays[k]; }
    }
  }

  function recordFor(card) {
    var existing = Store.get(card.id.key);
    if (existing) return existing;
    return {
      key: card.id.key,
      kind: card.id.kind,
      cat: card.id.cat,
      url: card.id.url || '',
      title: cardTitle(card.el),
      price: cardPrice(card.el),
      note: ''
    };
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /* ======================================================================
   * 7. חלונית ההערה
   * ====================================================================== */

  var REASONS = ['מחיר לא ריאלי', 'מתווך', 'רחוק מדי', 'תמונות מטעות',
                 'כבר נמכר / לא זמין', 'מצב גרוע', 'מוכר לא אמין', 'כבר ראיתי'];

  function openSheet(build) {
    closeSheet();
    var scrim = document.createElement('div');
    scrim.className = 'y2d-scrim';
    scrim.id = 'y2d-scrim';
    var sheet = document.createElement('div');
    sheet.className = 'y2d-sheet';
    scrim.appendChild(sheet);
    scrim.addEventListener('click', function (e) { if (e.target === scrim) closeSheet(); });
    layer.appendChild(scrim);
    build(sheet);
    return sheet;
  }

  function closeSheet() {
    var s = document.getElementById('y2d-scrim');
    if (s) s.remove();
  }

  function openNote(rec) {
    var isNew = !Store.has(rec.key);
    openSheet(function (sheet) {
      var head = document.createElement('div');
      head.innerHTML =
        '<h3>' + esc(isNew ? 'סימון המודעה כלא רלוונטית' : 'עריכת הסימון') + '</h3>' +
        '<p class="sub">' + esc(rec.title || '(ללא כותרת)') +
        (rec.price ? ' · ' + esc(rec.price) : '') +
        '<br>' + esc('מזהה מודעה: ' + rec.key + (rec.cat ? '  ·  ' + hebCat(rec.cat) : '')) + '</p>';
      sheet.appendChild(head);

      var ta = document.createElement('textarea');
      ta.placeholder = 'הערה על המודעה (למה היא לא רלוונטית?)';
      ta.value = rec.note || '';
      sheet.appendChild(ta);

      var chips = document.createElement('div');
      chips.className = 'y2d-chips';
      REASONS.forEach(function (r) {
        var b = document.createElement('button');
        b.type = 'button'; b.className = 'y2d-chip'; b.textContent = r;
        b.addEventListener('click', function () {
          ta.value = ta.value ? (ta.value.replace(/\s*$/, '') + ', ' + r) : r;
          ta.focus();
        });
        chips.appendChild(b);
      });
      sheet.appendChild(chips);

      var row = document.createElement('div');
      row.className = 'y2d-row';

      var save = document.createElement('button');
      save.className = 'y2d-act p';
      save.textContent = isNew ? 'שמור דיסלייק' : 'עדכן';
      save.addEventListener('click', function () {
        rec.note = ta.value.trim();
        Store.put(rec);
        closeSheet();
        applyMarks();
        Store.toast(isNew ? 'המודעה סומנה' : 'ההערה עודכנה');
      });
      row.appendChild(save);

      if (!isNew) {
        var del = document.createElement('button');
        del.className = 'y2d-act d';
        del.textContent = 'הסר סימון';
        del.addEventListener('click', function () {
          Store.remove(rec.key);
          closeSheet();
          applyMarks();
          Store.toast('הסימון הוסר');
        });
        row.appendChild(del);
      }

      var cancel = document.createElement('button');
      cancel.className = 'y2d-act s';
      cancel.textContent = 'ביטול';
      cancel.addEventListener('click', closeSheet);
      row.appendChild(cancel);

      sheet.appendChild(row);
      setTimeout(function () { try { ta.focus(); } catch (e) {} }, 60);
    });
  }

  function hebCat(cat) {
    var map = {
      realestate: 'נדל"ן', vehicles: 'רכב', products: 'יד שנייה',
      items: 'מודעות', commercial: 'מסחרי', b2b: 'עסקי', shop: 'חנות', pets: 'בעלי חיים'
    };
    return map[cat] || cat;
  }

  /* ======================================================================
   * 8. חלונית הניהול
   * ====================================================================== */

  function openPanel() {
    openSheet(function (sheet) {
      var all = Store.all();
      var keys = Object.keys(all).sort(function (a, b) {
        return (all[b].updated || 0) - (all[a].updated || 0);
      });

      var head = document.createElement('div');
      head.innerHTML = '<h3>' + esc('מודעות שסימנתי (' + keys.length + ')') + '</h3>' +
        '<p class="sub">' + esc(Store.hasBridge()
          ? 'הסימונים נשמרים באפליקציה עצמה ונשארים גם אחרי סגירה, ניקוי עוגיות או עדכון האתר.'
          : 'הסימונים נשמרים בדפדפן (localStorage).') + '</p>';
      sheet.appendChild(head);

      var modes = document.createElement('div');
      modes.className = 'y2d-modes';
      [['mark', 'עמעום + תווית'], ['collapse', 'כיווץ'], ['hide', 'הסתרה מלאה']].forEach(function (m) {
        var b = document.createElement('button');
        b.type = 'button';
        b.textContent = m[1];
        if ((Store.prefs().mode || 'mark') === m[0]) b.className = 'on';
        b.addEventListener('click', function () {
          Store.setPref('mode', m[0]);
          closeSheet(); applyMarks(); openPanel();
        });
        modes.appendChild(b);
      });
      sheet.appendChild(modes);

      var search = document.createElement('input');
      search.className = 'y2d-search';
      search.placeholder = 'חיפוש בכותרת / בהערה / במזהה';
      sheet.appendChild(search);

      var list = document.createElement('div');
      list.className = 'y2d-list';
      sheet.appendChild(list);

      function render(filter) {
        list.innerHTML = '';
        var f = (filter || '').trim().toLowerCase();
        var shown = 0;
        keys.forEach(function (k) {
          var r = all[k];
          if (f && (String(r.title || '') + ' ' + String(r.note || '') + ' ' + k).toLowerCase().indexOf(f) < 0) return;
          shown++;
          var it = document.createElement('div');
          it.className = 'y2d-item';
          var d = r.updated ? new Date(r.updated) : null;
          it.innerHTML =
            '<div class="t">' + esc(r.title || '(ללא כותרת)') + (r.price ? ' · ' + esc(r.price) : '') + '</div>' +
            '<div class="m">' + esc(hebCat(r.cat || '') + ' · מזהה ' + k +
              (d ? ' · ' + d.toLocaleDateString('he-IL') : '')) + '</div>' +
            (r.note ? '<div class="n">' + esc(r.note) + '</div>' : '');
          var b = document.createElement('div');
          b.className = 'b';

          if (r.url) {
            var open = document.createElement('button');
            open.textContent = 'פתח מודעה';
            open.addEventListener('click', function () { closeSheet(); location.href = r.url; });
            b.appendChild(open);
          }
          var edit = document.createElement('button');
          edit.textContent = 'עריכת הערה';
          edit.addEventListener('click', function () { openNote(r); });
          b.appendChild(edit);

          var rm = document.createElement('button');
          rm.textContent = 'הסר';
          rm.addEventListener('click', function () {
            Store.remove(k); it.remove(); applyMarks();
          });
          b.appendChild(rm);

          it.appendChild(b);
          list.appendChild(it);
        });
        if (!shown) {
          var e = document.createElement('div');
          e.className = 'y2d-empty';
          e.textContent = keys.length ? 'אין תוצאות לחיפוש' : 'עדיין לא סימנת מודעות';
          list.appendChild(e);
        }
      }
      search.addEventListener('input', function () { render(search.value); });
      render('');

      var row = document.createElement('div');
      row.className = 'y2d-row';

      var exp = document.createElement('button');
      exp.className = 'y2d-act s';
      exp.textContent = 'ייצוא';
      exp.addEventListener('click', function () { exportData(); });
      row.appendChild(exp);

      var imp = document.createElement('button');
      imp.className = 'y2d-act s';
      imp.textContent = 'ייבוא';
      imp.addEventListener('click', function () { importData(); });
      row.appendChild(imp);

      var close = document.createElement('button');
      close.className = 'y2d-act p';
      close.textContent = 'סגור';
      close.addEventListener('click', closeSheet);
      row.appendChild(close);

      sheet.appendChild(row);
    });
  }

  function exportData() {
    var json = JSON.stringify(Store.all(), null, 2);
    openSheet(function (sheet) {
      sheet.innerHTML = '<h3>ייצוא הסימונים</h3><p class="sub">העתק את הטקסט ושמור אותו אצלך.</p>';
      var ta = document.createElement('textarea');
      ta.style.minHeight = '220px';
      ta.value = json;
      sheet.appendChild(ta);
      var row = document.createElement('div'); row.className = 'y2d-row';
      var copy = document.createElement('button');
      copy.className = 'y2d-act p'; copy.textContent = 'העתק';
      copy.addEventListener('click', function () {
        ta.select();
        try { document.execCommand('copy'); Store.toast('הועתק'); } catch (e) {}
      });
      var back = document.createElement('button');
      back.className = 'y2d-act s'; back.textContent = 'חזרה';
      back.addEventListener('click', function () { closeSheet(); openPanel(); });
      row.appendChild(copy); row.appendChild(back);
      sheet.appendChild(row);
    });
  }

  function importData() {
    openSheet(function (sheet) {
      sheet.innerHTML = '<h3>ייבוא סימונים</h3><p class="sub">הדבק כאן JSON שיוצא קודם. הייבוא מתמזג עם הקיים.</p>';
      var ta = document.createElement('textarea');
      ta.style.minHeight = '220px';
      sheet.appendChild(ta);
      var row = document.createElement('div'); row.className = 'y2d-row';
      var ok = document.createElement('button');
      ok.className = 'y2d-act p'; ok.textContent = 'ייבא';
      ok.addEventListener('click', function () {
        try {
          var obj = JSON.parse(ta.value);
          if (!obj || typeof obj !== 'object') throw new Error('bad');
          var merged = Object.assign({}, Store.all());
          for (var k in obj) if (obj.hasOwnProperty(k) && obj[k] && obj[k].key) merged[k] = obj[k];
          Store.replaceAll(merged);
          closeSheet(); applyMarks(); openPanel();
          Store.toast('הייבוא הושלם');
        } catch (e) {
          Store.toast('JSON לא תקין');
        }
      });
      var back = document.createElement('button');
      back.className = 'y2d-act s'; back.textContent = 'חזרה';
      back.addEventListener('click', function () { closeSheet(); openPanel(); });
      row.appendChild(ok); row.appendChild(back);
      sheet.appendChild(row);
    });
  }

  /* ======================================================================
   * 9. מעקב אחרי שינויים בדף (SPA, גלילה אינסופית, מפה)
   * ====================================================================== */

  var scanTimer = null, rafPending = false;

  function scheduleScan(delay) {
    if (scanTimer) clearTimeout(scanTimer);
    scanTimer = setTimeout(function () { scanTimer = null; safeApply(); }, delay || 180);
  }

  function safeApply() {
    try { buildLayer(); applyMarks(); } catch (e) { /* לעולם לא לשבור את האתר */ }
  }

  function schedulePosition() {
    if (rafPending) return;
    rafPending = true;
    requestAnimationFrame(function () {
      rafPending = false;
      try { positionOverlays(); } catch (e) {}
    });
  }

  function install() {
    injectCss();
    buildLayer();
    applyMarks();

    var mo = new MutationObserver(function (muts) {
      for (var i = 0; i < muts.length; i++) {
        var t = muts[i].target;
        if (layer && (t === layer || layer.contains(t))) continue;   // התעלמות מהשינויים שלנו
        scheduleScan(200);
        return;
      }
    });
    mo.observe(document.documentElement, {
      childList: true, subtree: true,
      attributes: true, attributeFilter: ['href', 'src', 'class']
    });

    window.addEventListener('scroll', schedulePosition, { passive: true, capture: true });
    window.addEventListener('resize', function () { schedulePosition(); scheduleScan(250); }, { passive: true });
    window.addEventListener('orientationchange', function () { scheduleScan(350); });
    document.addEventListener('visibilitychange', function () { if (!document.hidden) scheduleScan(120); });

    // ניווט SPA (Next.js client-side routing)
    var push = history.pushState, repl = history.replaceState;
    history.pushState = function () { var r = push.apply(this, arguments); onRoute(); return r; };
    history.replaceState = function () { var r = repl.apply(this, arguments); onRoute(); return r; };
    window.addEventListener('popstate', onRoute);
    window.addEventListener('hashchange', onRoute);

    // רשת ביטחון: סריקה תקופתית קלה כל עוד הטאב גלוי
    setInterval(function () { if (!document.hidden) safeApply(); }, 2000);
  }

  var lastHref = location.href;
  function onRoute() {
    if (location.href === lastHref) return;
    lastHref = location.href;
    closeSheet();
    scheduleScan(250);
    scheduleScan(900);   // גם אחרי שהתוכן החדש נטען
  }

  /* ======================================================================
   * 10. API חיצוני (לאנדרואיד / למשתמש מתקדם)
   * ====================================================================== */

  window.__YAD2_DISLIKE__ = {
    version: '1.0',
    rescan: safeApply,
    openPanel: function () { safeApply(); openPanel(); },
    // נקרא מכפתור "חזור" של אנדרואיד: אם פתוחה חלונית של התוסף — סוגרים
    // אותה ומדווחים true, כדי שהאתר עצמו לא יחזור אחורה.
    handleBack: function () {
      if (document.getElementById('y2d-scrim')) { closeSheet(); return true; }
      return false;
    },
    count: function () { return Store.count(); },
    idFromUrl: idFromUrl,
    currentPageAd: currentPageAd,
    // הוספה/הסרה מתוכנתת של סימון. עוברת דרך אותו מסלול כמו לחיצה על
    // הכפתור: נשמר באחסון (גשר אנדרואיד או localStorage) והעמוד מסומן מחדש.
    set: function (rec) {
      if (!rec || !rec.key) return false;
      Store.put(rec);
      safeApply();
      return true;
    },
    unset: function (key) {
      if (!key) return false;
      Store.remove(key);
      safeApply();
      return true;
    },
    dump: function () { return JSON.stringify(Store.all()); }
  };

  ready(install);
})();
