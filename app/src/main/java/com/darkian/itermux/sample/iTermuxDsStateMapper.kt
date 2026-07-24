package com.darkian.itermux.sample

import com.darkian.itermux.core.iTermuxBootstrapState
import com.darkian.itermux.core.iTermuxDegradedCause
import com.darkian.itermux.core.iTermuxRuntime
import com.darkian.itermux.core.iTermuxSessionState

enum class iTermuxDsRuntimeState {
    RUNTIME_LOADING,
    RUNTIME_READY,
    RUNTIME_RECOVERING,
    RUNTIME_UNAVAILABLE,
}

object iTermuxDsStateMapper {
    fun fromRuntime(runtime: iTermuxRuntime): iTermuxDsRuntimeState {
        return fromBootstrapState(
            state = runtime.bootstrapState,
            degradedCause = runtime.degradedCause,
        )
    }

    fun fromBootstrapState(
        state: iTermuxBootstrapState,
        degradedCause: iTermuxDegradedCause?,
    ): iTermuxDsRuntimeState {
        return when (state) {
            iTermuxBootstrapState.UNINITIALIZED,
            iTermuxBootstrapState.EXTRACTING,
            iTermuxBootstrapState.VERIFYING,
            -> iTermuxDsRuntimeState.RUNTIME_LOADING

            iTermuxBootstrapState.READY -> iTermuxDsRuntimeState.RUNTIME_READY

            iTermuxBootstrapState.PARTIAL,
            iTermuxBootstrapState.RECOVERING,
            -> iTermuxDsRuntimeState.RUNTIME_RECOVERING

            iTermuxBootstrapState.CORRUPTED,
            iTermuxBootstrapState.FAILED,
            -> iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE

            iTermuxBootstrapState.DEGRADED -> when (degradedCause) {
                iTermuxDegradedCause.MISSING_BINARY,
                iTermuxDegradedCause.PERMISSION_CHANGED,
                -> iTermuxDsRuntimeState.RUNTIME_RECOVERING

                iTermuxDegradedCause.CORRUPTED_INSTALL,
                iTermuxDegradedCause.ABI_MISMATCH,
                iTermuxDegradedCause.SANDBOX_INVALIDATED,
                null,
                -> iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE
            }
        }
    }

    fun fromSessionState(state: iTermuxSessionState): iTermuxDsRuntimeState {
        return when (state) {
            iTermuxSessionState.STARTING -> iTermuxDsRuntimeState.RUNTIME_LOADING
            iTermuxSessionState.RUNNING,
            iTermuxSessionState.SUSPENDED,
            iTermuxSessionState.READY,
            -> iTermuxDsRuntimeState.RUNTIME_READY

            iTermuxSessionState.KILLED_BY_OS,
            iTermuxSessionState.RECOVERING,
            -> iTermuxDsRuntimeState.RUNTIME_RECOVERING

            iTermuxSessionState.DEAD -> iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE
        }
    }
}
