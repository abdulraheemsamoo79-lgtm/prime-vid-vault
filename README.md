# PRIME VID VAULT — TikTok Downloader (Native Android App)

Native Android app. TikTok link paste karo → video fetch hoti hai (free tikwm API) →
download dabao → video seedha Gallery me save. **Koi permission nahi chahiye:**
Android 10+ pe system DownloadManager khud public Downloads folder me save karta
hai, isliye app koi storage/media permission nahi mangti.

## Features
- TikTok link paste + clipboard auto-fill / auto-fetch
- Cover, creator, title, views/likes/comments/shares preview
- 5 download options: No-Watermark HD, No-Watermark, With Watermark, MP3 Audio, Cover Image
- DownloadManager se download + live progress % + Saved/Failed status app ke andar
- MediaScanner scan, taaki video turant Gallery me dikhe
- Download history (Your Vault) — SharedPreferences me save, delete / clear support

## APK kaise banayein (Android Studio)
1. Android Studio (latest) install karo — JDK uske saath bundled aata hai.
2. Android Studio me **File → Open** → is `PrimeVidVault` folder ko select karo.
3. Pehli baar Gradle sync hone do (internet chahiye — dependencies download hongi).
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. APK yahan milegi: `app/build/outputs/apk/debug/app-debug.apk`
6. APK ko phone me copy karke install karo (install ke waqt "Unknown sources / Install unknown apps" allow karna hoga).

Release (signed) APK ke liye: **Build → Generate Signed App Bundle / APK** → apni keystore banao.

## Tech notes
- Language: Kotlin, minSdk 24 (Android 7.0+), targetSdk 34
- Zero third-party networking libs — `HttpURLConnection` + `org.json` (Android built-in)
- API: `https://www.tikwm.com/api/?url=<tiktok link>&hd=1` (free, no key)
- Files save hoti hain: `Downloads/PrimeVidVault/` — Gallery app inhe dikhata hai
- Agar tikwm API kabhi down ho, app error message dikhayegi — API ka status `tikwm.com` par check karo.
