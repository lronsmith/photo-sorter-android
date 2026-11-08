# Photo Sorter for Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan is built for **parallel** subagents: every task lists `Depends on`; any task whose dependencies are all `done` may start.

**Goal:** A signed Android APK, released from the public repo `lronsmith/photo-sorter-android`, that lets anyone with a Samsung phone swipe through their gallery (right keep / left trash / up later) and trash byte-identical duplicates.

**Architecture:** One Gradle `app` module in Kotlin + Jetpack Compose. Pure-Kotlin cores (`SwipeQueue`, `DuplicateFinder`) and small Android adapters behind interfaces (`MediaSource`, `DecisionStore`, `SettingsStore`, `Trasher`) are built in parallel; one ViewModel + screen per destination consumes those interfaces; an integration task wires them in `AppContainer` + Navigation. GitHub Actions runs CI on every PR and builds, signs and publishes the APK after CI passes on `main`.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, Room (KSP), DataStore Preferences, Coil 3 (+ coil-video), Media3 ExoPlayer, kotlinx-coroutines; JUnit4, kotlin-test, Robolectric, kotlinx-coroutines-test, Compose ui-test; Gradle Kotlin DSL + version catalog; GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-09-photo-sorter-android-design.md` (mockups in `docs/superpowers/specs/mockups/`). Every implementer reads the spec sections named in their task.

## Global Constraints

- `minSdk = 30`; `compileSdk`/`targetSdk` = newest stable API level supported by the current stable Android Gradle Plugin (chosen in T1, then fixed).
- applicationId / namespace / root package: `io.github.lronsmith.photosorter`. App name: `Photo Sorter`.
- JDK 21 (local and CI). Kotlin `jvmTarget` 17.
- Dark theme only. Portrait only (`android:screenOrientation="portrait"` on the activity).
- No `INTERNET` permission; the app never uses the network.
- Manifest permissions exactly: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`, `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`), `MANAGE_MEDIA`.
- The app never deletes permanently: only `MediaStore.createTrashRequest(…, true)` (trash) and `(…, false)` (restore). Requests carry ≤ 500 URIs.
- `versionName` from `gradle.properties` key `appVersionName` (`1.0.0`); `versionCode` = env `BUILD_NUMBER`, default `1`.
- Release signing reads env `SIGNING_KEYSTORE_PATH`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`; signing applies only when all four are set. `isMinifyEnabled = false`.
- No keystores, passwords, tokens or personal data in any commit. Every commit's author/committer email is `46252418+lronsmith@users.noreply.github.com` (check `git config user.email` before committing; worktrees share the repo's local config).
- Commit messages: conventional prefix (`feat:`, `fix:`, `test:`, `ci:`, `docs:`, `build:`), ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- UI copy is English, hard-coded in Compose (no string resources needed). Colours come from `ui/theme/Theme.kt` tokens only.
- Paths below: `$PKG` = `app/src/main/java/io/github/lronsmith/photosorter`, `$TEST` = `app/src/test/java/io/github/lronsmith/photosorter`.
- Run Gradle from Git Bash as `./gradlew …` at the worktree root.

## Review Focus

1. **Rapid left swipes with instant trash**: five quick left swipes while trash requests are still completing must trash all five, in order, with none lost or duplicated, and undo must still work. → T12 test `rapidInstantTrashesAreSerializedAndAllRecorded`.
2. **Partial batch success**: the user confirms the first chunk's dialog and cancels the second (> 500 pending); only the confirmed chunk becomes `TRASHED`, the rest stays pending and a message says so. → T10 test `trashReturnsOnlyItemsFromSuccessfulChunks`, T12 test `trashPendingKeepsFailedChunkPending`.
3. **Missing MediaStore metadata**: `DATE_TAKEN` null/0, null `BUCKET_ID`/`BUCKET_DISPLAY_NAME`/`RELATIVE_PATH` (files at storage root, some screenshots/downloads) must map without crashing and sort sensibly. → T9 test `mapsRowWithMissingMetadata`.
4. **Large libraries** (20,000+ items): queue operations must stay O(1)-ish so swiping never stutters. → T5 test `twentyThousandItemsStayFast` (2 s timeout).
5. **File handles during the duplicate scan**: every stream opened is closed, including after a read error and after cancellation (videos are huge; leaks exhaust descriptors). → T6 test `closesEveryStreamIncludingOnErrorAndCancel`.

---

## Execution protocol (controller)

- **Branches.** Integration branch: `liam/initial-development` in `~/photo-sorter-android`, created from local `main` at the start. Each task Tn runs in its own worktree `~/psa-worktrees/Tn` on branch `task/Tn`, created from the integration branch tip **after** all of Tn's dependencies were merged into it. After Tn's review passes, the controller merges `task/Tn` into the integration branch (`git merge --no-ff`), then removes the worktree. Parallel tasks touch disjoint files, so merges are clean; if one isn't, the controller resolves it and re-runs `./gradlew :app:testDebugUnitTest`.
- **T2 commits on `main`** (bootstrap). When T2 is done, the controller merges `main` into the integration branch.
- **T3 is run by the controller itself**, not a subagent: it handles secret material and needs the user's confirmation.
- **Subagents**: implementer and reviewer per task, `model: opus`, `effort: high`. Reviewer gets the task text, the spec sections, and the diff `git diff <integration-tip>...task/Tn`.
- **Status table.** Whenever any task changes status, overwrite `~/photo-sorter-apk-proj-status.md` (i.e. `~/photo-sorter-apk-proj-status.md`) with the table below and print the same table to the user. Status values: `done`, `implementing`, `reviewing`, `waiting on Tn, Tm` (unfinished dependencies), `waiting on user`.

```markdown
# Photo Sorter Android — task status
Updated: <ISO timestamp>

| Task | Name | Depends on | Status |
|---|---|---|---|
| T1 | Toolchain + Gradle scaffold + shared model | — | implementing |
| T2 | GitHub repo, protection, environment | — | implementing |
| ... | | | |
```

### Dependency graph

| Task | Name | Depends on |
|---|---|---|
| T1 | Toolchain + Gradle scaffold + shared model | — |
| T2 | GitHub repo, protection, environment | — |
| T3 | Signing key + secrets (controller) | T2 |
| T4 | CI + release workflows + release signing | T1 |
| T5 | `SwipeQueue` | T1 |
| T6 | `DuplicateFinder` | T1 |
| T7 | Persistence: decisions DB + settings store | T1 |
| T9 | `MediaSource` + `MediaAccess` | T1 |
| T10 | `Trasher` + `RequestLauncher` | T1 |
| T11 | Swipe card components | T1 |
| T12 | Swipe screen | T5, T7, T9, T10, T11 |
| T13 | Start screen | T7, T9, T10 |
| T14 | Duplicates screen | T6, T7, T9, T10 |
| T15 | Settings + Trashed screens | T7, T9, T10 |
| T16 | App integration (container, navigation, activity) | T12, T13, T14, T15 |
| T17 | README, final review, PR, release verification | T3, T4, T16 |

(There is no T8; settings storage is folded into T7.)

---

### Task T1: Toolchain + Gradle scaffold + shared model

**Depends on:** —

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `$PKG/MainActivity.kt` (placeholder), `$PKG/ui/theme/Theme.kt`
- Create: `$PKG/model/MediaItem.kt`, `$PKG/model/MediaUris.kt`
- Create: `$TEST/fakes/TestItems.kt`, `$TEST/model/MediaItemTest.kt`
- Modify: `.gitignore` (add `.kotlin/`)
- Local only (not committed): Android SDK at `%LOCALAPPDATA%\Android\Sdk`, `local.properties`

**Interfaces:**
- Produces (`$PKG/model/MediaItem.kt`, package `io.github.lronsmith.photosorter.model`):
  - `enum class MediaKind { IMAGE, VIDEO }`
  - `enum class SortOrder { OLDEST_FIRST, NEWEST_FIRST }`
  - `data class MediaItem(val id: Long, val kind: MediaKind, val albumId: String, val albumName: String, val dateTaken: Long, val sizeBytes: Long, val relativePath: String, val displayName: String, val mimeType: String, val durationMs: Long?)` — no Android types, so pure JVM tests can build it. (Deviation from the spec's field list: the URI is derived, not stored.)
  - `data class Album(val id: String, val name: String, val count: Int, val cover: MediaItem)`
- Produces (`$PKG/model/MediaUris.kt`): `fun MediaItem.contentUri(): android.net.Uri` — `ContentUris.withAppendedId(Images|Video.Media.EXTERNAL_CONTENT_URI, id)` by kind.
- Produces (`$PKG/ui/theme/Theme.kt`): `@Composable fun PhotoSorterTheme(content: @Composable () -> Unit)` (Material 3 dark scheme) and `object PsColors` with `Background = 0xFF0E0F12`, `Surface = 0xFF1A1C21`, `SurfaceHigh = 0xFF22252B`, `Outline = 0xFF3A3E46`, `Keep = 0xFF20B26B`, `KeepText = 0xFF5FE0A0`, `Trash = 0xFFD64545`, `TrashText = 0xFFFF7A7A`, `Warn = 0xFFF0C24A`, `OnSurfaceDim = 0x99FFFFFF` (as `Color`).
- Produces (`$TEST/fakes/TestItems.kt`): `fun testItem(id: Long, sizeBytes: Long = 1000, dateTaken: Long = id * 1000, albumId: String = "camera", albumName: String = "Camera", relativePath: String = "DCIM/Camera/", kind: MediaKind = MediaKind.IMAGE, displayName: String = "IMG_$id.jpg"): MediaItem` (`mimeType` `image/jpeg` or `video/mp4`, `durationMs` null or `1000`).
- Produces (`gradle/libs.versions.toml`): **every** library any later task needs, so no later task edits the catalog or `app/build.gradle.kts` dependencies: compose BOM, material3, material-icons-extended, ui-tooling(-preview), activity-compose, navigation-compose, lifecycle-viewmodel-compose, lifecycle-runtime-compose, room-runtime, room-ktx, room-compiler (KSP), datastore-preferences, coil-compose, coil-video, media3-exoplayer, media3-ui, kotlinx-coroutines-android; tests: junit 4, kotlin-test-junit, robolectric, androidx-test-core, androidx-test-ext-junit, kotlinx-coroutines-test, compose ui-test-junit4, ui-test-manifest (`debugImplementation`). Latest stable versions at time of writing, pinned.

- [ ] **Step 1: Install the Android SDK command-line tools.** Download the current Windows "Command line tools only" zip (link on https://developer.android.com/studio#command-tools) to `$CLAUDE_JOB_DIR/tmp`, extract to `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`, accept licences (`yes | sdkmanager --licenses`), install `platform-tools`, `platforms;android-<N>` and `build-tools;<latest N.x>` for the chosen API level N. Set user env var `ANDROID_HOME` (`setx ANDROID_HOME …`). Check: `"$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin/sdkmanager.bat" --list_installed` lists all three.

- [ ] **Step 2: Create the Gradle wrapper and build files.** Download the current stable Gradle `-bin` zip to `$CLAUDE_JOB_DIR/tmp`, run `gradle wrapper --gradle-version <ver> --distribution-type bin` in the worktree, commit the wrapper. Write the build files per Interfaces/Global Constraints: AGP + Kotlin Android + Compose compiler + KSP plugins; `testOptions.unitTests.isIncludeAndroidResources = true`; `buildFeatures { compose = true; buildConfig = true }` (T16 reads `BuildConfig`; no later task edits `app/build.gradle.kts` except T4); `local.properties` with `sdk.dir` (gitignored).

- [ ] **Step 3: Manifest + placeholder activity.** Manifest with the exact permission list (Global Constraints), `MainActivity` exported launcher, `screenOrientation="portrait"`, `android:label="Photo Sorter"`, theme `@android:style/Theme.Material.NoActionBar`. `MainActivity` calls `setContent { PhotoSorterTheme { Text("Photo Sorter") } }` (replaced in T16).

- [ ] **Step 4: Write the model test** in `$TEST/model/MediaItemTest.kt`:

```kotlin
@Test fun testItemDefaults() {
    val item = testItem(7)
    assertEquals(7L, item.id); assertEquals(7000L, item.dateTaken)
    assertEquals("DCIM/Camera/", item.relativePath); assertEquals(MediaKind.IMAGE, item.kind)
    assertNull(item.durationMs)
}
@Test fun videoTestItemHasDuration() {
    val v = testItem(1, kind = MediaKind.VIDEO)
    assertEquals("video/mp4", v.mimeType); assertEquals(1000L, v.durationMs)
}
```

- [ ] **Step 5: Run it, see it fail** (`./gradlew :app:testDebugUnitTest --tests '*MediaItemTest'` → compilation error, `testItem` unresolved), **then write `MediaItem.kt`, `MediaUris.kt`, `Theme.kt`, `TestItems.kt`.**

- [ ] **Step 6: Full check.** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Expected: `BUILD SUCCESSFUL`, 2 tests passed, `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit** `build: Gradle scaffold, manifest, theme and shared media model` (record the chosen API level and AGP/Kotlin/Gradle versions in the commit body).

