# Claude Camera

אפליקציית Android עם ממשק משלה: לחיצה על "צלם תמונה" פותחת את המצלמה,
לאחר הצילום אפשר לערוך את התמונה (סיבוב, וסימון/ציור באצבע בשלושה
צבעים, עם ביטול ומחיקה), ובלחיצה אחת על "שתף עם Claude" התמונה
הערוכה נשלחת ישירות לאפליקציית **Claude** (`com.anthropic.claude`)
דרך מנגנון השיתוף הרגיל של אנדרואיד (Share Sheet).

אם Claude לא מותקנת במכשיר, האפליקציה נופלת חזרה לתפריט השיתוף
הרגיל של אנדרואיד במקום לקרוס.

## איך זה עובד
1. `MainActivity` מבקש הרשאת מצלמה ומפעיל את אפליקציית המצלמה
   המובנית של המכשיר (`ActivityResultContracts.TakePicture`), שומרת
   את הקובץ ב-cache דרך `FileProvider`.
2. `DrawView` מציגה את התמונה ומאפשרת ציור באצבע מעליה; סיבוב ב-90
   מעלות ורשימת קווים (strokes) נשמרים בנפרד מהביטמאפ המקורי כדי
   שביטול/ניקוי יהיו זולים.
3. בלחיצה על שיתוף, `DrawView.renderFlattened()` "אופה" את הסיבוב
   והציורים לתוך ביטמאפ אחת, שנשמרת כ-JPEG ומשותפת עם
   `Intent.ACTION_SEND` המכוון לחבילת `com.anthropic.claude`.

## איך בונים APK
1. פתח את התיקייה הזו ב-Android Studio (Open an existing project).
2. תן ל-Gradle להסתנכרן.
3. Build > Build Bundle(s)/APK(s) > Build APK(s).
4. ה-APK יהיה תחת `app/build/outputs/apk/debug/app-debug.apk`.

או דרך ה-GitHub Actions workflow המשותף בריפו (`.github/workflows/build-apk.yml`),
שבונה APK debug אוטומטית בכל push ומעלה אותו כ-artifact.
