package com.darkian.itermux.proot

import com.darkian.itermux.core.iTermuxEnvironment
import com.darkian.itermux.core.iTermuxRuntime
import com.darkian.itermux.core.iTermuxRuntimeFailureCause
import com.darkian.itermux.core.iTermuxSession
import com.darkian.itermux.core.iTermuxSessionBackend
import com.darkian.itermux.core.iTermuxSessionController
import com.darkian.itermux.core.iTermuxSessionMode
import com.darkian.itermux.core.iTermuxSessionState
import com.darkian.itermux.core.iTermuxShellSpec
import com.darkian.itermux.core.sessionStartFailureCause

/**
 * Optional rootless-Linux launcher that stays outside the core runtime module
 * while returning the same host-facing session contract as native sessions.
 *
 * The backend is proroot (https://github.com/coderredlab/proroot): a drop-in,
 * proot-compatible launcher with zero ptrace overhead. It is distributed as
 * arm64-v8a native libraries (`libproroot.so` and four companion `.so` files)
 * that the host packages under `jniLibs/arm64-v8a/`. The launcher auto-discovers
 * its companion libraries from its own directory, so only its path and a
 * writable `PROROOT_TMP_DIR` need to be supplied.
 */
object iTermuxProotPlugin {
    const val MODULE_ID: String = "internal-termux-proroot"

    /** Native launcher file name shipped in `jniLibs/arm64-v8a/`. */
    const val PROROOT_LAUNCHER: String = "libproroot.so"

    val BACKEND: iTermuxSessionBackend = iTermuxSessionBackend(id = "proroot")

    fun launch(
        runtime: iTermuxRuntime,
        distribution: iTermuxProotDistribution,
        sessionId: String,
        shellArguments: List<String> = emptyList(),
        inheritRuntimeEnvironment: Boolean = false,
        baseEnv: Map<String, String> = emptyMap(),
        extraEnv: Map<String, String> = emptyMap(),
        config: iTermuxProotConfig = iTermuxProotConfig(
            executable = "${runtime.paths.binDir}/$PROROOT_LAUNCHER",
        ),
    ): iTermuxSession {
        val startFailureCause = when {
            !runtime.isProotEnabled -> iTermuxRuntimeFailureCause.PROOT_UNAVAILABLE
            else -> runtime.sessionStartFailureCause()
        }

        return iTermuxSessionController.start(
            sessionId = sessionId,
            sessionFactory = {
                check(startFailureCause == null) {
                    "Session cannot start because $startFailureCause."
                }
                val mergedBaseEnv = linkedMapOf<String, String>()
                if (inheritRuntimeEnvironment) {
                    mergedBaseEnv.putAll(runtime.environment)
                }
                mergedBaseEnv.putAll(baseEnv)

                val mergedExtraEnv = linkedMapOf(
                    "ITERMUX_SESSION_BACKEND" to BACKEND.id,
                    "PROOT_DISTRO_NAME" to distribution.name,
                    "PROOT_DISTRO_ROOTFS" to distribution.rootfsPath,
                    // proroot writes runtime config files here; must be a
                    // writable app-owned directory (the runtime files dir).
                    "PROROOT_TMP_DIR" to runtime.paths.filesDir,
                )
                mergedExtraEnv.putAll(extraEnv)

                iTermuxSession(
                    id = sessionId,
                    backend = BACKEND,
                    mode = iTermuxSessionMode.LOGIN_SHELL,
                    shellSpec = iTermuxShellSpec(
                        executable = config.executable,
                        arguments = buildArguments(
                            distribution = distribution,
                            shellArguments = shellArguments,
                            config = config,
                        ),
                        workingDirectory = runtime.defaultWorkingDirectory,
                        environment = iTermuxEnvironment.build(
                            paths = runtime.paths,
                            baseEnv = mergedBaseEnv,
                            extraEnv = mergedExtraEnv,
                        ),
                    ),
                )
            },
            failureSessionFactory = {
                iTermuxSession(
                    id = sessionId,
                    backend = BACKEND,
                    mode = iTermuxSessionMode.LOGIN_SHELL,
                    shellSpec = iTermuxShellSpec(
                        executable = config.executable,
                        arguments = buildArguments(
                            distribution = distribution,
                            shellArguments = shellArguments,
                            config = config,
                        ),
                        workingDirectory = runtime.defaultWorkingDirectory,
                        environment = runtime.environment,
                    ),
                    state = iTermuxSessionState.DEAD,
                    failureCause = startFailureCause ?: iTermuxRuntimeFailureCause.SESSION_START_FAILED,
                )
            },
        )
    }

    private fun buildArguments(
        distribution: iTermuxProotDistribution,
        shellArguments: List<String>,
        config: iTermuxProotConfig,
    ): List<String> {
        val arguments = mutableListOf<String>()
        arguments.addAll(config.extraArguments)
        arguments += listOf("-r", distribution.rootfsPath)
        config.bindPaths.forEach { bindPath ->
            arguments += listOf("-b", bindPath)
        }
        arguments += listOf("-w", distribution.workingDirectory, distribution.loginShell)
        arguments.addAll(shellArguments)
        return arguments
    }
}

data class iTermuxProotDistribution(
    val name: String,
    val rootfsPath: String,
    val loginShell: String = "/bin/sh",
    val workingDirectory: String = "/root",
)

/**
 * Launcher configuration for a proroot session.
 *
 * Defaults use proroot's proot-compatible flags: `--link2symlink` for hardlink
 * emulation and `-0` for the fakeroot uid/gid=0 mapping. proroot auto-discovers
 * its companion `.so` files from the launcher's own directory, so only the
 * launcher [executable] path needs to be supplied.
 */
data class iTermuxProotConfig(
    val executable: String,
    val bindPaths: List<String> = listOf("/dev", "/proc", "/sys"),
    val extraArguments: List<String> = listOf("--link2symlink", "-0"),
)

fun iTermuxRuntime.createProotSession(
    distribution: iTermuxProotDistribution,
    sessionId: String,
    shellArguments: List<String> = emptyList(),
    inheritRuntimeEnvironment: Boolean = false,
    baseEnv: Map<String, String> = emptyMap(),
    extraEnv: Map<String, String> = emptyMap(),
    config: iTermuxProotConfig = iTermuxProotConfig(
        executable = "${paths.binDir}/${iTermuxProotPlugin.PROROOT_LAUNCHER}",
    ),
): iTermuxSession {
    return iTermuxProotPlugin.launch(
        runtime = this,
        distribution = distribution,
        sessionId = sessionId,
        shellArguments = shellArguments,
        inheritRuntimeEnvironment = inheritRuntimeEnvironment,
        baseEnv = baseEnv,
        extraEnv = extraEnv,
        config = config,
    )
}