---

### Task T2: GitHub repo, protection, environment

**Depends on:** —
**Works on:** local `main` in `~/photo-sorter-android` (no worktree).

**Files:**
- Create: `LICENSE` (MIT, `Copyright (c) 2026 lronsmith`), `README.md` (stub: title, one-sentence description, "Download: see Releases", "Status: in development")

**Interfaces:**
- Produces: public repo `lronsmith/photo-sorter-android` with `main` pushed; ruleset `main` requiring status check context **`build`** (T4's CI job must be named exactly `build`); environment **`release`** restricted to branch `main` (T3 stores secrets there; T4's release job uses it).

- [ ] **Step 1: Pre-push privacy check.** `git config user.email` → `46252418+lronsmith@users.noreply.github.com`; `git log --all --format='%ae %ce' | sort -u` → only that address; `git ls-files` contains no `*.jks`, `*.keystore`, `key.properties`, `local.properties`.

- [ ] **Step 2: Add `LICENSE` and the README stub, commit** `docs: licence and README stub`.

- [ ] **Step 3: Create and push.** `gh repo create lronsmith/photo-sorter-android --public --source . --remote origin --push --description "Swipe through your phone's gallery to keep or trash photos, and remove exact duplicates"`, then `gh repo edit lronsmith/photo-sorter-android --enable-wiki=false --enable-projects=false`.

- [ ] **Step 4: Security settings.**
  - Secret scanning + push protection: `gh api -X PATCH repos/lronsmith/photo-sorter-android --input -` with `{"security_and_analysis":{"secret_scanning":{"status":"enabled"},"secret_scanning_push_protection":{"status":"enabled"}}}`.
  - Fork PR workflow approval: `gh api -X PUT repos/lronsmith/photo-sorter-android/actions/permissions/fork-pr-contributor-approval -f approval_policy=all_external_contributors`.
  - Default workflow token read-only: `gh api -X PUT repos/lronsmith/photo-sorter-android/actions/permissions/workflow -f default_workflow_permissions=read -F can_approve_pull_request_reviews=false`.

- [ ] **Step 5: Ruleset.** `gh api -X POST repos/lronsmith/photo-sorter-android/rulesets --input ruleset.json` (file in `$CLAUDE_JOB_DIR/tmp`, not committed):

```json
{"name":"main","target":"branch","enforcement":"active","bypass_actors":[],
 "conditions":{"ref_name":{"include":["~DEFAULT_BRANCH"],"exclude":[]}},
 "rules":[{"type":"deletion"},{"type":"non_fast_forward"},
  {"type":"pull_request","parameters":{"required_approving_review_count":0,"dismiss_stale_reviews_on_push":false,"require_code_owner_review":false,"require_last_push_approval":false,"required_review_thread_resolution":false}},
  {"type":"required_status_checks","parameters":{"strict_required_status_checks_policy":true,"required_status_checks":[{"context":"build"}]}}]}
```

- [ ] **Step 6: Environment.** `gh api -X PUT repos/lronsmith/photo-sorter-android/environments/release --input -` with `{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}`, then `gh api -X POST repos/lronsmith/photo-sorter-android/environments/release/deployment-branch-policies -f name=main -f type=branch`.

- [ ] **Step 7: Verify.**
  - `gh api repos/lronsmith/photo-sorter-android/rules/branches/main --jq '[.[].type]'` → contains `deletion`, `non_fast_forward`, `pull_request`, `required_status_checks`.
  - `gh api repos/lronsmith/photo-sorter-android/environments/release/deployment-branch-policies --jq '.branch_policies[].name'` → `main`.
  - `gh api repos/lronsmith/photo-sorter-android --jq '.visibility, .security_and_analysis.secret_scanning_push_protection.status'` → `public`, `enabled`.
  - `gh api repos/lronsmith/photo-sorter-android/collaborators --jq '.[].login'` → only `lronsmith`.

---

### Task T3: Signing key + secrets (run by the controller)

**Depends on:** T2

**Interfaces:**
- Produces: environment `release` secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `photo-sorter`), `KEY_PASSWORD` (= the keystore password). Consumed by T4's release workflow.

- [ ] **Step 1: Generate** into `~/photo-sorter-signing-backup/` (outside every repo): a 32-character random password (`python -c "import secrets,string; print(''.join(secrets.choice(string.ascii_letters+string.digits) for _ in range(32)))"`), written to `password.txt`; then `keytool -genkeypair -keystore photo-sorter-release.jks -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias photo-sorter -dname "CN=Photo Sorter" -storepass:file password.txt -keypass:file password.txt`. Never echo the password into the conversation or a log.
- [ ] **Step 2: Upload** with `gh secret set <NAME> --env release --repo lronsmith/photo-sorter-android` reading from files/stdin (base64 of the `.jks` via `base64 -w0`). Check: `gh secret list --env release --repo lronsmith/photo-sorter-android` lists the four names.
- [ ] **Step 3: Record the certificate fingerprint** (`keytool -list -v -keystore … -storepass:file password.txt | grep SHA256`) in `~/photo-sorter-signing-backup/README.txt` along with the alias and "losing this key means updates can't install over the old app". Print the fingerprint (public, safe) to the user; T17 compares against it.
- [ ] **Step 4: Ask the user** to move `~/photo-sorter-signing-backup/` to a password manager or other safe place. Status `waiting on user`. After they confirm, delete the local folder if it still exists and mark `done`.

---

### Task T4: CI + release workflows + release signing

**Depends on:** T1

**Files:**
- Create: `.github/workflows/ci.yml`, `.github/workflows/release.yml`
- Modify: `app/build.gradle.kts` (`versionCode`/`versionName`, `signingConfigs.release`, `buildTypes.release`), `gradle.properties` (`appVersionName=1.0.0`)

**Interfaces:**
- Consumes: T2's ruleset context `build`, environment `release`; T3's secret names.
- Produces: CI job id and name `build`; release asset names `photo-sorter.apk`, `photo-sorter.apk.sha256`; tag `v<appVersionName>-build.<run_number>`.

- [ ] **Step 1: Version + signing in Gradle.** `versionName = providers.gradleProperty("appVersionName").get()`, `versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()`. A `release` signing config created only when the four `SIGNING_*` env vars are all non-blank; `buildTypes.release.signingConfig` set only in that case.

- [ ] **Step 2: Verify unsigned path.** `./gradlew :app:assembleRelease` with no `SIGNING_*` vars → success, `app/build/outputs/apk/release/app-release-unsigned.apk` exists.

- [ ] **Step 3: Verify signed path with a throwaway key** (in `$CLAUDE_JOB_DIR/tmp`, never in the worktree): generate with `keytool` (`-dname "CN=Throwaway"`), export the four `SIGNING_*` vars and `BUILD_NUMBER=42`, run `./gradlew :app:assembleRelease`, then `"$ANDROID_HOME/build-tools/<ver>/apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk` → `CN=Throwaway`; `aapt2 dump badging` (same build-tools dir) → `versionCode='42' versionName='1.0.0'`. Delete the throwaway key.

