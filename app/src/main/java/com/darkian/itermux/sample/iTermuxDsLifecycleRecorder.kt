package com.darkian.itermux.sample

import com.darkian.itermux.core.iTermuxBootstrapState
import com.darkian.itermux.core.iTermuxDegradedCause
import com.darkian.itermux.core.iTermuxEnvironmentValidationResult
import com.darkian.itermux.core.iTermuxRuntime
import com.darkian.itermux.core.iTermuxRuntimeFailureCause
import com.darkian.itermux.core.iTermuxRuntimeLifecycleListener
import com.darkian.itermux.core.iTermuxSession
import com.darkian.itermux.core.iTermuxSessionState

data class iTermuxDsLifecycleEvent(
    val source: String,
    val rawState: String,
    val normalizedState: iTermuxDsRuntimeState,
    val cause: String? = null,
)

class iTermuxDsLifecycleRecorder {
    private val mutableEvents = mutableListOf<iTermuxDsLifecycleEvent>()

    val events: List<iTermuxDsLifecycleEvent>
        get() = mutableEvents.toList()

    val sessionStates: Map<String, iTermuxSessionState>
        get() = mutableSessionStates.toMap()

    var normalizedState: iTermuxDsRuntimeState = iTermuxDsRuntimeState.RUNTIME_LOADING
        private set

    var lastBootstrapState: iTermuxBootstrapState? = null
        private set

    var lastBootstrapFailureCause: iTermuxRuntimeFailureCause? = null
        private set

    var lastEnvironmentValidation: iTermuxEnvironmentValidationResult? = null
        private set

    var lastDegradedCause: iTermuxDegradedCause? = null
        private set

    val listener: iTermuxRuntimeLifecycleListener = object : iTermuxRuntimeLifecycleListener {
        override fun onBootstrapState(
            state: iTermuxBootstrapState,
            cause: iTermuxRuntimeFailureCause?,
        ) {
            lastBootstrapState = state
            lastBootstrapFailureCause = cause
            normalizedState = iTermuxDsStateMapper.fromBootstrapState(
                state = state,
                degradedCause = if (state == iTermuxBootstrapState.DEGRADED) {
                    lastDegradedCause
                } else {
                    null
                },
            )
            recordEvent(
                source = "bootstrap",
                rawState = state.name,
                normalizedState = normalizedState,
                cause = cause?.name ?: lastDegradedCause?.name,
            )
        }

        override fun onSessionState(
            sessionId: String,
            state: iTermuxSessionState,
        ) {
            mutableSessionStates[sessionId] = state
            normalizedState = iTermuxDsStateMapper.fromSessionState(state)
            recordEvent(
                source = "session:$sessionId",
                rawState = state.name,
                normalizedState = normalizedState,
            )
        }

        override fun onEnvironmentValidation(
            result: iTermuxEnvironmentValidationResult,
            cause: iTermuxDegradedCause?,
        ) {
            lastEnvironmentValidation = result
            lastDegradedCause = cause
            normalizedState = when (result) {
                iTermuxEnvironmentValidationResult.VALID -> {
                    lastBootstrapState?.let(iTermuxDsStateMapper::fromBootstrapStateWithNoCause)
                        ?: normalizedState
                }

                iTermuxEnvironmentValidationResult.DEGRADED -> iTermuxDsStateMapper.fromBootstrapState(
                    state = iTermuxBootstrapState.DEGRADED,
                    degradedCause = cause,
                )
            }
            recordEvent(
                source = "environment",
                rawState = result.name,
                normalizedState = normalizedState,
                cause = cause?.name,
            )
        }
    }

    fun seedRuntime(runtime: iTermuxRuntime) {
        lastBootstrapState = runtime.bootstrapState
        lastBootstrapFailureCause = runtime.failureCause
        lastDegradedCause = runtime.degradedCause
        normalizedState = iTermuxDsStateMapper.fromRuntime(runtime)
        recordEvent(
            source = "runtime",
            rawState = runtime.bootstrapState.name,
            normalizedState = normalizedState,
            cause = runtime.degradedCause?.name ?: runtime.failureCause?.name,
        )
    }

    fun recordSessionSnapshot(session: iTermuxSession) {
        mutableSessionStates[session.id] = session.state
        normalizedState = iTermuxDsStateMapper.fromSessionState(session.state)
        recordEvent(
            source = "session:${session.id}",
            rawState = session.state.name,
            normalizedState = normalizedState,
            cause = session.failureCause?.name,
        )
    }

    fun eventLines(): List<String> {
        return events.map { event ->
            buildString {
                append(event.source)
                append(" -> ")
                append(event.rawState)
                append(" => ")
                append(event.normalizedState)
                if (event.cause != null) {
                    append(" (")
                    append(event.cause)
                    append(')')
                }
            }
        }
    }

    private val mutableSessionStates = linkedMapOf<String, iTermuxSessionState>()

    private fun recordEvent(
        source: String,
        rawState: String,
        normalizedState: iTermuxDsRuntimeState,
        cause: String? = null,
    ) {
        mutableEvents += iTermuxDsLifecycleEvent(
            source = source,
            rawState = rawState,
            normalizedState = normalizedState,
            cause = cause,
        )
    }
}

private fun iTermuxDsStateMapper.fromBootstrapStateWithNoCause(
    state: iTermuxBootstrapState,
): iTermuxDsRuntimeState {
    return fromBootstrapState(
        state = state,
        degradedCause = null,
    )
}
