# MyAppStore

ריפו שמכיל כמה אפליקציות אנדרואיד עצמאיות, כל אחת בתיקייה נפרדת משלה עם
פרויקט Gradle משלה (כולל Gradle Wrapper). ה-workflow המשותף
(`.github/workflows/build-apk.yml`) בונה APK debug לכל אחת מהאפליקציות בכל
push, ומעלה כל APK כ-artifact נפרד.

## אפליקציות

- [`AgoraApp/`](AgoraApp/) — דפדפן-קיוסק נעול לאתר agora.co.il בלבד.
- [`ClaudeCameraLauncher/`](ClaudeCameraLauncher/) — אפליקציית שיגור שפותחת
  מיד את אפליקציית Claude (ראו את ה-README של האפליקציה למגבלות).
- [`Yad2App/`](Yad2App/) — דפדפן-קיוסק לאתר yad2.co.il, עם תוסף לסימון מודעות
  בדיסלייק + הערה שנשמרת ומוצגת בכל סוגי העמודים.

## הוספת אפליקציה חדשה
צרו תיקייה חדשה בשורש הריפו עם פרויקט Gradle עצמאי (כולל `gradlew`,
`gradle/wrapper/`, `settings.gradle`, `build.gradle`, `app/`), והוסיפו אותה
לרשימת ה-matrix ב-`.github/workflows/build-apk.yml`.