- [ ] **Step 4: Write `ci.yml`**: `name: CI`; on `pull_request` and `push` (branches `[main]`); `permissions: contents: read`; one job `build` (`name: build`, `runs-on: ubuntu-latest`): checkout → `actions/setup-java` (temurin, 21) → `gradle/actions/setup-gradle` → `./gradlew lint testDebugUnitTest assembleDebug`. No secrets, no artifact upload.

- [ ] **Step 5: Write `release.yml`**, following the Windows repo's pattern (`~/photo-sorter/.github/workflows/release.yml`): trigger `workflow_run` of `[CI]`, `types: [completed]`, `branches: [main]`; job `if: github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push'`; `environment: release`; `permissions: contents: write`; `concurrency: {group: release, cancel-in-progress: false}`; checkout `ref: ${{ github.event.workflow_run.head_sha }}`; setup-java 21; setup-gradle; decode `KEYSTORE_BASE64` to `$RUNNER_TEMP/release.jks`; `assembleRelease` with `BUILD_NUMBER: ${{ github.run_number }}` and the four `SIGNING_*` vars mapped from the secrets; `apksigner verify --print-certs` (newest dir under `$ANDROID_HOME/build-tools`); copy to `photo-sorter.apk`; `sha256sum photo-sorter.apk > photo-sorter.apk.sha256`; version from `grep '^appVersionName=' gradle.properties | cut -d= -f2`; `gh release create "v$VERSION-build.$RUN" photo-sorter.apk photo-sorter.apk.sha256 --target "$SHA" --title "Photo Sorter v$VERSION (build $RUN)" --generate-notes --latest` with `GH_TOKEN: ${{ github.token }}`; final step `rm -f "$RUNNER_TEMP/release.jks"` with `if: always()`. Secrets go only through `env:`, never inline in `run:`.

- [ ] **Step 6: Pin actions to commit SHAs** (`gh api repos/<owner>/<action>/git/ref/tags/<tag> --jq .object.sha`, dereferencing annotated tags), with the tag in a trailing comment, e.g. `uses: actions/checkout@<sha> # v5`.

- [ ] **Step 7: Lint the workflows.** Download the `actionlint` Windows release binary to `$CLAUDE_JOB_DIR/tmp`, run it on `.github/workflows/*.yml` → no output, exit 0.

- [ ] **Step 8: Commit** `ci: CI workflow and signed release workflow`.

---

### Task T5: `SwipeQueue`

**Depends on:** T1

**Files:**
- Create: `$PKG/queue/SwipeQueue.kt`
- Test: `$TEST/queue/SwipeQueueTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `testItem` (T1).
- Produces (package `…photosorter.queue`):
  - `sealed interface SwipeAction { val item: MediaItem; data class Keep(override val item: MediaItem) : SwipeAction; data class Trash(override val item: MediaItem) : SwipeAction; data class Later(override val item: MediaItem) : SwipeAction }`
  - `class SwipeQueue(items: List<MediaItem>)` with `val current: MediaItem?`, `fun peekNext(n: Int): List<MediaItem>` (the n items after current), `val remaining: Int` (including current), `val total: Int` (initial size minus dropped), `val decided: Int` (keep + trash count, net of undos), `val canUndo: Boolean`, `fun keep(): SwipeAction.Keep`, `fun trash(): SwipeAction.Trash`, `fun later(): SwipeAction.Later` (each throws `IllegalStateException` when `current == null`), `fun undo(): SwipeAction?` (null when nothing to undo; afterwards `current == returned.item`), `fun drop(id: Long)` (removes the item wherever it is in the queue, removes its undo entries, decrements `total`).
  - Implementation: `ArrayDeque` for the queue, `ArrayDeque` stack for undo.

- [ ] **Step 1: Write the failing tests:**

```kotlin
private fun q(n: Int) = SwipeQueue((1L..n).map { testItem(it) })

