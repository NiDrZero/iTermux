# Bootstrap payload (arm64-v8a)

This directory is where the Atomux **bootstrap payload** — a compressed rootfs
prefix — is packaged into the APK. The runtime extracts it on first launch to
build the host-owned `usr` prefix (the shell, coreutils, and the base package
set that make a session usable).

The payload is **not committed** to this repository. Like the proroot `.so`
set, it is a binary runtime artifact fetched/built at build time and dropped
here for a local build.

## Required file

- `bootstrap.tar.xz` — an **xz-compressed tar** archive of the arm64-v8a rootfs
  prefix.

The full asset path the runtime resolves is:

```
itermux/bootstrap/arm64-v8a/bootstrap.tar.xz
```

This exact path is produced by `iTermuxBootstrapResolver.defaultVariants()` for
the arm64-v8a variant and is what `context.assets.open(...)` reads at install
time. Do not rename it.

## How the runtime consumes it

On `iTermux.initialize(context, ...)` the runtime:

1. Resolves the device ABI to the arm64-v8a variant and its asset path.
2. Detects whether `bootstrap.tar.xz` is actually packaged (`hasAsset`).
3. If present and a bootstrap is required, auto-installs it: opens the asset
   stream, decompresses xz, untars into a staging prefix, then atomically moves
   staging into place and marks the runtime `READY`.

If the payload is **absent**, the app still launches safely — the runtime
reports `bootstrapRequired = true`, `bootstrapPayloadPackaged = false`, and no
extraction is attempted. This is the current default state of the repo.

See `iTermux.installBootstrap`, `iTermuxBootstrapInstaller`, and
`iTermuxRuntimeInitializer` for the exact contract.

## How to obtain the payload

Use the helper script from the repo root:

```
scripts/fetch-bootstrap.sh
```

It stages a `bootstrap.tar.xz` into this directory. See the script header for
the source it pulls from and how to point it at a different rootfs. The archive
should expand to a standard prefix layout (e.g. `bin/`, `lib/`, `etc/`) at its
top level — the installer normalizes leading `./` and rejects any entry that
would escape the prefix directory.

## Archive requirements

- **Format:** xz-compressed tar (`.tar.xz`).
- **ABI:** arm64-v8a binaries only. proroot ships an arm64-only launcher, so no
  other ABI can host a session; a mismatched payload will fail at runtime.
- **Android 8.0+ (API 26):** matches the project `minSdk`.
- **Executable bits matter:** the installer preserves tar entry modes. A payload
  whose binaries lack the execute bit will extract but not run.

## Why not committed

Consistent with the bootstrap-payload policy (see `AUDIT.md`): Atomux does not
commit binary runtime payloads. `.gitignore` excludes
`**/assets/itermux/bootstrap/**/*.tar.xz`. Track the rootfs source/version you
vendored and re-fetch when upgrading.
