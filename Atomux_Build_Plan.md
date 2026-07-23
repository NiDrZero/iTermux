# Standalone Termux Fork (Kotlin + Compose): Build Plan

## What This Actually Is

A standalone Android terminal app. Built on iTermux's runtime core. Own package name, own launcher icon, own Compose UI.

Not a library for other apps. Not a community project. A personal tool that happens to be open source.

Built with AI assistance. Not independently audited. Use at your own discretion.

## Why Build On iTermux

iTermux already solved the hard part: a Termux runtime that doesn't hardcode paths to `com.termux`. It can run under any package name.

Normally that's for embedding inside someone else's app. Here, the twist is simple: **your own app becomes that host.** Same runtime, different caller.

Benefit: you inherit path-independence and clean architecture for free. You just build a UI on top.

## Why Keep Termux's Terminal Core

Termux already ships two reusable pieces:
- `terminal-emulator` — handles VT100/xterm parsing
- `terminal-view` — handles rendering + touch input

Don't rewrite these. Wrap them.

Reason: reimplementing a terminal emulator is a multi-year rabbit hole. Not worth it for a personal tool.

## UI Plan

- Terminal surface: keep the existing `TerminalView`, wrap it in Compose using `AndroidView`.
- Everything else — nav, tabs, settings, theming, package screens — build fresh in Compose.

This is the low-risk path. Full custom Compose rendering (drawing every glyph yourself) stays a "maybe later" idea, not a v1 requirement.

## Architecture At a Glance

| Piece | Approach | Why |
|---|---|---|
| Terminal engine | Reuse Termux's core libraries | Don't reinvent VT100 parsing |
| Rendering | `AndroidView` wrapping `TerminalView` | Fast, low-risk, proven |
| App shell | 100% Compose + Material 3 | It's your app now — own it fully |
| Runtime | Host-agnostic, but your app is the permanent host | Keeps iTermux's flexibility without needing a 3rd-party host |
| Packages | Small curated bootstrap, offline | No bloat, no network dependency on first run |
| proot | Optional module, not core | Skip it unless you actually need glibc tools |
| Package name | Your own, unique | No conflict with real Termux |

## Build Order

1. **Define the fork** — package name, signing key, Android version range, proot in/out for v1.
2. **Verify the runtime** — confirm paths resolve correctly under your package name, bootstrap installs, shell launches.
3. **Build the app shell** — Compose launcher, nav, tabs, settings, terminal screen wrapping `AndroidView`.
4. **Lock the bootstrap** — pick your package list, bundle it, test offline first-run.
5. **(Optional) Add proot** — separate module, only if you need it.
6. **Set up upstream tracking** — separate branch for pulling Termux updates, reviewed manually before merging.

## Testing (Keep It Light But Real)

No one else reviews your code. So:
- Smoke test every phase: cold install → bootstrap → working shell → no leftover `com.termux` paths.
- Keep basic tests around path resolution and session handling — these break silently during upstream merges.
- Any AI-generated change touching shell exec, package installs, networking, or credentials → read the diff yourself before trusting it.

## Shipping It

- Direct APK releases from your own repo. Skip Play Store / F-Droid — not worth the overhead for a solo tool.
- Sign with a key only you control.
- Note which upstream Termux version each release is based on, so future-you knows what changed.

## Privacy & Permissions

- Minimal permissions by default.
- No telemetry, no crash reporting, no analytics — unless you explicitly add it later.
- Document what's bundled in the app vs. fetched at runtime.

## The Rules (Put These in Your Repo)

**On contributions:**
- Open source, not open-contribution.
- PRs get closed, unreviewed.
- Forks are fine.
- Issues welcome for bugs/reference — not a roadmap request line.

**On AI + trust:**
- Built with AI assistance.
- No formal security review.
- Use at your own risk — especially anything touching keys, network, or package execution.

**On security reports:**
- Accepted privately (GitHub's private reporting feature or direct contact).
- No public issue for vulnerabilities.
- You investigate and fix it yourself — don't merge outside patches, just use reports as a signal.
- Upstream Termux security fixes are a separate process (handled via your upstream-tracking branch).

## Watch Out For

- **Upstream drift** — Termux changes internals over time; your wrapper can break silently if you don't check merges regularly.
- **No second reviewer** — you're the only line of defense against bad AI-generated code in sensitive areas. Budget time for manual review, not just testing.
- **proot creep** — easy to justify adding it "just in case." Ask yourself periodically if you actually use it before keeping it.