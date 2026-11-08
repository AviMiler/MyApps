---
name: build-and-deliver-apk
description: Build an Android app from the MyApps repo (AgoraApp, ClaudeCameraLauncher, HebrewCalendar) with GitHub Actions and hand the finished APK to the user inside the Claude session. Use when the user asks to build an APK, "תבנה apk", to send the app file, or when an APK that was delivered will not update/install over the old one, or a widget fails to load.
---

# Build an APK in CI and deliver it to the user

The cloud session has **no Android SDK**, so the APK is never built locally. GitHub Actions builds it,
and the file is downloaded from the run's artifact and sent with `SendUserFile`. Reply to the user in Hebrew.

## 0. Rules of honesty
- A green build proves it compiles and the unit tests pass. It does not prove the UI works. Say that
  nothing was run on a device; the user is the one who verifies.
- Mockups drawn with Playwright/HTML are mockups, never call them screenshots of the app. Do not let a
  mockup hide a real problem (for example clipping that the real view would not have).
- Never push an empty commit or close/reopen a PR to kick CI. Re-run the run instead.

## 1. Build
1. Commit and push to the session branch (`claude/...`). `.github/workflows/build-apk.yml` runs on pushes to
   `main` and `claude/**`; it builds every app in a matrix, `fail-fast: false`, and publishes the fixed
   Release (`hebrew-calendar-latest`) **only from `main`** so a branch build never replaces the stable APK.
2. Find the run for the commit (public API, no token needed):
   `curl -s "https://api.github.com/repos/AviMiler/MyApps/actions/runs?head_sha=$(git rev-parse HEAD)"`
   and poll until `status == completed`. Foreground `sleep` is blocked: wait with
   `read -t 15 </dev/zero` in a loop (use the Bash timeout of 590000 ms; ~2-3 minutes per build).
3. If a job failed: `mcp__github__actions_list` `list_workflow_jobs`, then `mcp__github__get_job_logs`
   (`return_content: true`, small `tail_lines`; it can be huge, grep the saved file for `e: `, `FAILURE`,
   `What went wrong`).
   - `504` / SSL timeout while downloading `gradle-8.9-bin.zip` = GitHub outage, not our code.
     Confirm with `curl -sIL https://services.gradle.org/distributions/gradle-8.9-bin.zip` (a 504 at the
     end), wait, then `mcp__github__actions_run_trigger` `rerun_workflow_run` with the run id.
   - Anything else: fix the code, commit, push (new run).
   - The tools are deferred: load `mcp__github__actions_list`, `actions_get`, `actions_run_trigger`,
     `get_job_logs` with ToolSearch first.

## 2. Get the file into the session
1. `mcp__github__actions_list` `list_workflow_run_artifacts` for the run id. Artifact names:
   `HebrewCalendar-debug-apk`, `AgoraApp-debug-apk`, `ClaudeCameraLauncher-debug-apk`. Note each `digest`.
2. `mcp__github__actions_get` `download_workflow_run_artifact` (artifact id) returns a **temporary signed URL**
   (valid about 10 minutes) on `productionresultssa<N>.blob.core.windows.net`.
3. The session network is an allowlist. The shard `<N>` changes between runs, so a new one is usually
   blocked (`curl: (56) CONNECT tunnel failed, response 403`). Add the exact host with
   `mcp__claude-code-remote__update_environment` (`add_allowed_hosts`; exact names only, no wildcards; the
   user gets an approval prompt; mention it in the reply). Then call `download_workflow_run_artifact`
   again for a fresh URL and download right away.
4. Download with curl into the scratchpad (single-quote the whole URL, it contains `&`):
   `curl -sS --max-time 120 -o a.zip '<url>'`, check `sha256sum a.zip` equals the artifact `digest`,
   unzip with `python3 -I -m zipfile -e a.zip out` and rename `out/app-debug.apk` to `<App>.apk`.
5. Send with `SendUserFile` (`display: "attach"`, caption in Hebrew). Use the full absolute path.
   When asked for several apps, send the one the user named first.

## 3. The APK must update over the installed one
Android only updates an app when the signature is the same and `versionCode` is not lower. Each app
therefore has, in `app/build.gradle`:
```groovy
defaultConfig { versionCode Integer.parseInt(System.getenv("GITHUB_RUN_NUMBER") ?: "1") }
signingConfigs { debug { storeFile file('debug.keystore'); storePassword 'android'
                         keyAlias 'androiddebugkey'; keyPassword 'android' } }
```
with a committed `app/debug.keystore` (debug builds only, on purpose). Notes:
- Write `Integer.parseInt(...)`. `versionCode (expr).toInteger()` is parsed by Groovy as a call to the
  `versionCode(...)` method and fails with "Value is null".
- Before the fixed key existed, every CI run signed with a fresh random key, so updates failed
  ("only reinstall works"). Switching to the fixed key needs **one** uninstall/reinstall; tell the user.
- `keytool -printcert -jarfile` prints nothing for v2-only signatures, so the signature cannot be checked
  here. The proof is that the next APK updates over this one; say so.
- A new app must get the same `signingConfigs`/`versionCode` and be added to the workflow matrix.

## 4. Widgets (RemoteViews) pitfalls
- Symptom "לא ניתן להוסיף widget" (cannot add widget) = the layout could not be inflated. RemoteViews only
  allows FrameLayout, LinearLayout, RelativeLayout, GridLayout, TextView, ImageView, Button, ImageButton,
  ProgressBar, Chronometer, TextClock, AnalogClock, ViewFlipper, list/stack views, ViewStub.
  A plain `<View>` is not allowed: use an `ImageView` (colored with `setBackgroundColor`).
- Opaque cells with square corners poke out of a rounded widget background. Keep cells transparent and draw
  thin line views on top.
- `RemoteViews.setViewLayoutMargin` needs API 31 (minSdk is 26), so use separate layouts or visibility
  toggles instead of runtime margins.

## 5. Checklist for the final message
- What was built (commit, run result incl. unit tests) and what was *not* verified.
- The file(s) sent; whether the user must uninstall first (only when the signing key changed).
- Any network allowlist host that was added.
