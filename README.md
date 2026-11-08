# Photo Sorter for Android

A small app for clearing out the photos and videos on your phone. It shows them
one at a time, full screen, and you swipe:

- **right** to keep it (it won't be shown again),
- **left** to trash it (into Android's trash, so you can still get it back),
- **up** to decide later (it goes to the back of the queue).

It can also find **exact duplicates** (byte-for-byte identical files, not just
similar-looking photos) and trash the extra copies in one go.

It was made for Samsung phones (Android 11 or newer) but should work on other
Android phones too. It never deletes anything permanently and never uses the
internet.

## Install

1. On your phone, open the
   [latest release](https://github.com/lronsmith/photo-sorter-android/releases/latest).
2. Under **Assets**, tap `photo-sorter.apk` to download it.
3. Open the downloaded file (from the download notification, or in **My Files →
   Downloads**).
4. If the phone says it isn't allowed to install unknown apps from this source,
   tap **Settings**, turn on **Allow from this source** (for your browser or My
   Files), then go back.
5. Tap **Install**, then **Open**.

Google Play Protect may warn that it doesn't recognise the app, because it isn't
from the Play Store. Choose the option to install anyway (the wording varies).

**Updates** install the same way: download the newest `photo-sorter.apk` and
open it. It installs over the old version and keeps your decisions. Don't
uninstall first: uninstalling forgets which photos you've kept.

## First run

1. **Allow photo access.** When Android asks, choose to allow all photos so the
   app can see your whole library. If you allow only selected photos, the app
   works with just those and shows "Only photos you selected are visible ·
   Change"; tap it to pick more. If you said no, tap **Grant access** (or
   **Open settings** if Android no longer asks).
2. **Optional: turn on Media management.** Tap the Settings icon (top right of
   the album screen) → **Media management**, and allow it on the page Android
   opens. With it on, a left swipe trashes the photo straight away, with no
   pop-up. (Not available on Android 11.)

   Without Media management, left swipes are collected instead of trashed
   right away. A red **🗑** chip (for example "🗑 37") counts them; tap it and
   Android asks you to confirm the whole batch at once (one confirmation per
   500 items). Until you do, the album screen also shows "37 waiting to trash ·
   Trash now".

## Using it

1. **Choose albums**: tap an album to include or leave it out. New albums are
   included automatically.
2. Pick **Oldest** or **Newest** first, then tap **Start ›**.
3. Swipe right (keep), left (trash) or up (later). Videos play on their own
   without sound; tap a video to turn the sound on or off.
4. **↶ Undo** takes back your last swipe, and you can keep tapping it to go
   further back through the swipes made since you opened the swipe screen.
   Undoing a trash brings the photo back out of the trash.
5. When you run out, you'll see **All done** with how many you kept and
   trashed.

"Later" only lasts while you're swiping; next time that photo is simply
undecided again.

### Find duplicates

Tap **Find duplicates** on the album screen. It checks your whole library
(ignoring the album selection) for files that are exactly the same. For each
group it keeps one copy, preferring the one in the Camera folder, otherwise the
oldest, and marks the rest for the trash.

- Tap a copy to keep that one instead. Long-press a copy to see which folder
  it's in.
- **Trash N copies** trashes all the extra copies at once.
- Leaving the screen stops the scan; **↻ Rescan** starts again.

**Tip:** run Find duplicates *before* you start swiping. Otherwise the copy it
keeps could be one you already swiped left on, so every copy ends up in the
trash. Nothing is lost either way: everything stays recoverable (see below).

## Where trashed photos go

Trashed photos and videos go into Android's trash, not straight to deletion.
They stay recoverable for about **30 days**, after which Android removes them
for good.

To get something back: **Settings → Trashed by this app**. Tap the items you
want (or **Select all**), then **Restore N selected**; with nothing selected
the button is **Restore all**. Restored photos show up in the swipe queue
again.

**Samsung Gallery's Recycle bin:** Not yet checked.

**Settings → Reset decisions** makes the photos you kept show up again. Trashed
items stay in the trash. Photos waiting to be trashed are put back in the queue too.

## Privacy

The app has **no internet permission at all**, so nothing ever leaves your
phone. The only permissions it uses are access to your photos and videos, and
(only if you turn it on) Media management.

## For developers

### Build and test

Requirements: JDK 21 and the Android SDK command-line tools (Android Studio is
not needed). Point Gradle at the SDK with `sdk.dir` in `local.properties` or
`ANDROID_HOME`, and accept the SDK licences (`sdkmanager --licenses`); the
Android Gradle Plugin downloads the platform and build-tools it needs.

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests (Robolectric), no device needed
./gradlew lint testDebugUnitTest assembleDebug   # what CI runs
```

| | |
|---|---|
| minSdk | 30 (Android 11) |
| compileSdk / targetSdk | 37 |
| Android Gradle Plugin | 9.5.1 (built-in Kotlin) |
| Kotlin | 2.4.21 |
| Gradle | 9.9.0 (wrapper) |
| JDK | 21 (bytecode target 17) |

Versions live in `gradle/libs.versions.toml` and `app/build.gradle.kts`.
`versionName` comes from `appVersionName` in `gradle.properties`; `versionCode`
from the `BUILD_NUMBER` environment variable (default 1).

### Releases and signing

- Every merge to `main` runs CI (`.github/workflows/ci.yml`). When CI passes on
  `main`, the Release workflow (`.github/workflows/release.yml`) builds that
  same commit, signs it, and publishes `photo-sorter.apk` plus
  `photo-sorter.apk.sha256` as a GitHub release tagged
  `v<version>-build.<run number>`.
- The signing secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
  `KEY_PASSWORD` live only in the GitHub environment `release`, whose
  deployment branch policy allows only `main`. Pull requests never see them.
- The release build is signed only when the `SIGNING_KEYSTORE_PATH`,
  `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`
  environment variables are all set; otherwise `assembleRelease` produces an
  unsigned APK.

> **Don't lose the signing key.** Android only installs an update over the app
> if it is signed with the same key. Without it, new APKs couldn't install over
> the old app; users would have to uninstall it first, which wipes their
> decisions. Keep the keystore backup and its password somewhere safe.

### Manual checklist

Run on a Samsung phone for the first release:

- [ ] Grant / deny photo access; Android 14 "Selected photos only".
- [ ] Grant Media management; a left swipe trashes with no dialog.
- [ ] Revoke it; left swipes batch; one dialog trashes the batch.
- [ ] Check whether trashed items appear in Samsung Gallery's Recycle bin;
      record the answer in this README.
- [ ] Undo each action type; restore from the Trashed page.
- [ ] Video playback and the sound toggle.
- [ ] Duplicate scan on the real library; trash; verify in Gallery.
- [ ] Install a newer release APK over the old one; decisions survive.
- [ ] A trashed item no longer appears in the swipe queue or Start counts.
- [ ] With Media management on, a left swipe shows no system dialog.
- [ ] Thumbnails show on the Trashed page.
- [ ] Trash an item, force-stop the app and reopen it: the Trashed page still lists it.
