# Upstream Sync

Atomux keeps the editable Android project in the repo root and uses `upstream/`
only as clean mirrors. Upstream syncing should stay explicit so the fork only
absorbs reviewed changes.

## Two separate upstream lineages

Atomux has two distinct upstreams, and they must never be collapsed into one
"upstream":

- **Fork upstream — [`2kDarki/iTermux`](https://github.com/2kDarki/iTermux)**:
  this repository's direct parent. It is the embeddable, host-agnostic Termux
  runtime that Atomux builds on. This is the primary lineage to track for
  runtime, path-resolution, bootstrap, and session-contract changes.
- **Termux upstream — [`termux/termux-app`](https://github.com/termux/termux-app)**:
  the original project that `2kDarki/iTermux` itself descends from. It is a
  reference-only source for terminal-emulator/terminal-view internals and
  upstream security fixes. Changes flow Termux → fork → Atomux, reviewed at
  each hop; Atomux does not merge Termux directly without going through the
  fork lineage's framing.

Mirror each lineage under its own path (for example `upstream/itermux` for the
fork parent and `upstream/termux-app` for Termux) and compare against the
lineage that actually owns the file you are reviewing. Do not diff a
fork-runtime file against Termux, or a terminal-core file against the fork
parent, without knowing which lineage introduced it.

## Reference refs

- Preferred base ref: `upstream/main`
- Typical compare target while developing: `HEAD`
- Fallback when no upstream-tracking ref exists yet: pass an explicit base ref
  such as `origin/main`

`tools/merge-check.sh` will try `upstream/main` first, then fall back to
`origin/main` when no base ref is passed.

## Procedure

1. Refresh the clean clone under `upstream/` from the real upstream source.
2. Update the local tracking ref you want to compare against.
   Example: `git fetch upstream main:upstream/main`
3. Run `bash tools/merge-check.sh upstream/main HEAD`
4. Review the output:
   - `SAFE`: upstream changed files that are not tagged as fork divergences
   - `REVIEW`: upstream changed a tagged file, but the three-way merge is clean
   - `CONFLICT`: upstream changed a tagged file and the three-way merge reports
     overlapping edits
5. Port only the reviewed changes into the project root modules. Do not develop
   directly inside `upstream/`.

## Expectations

- Tagged files must keep the merge marker near the top of the file.
- `merge-check.sh` is a review gate, not an auto-merge tool.
- If the script reports `REVIEW` or `CONFLICT`, stop and inspect those files
  before pulling logic across.
