# internal-termux Phase 0 Audit

## Purpose

This document is the surgery map for the fork. It inventories the upstream
assumptions that must be handled before `internal-termux` can move from a
standalone `com.termux` app into an embeddable Android library.

The upstream clones under `upstream/` are reference-only. No edits were made
there during this audit.

## Audit rules

- During the Phase 0 slice, the future Android project area stayed empty until
  this audit and the repo guardrail docs were complete.
- This inventory focuses on assumptions that materially affect prefix
  parameterization, package renaming, library conversion, bootstrap control,
  and future merge protection.
- Mechanical package declarations are grouped by source tree when they express
  the same namespace assumption everywhere in that tree.

## Current repo state

- At the end of the Phase 0 slice, the future Android project area was still
  intentionally empty.
- `upstream/termux-app` is the primary fork source.
- `upstream/termux-packages` and `upstream/proot-distro` are reference inputs
  for bootstrap and future proot work.

## Current embedded bootstrap matrix

Atomux targets **64-bit ARM only** (Snapdragon and other arm64 SoCs). The
bootstrap payload stays variant-specific, but only the arm64-v8a variant is
packaged. `armeabi-v7a` and `x86_64` were intentionally dropped: the proroot
launcher (see below) ships an arm64-v8a native library exclusively, so no other
ABI can host a session.

| ABI | Asset path | Support status | Notes |
| --- | --- | --- | --- |
| `arm64-v8a` | `core/src/main/assets/itermux/bootstrap/arm64-v8a/bootstrap.tar.xz` | Required | The only supported device path (Snapdragon / arm64). |
| `armeabi-v7a` | none | Dropped | Removed with the arm64-only trim; proroot has no 32-bit ARM build. |
| `x86_64` | none | Dropped | Removed with the arm64-only trim; drops emulator/Chromebook support. |
| Any other ABI list | none | Unsupported | Runtime must fail early with `UNSUPPORTED_ABI` before extraction. |

The app packages only `arm64-v8a` via `abiFilters` in `app/build.gradle.kts`,
and `minSdk` is raised to 26 (Android 8.0) to match proroot's minimum.

## Rootless-Linux launcher: proroot

