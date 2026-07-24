# Phase 9 Failure Injection

These seams are the deliberate breakpoints for Phase 9 discovery. The goal is
to make failures observable through the lifecycle listener and the DS
normalization layer before any full integration happens.

## Automated harness

Run the focused failure harness with:

```powershell
./gradlew.bat :core:testDebugUnitTest `
  --tests "com.darkian.itermux.core.iTermuxBootstrapStateMachineTest" `
  --tests "com.darkian.itermux.core.iTermuxAutoBootstrapTest" `
  --tests "com.darkian.itermux.core.iTermuxEnvironmentValidatorTest" `
  --tests "com.darkian.itermux.core.iTermuxRuntimeRefreshTest" `
  --tests "com.darkian.itermux.core.iTermuxSessionControllerTest" `
  --tests "com.darkian.itermux.core.iTermuxLifecycleListenerTest" `
  --console=plain
```

## Scenario table

| Scenario | Injection seam | Proof / procedure | Expected DS normalization |
| --- | --- | --- | --- |
| Interrupted bootstrap | Throw from the injected `bootstrapInstaller` on the first extraction attempt | Covered by `iTermuxBootstrapStateMachineTest.retriesOnceWhenPreviousFailureIsOutsideRetryWindow()` | `PARTIAL` then `RUNTIME_RECOVERING` while the retry is underway |
| Retry within timing window | Reuse the in-memory failure tracker with `lastFailureAtMillis` inside the 30-second gate | Covered by `iTermuxBootstrapStateMachineTest.doesNotRetryWhenPreviousFailureIsInsideRetryWindow()` | `FAILED` then `RUNTIME_UNAVAILABLE` |
| Corrupted bootstrap | Return a degraded runtime from the installer so verification fails | Covered by `iTermuxBootstrapStateMachineTest.verificationFailureSurfacesCorruptedState()` | `CORRUPTED` then `RUNTIME_UNAVAILABLE` |
| Unsupported ABI | Override `supportedAbis` / `supportedAbisOverride` with an unsupported list such as `listOf("x86")` | Covered by `iTermuxAutoBootstrapTest.initializeFailsEarlyWhenNoBootstrapVariantMatchesSupportedAbis()` | `FAILED` with `UNSUPPORTED_ABI`, normalized to `RUNTIME_UNAVAILABLE` |
| Recoverable degradation | Remove `bin/env` or force a non-executable core binary through `iTermuxEnvironmentFileAccess` | Covered by `iTermuxEnvironmentValidatorTest.surfacesMissingBinaryWhenCoreUtilityDisappears()` and `surfacesPermissionChangedWhenRequiredBinaryStopsBeingExecutable()` | `DEGRADED` then `RUNTIME_RECOVERING` |
| Structural degradation | Remove `etc/profile`, drift the selected ABI, or inject an unwritable runtime root | Covered by `iTermuxEnvironmentValidatorTest.surfacesCorruptedInstallWhenProfileIsMissing()`, `surfacesAbiMismatchWhenSelectedBootstrapVariantNoLongerMatchesDeviceAbi()`, and `surfacesSandboxInvalidatedWhenRuntimeStorageStopsBeingWritable()` plus `iTermuxRuntimeRefreshTest` refresh-path assertions | `DEGRADED` then `RUNTIME_UNAVAILABLE` |
| Session kill recovery | Call `iTermux.recoverSessionFromOsKill()` with a restart lambda | Covered by `iTermuxSessionControllerTest.successfullyRecoversOneTimeAfterOsKill()` and `iTermuxLifecycleListenerTest.dispatchesRecoverySessionLifecycleTransitions()` | `KILLED_BY_OS` / `RECOVERING` then back to `RUNTIME_READY` |
| Session kill exhaustion | Call `recoverSessionFromOsKill()` on a session that already used its retry or return `null` from restart | Covered by `iTermuxSessionControllerTest.transitionsToDeadWhenRecoveryBudgetIsExhausted()` and `transitionsToDeadWhenRestartFails()` | `DEAD` then `RUNTIME_UNAVAILABLE` |

## Manual app checks

Use `app/` as the DS spike once the automated harness is green.

1. Clear the sample app's data to observe a cold bootstrap path.
2. Launch the app and confirm the lifecycle timeline includes bootstrap states
   followed by normalized DS states.
3. Confirm the native session path logs `KILLED_BY_OS` -> `RECOVERING` -> `READY`.
4. Confirm the displayed normalized state never uses raw iTermux enums directly;
   only `iTermuxDsRuntimeState` should drive the DS-facing summary.
