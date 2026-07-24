# proroot native libraries (arm64-v8a)

This directory is where the [proroot](https://github.com/coderredlab/proroot)
launcher and its companion libraries are packaged into the Atomux APK. The
binaries are **not committed** to this repository — they are release artifacts
fetched from proroot and dropped here at build time.

## Required files

Download all five `.so` files from the
[proroot Releases](https://github.com/coderredlab/proroot/releases) page and
place them in this directory:

- `libproroot.so` — the launcher (the executable Atomux invokes)
- `libproroot-runtime.so`
- `libproroot-linker.so`
- `libproroot-bridge.so`
- `libproroot-stub-loader.so`

The launcher auto-discovers its four companions from its own directory
(`/proc/self/exe` dirname), so all five must sit together in `arm64-v8a/`.

## How Atomux invokes it

The `:proot-plugin` module builds a proot-compatible command line and sets
`PROROOT_TMP_DIR` to the app's writable files directory. See
`iTermuxProotPlugin` for the exact launch contract.

## Constraints

- **arm64-v8a only.** proroot ships no 32-bit ARM or x86 build; this matches
  Atomux's arm64-only ABI target.
- **Android 8.0+ (API 26).** Matches the project `minSdk`.
- A **glibc arm64 rootfs** is required inside the guest for a usable session.

## Why not committed

Consistent with the bootstrap-payload policy: Atomux does not commit binary
runtime payloads. Track the proroot release version you vendored and re-fetch
the `.so` set when upgrading.
