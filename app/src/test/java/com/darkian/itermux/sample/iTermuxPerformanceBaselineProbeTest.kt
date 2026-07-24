package com.darkian.itermux.sample

import com.darkian.itermux.core.iTermux
import com.darkian.itermux.core.iTermuxBootstrapState
import com.darkian.itermux.core.iTermuxRuntime
import com.darkian.itermux.core.iTermuxRuntimeInitializer
import com.darkian.itermux.core.iTermuxSession
import com.darkian.itermux.core.iTermuxSessionState
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.math.max
import kotlin.system.measureNanoTime

class iTermuxPerformanceBaselineProbeTest {
    @Test
    fun printsPhase9HostBaseline() {
        val filesDir = Files.createTempDirectory("itermux-phase9-baseline").toFile().absolutePath

        lateinit var coldRuntime: iTermuxRuntime
        val coldInitNanos = measureNanoTime {
            coldRuntime = initializedRuntime(filesDir)
        }

        lateinit var warmRuntime: iTermuxRuntime
        val warmInitNanos = measureNanoTime {
            warmRuntime = initializedRuntime(filesDir)
        }

        val memoryBeforeSession = usedMemoryBytes()
        lateinit var session: iTermuxSession
        val shellReadyNanos = measureNanoTime {
            session = iTermux.createSession(
                runtime = warmRuntime,
                sessionId = "baseline",
            )
        }
        val idleMemoryBytes = max(usedMemoryBytes() - memoryBeforeSession, 0L)

        lateinit var recovered: iTermuxSession
        val recoveryNanos = measureNanoTime {
            recovered = iTermux.recoverSessionFromOsKill(session) { killed ->
                killed.copy(
                    state = iTermuxSessionState.READY,
                    recoveryAttempts = killed.recoveryAttempts + 1,
                    failureCause = null,
                )
            }
        }

        assertEquals(iTermuxBootstrapState.READY, coldRuntime.bootstrapState)
        assertEquals(iTermuxBootstrapState.READY, warmRuntime.bootstrapState)
        assertEquals(iTermuxSessionState.RUNNING, session.state)
        assertEquals(iTermuxSessionState.READY, recovered.state)

        println(
            "PHASE9_BASELINE " +
                "cold_init_ms=${toMillis(coldInitNanos)} " +
                "warm_init_ms=${toMillis(warmInitNanos)} " +
                "shell_ready_ms=${toMillis(shellReadyNanos)} " +
                "idle_memory_mb=${toMegabytes(idleMemoryBytes)} " +
                "recovery_ms=${toMillis(recoveryNanos)}",
        )
    }

    private fun initializedRuntime(filesDir: String): iTermuxRuntime {
        return iTermuxRuntimeInitializer.initialize(
            filesDir = filesDir,
            hostPackageName = "com.darkian.host",
            supportedAbis = listOf("arm64-v8a"),
            isBootstrapPayloadPackaged = true,
            autoInstallBootstrap = true,
            bootstrapInstaller = { currentRuntime ->
                iTermux.installBootstrap(currentRuntime) {
                    ByteArrayInputStream(
                        bootstrapArchive(
                            "bin/sh" to "#!/bin/sh\necho embedded\n",
                            "bin/env" to "#!/bin/sh\nenv\n",
                            "etc/profile" to "export TERM=xterm-256color\n",
                        ),
                    )
                }
            },
        )
    }

    private fun bootstrapArchive(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        XZCompressorOutputStream(output).use { xzOutput ->
            TarArchiveOutputStream(xzOutput).use { tarOutput ->
                tarOutput.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                entries.forEach { (name, contents) ->
                    val bytes = contents.toByteArray(StandardCharsets.UTF_8)
                    val entry = TarArchiveEntry(name).apply {
                        size = bytes.size.toLong()
                        mode = if (name.startsWith("bin/")) 0b111_101_101 else 0b110_100_100
                    }
                    tarOutput.putArchiveEntry(entry)
                    tarOutput.write(bytes)
                    tarOutput.closeArchiveEntry()
                }
                tarOutput.finish()
            }
        }
        return output.toByteArray()
    }

    private fun usedMemoryBytes(): Long {
        System.gc()
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun toMillis(nanos: Long): Long = nanos / 1_000_000L

    private fun toMegabytes(bytes: Long): String = "%.2f".format(bytes / 1024.0 / 1024.0)
}