The optional proot backend is now
[`coderredlab/proroot`](https://github.com/coderredlab/proroot) — a drop-in,
proot-compatible launcher with zero ptrace overhead, distributed as arm64-v8a
native libraries.

| Item | Value |
| --- | --- |
| Launcher | `libproroot.so` (plus 4 companion `.so` files, auto-discovered from its own dir) |
| Packaging | `app/src/main/jniLibs/arm64-v8a/` (binaries fetched from proroot Releases, not committed) |
| CLI | proot-compatible: `-r <rootfs>`, `-b <host>[:guest]`, `-w <dir>`, `-0`, `--link2symlink` |
| Required env | `PROROOT_TMP_DIR` = app files dir (writable runtime config location) |
| Minimum | Android 8.0+ (API 26), arm64-v8a, glibc arm64 rootfs |

proroot supersedes the legacy `upstream/proot-distro` reference as the launcher
implementation. `proot-distro` remains a reference only for distro rootfs
layout, not for the launcher binary.

## Category A: Package identity and Android app ownership

### A1. App ID, shared user, and authorities

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/app/build.gradle` | 16 | App namespace is `com.termux`. | Library extraction cannot keep the upstream app namespace as-is. | Phase 2, Phase 3 |
| `upstream/termux-app/app/src/main/AndroidManifest.xml` | 5 | `android:sharedUserId="${TERMUX_PACKAGE_NAME}"`. | Shared-user assumptions tie the app to the Termux plugin ecosystem and package identity. | Phase 2 |
| `upstream/termux-app/app/src/main/AndroidManifest.xml` | 162, 173 | Provider authorities use `${TERMUX_PACKAGE_NAME}.documents` and `${TERMUX_PACKAGE_NAME}.files`. | Host embedding and renamed package IDs must not collide or break content access. | Phase 2, Phase 3 |
| `upstream/termux-app/app/src/main/res/xml/shortcuts.xml` | 6-8, 21-22, 34-35, 49-50 | Launcher shortcuts explicitly target `com.termux` classes and warn that manual patching is required. | This is a direct standalone-app seam and cannot survive library mode unchanged. | Phase 2, Phase 3 |
| `upstream/termux-app/app/src/main/res/values/strings.xml` | 4 | `TERMUX_PACKAGE_NAME` XML entity is `com.termux`. | Resource-level identity assumption feeds manifests and user-facing text. | Phase 2 |
| `upstream/termux-app/termux-shared/src/main/res/values/strings.xml` | 4 | Shared library also defines `TERMUX_PACKAGE_NAME` as `com.termux`. | Fork will need one source of truth for renamed identity. | Phase 2 |

### A2. Central package-name constants and plugin naming

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` | 294-324 | Documentation states `TERMUX_PACKAGE_NAME` must match app `applicationId` and package declarations. | This is the core contract that later refactors must break cleanly. | Phase 1, Phase 2 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` | 352-444 | `TERMUX_PACKAGE_NAME`, plugin package names, and F-Droid URLs are derived from `com.termux`. | Renaming is broader than one manifest; plugin assumptions are wired into shared constants. | Phase 2 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` | 938-979 | Activity and service names are derived as fully qualified `com.termux...` class names. | Library conversion must replace class-name string assumptions with host-facing seams. | Phase 2, Phase 3 |

### A3. Shared-user and plugin coupling documentation in code

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxUtils.java` | 145-243 | Multiple helpers assume callers share `sharedUserId` with the Termux app or Termux:API. | These helpers encode plugin-era package boundaries that do not map directly to an embedded library. | Phase 2, Phase 3 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxBootstrap.java` | 50-67 | Bootstrap helpers assume shared-user access with the Termux app. | Embedded bootstrap control will need a host-owned path instead. | Phase 3, Phase 4 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/notification/TermuxNotificationUtils.java` | 22 | Notification ID pool is shared because of `android:sharedUserId`. | Notification and service behavior must be re-evaluated once shared-user assumptions are removed. | Phase 2, Phase 3 |

### A4. Mechanical namespace ownership by module

These are broad, mechanical package/namespace assumptions that will require
rename or relocation work but do not each need bespoke design:

| Source tree | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- |
| `upstream/termux-app/app/src/main/java/com/termux/**` | All app code lives under `com.termux.app`, `com.termux.filepicker`, and related packages. | This is the standalone app layer that later becomes the forked runtime core. | Phase 2, Phase 3 |
| `upstream/termux-app/app/src/test/java/com/termux/**` | Tests assert `com.termux` package ownership. | Test suite will need the same rename pass as production code. | Phase 2 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/**` | Shared support library lives under `com.termux.shared`. | Forked shared utilities will need a project namespace strategy. | Phase 2 |
| `upstream/termux-app/termux-shared/src/androidTest/java/com/termux/shared/**` | Android tests assert `com.termux.shared`. | Same rename burden as shared runtime code. | Phase 2 |
| `upstream/termux-app/terminal-emulator` | Gradle namespace is `com.termux.emulator`; sources use `com.termux.terminal`. | These modules likely move into the forked `core/` module and must be namespaced deliberately. | Phase 3 |
| `upstream/termux-app/terminal-view` | Gradle namespace is `com.termux.view`; sources use `com.termux.view`. | Same as above; view module becomes part of the embeddable terminal surface. | Phase 3 |
| `upstream/termux-app/termux-shared/build.gradle` | Publishing group includes `com.termux:termux-am-library` and `groupId = 'com.termux'`. | Maven/publishing identity is currently Termux-branded and unsuitable as-is for the fork. | Phase 2, Phase 6 |

## Category B: Hardcoded app-data and prefix paths

### B1. Single-file hotspot: `TermuxConstants.java`

`TermuxConstants.java` is the main path-coupling hotspot. It derives most of
the runtime filesystem from `TERMUX_PACKAGE_NAME` and `/data/data/...`.

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` | 581-924 | `TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH`, `TERMUX_FILES_DIR_PATH`, `TERMUX_PREFIX_DIR_PATH`, `TERMUX_BIN_PREFIX_DIR_PATH`, `TERMUX_ETC_PREFIX_DIR_PATH`, home/config/storage paths, staging paths, env files, apps dir, socket paths, and other descendants all resolve from `/data/data/com.termux/files/...`. | This is the primary Phase 1 target. It must become runtime-derived from the host context instead of a hardcoded Termux package path. | Phase 1 |
| `upstream/termux-app/termux-shared/src/main/res/values/strings.xml` | 12 | Resource entity `TERMUX_PREFIX_DIR_PATH` is `/data/data/com.termux/files/usr`. | User-facing diagnostics still expose the hardcoded path even if code is fixed. | Phase 1, Phase 2 |

### B2. Runtime code that depends on those constants

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/app/src/main/java/com/termux/app/TermuxInstaller.java` | 78-215, 257 | Bootstrap install logic validates, deletes, creates, stages, and renames the hardcoded prefix directories. | Prefix parameterization must land here cleanly or bootstrap breaks immediately. | Phase 1, Phase 4 |
| `upstream/termux-app/app/src/main/java/com/termux/app/TermuxInstaller.java` | 92-94 | Installer compares `activity.getFilesDir()` to the constant path after rewriting `/data/user/0/` to `/data/data/`. | Upstream still treats one concrete path layout as canonical. | Phase 1 |
| `upstream/termux-app/app/src/main/java/com/termux/app/TermuxOpenReceiver.java` | 209 | Received file paths are accepted only if they live under `TERMUX_FILES_DIR_PATH` or storage. | Embedded hosts may need different path ownership rules. | Phase 1, Phase 3 |
| `upstream/termux-app/app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java` | 41 | `TERMUX_RECEIVEDIR` is hardcoded to `TERMUX_FILES_DIR_PATH + "/home/downloads"`. | File import paths are coupled to the current app-owned home layout. | Phase 1, Phase 3 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/file/TermuxFileUtils.java` | 56-57, 90, 125-137, 177-288, 331-406 | File helpers normalize, validate, and report paths against `TERMUX_PREFIX_DIR_PATH` and `TERMUX_FILES_DIR_PATH`. | These helpers must remain correct after prefix parameterization; they are a major audit hotspot. | Phase 1 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxUtils.java` | 190-207, 487, 605-608 | Diagnostics and apt helper execution assume fixed files and prefix paths. | Shared utility layer will need runtime-aware path resolution too. | Phase 1 |

## Category C: `$PREFIX`, shell environment, and bootstrap assumptions

### C1. Environment injection still resolves to fixed constants

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java` | 28-29, 79-90, 108 | `PREFIX`, `PATH`, and `TMPDIR` are injected from fixed `TermuxConstants` paths. | This is the exact Phase 1 seam for runtime-derived `$PREFIX` injection. | Phase 1 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellUtils.java` | 34-36, 60, 70, 85-114 | Shebang repair and shell fallback rewrite to `$PREFIX/bin/...` and clear `$PREFIX/tmp`. | Script startup and temp cleanup both assume one prefix root. | Phase 1 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/file/TermuxFileUtils.java` | 56-57, 90 | `$PREFIX` text substitution is hardwired to `TermuxConstants.TERMUX_PREFIX_DIR_PATH`. | String-level path normalization will need to accept an injected prefix, not a compile-time constant. | Phase 1 |

### C2. Bootstrap packaging explicitly bakes the prefix

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/app/src/main/res/values/strings.xml` | 35-39 | Upstream warns that bootstrap binaries are compiled with a hardcoded `$PREFIX` path. | This is the maintenance constraint that must shape controlled bootstrap work. | Phase 4 |
| `upstream/termux-app/termux-shared/src/main/res/raw/apt_info_script.sh` | 5, 25 | Script template expects `@TERMUX_PREFIX@/...`. | Generated shell assets will need a host-aware substitution path. | Phase 1, Phase 4 |
| `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxUtils.java` | 605-608 | Helper injects `TERMUX_PREFIX_DIR_PATH` into the apt info script and executes `.../bin/bash`. | Shared shell helpers assume prefix-specific generated assets. | Phase 1, Phase 4 |

## Category D: Standalone app and UI entry-point seams

These files represent the current standalone-app surface that must be replaced,
stubbed, or inverted when the project becomes an Android library.

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/app/src/main/AndroidManifest.xml` | 41-197 | App manifest declares `TermuxApplication`, `TermuxActivity`, `SettingsActivity`, `FileReceiverActivity`, `TermuxDocumentsProvider`, `TermuxOpenReceiver`, and `TermuxService`. | This is the current app shell that cannot remain the public entry point in library mode. | Phase 3 |
| `upstream/termux-app/app/src/main/java/com/termux/app/TermuxActivity.java` | 80-1008 | Main terminal UI activity owns layout inflation, service binding, session lifecycle, permissions, settings navigation, and bootstrap trigger logic. | This is the biggest standalone UI seam to replace with an embed API and host-owned Activity. | Phase 3 |
| `upstream/termux-app/app/src/main/java/com/termux/app/TermuxService.java` | 58-829 | Long-lived terminal/service lifecycle assumes a foreground Termux app and can relaunch `TermuxActivity`. | Library hosts need service behavior without relaunching a standalone activity. | Phase 3 |
| `upstream/termux-app/app/src/main/java/com/termux/app/TermuxApplication.java` | 20-... | App-level initialization assumes a standalone Termux application process. | Host app should own the process/application layer after library conversion. | Phase 3 |
| `upstream/termux-app/app/src/main/java/com/termux/app/TermuxInstaller.java` | 60-258 | Bootstrap flow is driven from activity/service assumptions. | Bootstrap must move behind `InternalTermux.init()` later. | Phase 3, Phase 4 |
| `upstream/termux-app/app/src/main/java/com/termux/app/activities/SettingsActivity.java` | 29-... | Settings are exposed as a standalone app activity. | Library should not own app-level settings screens. | Phase 3 |
| `upstream/termux-app/app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java` | 39-... | File import flow is activity-based and directly starts `TermuxService`. | Host app will need a different entry contract for imports. | Phase 3 |
| `upstream/termux-app/app/src/main/java/com/termux/filepicker/TermuxDocumentsProvider.java` | 34-... | Documents provider is declared under the standalone app identity. | Embedded hosts may or may not keep this provider; decision should be informed by actual use. | Phase 3 |
| `upstream/termux-app/app/src/main/java/com/termux/app/TermuxOpenReceiver.java` | 30-... | Receiver/content-provider bridge assumes standalone package and direct service launch. | Another standalone seam to re-evaluate for host embedding. | Phase 3 |
| `upstream/termux-app/app/src/main/res/layout/activity_termux.xml` | 1-113 | Root layout is specific to `TermuxActivity`. | UI layout belongs to the host after library conversion. | Phase 3 |
| `upstream/termux-app/app/src/main/res/values/themes.xml` and `values-night/themes.xml` | multiple `TermuxActivity` theme entries | Activity-specific theming is wired into the app. | Host UI should own app chrome and layout styling. | Phase 3 |
| `upstream/termux-app/app/src/main/res/xml/root_preferences.xml`, `termux_preferences.xml`, `termux_api_preferences.xml`, `termux_float_preferences.xml`, `termux_tasker_preferences.xml`, `termux_widget_preferences.xml` | top-level fragment refs | Preferences XML hardcode Termux app fragment classes. | These are part of the standalone settings surface, not the embeddable runtime core. | Phase 3 |

## Category E: Build, publishing, and merge-sensitive assumptions

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-app/termux-shared/build.gradle` | 31, 93 | Shared library depends on/publishes under `com.termux` coordinates. | Fork must decide whether to vendor, relocate, or replace these publishing assumptions. | Phase 2, Phase 6 |
| `upstream/termux-app/terminal-emulator/build.gradle` | 5, 78 | Terminal emulator module namespace and publishing group are Termux-branded. | These modules will likely be vendored into `main/core/` and tagged for merge protection. | Phase 3, Phase 6 |
| `upstream/termux-app/terminal-view/build.gradle` | 5, 55 | Terminal view module namespace and publishing group are Termux-branded. | Same as above. | Phase 3, Phase 6 |
| `upstream/termux-app/app/src/main/cpp/termux-bootstrap.c` | 6 | JNI symbol name is tied to `Java_com_termux_app_TermuxInstaller_getZip`. | Native bootstrap glue is package-name-sensitive and must be renamed carefully. | Phase 2, Phase 4 |

## Category F: Reference notes from `termux-packages`

These repos are not part of the forked project tree, but they materially
constrain later phases.

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/termux-packages/scripts/properties.sh` | 917-957, 1155, 1203, 2485-2553 | Build tooling still treats Termux prefix layout as a validated, often classical `/usr`-merge structure and documents default values under `/data/data/com.termux/files/usr/...`. | Phase 4 bootstrap generation cannot be treated as a simple asset copy; prefix decisions affect package build assumptions. | Phase 4 |
| `upstream/termux-packages/scripts/generate-bootstraps.sh` | 256-274, 417-436 | Bootstrap generation substitutes `@TERMUX_PREFIX@` and creates the rootfs under `${TERMUX_PREFIX}`. | Controlled bootstrap work will need a deterministic replacement strategy. | Phase 4 |
| `upstream/termux-packages/scripts/build-bootstraps.sh` | 210-228, 407-418 | Same as above for the alternate bootstrap path. | Same implication as above. | Phase 4 |
| `upstream/termux-packages/scripts/bootstrap/termux-bootstrap-second-stage.sh` | 1, 4, 173-295 | Second-stage bootstrap scripts are shebanged and parameterized around `TERMUX_PREFIX`. | Embedded bootstrap extraction must preserve or replace this contract. | Phase 4 |
| `upstream/termux-packages/scripts/bootstrap/01-termux-bootstrap-second-stage-fallback.sh` | 1, 27 | Fallback bootstrap script also relies on `@TERMUX_PREFIX@`. | Same as above. | Phase 4 |

## Category G: Reference notes from `proot-distro`

`proot-distro` is reference-only today, but the future plugin work already has
visible hardcoded Termux assumptions that must not leak into core.

| File | Lines | Assumption | Why it matters | Future phase |
| --- | --- | --- | --- | --- |
| `upstream/proot-distro/install.sh` | 3-5 | Default install variables assume `TERMUX_APP_PACKAGE=com.termux` and prefix `/data/data/${TERMUX_APP_PACKAGE}/files/usr`. | The plugin cannot be dropped into `internal-termux` unchanged later. | Phase 5 |
| `upstream/proot-distro/proot-distro.sh` | 37, 43, 46, 59, 687-761, 1908-2183, 3095-3096 | Runtime scripts use `@TERMUX_PREFIX@` for PATH, plugin dirs, runtime dirs, emulator paths, bind mounts, and dpkg checks. | proot plugin needs its own parameterized prefix strategy from day one. | Phase 5 |
| `upstream/proot-distro/proot-distro.sh` | 814-818, 1788-1795 | Login environment explicitly exports `HOME`, `PATH`, `PREFIX`, and `TMPDIR` with `/data/data/com.termux/files/...`. | This is the strongest evidence that Phase 5 must isolate and rework proot environment injection. | Phase 5 |
| `upstream/proot-distro/proot-distro.sh` | 487-523, 2084, 2159 | Installed rootfs layout and bind paths still create/target `data/data/com.termux/...` inside the distro tree. | Embedded plugin work will need a different bind/path model. | Phase 5 |
| `upstream/proot-distro/distro-plugins/termux.sh` | 20, 23-24 | Termux distro plugin hardcodes `./data/data/com.termux/...`. | Another concrete proof that proot support is not ready to consume as-is. | Phase 5 |
| `upstream/proot-distro/README.md` and `CONTRIBUTING.md` | 126, 221, 236, 316, 482, 645 | Documentation still presents `com.termux` and `$PREFIX/etc/proot-distro` as the normal installation model. | Future plugin docs must be project-specific, not copied verbatim. | Phase 5 |

## Category H: Immediate fork hotspots

These files are the likely first high-risk touch points once implementation
starts:

1. `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java`
2. `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java`
3. `upstream/termux-app/termux-shared/src/main/java/com/termux/shared/termux/file/TermuxFileUtils.java`
4. `upstream/termux-app/app/src/main/java/com/termux/app/TermuxInstaller.java`
5. `upstream/termux-app/app/src/main/AndroidManifest.xml`
6. `upstream/termux-app/app/src/main/java/com/termux/app/TermuxActivity.java`
7. `upstream/termux-app/app/src/main/res/xml/shortcuts.xml`
8. `upstream/proot-distro/install.sh`
9. `upstream/proot-distro/proot-distro.sh`

## Implications for the next slice

- Do not scaffold the forked Android project blindly around the current upstream Activity/Service
  split. The audit shows that package identity, prefix resolution, bootstrap,
  and UI ownership are all intertwined.
- The Android skeleton should be informed first by the `TermuxConstants` /
  `TermuxShellEnvironment` / `TermuxInstaller` seam, because that is where
  runtime ownership actually starts.
- proot must remain optional and isolated. Its upstream scripts are visibly more
  hardcoded than the core runtime and should not influence the first library
  skeleton.
