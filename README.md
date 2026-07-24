# Atomux

Atomux is a standalone Android terminal app built on iTermux's runtime core. It
keeps Termux-style paths, bootstrap, and session wiring under host-app ownership
— but here the host is Atomux itself, not a third-party IDE. Same
path-independent runtime, own package identity, own app shell.

Application ID: `com.nidrzero.atomux`

## Modules

- `core/`: runtime library with host-derived paths, runtime config,
  native session builders, and offline bootstrap installation
- `proot-plugin/`: optional proot launcher that returns the same host-facing
  session contract as `core`
- `sample-app/`: the Atomux host app that owns the runtime and verifies the
  runtime API surface
- `tools/`: verification and merge-protection scripts

## Package convention

The runtime modules retain the upstream `com.darkian.itermux.*` source
namespace. The installed app identity is `com.nidrzero.atomux`.

## Current state

- `iTermux.initialize()` derives the runtime from the host app, writes
  `termux.env`, reads `termux.properties`, and auto-installs the packaged
  offline bootstrap on cold start when the payload is available.
- Native sessions come from `core`, while proot sessions stay isolated in the
  optional plugin and share the same `iTermuxSession` contract.
- `sample-app/` is the Atomux host and the runtime verification surface.
- Bootstrap payloads are not committed to the repo. The removed placeholder
  archives were synthetic; a real per-ABI bootstrap must be built and supplied
  before a cold offline first-run works on device.
- `tools/merge-check.sh` reports `SAFE`, `REVIEW`, and `CONFLICT` states for
  tagged fork files relative to an upstream-tracking ref.

## Verification helpers

- `./gradlew.bat :core:testDebugUnitTest`
- `./gradlew.bat :proot-plugin:testDebugUnitTest`
- `./gradlew.bat assembleDebug`
- `powershell -ExecutionPolicy Bypass -File tools\\verify-no-termux-literals.ps1`
- `powershell -ExecutionPolicy Bypass -File tools\\verify-supported-packages-sync.ps1`
- `bash tools/merge-check.sh [base-ref] [head-ref]`
