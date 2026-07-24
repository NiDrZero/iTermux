# Phase 9 Discovery Matrix And Baseline

## Multi-device matrix

This is the matrix Phase 9 tracks against. The rows are recorded now so future
device passes land against an explicit target list instead of ad-hoc testing.

| Device class | Why it matters | Current status | Notes |
| --- | --- | --- | --- |
| Modern `arm64-v8a` flagship | Primary production path | Pending device pass | Use for cold/warm bootstrap and normal shell flow. |
| Mid-range `arm64-v8a` | Most likely background-kill environment | Pending device pass | Prioritize kill/recovery and idle-memory checks here. |
| Older `armeabi-v7a` | ABI fallback validation | Pending device pass | Confirms the 32-bit packaged bootstrap path. |
| `x86_64` emulator / Chromebook | Emulator-specific ABI path | Pending emulator pass | Use for ABI routing and repeatable scripted checks. |
| Low-storage device | Extraction under disk pressure | Pending device pass | Use for interrupted extraction and retry-window behavior. |

## First host-side baseline

Captured on 2026-04-16 with:

```powershell
./gradlew.bat :app:testDebugUnitTest `
  --tests "com.darkian.itermux.sample.iTermuxPerformanceBaselineProbeTest" `
  --rerun-tasks `
  --console=plain `
  --info
```

Observed probe output:

```text
PHASE9_BASELINE cold_init_ms=3349 warm_init_ms=36 shell_ready_ms=19 idle_memory_mb=0.00 recovery_ms=4
```

| Metric | Value | Measurement note |
| --- | --- | --- |
| Cold `init()` to `READY` | `3349 ms` | Host-side JVM probe with packaged bootstrap auto-install. |
| Warm `init()` to `READY` | `36 ms` | Same temp runtime after bootstrap already exists. |
| Session open to shell-ready surrogate | `19 ms` | Current surrogate is `iTermux.createSession()` returning `RUNNING`; live prompt timing will replace this once a real terminal view/process path lands. |
| Idle memory overhead | `0.00 MB` | Approximate heap delta after warm runtime + session creation in the unit-test harness. |
| Recovery latency | `4 ms` | `recoverSessionFromOsKill()` through the one-shot restart path in the host-side probe. |

## Interpretation

- This is the first comparison point, not a pass/fail bar.
- The numbers are host-side JVM baselines, not on-device Android measurements.
- Device rows in the matrix stay open until physical or emulator passes replace
  the placeholder status.
