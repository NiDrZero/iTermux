package com.darkian.itermux.sample

import com.darkian.itermux.core.iTermuxBootstrapState
import com.darkian.itermux.core.iTermuxDegradedCause
import com.darkian.itermux.core.iTermuxSessionState
import org.junit.Assert.assertEquals
import org.junit.Test

class iTermuxDsStateMapperTest {
    @Test
    fun mapsBootstrapLifecycleIntoDsRuntimeStates() {
        assertEquals(iTermuxDsRuntimeState.RUNTIME_LOADING, iTermuxDsStateMapper.fromBootstrapState(iTermuxBootstrapState.UNINITIALIZED, null))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_LOADING, iTermuxDsStateMapper.fromBootstrapState(iTermuxBootstrapState.EXTRACTING, null))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_LOADING, iTermuxDsStateMapper.fromBootstrapState(iTermuxBootstrapState.VERIFYING, null))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_READY, iTermuxDsStateMapper.fromBootstrapState(iTermuxBootstrapState.READY, null))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_RECOVERING, iTermuxDsStateMapper.fromBootstrapState(iTermuxBootstrapState.PARTIAL, null))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_RECOVERING, iTermuxDsStateMapper.fromBootstrapState(iTermuxBootstrapState.RECOVERING, null))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE, iTermuxDsStateMapper.fromBootstrapState(iTermuxBootstrapState.CORRUPTED, null))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE, iTermuxDsStateMapper.fromBootstrapState(iTermuxBootstrapState.FAILED, null))
    }

    @Test
    fun mapsDegradedBootstrapCausesByRecoverability() {
        assertEquals(
            iTermuxDsRuntimeState.RUNTIME_RECOVERING,
            iTermuxDsStateMapper.fromBootstrapState(
                iTermuxBootstrapState.DEGRADED,
                iTermuxDegradedCause.MISSING_BINARY,
            ),
        )
        assertEquals(
            iTermuxDsRuntimeState.RUNTIME_RECOVERING,
            iTermuxDsStateMapper.fromBootstrapState(
                iTermuxBootstrapState.DEGRADED,
                iTermuxDegradedCause.PERMISSION_CHANGED,
            ),
        )
        assertEquals(
            iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE,
            iTermuxDsStateMapper.fromBootstrapState(
                iTermuxBootstrapState.DEGRADED,
                iTermuxDegradedCause.CORRUPTED_INSTALL,
            ),
        )
        assertEquals(
            iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE,
            iTermuxDsStateMapper.fromBootstrapState(
                iTermuxBootstrapState.DEGRADED,
                iTermuxDegradedCause.ABI_MISMATCH,
            ),
        )
        assertEquals(
            iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE,
            iTermuxDsStateMapper.fromBootstrapState(
                iTermuxBootstrapState.DEGRADED,
                iTermuxDegradedCause.SANDBOX_INVALIDATED,
            ),
        )
    }

    @Test
    fun mapsSessionLifecycleIntoDsRuntimeStates() {
        assertEquals(iTermuxDsRuntimeState.RUNTIME_LOADING, iTermuxDsStateMapper.fromSessionState(iTermuxSessionState.STARTING))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_READY, iTermuxDsStateMapper.fromSessionState(iTermuxSessionState.RUNNING))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_READY, iTermuxDsStateMapper.fromSessionState(iTermuxSessionState.SUSPENDED))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_RECOVERING, iTermuxDsStateMapper.fromSessionState(iTermuxSessionState.KILLED_BY_OS))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_RECOVERING, iTermuxDsStateMapper.fromSessionState(iTermuxSessionState.RECOVERING))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_READY, iTermuxDsStateMapper.fromSessionState(iTermuxSessionState.READY))
        assertEquals(iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE, iTermuxDsStateMapper.fromSessionState(iTermuxSessionState.DEAD))
    }
}
