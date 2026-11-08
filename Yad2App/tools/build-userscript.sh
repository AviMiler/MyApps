#!/usr/bin/env bash
# בונה את גרסת ה-userscript (לדפדפן שולחני עם Tampermonkey) מתוך *אותו*
# קובץ מקור שהאפליקציה מזריקה, כדי שלא תיווצר סטייה בין השניים.
set -euo pipefail
cd "$(dirname "$0")/.."

SRC="app/src/main/assets/yad2_dislike.js"
OUT="userscript/yad2-dislike.user.js"

{
  cat <<'HEADER'
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
HEADER
  cat "$SRC"
} > "$OUT"

echo "נבנה: $OUT"
