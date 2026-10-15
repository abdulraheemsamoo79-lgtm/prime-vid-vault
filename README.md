# PRIME VID VAULT — TikTok Downloader (Native Android App)

Native Android app. Paste a TikTok link → the video is fetched (free tikwm API) →
press download → the video saves straight to your Gallery. **No permission needed:**
on Android 10+, the system DownloadManager saves to the public Downloads folder
on the app's behalf, so the app never asks for storage/media permission.

## Download

Get the latest APK from
[GitHub Releases](https://github.com/abdulraheemsamoo79-lgtm/prime-vid-vault/releases)
or the
[download page](https://abdulraheemsamoo79-lgtm.github.io/prime-vid-vault/)
(enable Pages from the `docs/` folder in repo Settings first).

## UI — v3.1 Aurora

Premium dark theme: deep black + midnight navy, electric violet → cyan
gradient CTAs, dark glass cards, gradient logo mark, gradient download
progress bar, smooth animations (layout transitions, card fade+slide entrance,
cover/avatar fade-in, animated progress, ripple press feedback, smooth scroll
to the result card).

New in v3.0:
- Bold gradient hero — "Download anything."
- Unified input pill (link field + Paste in one bar) + full-width Fetch button
- Download options as a 2-column card grid, HD option highlighted with badge
- Play-button overlay on the cover thumbnail
- Share-to-app tip banner
- Download history now persists until YOU delete it — nothing auto-removed

## Features
- TikTok link paste + clipboard auto-fill / auto-fetch
- **Share-to-app:** PRIME VID VAULT appears in TikTok's Share sheet — tap it,
  confirm "Do you want to download this video?", and the No-Watermark HD
  video downloads automatically. No link copying needed.
- Cover, creator avatar, name, @username, title, duration, views/likes/comments/shares preview
- 5 download options: No-Watermark HD, No-Watermark, With Watermark, MP3 Audio, Cover Image
- DownloadManager download + live progress % + Saved/Failed status inside the app
- MediaScanner scan, so the video shows in Gallery immediately
- Download history (Recent Downloads) — saved in SharedPreferences, persists
  until you delete it; per-item delete + clear-all support
- Quick actions: Copy Caption, Open in TikTok, Share Link
- Fully English UI

## Build the APK (GitHub Actions — no PC needed)
1. Push this project to a GitHub repo (`main` branch). The included
   `.github/workflows/build-apk.yml` runs automatically on every push.
2. Repo → Actions tab → latest run → Artifacts → `prime-vid-vault-apk` → unzip.
3. Install `app-debug.apk` on your phone (allow "Install unknown apps" when asked).

## Public releases (auto)

Push a version tag (e.g. `git tag v3.1 && git push origin v3.1`) and the
workflow builds the APK and publishes a public **GitHub Release** with
`prime-vid-vault.apk` attached — the download page and the in-app updater
both point at the latest release, so users always get the newest build
without manual re-downloads.

## Build the APK (Android Studio)
1. Install Android Studio (latest) — JDK comes bundled with it.
2. Android Studio → **File → Open** → select this `PrimeVidVault` folder.
3. Let Gradle sync finish on first open (internet needed — dependencies download).
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. APK location: `app/build/outputs/apk/debug/app-debug.apk`
6. Copy the APK to your phone and install it (allow "Unknown sources / Install unknown apps" when asked).

For a signed release APK: **Build → Generate Signed App Bundle / APK** → create your keystore.

## Tech notes
- Language: Kotlin, minSdk 24 (Android 7.0+), targetSdk 34
- Zero third-party networking libs — `HttpURLConnection` + `org.json` (Android built-in)
- API: `https://www.tikwm.com/api/?url=<tiktok link>&hd=1` (free, no key)
- Files save to: `Downloads/PrimeVidVault/` — Gallery shows them
- If the tikwm API is ever down, the app shows an error message — check the API status at `tikwm.com`.
