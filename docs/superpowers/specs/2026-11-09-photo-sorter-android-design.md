# Photo Sorter for Android — Design

Date: 2026-11-09
Status: Approved in conversation, pending written-spec review
Repo: `lronsmith/photo-sorter-android` (new, public). The Windows app
(`lronsmith/photo-sorter`, Rust) is left as is; no code is shared.

## Purpose

An Android app for anyone with a Samsung phone (it is being built for a
friend of the author, not for one specific device) for quickly culling the
photo library that Samsung Gallery shows. Photos and videos appear one at a
time as full-screen cards:

- swipe **right** = keep (remember the decision, never show it again),
- swipe **left** = trash (Android's recoverable trash),
- swipe **up** = later (send to the back of the queue).

A second tool finds **byte-identical duplicates** (same SHA-256, not "similar"
photos) across the whole library and trashes the extra copies.

The APK is published from GitHub Releases, signed automatically by the release
workflow after a merge to `main`.

## Success criteria

- Installs on Android 11+ (minSdk 30) and works on any Samsung phone running
  One UI (other Android phones should work too, but aren't a target),
  including Android 14+ "selected photos only" access. Users are not assumed
  to be technical: the README's install steps are written for them.
- Shows every photo and video from the chosen albums, oldest first by default,
  with a newest-first option.
- Right / left / up swipes keep / trash / defer; undo reverses swipes one at a
  time, repeatedly.
- With "Media management" granted, a left swipe trashes instantly with no
  dialog. Without it, left swipes queue up and one system dialog trashes the
  batch.
- Nothing is ever deleted permanently by the app. Everything it trashes is
  listed in "Trashed by this app" and can be restored until Android purges it
  (~30 days).
- The duplicate scan finds every group of byte-identical files, keeps one per
  group (Camera copy, else oldest; user can change it) and trashes the rest in
  one action.
- Every merge to `main` that passes CI produces a signed `photo-sorter.apk` on a
  GitHub release; each new APK installs over the previous one.
- No keys, passwords, or personal data are in the repo or its history.

## Technology

- Kotlin, Jetpack Compose (Material 3), **dark theme only**.
- Navigation Compose (4 destinations + the Trashed sub-page).
- Room (decisions), DataStore Preferences (album filter, sort order).
- Coil 3 for images and video thumbnails (`coil-video` frame decoder).
- Media3 ExoPlayer for video playback on the swipe card.
- kotlinx-coroutines; no DI framework (a small hand-written `AppContainer`).
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`), Gradle
  wrapper committed.
- JDK 21 (already installed locally; also used in CI).
- `minSdk = 30`. `compileSdk`/`targetSdk` = the newest stable API level the
  current stable Android Gradle Plugin supports, chosen in the scaffold task and
  recorded in the README.
- Package / applicationId: `io.github.lronsmith.photosorter`. App name: "Photo
  Sorter".
- `versionName` from `gradle.properties` (`1.0.0` to start); `versionCode` from
  env `BUILD_NUMBER` (the release workflow's run number), defaulting to `1`.

### Permissions (manifest)

- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (API 33+).
- `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+), so partial access works.
- `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"`.
- `MANAGE_MEDIA` (special app access, API 31+; ignored on API 30, which always
  uses the batch path).
- No `INTERNET` permission. The app never touches the network.

## Architecture

One `app` module. Each unit has one job and a small interface so units can be
built and tested independently.

| Unit | Responsibility | Depends on |
|---|---|---|
| `media/MediaRepository` | Query MediaStore images + videos (merged), albums with counts and cover URIs, and items for a set of albums sorted by date. Maps cursor rows to `MediaItem`. Also queries trashed items by ID (`QUERY_ARG_MATCH_TRASHED`). | Android `ContentResolver` |
| `decisions/` (Room) | `Decision(mediaId: Long PK, state: KEPT/PENDING_TRASH/TRASHED, source: SWIPE/DUPLICATE, decidedAt: Long)`. DAO: upsert, delete, get by states, reset, prune. | Room |
| `prefs/SettingsStore` | DataStore: `excludedAlbumIds: Set<String>`, `order: OLDEST_FIRST/NEWEST_FIRST`. Excluded (not included) set, so new albums are included automatically. | DataStore |
| `queue/SwipeQueue` | **Pure Kotlin.** Ordered queue of `MediaItem`; `keep()`, `trash()`, `later()` act on the current item and push an undo entry; `undo()` returns the reversed action. | nothing |
| `trash/Trasher` | `canTrashInstantly()` (`MediaStore.canManageMedia`, false on API 30); builds trash / restore `IntentSenderRequest`s via `MediaStore.createTrashRequest(…, true/false)`, chunked to ≤500 URIs per request. Launching is done by the UI with `StartIntentSenderForResult`; with Media management the request completes without a dialog. | Android |
| `dupes/DuplicateFinder` | **Pure Kotlin.** Input: list of `(id, size, relativePath, dateTaken)` + an `openStream(id)` function. Groups by size, SHA-256-hashes only same-size candidates (64 KB buffer), groups by hash, picks the keeper, reports progress (bytes hashed / total), cancellable via coroutine cancellation. | nothing (`java.security.MessageDigest`) |
| `ui/` | Screens: Start, Swipe, Duplicates, Settings, Trashed. One ViewModel per screen wiring the units above. | all above |

### `MediaItem`

`id: Long`, `uri: Uri`, `kind: IMAGE/VIDEO`, `albumId: String` (bucket ID),
`albumName: String`, `dateTaken: Long` (ms; falls back to `DATE_MODIFIED*1000`
when `DATE_TAKEN` is null or 0), `sizeBytes: Long`, `relativePath: String`,
`displayName: String`, `mimeType: String`, `durationMs: Long?` (videos).

IDs come from the shared MediaStore files table, so image and video IDs never
collide.

## Data flow

### Start → Swipe

1. Start loads albums and the queue count from `MediaRepository`, minus items
   with any decision row.
2. On "Start", the Swipe screen builds `SwipeQueue` from the chosen albums in
   the chosen order, excluding decided items. `PENDING_TRASH` items are excluded
   from the queue but count towards the pending-trash chip.
3. **Right**: write `KEPT`, advance.
4. **Up**: move the item to the back of the in-memory queue, advance. Not
   persisted; after a restart the item is simply undecided again.
5. **Left**:
   - `canTrashInstantly()` → the card advances immediately and a single-item
     trash request (no dialog) is queued; requests run one at a time. On
     `RESULT_OK` write `TRASHED`; on failure write `PENDING_TRASH` instead.
   - otherwise → write `PENDING_TRASH`; the pending chip count increments.
6. **Pending chip** ("🗑 37"): tap → one trash request for all `PENDING_TRASH`
   items (chunked). `RESULT_OK` → those rows become `TRASHED`. Cancelled /
   failed → rows stay `PENDING_TRASH`, a snackbar says so.
7. **Undo** (repeatable, this session only): KEEP → delete row; PENDING_TRASH →
   delete row; TRASHED → restore request, on `RESULT_OK` delete row; LATER →
   move the item back to the front. The undone item becomes the current card.
8. **End of queue**: "All done" state with session counts (kept / trashed /
   pending) and the pending-trash button if any, plus "Back to albums".

### Duplicates

1. Scan covers the whole library (images + videos), ignoring the album filter,
   excluding items already trashed.
2. `DuplicateFinder` groups by size → hashes candidates → groups by hash.
   Progress bar + Cancel while running; leaving the screen cancels the scan.
   Nothing is cached.
3. Keeper rule per group: copies whose `relativePath` starts with
   `DCIM/Camera/` are preferred; among the preferred (or all, if none), the
   smallest `dateTaken`, then the smallest `id`.
4. Results sorted by bytes freed, descending. Tapping a copy makes it the
   keeper.
5. "Trash N copies" → trash request for every non-keeper (chunked; instant with
   Media management, else one dialog per chunk). On `RESULT_OK` write `TRASHED`
   rows with `source = DUPLICATE` and remove those groups from the list.

### Trashed by this app

- Lists `TRASHED` rows that MediaStore still has in its trash, with days left
  from `DATE_EXPIRES`.
- Restore selected / Restore all → restore request; on `RESULT_OK` delete those
  rows (items become undecided and reappear in the swipe queue).
- **Prune** (run when Start loads): delete `TRASHED` rows whose IDs are no
  longer in MediaStore (purged) or are live again (restored elsewhere, e.g.
  from Samsung Gallery's Recycle bin). Prune runs only when the app has full
  photo access: with "selected photos only" access it is skipped, so items
  outside the selection aren't forgotten.

### Reset decisions

Confirmation dialog, then deletes `KEPT` and `PENDING_TRASH` rows. `TRASHED`
rows are kept because they back the Trashed list.

## Screens

Mockups chosen during brainstorming are in `docs/superpowers/specs/mockups/`.
Gradients stand in for photos. Everything is dark.

### Start — `start-screen.html`, design B (modified)

- Top: title "Choose albums", subtitle "N of M selected · X photos & videos",
  Settings icon top-right. (Design B's Duplicates icon is dropped; see the
  bottom bar.)
- Body: 2-column grid of album covers with name + count. Included albums have
  a white outline + check; excluded ones are dimmed. Tap toggles.
- If pending trash exists: a banner above the grid, "37 waiting to trash ·
  Trash now".
- Bottom bar: row 1 = Oldest/Newest segmented toggle + "Start ›" button
  (disabled when nothing is selected or the queue is empty); row 2 = a **big
  full-width "Find duplicates" button** (styled like design A's secondary
  button).
- No access: the grid is replaced by an explanation + "Grant access" (or "Open
  settings" when permanently denied). Partial access: banner "Only photos you
  selected are visible · Change".

### Swipe — `swipe-screen.html`, design B (full-bleed)

- Photo/video fills the screen edge to edge on black, fit to screen
  (letterboxed, not cropped).
- Thin progress bar at the top; "↑ LATER" hint under it.
- Soft edge glows: red on the left ("TRASH"), green on the right ("KEEP"),
  intensifying as the card is dragged toward that side. Gesture only, no
  action buttons.
- Bottom gradient overlay: date taken (e.g. "Sunday 4 Aug 2019"), album · file
  name; then "↶ Undo" chip (left) and the red pending-trash chip "🗑 37"
  (right, shown whenever pending > 0).
- Gesture: the card follows the finger with slight rotation. Release past 30%
  of the width (or a fast horizontal fling) → keep/trash; past 20% of the
  height upward (or a fast upward fling) → later; otherwise it springs back.
- Videos autoplay muted and loop; tap toggles sound.
- The next 2 items are preloaded.
- Unloadable item (deleted elsewhere) → dropped silently, next card shown.
  Undecodable file → placeholder with the file name, still swipeable.

### Duplicates — `duplicates-screen.html`, design B (thumbnail strips)

- Top bar: back, "Duplicates", "↻ Rescan".
- Scanning: progress bar with bytes / files done and Cancel.
- Results: headline bytes freed ("1.3 GB") + "142 copies · 61 groups"; hint
  "Tap a copy to keep it instead".
- Each group: caption (file name · size), then a horizontal row of its copies'
  thumbnails. The keeper has a green outline + "KEEP" badge; the others are
  dimmed with a bin badge. Long-press a thumbnail shows its album/path.
- Pinned bottom button: "Trash N copies" (red).
- No duplicates: "No duplicates found" empty state.

### Settings — `settings-screen.html`, design A (grouped list)

- **Permissions**: "Photo & video access" (All photos ✓ / Selected only ·
  Change / Not granted · Grant); "Media management — Trash without asking each
  time" with a switch reflecting the current state; tapping opens
  `Settings.ACTION_REQUEST_MANAGE_MEDIA` (hidden on API 30). State is re-read
  on resume.
- **Library**: "Trashed by this app — Restore within ~30 days · N ›" → Trashed
  page.
- **Data**: "Reset decisions — Show kept photos again" (red text) →
  confirmation dialog.
- **About**: version (`versionName (build versionCode)`), "Source on GitHub ↗".

### Trashed page (from Settings)

As in the right half of settings design C: 3-column grid of trashed items,
each tile showing days until purge; tap to multi-select; "Select all";
pinned green "Restore N selected" button; empty state "Nothing in the trash".

## Error handling

| Situation | Behaviour |
|---|---|
| Media permission not granted | Start shows explanation + Grant; permanently denied → "Open settings" (app details). |
| Android 14+ partial access | Banner on Start: only selected items visible, "Change" re-opens the picker. |
| Item disappears mid-session | Card load fails → item dropped from the queue silently. |
| Undecodable file | Placeholder card with the file name; swipes still work. |
| Trash/restore request cancelled or fails | Rows unchanged (pending stays pending); snackbar. |
| Media management revoked mid-session | The next left swipe falls back to `PENDING_TRASH`. |
| Duplicate scan: file unreadable | Skipped, counted in "N files couldn't be read" under the summary. |

## Repository, CI and release

### Repo

- New public repo `lronsmith/photo-sorter-android`, MIT licence.
- `.gitignore` includes `*.jks`, `*.keystore`, `key.properties`,
  `local.properties`, `build/`, `.gradle/`, `.idea/`, `.superpowers/`.
- Commits use the author's GitHub noreply email, set in this repo's **local**
  git config (the global config holds a personal address). Every task that
  commits verifies `git config user.email` first; before the first push,
  `git log --all --format='%ae %ce'` must show only the noreply address.
- Enable secret scanning + push protection.
- Actions: require approval for workflow runs from all outside contributors'
  fork PRs.
- Bootstrap: the commits made before the GitHub repo exists (this spec,
  mockups, `.gitignore`, licence, README stub) are pushed directly to `main`,
  then protection is turned on; all further work goes through PRs.

### Branch protection (ruleset on `main`, no bypass actors)

- Require a pull request before merging (0 required approvals; the owner can't
  approve their own PR, and as sole collaborator only the owner can merge).
- Require status check `CI / build` (the CI job) to pass, branch up to date.
- Block force pushes and deletion.

### CI — `.github/workflows/ci.yml`

- On `pull_request` and `push` to `main`. `permissions: contents: read`.
- Ubuntu, JDK 21 (Temurin), `gradle/actions/setup-gradle` (includes wrapper
  validation), run `./gradlew lint testDebugUnitTest assembleDebug`.
- No artifacts uploaded, no secrets used.

### Release — `.github/workflows/release.yml`

- Trigger: `workflow_run` of CI, `completed`, branch `main`; job runs only if
  the run concluded `success` and its event was `push`. Checks out
  `workflow_run.head_sha`.
- `environment: release`. That environment has a deployment branch policy
  allowing only `main` and holds the secrets `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. No repo-level signing
  secrets.
- `permissions: contents: write`; `concurrency: release`.
- Steps: decode the keystore into `$RUNNER_TEMP`; `BUILD_NUMBER=${{
  github.run_number }} ./gradlew assembleRelease` (the release `signingConfig`
  reads the keystore path and passwords from env vars and is only applied when
  they are all present); `apksigner verify --print-certs`; copy to
  `photo-sorter.apk`, write `photo-sorter.apk.sha256`; `gh release create
  v<versionName>-build.<run_number> … --generate-notes --latest`.
- Third-party actions are pinned to full commit SHAs.

### Signing key (one-off, during setup)

- `keytool -genkeypair -keyalg RSA -keysize 4096 -validity 10000 -alias
  photo-sorter -dname "CN=Photo Sorter"` with a random 32-character password
  (store and key password the same), written to
  `~/photo-sorter-signing-backup/` (outside any repo).
- Upload with `gh secret set --env release`.
- The author moves the backup folder somewhere safe and confirms; then the
  local copy is deleted.
- Losing the key means updates can't install over the old app (uninstall
  wipes the decisions database). README says so.

### Install

README: download `photo-sorter.apk` from the latest release, allow "Install
unknown apps" for the browser / My Files, install. Later APKs install over it.

## Testing

Automated tests run on the JVM (CI and locally), with no device or emulator:

- `SwipeQueue`: keep, trash, later, undo chains of each, undo at the start, an
  empty queue, a queue of one.
- `DuplicateFinder`: size pre-grouping, only candidates hashed (asserted via the
  `openStream` call log), hash grouping, keeper rule (Camera wins; oldest; id
  tie-break), unreadable files, cancellation.
- `MediaRepository` mapping: `MatrixCursor` → `MediaItem` (date fallback, video
  duration), query arguments for album filter + order (Robolectric).
- Room DAO with an in-memory DB (Robolectric): upsert, state transitions,
  reset keeps `TRASHED`, prune.
- `Trasher`: instant vs batch decision, chunking at 500.
- ViewModels with fake repositories + `kotlinx-coroutines-test`: swipe → DB
  writes, undo → restore, pending chip, error snackbars, end-of-queue state.
- Compose UI tests under Robolectric: drag past threshold triggers the right
  action; a short drag springs back.

Local toolchain: the first task installs the Android command-line SDK tools to
`%LOCALAPPDATA%\Android\Sdk` (no admin rights, no Android Studio) so agents can
run `./gradlew test` locally.

Not automated (deliberately): emulator / instrumented tests.

### Manual checklist (README, run on a Samsung phone for the first release)

1. Grant / deny photo access; Android 14 "Selected photos only".
2. Grant Media management; a left swipe trashes with no dialog.
3. Revoke it; left swipes batch; one dialog trashes the batch.
4. Check whether trashed items appear in Samsung Gallery's Recycle bin; record
   the answer in the README.
5. Undo each action type; restore from the Trashed page.
6. Video playback and the sound toggle.
7. Duplicate scan on the real library; trash; verify in Gallery.
8. Install a newer release APK over the old one; decisions survive.

## Out of scope

- Similar-photo (perceptual) duplicate detection.
- Light theme, tablets/landscape-specific layouts, iOS.
- Moving or copying files, editing metadata, favourites/albums.
- Play Store publishing.
- Any network access.
