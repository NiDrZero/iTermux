# Contributing

## Project shape

`internal-termux` is not a full Termux mirror. The repo is intentionally split
into:

- `upstream/` for clean reference clones only
- the repo root for the actual forked project modules (`core/`, `proot-plugin/`,
  `app/`, `tools/`, Gradle files)

Do not edit files under `upstream/`. Pull and diff there, but keep it as the
reference point.

## Phase discipline

- Do not start a new roadmap phase until the current phase deliverables and
  verification notes are complete.
- Phase 0 must be complete before any Android skeleton work is created in
  the project root.
- If a phase goes sideways, stop, re-scope, and update `tasks/todo.md` before
  continuing.

## Scope boundaries

1. `internal-termux` supports only the packages explicitly approved by the
   project.
2. Until `supported-packages.txt` exists, do not assume support beyond the
   minimal bootstrap set needed for shell startup and IDE-critical tooling.
3. Once `supported-packages.txt` exists, anything outside that file is out of
   scope unless the file is updated in the same change.
4. proot is an optional plugin, never a dependency of core.
5. `$PREFIX` is always derived at runtime from the host context. Never hardcode
   `/data/data/com.termux/files/usr` or any renamed equivalent.
6. The library exposes runtime and terminal surfaces only. Host layout and app
   chrome belong to the embedding app.
7. If scope starts drifting toward “full Termux,” stop and tighten the package
   list instead of expanding the fork.

## Divergence tagging

Every file changed from upstream must carry a clear merge marker near the top:

```java
// INTERNAL-TERMUX MODIFIED - merge carefully
```

Use the equivalent comment style for Kotlin, XML, shell, Gradle, or Markdown.
This is required for later merge-protection tooling.

## Upstream hygiene

- Treat `upstream/termux-app` as the clean mirror of the main fork source.
- Treat `upstream/termux-packages` and `upstream/proot-distro` as reference
  inputs only.
- Never make project changes in the upstream clones.
- When upstream changes are pulled, review them against tagged fork files
  before merging any logic into the project root modules.

## Merge protection workflow

1. Refresh the upstream mirror into a dedicated ref such as `upstream/main`.
   If that ref is not available yet, pass explicit refs to the merge-check
   script instead of guessing.
2. Run `bash tools/merge-check.sh upstream/main HEAD` before merging upstream
   logic into the project modules.
3. Treat `SAFE` entries as untagged upstream changes, `REVIEW` entries as
   tagged files that changed cleanly but still need human inspection, and
   `CONFLICT` entries as tagged files with overlapping edits that must be
   resolved deliberately.
4. Keep the upstream clones untouched; only port the reviewed changes into the
   real project modules after the script output has been inspected.
5. See [docs/upstream-sync.md](docs/upstream-sync.md) for the step-by-step
   procedure and ref conventions.

## Change style

- Prefer the smallest change that moves the current roadmap phase forward.
- Fix root causes instead of layering temporary path shims.
- Keep the fork boundaries explicit; avoid silent behavior changes from upstream.
- Document important assumptions in `AUDIT.md`, `tasks/todo.md`, or the relevant
  phase docs instead of leaving them implicit in code.

## Verification

- Do not mark a slice complete without updating `tasks/todo.md` review notes.
- Prefer focused verification tied to the phase you changed.
- If device or build verification is still pending, state that explicitly rather
  than implying the phase is done.
