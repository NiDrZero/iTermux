package com.darkian.itermux.proot

import com.darkian.itermux.core.iTermuxConfig
import com.darkian.itermux.core.iTermuxRuntime
import com.darkian.itermux.core.iTermuxRuntimeInitializer
import com.darkian.itermux.core.iTermuxRuntimeFailureCause
import com.darkian.itermux.core.iTermuxSessionMode
import com.darkian.itermux.core.iTermuxSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class iTermuxProotPluginTest {
    @Test
    fun createsProotSessionUsingSharedHostFacingContract() {
        val runtime = readyRuntime("itermux-proot-session")

        val session = runtime.createProotSession(
            distribution = iTermuxProotDistribution(
                name = "debian",
                rootfsPath = "/var/lib/proot-distro/debian",
            ),
            sessionId = "debian-main",
        )

        assertEquals("debian-main", session.id)
        assertEquals(iTermuxProotPlugin.BACKEND, session.backend)
        assertEquals(iTermuxSessionMode.LOGIN_SHELL, session.mode)
        assertEquals(
            "${runtime.paths.binDir}/${iTermuxProotPlugin.PROROOT_LAUNCHER}",
            session.shellSpec.executable,
        )
        assertEquals(
            listOf(
                "--link2symlink",
                "-0",
                "-r",
                "/var/lib/proot-distro/debian",
                "-b",
                "/dev",
                "-b",
                "/proc",
                "-b",
                "/sys",
                "-w",
                "/root",
                "/bin/sh",
            ),
            session.shellSpec.arguments,
        )
        assertEquals("proroot", session.shellSpec.environment["ITERMUX_SESSION_BACKEND"])
        assertEquals("debian", session.shellSpec.environment["PROOT_DISTRO_NAME"])
        assertEquals(runtime.paths.filesDir, session.shellSpec.environment["PROROOT_TMP_DIR"])
        assertFalse(session.shellSpec.environment.containsKey("HOST_ONLY"))
    }

    @Test
    fun allowsRuntimeEnvironmentInheritanceOnlyWhenExplicitlyRequested() {
        val runtime = readyRuntime("itermux-proot-inherit")

        val session = runtime.createProotSession(
            distribution = iTermuxProotDistribution(
                name = "debian",
                rootfsPath = "/var/lib/proot-distro/debian",
            ),
            sessionId = "debian-inherit",
            inheritRuntimeEnvironment = true,
        )

        assertEquals("present", session.shellSpec.environment["HOST_ONLY"])
        assertTrue(session.shellSpec.environment.containsKey("PREFIX"))
    }

    @Test
    fun returnsDeadSessionWithNamedCauseWhenProotIsDisabled() {
        val runtime = iTermuxRuntimeInitializer.initialize(
            filesDir = Files.createTempDirectory("itermux-proot-disabled").toFile().absolutePath,
            hostPackageName = "com.darkian.host",
            config = iTermuxConfig(prootEnabled = false),
        )

        val session = runtime.createProotSession(
            distribution = iTermuxProotDistribution(
                name = "debian",
                rootfsPath = "/var/lib/proot-distro/debian",
            ),
            sessionId = "disabled-proot",
        )

        assertEquals(iTermuxSessionState.DEAD, session.state)
        assertEquals(iTermuxRuntimeFailureCause.PROOT_UNAVAILABLE, session.failureCause)
    }

    @Test
    fun returnsDeadSessionWhenRuntimeIsNotReady() {
        val runtime = iTermuxRuntimeInitializer.initialize(
            filesDir = Files.createTempDirectory("itermux-proot-not-ready").toFile().absolutePath,
            hostPackageName = "com.darkian.host",
            config = iTermuxConfig(prootEnabled = true),
        )

        val session = runtime.createProotSession(
            distribution = iTermuxProotDistribution(
                name = "debian",
                rootfsPath = "/var/lib/proot-distro/debian",
            ),
            sessionId = "not-ready-proot",
        )

        assertEquals(iTermuxSessionState.DEAD, session.state)
        assertEquals(iTermuxRuntimeFailureCause.SESSION_START_FAILED, session.failureCause)
    }

    private fun readyRuntime(prefix: String): iTermuxRuntime {
        val initial = iTermuxRuntimeInitializer.initialize(
            filesDir = Files.createTempDirectory(prefix).toFile().absolutePath,
            hostPackageName = "com.darkian.host",
            config = iTermuxConfig(prootEnabled = true),
            extraEnv = mapOf("HOST_ONLY" to "present"),
        )
        File(initial.paths.binDir).mkdirs()
        File(initial.paths.binDir, "sh").writeText("#!/bin/sh\necho ready\n")
        File(initial.paths.binDir, "env").writeText("#!/bin/sh\nenv\n")
        File(initial.paths.etcDir).mkdirs()
        File(initial.paths.etcDir, "profile").writeText("export TERM=xterm-256color\n")
        return iTermuxRuntimeInitializer.refresh(
            identity = initial.identity,
            paths = initial.paths,
            supportedPackages = initial.supportedPackages,
            isProotEnabled = true,
            supportedAbis = initial.supportedAbis,
            bootstrapAssetPath = initial.bootstrapAssetPath,
            bootstrapVariantAbi = initial.bootstrapVariantAbi,
            isBootstrapPayloadPackaged = initial.isBootstrapPayloadPackaged,
            extraEnv = mapOf("HOST_ONLY" to "present"),
        )
    }
}
