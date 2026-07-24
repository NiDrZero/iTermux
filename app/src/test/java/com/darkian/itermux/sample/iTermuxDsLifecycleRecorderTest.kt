package com.darkian.itermux.sample

import com.darkian.itermux.core.iTermuxBootstrapState
import com.darkian.itermux.core.iTermuxDegradedCause
import com.darkian.itermux.core.iTermuxEnvironmentValidationResult
import com.darkian.itermux.core.iTermuxRuntime
import com.darkian.itermux.core.iTermuxRuntimeFailureCause
import com.darkian.itermux.core.iTermuxRuntimeInitializer
import com.darkian.itermux.core.iTermuxSession
import com.darkian.itermux.core.iTermuxSessionBackend
import com.darkian.itermux.core.iTermuxSessionMode
import com.darkian.itermux.core.iTermuxSessionState
import com.darkian.itermux.core.iTermuxShellSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class iTermuxDsLifecycleRecorderTest {
    @Test
    fun normalizesBootstrapAndSessionRecoveryEventsThroughTheListenerContract() {
        val recorder = iTermuxDsLifecycleRecorder()
        val listener = recorder.listener

        listener.onBootstrapState(iTermuxBootstrapState.UNINITIALIZED, null)
        listener.onBootstrapState(iTermuxBootstrapState.EXTRACTING, null)
        listener.onBootstrapState(iTermuxBootstrapState.READY, null)
        listener.onEnvironmentValidation(iTermuxEnvironmentValidationResult.VALID, null)
        listener.onSessionState("sample", iTermuxSessionState.STARTING)
        listener.onSessionState("sample", iTermuxSessionState.RUNNING)
        listener.onSessionState("sample", iTermuxSessionState.KILLED_BY_OS)
        listener.onSessionState("sample", iTermuxSessionState.RECOVERING)
        listener.onSessionState("sample", iTermuxSessionState.READY)

        assertEquals(iTermuxDsRuntimeState.RUNTIME_READY, recorder.normalizedState)
        assertEquals(
            listOf(
                iTermuxDsRuntimeState.RUNTIME_LOADING,
                iTermuxDsRuntimeState.RUNTIME_LOADING,
                iTermuxDsRuntimeState.RUNTIME_READY,
                iTermuxDsRuntimeState.RUNTIME_READY,
                iTermuxDsRuntimeState.RUNTIME_LOADING,
                iTermuxDsRuntimeState.RUNTIME_READY,
                iTermuxDsRuntimeState.RUNTIME_RECOVERING,
                iTermuxDsRuntimeState.RUNTIME_RECOVERING,
                iTermuxDsRuntimeState.RUNTIME_READY,
            ),
            recorder.events.map(iTermuxDsLifecycleEvent::normalizedState),
        )
        assertEquals(iTermuxSessionState.READY, recorder.sessionStates["sample"])
    }

    @Test
    fun seedsRuntimeAndSnapshotsSessionsUsingTheNormalizationLayer() {
        val recoverableRecorder = iTermuxDsLifecycleRecorder()
        recoverableRecorder.seedRuntime(
            runtime = runtimeSnapshot(
                bootstrapState = iTermuxBootstrapState.DEGRADED,
                failureCause = iTermuxRuntimeFailureCause.ENVIRONMENT_DEGRADED,
                degradedCause = iTermuxDegradedCause.MISSING_BINARY,
            ),
        )
        recoverableRecorder.recordSessionSnapshot(
            iTermuxSession(
                id = "sample",
                backend = iTermuxSessionBackend(id = "native"),
                mode = iTermuxSessionMode.LOGIN_SHELL,
                shellSpec = iTermuxShellSpec(
                    executable = "/tmp/usr/bin/sh",
                    arguments = emptyList(),
                    workingDirectory = "/tmp/home",
                    environment = emptyMap(),
                ),
                state = iTermuxSessionState.READY,
                recoveryAttempts = 1,
            ),
        )

        assertEquals(iTermuxDsRuntimeState.RUNTIME_READY, recoverableRecorder.normalizedState)

        val structuralRecorder = iTermuxDsLifecycleRecorder()
        structuralRecorder.seedRuntime(
            runtime = runtimeSnapshot(
                bootstrapState = iTermuxBootstrapState.DEGRADED,
                failureCause = iTermuxRuntimeFailureCause.ENVIRONMENT_DEGRADED,
                degradedCause = iTermuxDegradedCause.SANDBOX_INVALIDATED,
            ),
        )

        assertEquals(iTermuxDsRuntimeState.RUNTIME_UNAVAILABLE, structuralRecorder.normalizedState)
    }

    private fun runtimeSnapshot(
        bootstrapState: iTermuxBootstrapState,
        failureCause: iTermuxRuntimeFailureCause?,
        degradedCause: iTermuxDegradedCause?,
    ): iTermuxRuntime {
        val runtime = iTermuxRuntimeInitializer.initialize(
            filesDir = Files.createTempDirectory("itermux-ds-lifecycle").toFile().absolutePath,
            hostPackageName = "com.darkian.host",
        )
        return runtime.copy(
            bootstrapState = bootstrapState,
            failureCause = failureCause,
            degradedCause = degradedCause,
            isBootstrapRequired = bootstrapState == iTermuxBootstrapState.UNINITIALIZED,
        )
    }
}
