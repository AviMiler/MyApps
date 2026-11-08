# MyAppStore

ריפו שמכיל כמה אפליקציות אנדרואיד עצמאיות, כל אחת בתיקייה נפרדת משלה עם
פרויקט Gradle משלה (כולל Gradle Wrapper). ה-workflow המשותף
(`.github/workflows/build-apk.yml`) בונה APK debug לכל אחת מהאפליקציות בכל
push, ומעלה כל APK כ-artifact נפרד.

## אפליקציות

- [`AgoraApp/`](AgoraApp/) — דפדפן-קיוסק נעול לאתר agora.co.il בלבד.
- [`AlarmClock/`](AlarmClock/) — שעון מעורר מלא עם עקיפת הגדרות הצליל של
  הטלפון ומשך צלצול חופשי בדקות ושניות.
- [`ClaudeCameraLauncher/`](ClaudeCameraLauncher/) — אפליקציית שיגור שפותחת
  מיד את אפליקציית Claude (ראו את ה-README של האפליקציה למגבלות).
- [`HebrewCalendar/`](HebrewCalendar/) — לוח שנה עברי עם חגים, סנכרון ליומן Google
  וווידג'ט חודשי למסך הבית.
- [`SmsForwarder/`](SmsForwarder/) — **הלאה**: העברה אוטומטית של הודעות SMS
  מאנשי קשר נבחרים לאיש קשר אחר, עם מסלולים, סינון וקודי אימות, מצב שבת וחג,
  שעות שקט, תשובה חוזרת ועוד.
- [`Yad2App/`](Yad2App/) — דפדפן-קיוסק לאתר yad2.co.il, עם תוסף לסימון מודעות
  בדיסלייק + הערה שנשמרת ומוצגת בכל סוגי העמודים.

## כללי עבודה
עובדים **רק על `main`**. לא פותחים ענפים נוספים ולא מפזרים עבודה בין ענפים:
כל שינוי נעשה ונדחף ישירות ל-`main`.

## הוספת אפליקציה חדשה
צרו תיקייה חדשה בשורש הריפו עם פרויקט Gradle עצמאי (כולל `gradlew`,
`gradle/wrapper/`, `settings.gradle`, `build.gradle`, `app/`), והוסיפו אותה
לרשימת ה-matrix ב-`.github/workflows/build-apk.yml`.
