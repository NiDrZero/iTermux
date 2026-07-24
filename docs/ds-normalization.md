# DS Normalization Table

Phase 9 keeps DS logic on four states instead of mirroring every raw iTermux
state. The normalization layer lives in
`app/src/main/java/com/darkian/itermux/sample/iTermuxDsStateMapper.kt`
and `iTermuxDsLifecycleRecorder.kt`.

| Raw iTermux signal | DS runtime state | Notes |
| --- | --- | --- |
| `UNINITIALIZED`, `EXTRACTING`, `VERIFYING` | `RUNTIME_LOADING` | Cold bootstrap or verification is still in flight. |
| `READY` | `RUNTIME_READY` | Runtime contract is satisfied and the host can open sessions. |
| `PARTIAL`, `RECOVERING` | `RUNTIME_RECOVERING` | Bootstrap repair is in progress. |
| `CORRUPTED`, `FAILED` | `RUNTIME_UNAVAILABLE` | Runtime is not usable without host intervention. |
| `DEGRADED` + `MISSING_BINARY` | `RUNTIME_RECOVERING` | Missing core utility is treated as a recoverable repair path. |
| `DEGRADED` + `PERMISSION_CHANGED` | `RUNTIME_RECOVERING` | Executable-bit drift is treated as recoverable. |
| `DEGRADED` + `CORRUPTED_INSTALL` | `RUNTIME_UNAVAILABLE` | Structural damage requires reinstall or explicit repair. |
| `DEGRADED` + `ABI_MISMATCH` | `RUNTIME_UNAVAILABLE` | Wrong bootstrap variant is a structural mismatch. |
| `DEGRADED` + `SANDBOX_INVALIDATED` | `RUNTIME_UNAVAILABLE` | Host storage access is no longer trustworthy. |
| Session `STARTING` | `RUNTIME_LOADING` | Session startup is still materializing. |
| Session `RUNNING`, `SUSPENDED`, `READY` | `RUNTIME_READY` | The session is usable or resumable without recovery. |
| Session `KILLED_BY_OS`, `RECOVERING` | `RUNTIME_RECOVERING` | One-shot self-restoration is underway. |
| Session `DEAD` | `RUNTIME_UNAVAILABLE` | iTermux recovery is exhausted; DS decides next UX. |

## Rules

- Raw iTermux bootstrap and session states stay inside the normalization layer.
- DS-facing code reads only `iTermuxDsRuntimeState`.
- `DEGRADED` is normalized only after the cause is known; `onEnvironmentValidation()`
  supplies that cause when the bootstrap callback alone cannot.
- The sample app is the reference implementation for Phase 9 normalization.
