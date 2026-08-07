# Claude Camera Launcher

אפליקציית "שיגור" (launcher shim) ללא ממשק משלה: לחיצה על האייקון פותחת מיד את
אפליקציית **Claude** (`com.anthropic.claude`) וסוגרת את עצמה.

## חשוב: אין דילוג ישיר למצלמה בתוך Claude
נכון להיום Anthropic לא מפרסמת deep link / Intent ציבורי שקופץ ישר למסך המצלמה
בתוך אפליקציית Claude. לכן האפליקציה פותחת את המסך הראשי הרגיל של Claude (שבו
כפתור המצלמה כבר קיים כלחיצה אחת). אם בעתיד יתפרסם API/URI רשמי למצלמה, צריך
לעדכן רק את הקריאה ב-`MainActivity.kt`.

אם Claude לא מותקנת במכשיר, האפליקציה מפנה אוטומטית ל-Google Play להתקנה.

## איך בונים APK
1. פתח את התיקייה הזו ב-Android Studio (Open an existing project).
2. תן ל-Gradle להסתנכרן.
3. Build > Build Bundle(s)/APK(s) > Build APK(s).
4. ה-APK יהיה תחת `app/build/outputs/apk/debug/app-debug.apk`.

או דרך ה-GitHub Actions workflow המשותף בריפו (`.github/workflows/build-apks.yml`),
שבונה APK debug אוטומטית בכל push ומעלה אותו כ-artifact.
