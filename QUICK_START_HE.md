# AppBlocker — התחלה מהירה

## 1. התקנת השרת והאתר על Ubuntu

```bash
cd AppBlocker
chmod +x install-server.sh build-android.sh
./install-server.sh
```

הסקריפט יבקש ממך לבחור סיסמת Admin לאתר. הוא לא מתקין חבילות מערכת ולא דורש `sudo`.

בסיום הוא ידפיס שתי כתובות:

- כתובת האתר במחשב עצמו.
- כתובת השרת שהטלפון צריך להשתמש בה כאשר הוא באותה רשת Wi-Fi.

## 2. בניית APK

```bash
./build-android.sh
```

הסקריפט מוריד לחשבון המשתמש בלבד את Android SDK ו-Gradle החינמיים, בונה את האפליקציה ומייצר:

`release/AppBlocker-v1.0.0-debug.apk`

אין צורך ב-Android Studio ואין צורך ב-`sudo`.

## 3. חיבור הטלפון

1. התקן את ה-APK.
2. פתח AppBlocker.
3. הזן את כתובת השרת שהתקבלה בשלב 1.
4. לחץ `Create pairing code`.
5. פתח את אתר AppBlocker במחשב.
6. עבור אל `Pair device`.
7. הזן את הקוד ואשר.
8. חזור לטלפון והשלים את הרשאות ההקמה המוצגות.

## 4. Samsung

יש להפעיל Accessibility ו-Usage Access, ולוודא שהאפליקציה אינה נכנסת ל-Sleeping/Deep Sleeping apps. האפליקציה מפנה למסכים הרלוונטיים ולא משנה הגדרות אלה בעצמה.

## 5. Xiaomi / Redmi / POCO

יש להפעיל Accessibility ו-Usage Access, לאפשר Autostart, ולהגדיר Battery ל-`No restrictions`. אם גרסת HyperOS/MIUI מציגה אפשרות Lock ב-Recents, מומלץ להפעיל גם אותה.

## 6. ניהול

כל חסימה, שינוי זמן, תמונות חסימה, התראות ואישור הסרה נעשים באתר בלבד.

## 7. חשוב לדעת

האפליקציה לא עושה Root, לא מבצעת Factory Reset ולא משנה או מוחקת מידע של אפליקציות אחרות.

ללא Root/Device Owner, Android עדיין מאפשר דרכי עקיפה ברמת מערכת כגון Safe Mode, ADB, Force Stop או ביטול Accessibility. אין דרך בטוחה לחסום לחלוטין את הנתיבים האלה באפליקציה רגילה בלי להפר את דרישת שמירת המידע שלך.