@Test fun keepAndTrashAdvance() { val s = q(3); assertEquals(1L, s.keep().item.id); assertEquals(2L, s.trash().item.id); assertEquals(3L, s.current?.id); assertEquals(2, s.decided); assertEquals(1, s.remaining) }
@Test fun laterMovesToBack() { val s = q(3); s.later(); assertEquals(listOf(2L, 3L, 1L), listOf(s.current!!.id) + s.peekNext(2).map { it.id }); assertEquals(0, s.decided) }
@Test fun laterOnSingleItemKeepsIt() { val s = q(1); s.later(); assertEquals(1L, s.current?.id) }
@Test fun undoKeepRestoresCurrent() { val s = q(3); s.keep(); val u = s.undo(); assertTrue(u is SwipeAction.Keep); assertEquals(1L, s.current?.id); assertEquals(0, s.decided) }
@Test fun undoLaterBringsItemBackToFront() { val s = q(3); s.later(); s.undo(); assertEquals(listOf(1L, 2L, 3L), listOf(s.current!!.id) + s.peekNext(2).map { it.id }) }
@Test fun undoChainsBackToStart() { val s = q(4); s.keep(); s.later(); s.trash(); repeat(3) { assertNotNull(s.undo()) }; assertNull(s.undo()); assertFalse(s.canUndo); assertEquals(1L, s.current?.id); assertEquals(4, s.remaining) }
@Test fun emptyQueue() { val s = q(0); assertNull(s.current); assertFailsWith<IllegalStateException> { s.keep() }; assertNull(s.undo()) }
@Test fun finishingQueueEndsWithNullCurrent() { val s = q(2); s.keep(); s.trash(); assertNull(s.current); assertEquals(0, s.remaining); assertEquals(2, s.decided); s.undo(); assertEquals(2L, s.current?.id) }
@Test fun dropRemovesItemAndItsUndoEntries() { val s = q(3); s.keep(); s.drop(1); s.drop(3); assertNull(s.undo()); assertEquals(2L, s.current?.id); assertEquals(1, s.total) }
@Test(timeout = 2000) fun twentyThousandItemsStayFast() { val s = q(20_000); repeat(20_000) { s.later() }; repeat(20_000) { s.undo() }; repeat(20_000) { s.keep() }; assertEquals(20_000, s.decided); assertNull(s.current) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SwipeQueueTest'` → FAIL (unresolved `SwipeQueue`).
- [ ] **Step 3: Implement** `SwipeQueue.kt` per Interfaces.
- [ ] **Step 4: Run** the same command → 10 tests PASS.
- [ ] **Step 5: Commit** `feat: swipe queue with repeatable undo`.

---

### Task T6: `DuplicateFinder`

**Depends on:** T1

**Files:**
- Create: `$PKG/dupes/DuplicateFinder.kt`
- Test: `$TEST/dupes/DuplicateFinderTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `testItem`.
- Produces (package `…photosorter.dupes`):
  - `data class DuplicateGroup(val hash: String, val copies: List<MediaItem>, val keeperId: Long)` with `val bytesFreed: Long get() = copies.first().sizeBytes * (copies.size - 1)`.
  - `data class ScanProgress(val filesDone: Int, val filesTotal: Int, val bytesDone: Long, val bytesTotal: Long)`
  - `data class ScanResult(val groups: List<DuplicateGroup>, val unreadableCount: Int)` — groups sorted by `bytesFreed` desc, then by smallest copy id.
  - `object DuplicateFinder { suspend fun scan(items: List<MediaItem>, openStream: suspend (MediaItem) -> InputStream?, onProgress: (ScanProgress) -> Unit = {}): ScanResult; fun pickKeeper(copies: List<MediaItem>): MediaItem }`
  - Rules: only items whose `sizeBytes > 0` and shared with ≥1 other item are opened; SHA-256 (lower-case hex) read with a 64 KiB buffer; `null` stream or `IOException` → unreadable (counted, excluded); `coroutineContext.ensureActive()` between buffers; every stream closed via `use`. `filesTotal`/`bytesTotal` cover candidates only. Keeper: prefer `relativePath.startsWith("DCIM/Camera/")`, then smallest `dateTaken`, then smallest `id`. Does not switch dispatchers (callers do).

- [ ] **Step 1: Write the failing tests** (`bytes` map id → content; `open` returns `ByteArrayInputStream` wrapped to record opens/closes):

```kotlin
@Test fun groupsIdenticalContentOnly() = runTest { // ids 1,2 same bytes; 3 same size, different bytes; 4 unique size
    val r = scan(1 to "aaaa", 2 to "aaaa", 3 to "bbbb", 4 to "ccccc")
    assertEquals(listOf(listOf(1L, 2L)), r.groups.map { g -> g.copies.map { it.id } }); assertEquals(0, r.unreadableCount) }
@Test fun onlySameSizeCandidatesAreOpened() = runTest { scan(1 to "aa", 2 to "aa", 3 to "xyz"); assertEquals(setOf(1L, 2L), opened) }
@Test fun zeroByteFilesIgnored() = runTest { assertTrue(scan(1 to "", 2 to "").groups.isEmpty()); assertTrue(opened.isEmpty()) }
@Test fun keeperPrefersCameraThenOldestThenId() {
    val a = testItem(1, dateTaken = 50, relativePath = "Download/"); val b = testItem(2, dateTaken = 90); val c = testItem(3, dateTaken = 90)
    assertEquals(2L, DuplicateFinder.pickKeeper(listOf(a, b, c)).id)
    assertEquals(1L, DuplicateFinder.pickKeeper(listOf(a, testItem(4, dateTaken = 60, relativePath = "Pictures/"))).id) }
@Test fun unreadableFilesCountedAndExcluded() = runTest { // id 2 -> null stream, id 3 -> throws IOException mid-read
    val r = scan(1 to "aa", 2 to "aa", 3 to "aa", unreadable = setOf(2L), throwing = setOf(3L))
    assertTrue(r.groups.isEmpty()); assertEquals(2, r.unreadableCount) }
@Test fun groupsSortedByBytesFreed() = runTest { val r = scan(1 to "a", 2 to "a", 3 to "bbbbbb", 4 to "bbbbbb", 5 to "bbbbbb")
    assertEquals(listOf(10L, 1L), r.groups.map { it.bytesFreed }) }
@Test fun progressReachesTotals() = runTest { val p = mutableListOf<ScanProgress>(); scan(1 to "aa", 2 to "aa", progress = p::add)
    assertEquals(ScanProgress(2, 2, 4, 4), p.last()) }
@Test fun closesEveryStreamIncludingOnErrorAndCancel() = runTest {
    scan(1 to "aa", 2 to "aa", 3 to "aa", throwing = setOf(3L)); assertEquals(opened, closed)
    // ids 10, 11: 1 MiB each, identical; the recording stream for id 10 calls onFirstRead = { job.cancel() }
    val job = launch(start = CoroutineStart.LAZY) { scan(10 to big, 11 to big, onFirstRead = mapOf(10L to { cancelHook() })) }
    cancelHook = { job.cancel() }; job.join()
    assertTrue(job.isCancelled); assertEquals(setOf(10L), opened); assertEquals(opened, closed) }
@Test fun cancellationStopsScan() = runTest { // same setup: after cancel during id 10's first read, id 11 is never opened
    assertFalse(11L in opened) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*DuplicateFinderTest'` → FAIL.
- [ ] **Step 3: Implement** `DuplicateFinder.kt` per Interfaces.
- [ ] **Step 4: Run** → all PASS.
- [ ] **Step 5: Commit** `feat: exact-duplicate finder with size prefilter and keeper rule`.

---

### Task T7: Persistence — decisions DB + settings store

**Depends on:** T1

**Files:**
- Create: `$PKG/decisions/Decision.kt`, `DecisionDao.kt`, `PhotoSorterDb.kt`, `DecisionStore.kt`, `RoomDecisionStore.kt`
- Create: `$PKG/prefs/SettingsStore.kt`, `$PKG/prefs/DataStoreSettingsStore.kt`
- Create: `$TEST/fakes/FakeDecisionStore.kt`, `$TEST/fakes/FakeSettingsStore.kt`
- Test: `$TEST/decisions/RoomDecisionStoreTest.kt`, `$TEST/prefs/DataStoreSettingsStoreTest.kt`

**Interfaces:**
- Produces (package `…photosorter.decisions`):
  - `enum class DecisionState { KEPT, PENDING_TRASH, TRASHED }`, `enum class DecisionSource { SWIPE, DUPLICATE }`
  - `@Entity(tableName = "decisions") data class Decision(@PrimaryKey val mediaId: Long, val state: DecisionState, val source: DecisionSource, val decidedAt: Long)`
  - `data class DecisionCounts(val kept: Int, val pending: Int, val trashed: Int)`
  - `interface DecisionStore { fun observeCounts(): Flow<DecisionCounts>; suspend fun decidedIds(): Set<Long>; suspend fun idsIn(state: DecisionState): List<Long>; suspend fun set(mediaIds: Collection<Long>, state: DecisionState, source: DecisionSource); suspend fun clear(mediaIds: Collection<Long>); suspend fun resetKeptAndPending(); suspend fun pruneTrashed(stillTrashed: Set<Long>) }` — `set` upserts; `pruneTrashed` deletes `TRASHED` rows whose id is not in `stillTrashed`.
  - `@Database(entities = [Decision::class], version = 1, exportSchema = false) abstract class PhotoSorterDb : RoomDatabase { abstract fun decisions(): DecisionDao; companion object { fun create(context: Context): PhotoSorterDb } }` (file name `photo-sorter.db`).
  - `class RoomDecisionStore(db: PhotoSorterDb, clock: () -> Long = System::currentTimeMillis) : DecisionStore`
- Produces (package `…photosorter.prefs`):
  - `interface SettingsStore { val excludedAlbumIds: Flow<Set<String>>; val order: Flow<SortOrder>; suspend fun setAlbumIncluded(albumId: String, included: Boolean); suspend fun setOrder(order: SortOrder) }` — defaults: empty set, `OLDEST_FIRST`.
  - `class DataStoreSettingsStore(dataStore: DataStore<Preferences>) : SettingsStore` + `fun Context.settingsDataStore(): DataStore<Preferences>` (name `settings`, top-level `preferencesDataStore` delegate).
- Produces (test fakes): `class FakeDecisionStore : DecisionStore` (in-memory map, exposes `val rows: Map<Long, Decision>`), `class FakeSettingsStore(excluded: Set<String> = emptySet(), order: SortOrder = OLDEST_FIRST) : SettingsStore` (MutableStateFlows).

- [ ] **Step 1: Write the failing tests** (Robolectric, `@RunWith(AndroidJUnit4::class)`, in-memory Room with `allowMainThreadQueries()`, fixed clock `{ 123L }`):

```kotlin
@Test fun setThenDecidedIds() = runTest { store.set(listOf(1, 2), KEPT, SWIPE); store.set(listOf(3), PENDING_TRASH, SWIPE); assertEquals(setOf(1L, 2L, 3L), store.decidedIds()) }
@Test fun setUpsertsState() = runTest { store.set(listOf(1), PENDING_TRASH, SWIPE); store.set(listOf(1), TRASHED, SWIPE); assertEquals(listOf(1L), store.idsIn(TRASHED)); assertTrue(store.idsIn(PENDING_TRASH).isEmpty()) }
@Test fun countsFlow() = runTest { store.set(listOf(1), KEPT, SWIPE); store.set(listOf(2, 3), PENDING_TRASH, SWIPE); store.set(listOf(4), TRASHED, DUPLICATE); assertEquals(DecisionCounts(1, 2, 1), store.observeCounts().first()) }
@Test fun resetKeepsTrashed() = runTest { /* KEPT 1, PENDING 2, TRASHED 3 */ store.resetKeptAndPending(); assertEquals(setOf(3L), store.decidedIds()) }
@Test fun pruneTrashedOnlyTouchesTrashedRows() = runTest { /* TRASHED 1,2; KEPT 3 */ store.pruneTrashed(setOf(2)); assertEquals(setOf(2L, 3L), store.decidedIds()) }
@Test fun clearRemovesRows() = runTest { store.set(listOf(1, 2), KEPT, SWIPE); store.clear(listOf(1)); assertEquals(setOf(2L), store.decidedIds()) }
@Test fun storesSourceAndTimestamp() = runTest { store.set(listOf(9), TRASHED, DUPLICATE); val row = db.decisions().get(9)!!; assertEquals(DUPLICATE, row.source); assertEquals(123L, row.decidedAt) }
```

```kotlin
// DataStoreSettingsStoreTest: PreferenceDataStoreFactory.create(scope = backgroundScope) { tmpFolder.newFile("s.preferences_pb") }
@Test fun defaults() = runTest { assertEquals(emptySet<String>(), s.excludedAlbumIds.first()); assertEquals(SortOrder.OLDEST_FIRST, s.order.first()) }
@Test fun excludeAndInclude() = runTest { s.setAlbumIncluded("wa", false); s.setAlbumIncluded("dl", false); s.setAlbumIncluded("wa", true); assertEquals(setOf("dl"), s.excludedAlbumIds.first()) }
@Test fun orderPersists() = runTest { s.setOrder(SortOrder.NEWEST_FIRST); assertEquals(SortOrder.NEWEST_FIRST, s.order.first()) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*RoomDecisionStoreTest' --tests '*DataStoreSettingsStoreTest'` → FAIL.
- [ ] **Step 3: Implement** the entity, DAO (`@Upsert`, queries for ids by state, counts as `Flow`, `get(id)`, deletes), DB, `RoomDecisionStore`, both settings classes, both fakes. `set`/`clear` with > 500 ids are chunked (SQLite variable limit).
- [ ] **Step 4: Run** → all PASS.
- [ ] **Step 5: Commit** `feat: decision database and settings store`.

---

### Task T9: `MediaSource` + `MediaAccess`

**Depends on:** T1

**Files:**
- Create: `$PKG/media/MediaSource.kt`, `$PKG/media/MediaStoreSource.kt`, `$PKG/media/CursorMapping.kt`, `$PKG/media/Library.kt`, `$PKG/media/MediaAccess.kt`
- Create: `$TEST/fakes/FakeMediaSource.kt`
- Test: `$TEST/media/CursorMappingTest.kt`, `$TEST/media/LibraryTest.kt`, `$TEST/media/MediaAccessTest.kt`

**Interfaces:**
- Produces (package `…photosorter.media`):
  - `data class TrashedItem(val item: MediaItem, val expiresAt: Long)` (ms)
  - `interface MediaSource { suspend fun allItems(): List<MediaItem>; suspend fun trashedItems(ids: Collection<Long>): List<TrashedItem>; suspend fun exists(item: MediaItem): Boolean; suspend fun openStream(item: MediaItem): InputStream? }`
  - `object Library { fun albumsOf(items: List<MediaItem>): List<Album>; fun filterAndSort(items: List<MediaItem>, excludedAlbumIds: Set<String>, order: SortOrder): List<MediaItem> }` — albums: grouped by `albumId`, cover = newest item, sorted by count desc then name; sort: `OLDEST_FIRST` = `dateTaken` asc then `id` asc, `NEWEST_FIRST` = the exact reverse.
  - `object CursorMapping { fun toMediaItem(c: Cursor, kind: MediaKind): MediaItem; val IMAGE_PROJECTION: Array<String>; val VIDEO_PROJECTION: Array<String> }` — columns `_ID, DISPLAY_NAME, SIZE, MIME_TYPE, DATE_TAKEN, DATE_MODIFIED, BUCKET_ID, BUCKET_DISPLAY_NAME, RELATIVE_PATH` (+ `DURATION` for video). Fallbacks: `dateTaken` = `DATE_TAKEN` if > 0 else `DATE_MODIFIED * 1000` else 0; null `BUCKET_ID` → `""`; null `BUCKET_DISPLAY_NAME` → `"Internal storage"`; null `RELATIVE_PATH` → `""`; null `DISPLAY_NAME` → `""`; null `MIME_TYPE` → `""`; null `SIZE` → 0.
  - `class MediaStoreSource(resolver: ContentResolver) : MediaSource` — queries Images and Video `EXTERNAL_CONTENT_URI` on `Dispatchers.IO`; `trashedItems` uses a `Bundle` query with `QUERY_ARG_MATCH_TRASHED = MATCH_ONLY` and `_id IN (…)` in chunks of 500, reading `DATE_EXPIRES` (seconds → ms); `exists` queries by id (default match excludes trashed); `openStream` returns null on `FileNotFoundException`/`SecurityException`.
  - `enum class AccessLevel { FULL, PARTIAL, NONE }`; `object MediaAccess { fun permissionsToRequest(sdkInt: Int): Array<String>; fun level(sdkInt: Int, granted: (String) -> Boolean): AccessLevel; fun level(context: Context): AccessLevel }` — 30–32: `[READ_EXTERNAL_STORAGE]`; 33: `[READ_MEDIA_IMAGES, READ_MEDIA_VIDEO]`; 34+: those plus `READ_MEDIA_VISUAL_USER_SELECTED`. Level: 33+ images && video → FULL; 34+ else visual-user-selected → PARTIAL; 30–32 read-external → FULL; otherwise NONE.
- Produces (test fake): `class FakeMediaSource(var items: List<MediaItem> = emptyList()) : MediaSource` with `val trashed: MutableMap<Long, TrashedItem>`, `val missing: MutableSet<Long>` (for `exists`), `val contents: MutableMap<Long, ByteArray>` (for `openStream`).

- [ ] **Step 1: Write the failing tests:**

```kotlin
// CursorMappingTest (Robolectric, MatrixCursor with the projection columns)
@Test fun mapsImageRow() { /* id 5, "a.jpg", 2048, "image/jpeg", 1_600_000_000_000, 1_500_000_000, "b1", "Camera", "DCIM/Camera/" */
    assertEquals(MediaItem(5, IMAGE, "b1", "Camera", 1_600_000_000_000, 2048, "DCIM/Camera/", "a.jpg", "image/jpeg", null), item) }
@Test fun mapsVideoDuration() { /* DURATION 4200 */ assertEquals(4200L, item.durationMs) }
@Test fun mapsRowWithMissingMetadata() { /* DATE_TAKEN null, DATE_MODIFIED 1_500_000_000, BUCKET_* null, RELATIVE_PATH null */
    assertEquals(1_500_000_000_000, item.dateTaken); assertEquals("", item.albumId); assertEquals("Internal storage", item.albumName); assertEquals("", item.relativePath) }
@Test fun zeroDateTakenFallsBack() { /* DATE_TAKEN 0, DATE_MODIFIED 7 */ assertEquals(7000L, item.dateTaken) }

// LibraryTest (plain JUnit)
@Test fun oldestFirstWithIdTieBreak() { assertEquals(listOf(2L, 3L, 1L), Library.filterAndSort(listOf(testItem(1, dateTaken = 9), testItem(3, dateTaken = 5), testItem(2, dateTaken = 5)), emptySet(), OLDEST_FIRST).map { it.id }) }
@Test fun newestFirstIsReverse() { /* same input, NEWEST_FIRST */ assertEquals(listOf(1L, 3L, 2L), ids) }
@Test fun excludedAlbumsFiltered() { /* albums camera, wa; exclude wa */ assertTrue(result.all { it.albumId == "camera" }) }
@Test fun albumsCountedWithNewestCover() { /* camera: ids 1(d=1),2(d=5); wa: 3 */ assertEquals(listOf("camera" to 2, "wa" to 1), albums.map { it.id to it.count }); assertEquals(2L, albums[0].cover.id) }

// MediaAccessTest (plain JUnit, string constants from android.Manifest.permission)
@Test fun permissionsPerSdk() { assertArrayEquals(arrayOf(READ_EXTERNAL_STORAGE), MediaAccess.permissionsToRequest(30)); assertEquals(2, MediaAccess.permissionsToRequest(33).size); assertEquals(3, MediaAccess.permissionsToRequest(34).size) }
@Test fun levels() {
    assertEquals(FULL, MediaAccess.level(34) { it != READ_MEDIA_VISUAL_USER_SELECTED })
    assertEquals(PARTIAL, MediaAccess.level(34) { it == READ_MEDIA_VISUAL_USER_SELECTED })
    assertEquals(NONE, MediaAccess.level(33) { it == READ_MEDIA_IMAGES })
    assertEquals(FULL, MediaAccess.level(31) { it == READ_EXTERNAL_STORAGE })
    assertEquals(NONE, MediaAccess.level(30) { false }) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*CursorMappingTest' --tests '*LibraryTest' --tests '*MediaAccessTest'` → FAIL.
- [ ] **Step 3: Implement** all five files and the fake per Interfaces. `MediaStoreSource`'s queries are not unit-tested (Robolectric has no MediaStore provider); they are covered by the T17 manual checklist.
- [ ] **Step 4: Run** → all PASS.
- [ ] **Step 5: Commit** `feat: MediaStore source, library sorting and access levels`.

---

### Task T10: `Trasher` + `RequestLauncher`

**Depends on:** T1

**Files:**
- Create: `$PKG/trash/Trasher.kt`, `$PKG/trash/MediaStoreTrasher.kt`, `$PKG/trash/RequestLauncher.kt`
- Create: `$TEST/fakes/FakeTrasher.kt`
- Test: `$TEST/trash/MediaStoreTrasherTest.kt`

**Interfaces:**
- Produces (package `…photosorter.trash`):
  - `fun interface RequestLauncher { suspend fun launch(request: IntentSenderRequest): Boolean }` (true ⇔ `RESULT_OK`)
  - `@Composable fun rememberRequestLauncher(): RequestLauncher` — `rememberLauncherForActivityResult(StartIntentSenderForResult())`, one request at a time (a `Mutex`), each awaited with a `CompletableDeferred`.
  - `interface Trasher { fun canTrashInstantly(): Boolean; suspend fun trash(items: List<MediaItem>, launcher: RequestLauncher): List<MediaItem>; suspend fun restore(items: List<MediaItem>, launcher: RequestLauncher): List<MediaItem> }` — return the items whose chunk succeeded.
  - `const val TRASH_CHUNK = 500`
  - `class MediaStoreTrasher(private val sdkInt: Int, private val canManageMedia: () -> Boolean, private val requestFactory: (uris: List<Uri>, trash: Boolean) -> IntentSenderRequest) : Trasher` + `companion object { fun create(context: Context): MediaStoreTrasher }` (real factory = `MediaStore.createTrashRequest(resolver, uris, trash).intentSender` → `IntentSenderRequest.Builder(...).build()`; `canManageMedia = { MediaStore.canManageMedia(context) }`). `canTrashInstantly()` = `sdkInt >= 31 && canManageMedia()`. Empty input → no request, empty result. A launcher exception counts as that chunk failing.
- Produces (test fake): `class FakeTrasher(var instant: Boolean = false) : Trasher` with `val trashCalls: MutableList<List<Long>>`, `val restoreCalls: MutableList<List<Long>>`, `var trashResult: (List<MediaItem>) -> List<MediaItem> = { it }`, `var restoreResult: (List<MediaItem>) -> List<MediaItem> = { it }`, and `var gate: CompletableDeferred<Unit>? = null` (when set, `trash` awaits it before returning — used by T12's serialisation test).

- [ ] **Step 1: Write the failing tests** (Robolectric for `Uri`; the factory builds `IntentSenderRequest` from a dummy `PendingIntent` and records `(uris.size, trash)`):

```kotlin
@Test fun instantOnlyWithPermissionOnApi31Plus() { assertFalse(trasher(sdk = 30, manage = true).canTrashInstantly()); assertFalse(trasher(31, false).canTrashInstantly()); assertTrue(trasher(31, true).canTrashInstantly()) }
@Test fun chunksAt500() = runTest { trasher().trash((1L..1200).map { testItem(it) }, ok); assertEquals(listOf(500 to true, 500 to true, 200 to true), factoryCalls) }
@Test fun trashReturnsOnlyItemsFromSuccessfulChunks() = runTest { val results = ArrayDeque(listOf(true, false, true))
    val done = trasher().trash((1L..1200).map { testItem(it) }) { results.removeFirst() }
    assertEquals((1L..500) + (1001L..1200), done.map { it.id }) }
@Test fun restoreUsesUntrash() = runTest { trasher().restore(listOf(testItem(1)), ok); assertEquals(listOf(1 to false), factoryCalls) }
@Test fun emptyInputLaunchesNothing() = runTest { assertTrue(trasher().trash(emptyList(), ok).isEmpty()); assertTrue(factoryCalls.isEmpty()) }
@Test fun launcherExceptionFailsThatChunk() = runTest { assertTrue(trasher().trash(listOf(testItem(1))) { throw IllegalStateException() }.isEmpty()) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*MediaStoreTrasherTest'` → FAIL.
- [ ] **Step 3: Implement** the three files and the fake. (`rememberRequestLauncher` is covered by the manual checklist.)
- [ ] **Step 4: Run** → all PASS.
- [ ] **Step 5: Commit** `feat: trash/restore requests with 500-item chunking`.

---

### Task T11: Swipe card components

**Depends on:** T1
**Spec:** "Swipe" screen section; mockup `swipe-screen.html` design B.

**Files:**
- Create: `$PKG/ui/swipe/SwipeGesture.kt` (pure threshold logic), `$PKG/ui/swipe/SwipeCard.kt`, `$PKG/ui/swipe/MediaCard.kt`, `$PKG/ui/swipe/EdgeGlows.kt`
- Test: `$TEST/ui/swipe/SwipeGestureTest.kt`, `$TEST/ui/swipe/SwipeCardTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `contentUri()`, `PsColors`.
- Produces (package `…photosorter.ui.swipe`):
  - `enum class SwipeDirection { LEFT, RIGHT, UP }`
  - `data class SwipeProgress(val x: Float, val y: Float)` — drag as a fraction of width/height (−1..1), `SwipeProgress(0f, 0f)` at rest.
  - `fun resolveSwipe(dx: Float, dy: Float, vx: Float, vy: Float, width: Float, height: Float, flingVelocity: Float): SwipeDirection?` — horizontal wins when `|dx|/width >= |dy|/height`. RIGHT/LEFT if `|dx| > 0.30 * width` or (`|vx| > flingVelocity` and `vx` has the same sign as `dx`); UP if `-dy > 0.20 * height` or (`-vy > flingVelocity` and `dy < 0`); downward drags → null. `flingVelocity` = 800.dp in px.
  - `@Composable fun SwipeCard(key: Any, onSwiped: (SwipeDirection) -> Unit, modifier: Modifier = Modifier, onDrag: (SwipeProgress) -> Unit = {}, content: @Composable () -> Unit)` — test tag `"swipe-card"`; follows the finger with rotation `x * 12°`; on release animates off-screen then calls `onSwiped`, or springs back and reports `SwipeProgress(0f, 0f)`; state reset when `key` changes.
  - `@Composable fun MediaCard(item: MediaItem, undecodable: Boolean, muted: Boolean, onToggleMute: () -> Unit, onLoadFailed: () -> Unit, modifier: Modifier = Modifier)` — images: Coil `AsyncImage`, `ContentScale.Fit` on black, `onError` → `onLoadFailed`; videos: Media3 `ExoPlayer` in `AndroidView(PlayerView)`, no controller, `REPEAT_MODE_ONE`, autoplay, volume 0/1 from `muted`, tap → `onToggleMute`, player released on dispose, player error → `onLoadFailed`; `undecodable` → placeholder (icon + `item.displayName`, test tag `"placeholder"`).
  - `@Composable fun EdgeGlows(progress: SwipeProgress, modifier: Modifier = Modifier)` — left red / right green vertical gradients 46.dp wide with rotated "TRASH" / "KEEP" labels; resting alpha 0.25, rising linearly to 1.0 at the threshold in that direction.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// SwipeGestureTest — width 1000, height 2000, fling 2000
@Test fun rightPastThreshold() = assertEquals(RIGHT, resolveSwipe(310f, 0f, 0f, 0f, 1000f, 2000f, 2000f))
@Test fun leftPastThreshold() = assertEquals(LEFT, resolveSwipe(-310f, 20f, 0f, 0f, 1000f, 2000f, 2000f))
@Test fun upPastThreshold() = assertEquals(UP, resolveSwipe(30f, -410f, 0f, 0f, 1000f, 2000f, 2000f))
@Test fun shortDragReturnsNull() = assertNull(resolveSwipe(200f, -100f, 0f, 0f, 1000f, 2000f, 2000f))
@Test fun flingCounts() = assertEquals(LEFT, resolveSwipe(-50f, 0f, -2500f, 0f, 1000f, 2000f, 2000f))
@Test fun flingAgainstDragIgnored() = assertNull(resolveSwipe(50f, 0f, -2500f, 0f, 1000f, 2000f, 2000f))
@Test fun downwardIgnored() = assertNull(resolveSwipe(0f, 900f, 0f, 3000f, 1000f, 2000f, 2000f))

// SwipeCardTest — Robolectric + createComposeRule; SwipeCard(key = 1, onSwiped = { got += it }) { Box(Modifier.fillMaxSize()) }
@Test fun swipeRightKeeps() { rule.onNodeWithTag("swipe-card").performTouchInput { swipeRight() }; rule.waitForIdle(); assertEquals(listOf(RIGHT), got) }
@Test fun swipeLeftTrashes() { /* swipeLeft() */ assertEquals(listOf(LEFT), got) }
@Test fun swipeUpDefers() { /* swipeUp() */ assertEquals(listOf(UP), got) }
@Test fun shortDragSpringsBack() { rule.onNodeWithTag("swipe-card").performTouchInput { down(center); moveBy(Offset(width * 0.1f, 0f)); up() }; rule.waitForIdle(); assertTrue(got.isEmpty()) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SwipeGestureTest' --tests '*SwipeCardTest'` → FAIL.
- [ ] **Step 3: Implement** the four files per Interfaces.
- [ ] **Step 4: Run** → all PASS.
- [ ] **Step 5: Commit** `feat: swipe card, media card and edge glows`.

---

### Task T12: Swipe screen

**Depends on:** T5, T7, T9, T10, T11
**Spec:** "Data flow → Start → Swipe" steps 2–8; "Swipe" screen; error-handling rows 3–6.

**Files:**
- Create: `$PKG/ui/swipe/SwipeViewModel.kt`, `$PKG/ui/swipe/SwipeScreen.kt`
- Test: `$TEST/ui/swipe/SwipeViewModelTest.kt`

**Interfaces:**
- Consumes: `SwipeQueue`/`SwipeAction` (T5); `DecisionStore`, `SettingsStore`, fakes (T7); `MediaSource`, `Library`, `FakeMediaSource` (T9); `Trasher`, `RequestLauncher`, `FakeTrasher` (T10); `SwipeCard`, `MediaCard`, `EdgeGlows`, `SwipeDirection` (T11).
- Produces:
  - `data class SwipeUiState(val loading: Boolean = true, val current: MediaItem? = null, val upcoming: List<MediaItem> = emptyList(), val decided: Int = 0, val total: Int = 0, val pendingCount: Int = 0, val canUndo: Boolean = false, val undecodable: Set<Long> = emptySet(), val done: Boolean = false, val sessionKept: Int = 0, val sessionTrashed: Int = 0, val message: String? = null)` — `upcoming` = next 2.
  - `class SwipeViewModel(media: MediaSource, decisions: DecisionStore, settings: SettingsStore, trasher: Trasher) : ViewModel` with `val state: StateFlow<SwipeUiState>`, `fun onSwipe(direction: SwipeDirection, launcher: RequestLauncher)`, `fun onUndo(launcher: RequestLauncher)`, `fun onTrashPending(launcher: RequestLauncher)`, `fun onLoadFailed(item: MediaItem)`, `fun onMessageShown()`.
  - `@Composable fun SwipeScreen(viewModel: SwipeViewModel, onBack: () -> Unit)`
- Behaviour pinned here (beyond the spec): instant trash requests go through one `Channel`/`Mutex`-serialised worker in submission order; an undo of an item whose instant trash is still in flight waits for it, then restores. `sessionTrashed` counts both `TRASHED` and `PENDING_TRASH` swipes. Message copy: partial or failed batch → `"Some items weren't trashed. They're still waiting."`; instant trash failure → `"Couldn't trash that item. It's waiting in the batch."`; restore failure → `"Couldn't restore that item."`.

- [ ] **Step 1: Write the failing tests** (`StandardTestDispatcher` set as Main; items `testItem(1..5)`; `ok = RequestLauncher { true }`):

```kotlin
@Test fun loadsUndecidedItemsInOrderFromIncludedAlbums() // decided {2}, item 4 in excluded album "wa" -> current 1, upcoming [3, 5]
@Test fun rightKeeps() // onSwipe(RIGHT) -> rows[1].state == KEPT, current 2, decided 1, sessionKept 1
@Test fun upDefersWithoutPersisting() // onSwipe(UP) -> rows empty, current 2, item 1 last
@Test fun leftBatchModeQueuesPending() // instant=false -> rows[1] PENDING_TRASH, pendingCount 1, trashCalls empty
@Test fun leftInstantTrashes() // instant=true -> trashCalls [[1]], rows[1] TRASHED
@Test fun instantFailureFallsBackToPending() // trashResult = { emptyList() } -> rows[1] PENDING_TRASH, message "Couldn't trash that item. It's waiting in the batch."
@Test fun rapidInstantTrashesAreSerializedAndAllRecorded() // gate set; 5x onSwipe(LEFT); complete gate; advanceUntilIdle -> trashCalls == [[1],[2],[3],[4],[5]], all TRASHED
@Test fun undoEachKind() // keep->undo clears row; pending->undo clears row; instant trash->undo => restoreCalls [[id]] and row cleared; later->undo item is current again
@Test fun undoRestoreFailureKeepsTrashed() // restoreResult = { emptyList() } -> row stays TRASHED, message "Couldn't restore that item."
@Test fun trashPendingKeepsFailedChunkPending() // pending 1,2,3; trashResult = { it.take(2) } -> 1,2 TRASHED, 3 PENDING_TRASH, message "Some items weren't trashed. They're still waiting."
@Test fun loadFailedMissingItemIsDropped() // media.missing += 1; onLoadFailed(item1) -> current 2, total 4
@Test fun loadFailedExistingItemIsMarkedUndecodable() // onLoadFailed(item1) -> undecodable == {1}, current still 1
@Test fun endOfQueueIsDone() // swipe all 5 -> done, sessionKept + sessionTrashed == 5
@Test fun pendingFromEarlierSessionCountsButIsNotQueued() // rows[3] PENDING_TRASH before init -> pendingCount 1, item 3 not in queue
@Test fun instantFlagIsReadOnEverySwipe() // instant=true, LEFT on 1 -> TRASHED; set instant=false, LEFT on 2 -> PENDING_TRASH (Media management revoked mid-session)
```

(Each comment line is the arrange → act → assert the test encodes; values are exact.)

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SwipeViewModelTest'` → FAIL.
- [ ] **Step 3: Implement `SwipeViewModel`** per Interfaces.
- [ ] **Step 4: Implement `SwipeScreen`** per the spec's Swipe section: full-bleed black `Box`; `EdgeGlows`; `SwipeCard(key = current.id)` wrapping `MediaCard` (local `muted` state default true); top 3.dp progress bar (`decided / total`) and "↑ LATER" hint; bottom gradient with date (`EEEE d MMM yyyy`, device locale), `albumName · displayName`, "↶ Undo" chip (enabled iff `canUndo`) and red "🗑 N" chip when `pendingCount > 0`; snackbar for `message`; preload `upcoming` via `ImageLoader.enqueue`; `done` → "All done" panel with session counts, pending button, "Back to albums" (`onBack`); `loading` → centred progress; system back → `onBack`.
- [ ] **Step 5: Run** the T12 tests plus `./gradlew :app:assembleDebug` → PASS / BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `feat: swipe screen`.

---

### Task T13: Start screen

**Depends on:** T7, T9, T10
**Spec:** "Start" screen (design B modified), "Data flow" step 1, "Prune", error rows 1–2.

**Files:**
- Create: `$PKG/ui/start/StartViewModel.kt`, `$PKG/ui/start/StartScreen.kt`
- Test: `$TEST/ui/start/StartViewModelTest.kt`

**Interfaces:**
- Produces:
  - `data class AlbumRow(val album: Album, val included: Boolean)`
  - `data class StartUiState(val loading: Boolean = true, val access: AccessLevel = AccessLevel.NONE, val albums: List<AlbumRow> = emptyList(), val selectedAlbums: Int = 0, val queueCount: Int = 0, val order: SortOrder = SortOrder.OLDEST_FIRST, val pendingCount: Int = 0, val message: String? = null)` with `val canStart: Boolean get() = selectedAlbums > 0 && queueCount > 0`.
  - `class StartViewModel(media: MediaSource, decisions: DecisionStore, settings: SettingsStore, trasher: Trasher, accessLevel: () -> AccessLevel) : ViewModel` with `state`, `fun refresh()` (re-reads access; if not NONE: prune — `decisions.pruneTrashed(media.trashedItems(decisions.idsIn(TRASHED)).map { it.item.id }.toSet())` — then loads), `fun toggleAlbum(albumId: String)`, `fun setOrder(order: SortOrder)`, `fun onTrashPending(launcher: RequestLauncher)`, `fun onMessageShown()`.
  - `@Composable fun StartScreen(viewModel: StartViewModel, onStart: () -> Unit, onFindDuplicates: () -> Unit, onOpenSettings: () -> Unit)` — calls `refresh()` on every resume (`LifecycleResumeEffect`).
- `queueCount` = `Library.filterAndSort(all, excluded, order)` minus `decidedIds()`. Album counts show all items in the album (decided included). Same pending-batch message copy as T12.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun noAccessLoadsNothing() // accessLevel = { NONE } -> access NONE, albums empty, media.allItems never called
@Test fun albumsWithIncludedFlags() // items in camera(3), wa(1); excluded {"wa"} -> rows [camera included, wa excluded], selectedAlbums 1
@Test fun queueCountExcludesDecidedAndExcludedAlbums() // camera 1,2,3 + wa 4; KEPT 2; excluded wa -> queueCount 2
@Test fun toggleAlbumPersistsAndRecounts() // toggleAlbum("wa") -> settings.excluded == {} and queueCount 3
@Test fun cannotStartWithNothingSelectedOrEmptyQueue() // exclude all -> canStart false; all decided -> canStart false
@Test fun setOrderPersists() // setOrder(NEWEST_FIRST) -> settings order NEWEST_FIRST, state.order NEWEST_FIRST
@Test fun refreshPrunesTrashedRows() // TRASHED 1,2; media.trashed has only 2 -> decidedIds == {2}
@Test fun pendingBannerAndTrashNow() // PENDING 1,2 -> pendingCount 2; onTrashPending -> trashCalls [[1,2]], both TRASHED, pendingCount 0
@Test fun partialAccessStillLoads() // accessLevel = { PARTIAL } -> access PARTIAL, albums non-empty
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*StartViewModelTest'` → FAIL.
- [ ] **Step 3: Implement `StartViewModel`.**
- [ ] **Step 4: Implement `StartScreen`** per spec + `start-screen.html` design B: header "Choose albums", subtitle `"$selected of $total selected · $queueCount photos & videos"`, Settings icon top-right with `contentDescription = "Settings"` (no Duplicates icon); pending banner `"$n waiting to trash · Trash now"`; 2-column `LazyVerticalGrid` of covers (Coil, video covers via coil-video), white outline + check when included, alpha 0.45 when excluded; bottom bar: row of Oldest/Newest segmented control + "Start ›" (enabled iff `canStart`), then full-width "Find duplicates" outlined button. `NONE` → explanation text + "Grant access" (`RequestMultiplePermissions` with `MediaAccess.permissionsToRequest(SDK_INT)`); after a denial where `shouldShowRequestPermissionRationale` is false for all → button becomes "Open settings" (`ACTION_APPLICATION_DETAILS_SETTINGS`). `PARTIAL` → banner "Only photos you selected are visible · Change" (re-launches the permission request).
- [ ] **Step 5: Run** T13 tests + `./gradlew :app:assembleDebug` → PASS.
- [ ] **Step 6: Commit** `feat: start screen with album grid`.

---

### Task T14: Duplicates screen

**Depends on:** T6, T7, T9, T10
**Spec:** "Data flow → Duplicates", "Duplicates" screen (design B), error row 7.

**Files:**
- Create: `$PKG/ui/dupes/DuplicatesViewModel.kt`, `$PKG/ui/dupes/DuplicatesScreen.kt`
- Test: `$TEST/ui/dupes/DuplicatesViewModelTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface DuplicatesUiState { data object Idle : DuplicatesUiState; data class Scanning(val progress: ScanProgress?) : DuplicatesUiState; data class Results(val groups: List<DuplicateGroup>, val unreadableCount: Int, val trashing: Boolean = false, val message: String? = null) : DuplicatesUiState { val copiesToTrash: Int; val bytesFreed: Long } }` (empty `groups` = "No duplicates found").
  - `class DuplicatesViewModel(media: MediaSource, decisions: DecisionStore, trasher: Trasher, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel` with `state`, `fun scan()` (starts if not scanning; called by the screen on first composition and by "↻ Rescan"), `fun cancel()` (→ `Idle`), `fun setKeeper(hash: String, itemId: Long)`, `fun trashAll(launcher: RequestLauncher)`, `fun onMessageShown()`. `onCleared` cancels the scan (leaving the screen cancels).
  - `@Composable fun DuplicatesScreen(viewModel: DuplicatesViewModel, onBack: () -> Unit)`
- `trashAll`: trash every non-keeper across all groups in one `trasher.trash` call; succeeded ids → `decisions.set(ids, TRASHED, DUPLICATE)`; a group is removed when all its non-keepers succeeded; otherwise it stays with the succeeded copies removed (and is dropped if one copy remains). Partial failure message: `"Some copies weren't trashed."`.

- [ ] **Step 1: Write the failing tests** (FakeMediaSource with `contents`; `ioDispatcher` = test dispatcher):

```kotlin
@Test fun scanProducesResults() // 1,2 identical; 3 unique -> Results with 1 group [1,2], keeper 1, copiesToTrash 1
@Test fun progressReportedWhileScanning() // collect states -> contains Scanning(progress != null) before Results
@Test fun cancelReturnsToIdle()
@Test fun setKeeperChangesKeeper() // setKeeper(hash, 2) -> group.keeperId 2
@Test fun trashAllTrashesNonKeepersAndRecords() // groups [1,2],[3,4,5]; keepers 1,3 -> trashCalls [[2,4,5]]; rows 2,4,5 TRASHED with source DUPLICATE; groups empty
@Test fun partialTrashKeepsUnfinishedGroups() // trashResult = { it.filter { i -> i.id != 5L } } -> group [3,5] remains, message "Some copies weren't trashed."
@Test fun unreadableCountSurfaced() // contents missing for 2 of 3 same-size -> unreadableCount 2
@Test fun noDuplicates() // all unique -> Results(groups = empty)
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*DuplicatesViewModelTest'` → FAIL.
- [ ] **Step 3: Implement `DuplicatesViewModel`** (`DuplicateFinder.scan(media.allItems(), media::openStream)` on `ioDispatcher`).
- [ ] **Step 4: Implement `DuplicatesScreen`** per `duplicates-screen.html` design B: top bar (back, "Duplicates", "↻ Rescan"); Scanning → linear progress, `"$filesDone of $filesTotal files"` + human-readable bytes, Cancel; Results → headline bytes freed (`Formatter.formatShortFileSize`), `"$copies copies · $groups groups"`, hint "Tap a copy to keep it instead", optional `"$n files couldn't be read"`; `LazyColumn` of groups (caption `displayName · size`, `LazyRow` of 62.dp thumbnails, keeper green outline + "KEEP" badge, others alpha 0.55 + bin badge; tap → `setKeeper`; long-press → tooltip/snackbar with `albumName` + `relativePath`); pinned red "Trash N copies" button (disabled while `trashing` or N = 0); empty → "No duplicates found".
- [ ] **Step 5: Run** T14 tests + `./gradlew :app:assembleDebug` → PASS.
- [ ] **Step 6: Commit** `feat: duplicates screen`.

---

### Task T15: Settings + Trashed screens

**Depends on:** T7, T9, T10
**Spec:** "Settings" (design A), "Trashed page", "Reset decisions".

**Files:**
- Create: `$PKG/ui/settings/SettingsViewModel.kt`, `$PKG/ui/settings/SettingsScreen.kt`, `$PKG/ui/settings/TrashedViewModel.kt`, `$PKG/ui/settings/TrashedScreen.kt`, `$PKG/ui/settings/DaysLeft.kt`
- Test: `$TEST/ui/settings/SettingsViewModelTest.kt`, `$TEST/ui/settings/TrashedViewModelTest.kt`, `$TEST/ui/settings/DaysLeftTest.kt`

**Interfaces:**
- Produces:
  - `data class SettingsUiState(val access: AccessLevel = AccessLevel.NONE, val manageMediaSupported: Boolean = false, val instantTrash: Boolean = false, val trashedCount: Int = 0, val versionLabel: String = "")`
  - `class SettingsViewModel(decisions: DecisionStore, trasher: Trasher, accessLevel: () -> AccessLevel, sdkInt: Int, versionLabel: String) : ViewModel` with `state`, `fun refresh()`, `fun resetDecisions()`. `manageMediaSupported = sdkInt >= 31`; `instantTrash = trasher.canTrashInstantly()`; `trashedCount` from `observeCounts().trashed`.
  - `@Composable fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenTrashed: () -> Unit)` — `refresh()` on resume.
  - `fun daysLeft(expiresAtMs: Long, nowMs: Long): Int` = `max(0, floor((expiresAtMs - nowMs) / 86_400_000))`; label: 0 → `"today"`, else `"${n}d"`.
  - `data class TrashedUiState(val loading: Boolean = true, val items: List<TrashedItem> = emptyList(), val selected: Set<Long> = emptySet(), val restoring: Boolean = false, val message: String? = null)`
  - `class TrashedViewModel(media: MediaSource, decisions: DecisionStore, trasher: Trasher) : ViewModel` with `state`, `fun load()`, `fun toggle(id: Long)`, `fun selectAll()`, `fun restoreSelected(launcher: RequestLauncher)`, `fun restoreAll(launcher: RequestLauncher)`, `fun onMessageShown()`. Items = `media.trashedItems(decisions.idsIn(TRASHED))` sorted by `expiresAt` asc. Restored ids → `decisions.clear(ids)` and removed from the list and selection. Partial failure → `"Some items couldn't be restored."`.
  - `@Composable fun TrashedScreen(viewModel: TrashedViewModel, onBack: () -> Unit)`

- [ ] **Step 1: Write the failing tests:**

```kotlin
// DaysLeftTest
@Test fun floorsToWholeDays() { val now = 0L; assertEquals(6, daysLeft(6 * DAY + 5 * HOUR, now)); assertEquals(0, daysLeft(3 * HOUR, now)); assertEquals(0, daysLeft(-DAY, now)) }
// SettingsViewModelTest
@Test fun reflectsPermissionsAndCounts() // sdk 34, instant true, TRASHED 1,2 -> manageMediaSupported, instantTrash, trashedCount 2, versionLabel "1.0.0 (build 1)"
@Test fun api30HidesManageMedia() // sdk 30 -> manageMediaSupported false
@Test fun refreshPicksUpPermissionChange() // trasher.instant false -> refresh after setting true -> instantTrash true
@Test fun resetKeepsTrashed() // KEPT 1, PENDING 2, TRASHED 3 -> resetDecisions -> decidedIds {3}
// TrashedViewModelTest
@Test fun loadsTrashedSortedByExpiry() // TRASHED 1,2; trashed expiresAt 1->900, 2->100 -> items [2,1]
@Test fun omitsRowsNoLongerInTrash() // TRASHED 1,2; media.trashed has only 1 -> items [1]
@Test fun restoreSelectedClearsRows() // select 1 -> restoreCalls [[1]], decidedIds {2}, items [2], selected empty
@Test fun restoreAllPartialFailure() // restoreResult = { it.take(1) } -> 1 restored, 1 remains, message "Some items couldn't be restored."
@Test fun selectAllAndToggle()
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SettingsViewModelTest' --tests '*TrashedViewModelTest' --tests '*DaysLeftTest'` → FAIL.
- [ ] **Step 3: Implement** both ViewModels and `DaysLeft.kt`.
- [ ] **Step 4: Implement both screens** per `settings-screen.html` design A (grouped sections Permissions / Library / Data / About with the exact row copy from the spec; Media management row hidden when not supported, tap → `Settings.ACTION_REQUEST_MANAGE_MEDIA` with `package:` URI; photo access row: "All photos ✓" / "Selected only · Change" / "Not granted · Grant" → app details settings; "Reset decisions" → `AlertDialog` "Reset decisions?" / "Kept photos will show up again. Trashed items stay in the trash." / "Reset" / "Cancel"; "Source on GitHub ↗" opens `https://github.com/lronsmith/photo-sorter-android` via `ACTION_VIEW` — the browser does the networking, the app needs no permission) and the right half of design C for Trashed (3-column grid, days-left label per tile, tap to select with white outline + check, "Select all", pinned green "Restore N selected" — or "Restore all" when nothing is selected —, empty state "Nothing in the trash").
- [ ] **Step 5: Run** T15 tests + `./gradlew :app:assembleDebug` → PASS.
- [ ] **Step 6: Commit** `feat: settings and trashed screens`.

---

### Task T16: App integration

**Depends on:** T12, T13, T14, T15

**Files:**
- Create: `$PKG/AppContainer.kt`, `$PKG/ui/PhotoSorterApp.kt`
- Modify: `$PKG/MainActivity.kt`
- Test: `$TEST/ui/PhotoSorterAppTest.kt`

**Interfaces:**
- Consumes: every ViewModel/screen above.
- Produces:
  - `interface AppDeps { val media: MediaSource; val decisions: DecisionStore; val settings: SettingsStore; val trasher: Trasher; fun accessLevel(): AccessLevel; val sdkInt: Int; val versionLabel: String }`
  - `class AppContainer(context: Context) : AppDeps` — `PhotoSorterDb.create`, `DataStoreSettingsStore(context.settingsDataStore())`, `MediaStoreSource(context.contentResolver)`, `MediaStoreTrasher.create(context)`, `MediaAccess.level(context)`, `Build.VERSION.SDK_INT`, `"${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"` (`buildConfig` is enabled by T1; T16 does not edit `app/build.gradle.kts`). Created once in `MainActivity` via `lazy` held by a custom `Application` subclass `PhotoSorterApplication` (register in manifest).
  - `@Composable fun PhotoSorterApp(deps: AppDeps)` — `NavHost` with routes `start` (start destination), `swipe`, `duplicates`, `settings`, `trashed`; ViewModels created with `viewModel { … }` initializers from `deps`; `swipe` gets a fresh `SwipeViewModel` per navigation (scoped to its back-stack entry).

- [ ] **Step 1: Write the failing smoke test** (Robolectric + `createComposeRule`, fake deps with `testItem(1..3)`, access FULL):

```kotlin
@Test fun startShowsAlbumsAndNavigates() {
    rule.setContent { PhotoSorterTheme { PhotoSorterApp(fakeDeps) } }
    rule.onNodeWithText("Choose albums").assertIsDisplayed()
    rule.onNodeWithText("Find duplicates").performClick(); rule.onNodeWithText("Duplicates").assertIsDisplayed()
    Espresso.pressBack(); rule.onNodeWithContentDescription("Settings").performClick(); rule.onNodeWithText("Trashed by this app").assertIsDisplayed()
}
@Test fun startOpensSwipe() { /* click "Start ›" -> node with tag "swipe-card" exists */ }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*PhotoSorterAppTest'` → FAIL.
- [ ] **Step 3: Implement** `AppContainer`, `PhotoSorterApplication`, `PhotoSorterApp`, update `MainActivity` (`enableEdgeToEdge()`, `setContent { PhotoSorterTheme { PhotoSorterApp(app.container) } }`).
- [ ] **Step 4: Full check:** `./gradlew lint testDebugUnitTest assembleDebug` (the exact CI command) → BUILD SUCCESSFUL, all tests pass, no lint errors.
- [ ] **Step 5: Commit** `feat: wire screens, navigation and app container`.

---

### Task T17: README, final review, PR, release verification

**Depends on:** T3, T4, T16
**Works on:** the integration branch directly (controller, with a final reviewer subagent).

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Write the README** (for a non-technical Samsung user first, developer notes last): what the app does (swipe right keep / left trash / up later, Duplicates, Trashed); **Install**: open the [latest release](https://github.com/lronsmith/photo-sorter-android/releases/latest) on the phone, download `photo-sorter.apk`, open it, allow "Install unknown apps" for the browser/My Files when asked, Install; updates install the same way over the top. **First run**: allow photo access; optional "Media management" (Settings → Media management) for instant trash, and what happens without it. **Where trashed photos go**: recoverable ~30 days, restore from Settings → Trashed by this app; Samsung Gallery Recycle bin note (filled in after manual check item 4, "Not yet checked" until then). **Privacy**: no internet permission, nothing leaves the phone. **Developers**: build (`./gradlew assembleDebug`), tests, API levels / AGP / Kotlin versions (from `app/build.gradle.kts` / catalog), how releases are signed (environment `release`, merge to `main` only), the "don't lose the key" warning, and the manual checklist from the spec's Testing section as checkboxes.

- [ ] **Step 2: Repo hygiene checks** on the integration branch: `git log --all --format='%ae %ce' | sort -u` → only the noreply address; `git ls-files | grep -Ei '\.(jks|keystore)$|key\.properties|local\.properties'` → nothing; `git grep -nIiE 'password\s*=|storepass|BEGIN (RSA )?PRIVATE KEY'` → only env-var references in `release.yml`/`app/build.gradle.kts`; `git grep -n "outlook.com"` → nothing.

- [ ] **Step 3: Final whole-branch review** (fresh reviewer subagent, opus, high effort) of `git diff main...liam/initial-development` against the spec and this plan's Global Constraints and Review Focus. Fix findings via follow-up commits, re-run `./gradlew lint testDebugUnitTest assembleDebug`.

- [ ] **Step 4: Push and open the PR.** `git merge main` (bring in T2's commits), `git push -u origin liam/initial-development`, `gh pr create --base main --title "Photo Sorter for Android v1.0.0" --body …` (summary, test results, manual checklist; ends with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`). Watch: `gh pr checks --watch` → `build` passes.

- [ ] **Step 5: Hand to the user to merge.** Status `waiting on user`. Do not merge.

- [ ] **Step 6: After the merge, verify the release.** `gh run list --workflow Release --limit 1` → success; `gh release download --pattern 'photo-sorter.apk*'` into `$CLAUDE_JOB_DIR/tmp/release`; `sha256sum -c photo-sorter.apk.sha256` → OK; `apksigner verify --print-certs photo-sorter.apk` → `CN=Photo Sorter` and the SHA-256 digest equals T3's recorded fingerprint; `gh api repos/lronsmith/photo-sorter-android/secret-scanning/alerts --jq length` → `0`. Report the release URL and ask the user to run the manual checklist on the phone (and report Gallery Recycle-bin behaviour for the README).
