package com.darkian.itermux.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.darkian.itermux.core.iTermux
import com.darkian.itermux.core.iTermuxConfig

/**
 * Atomux launcher entry point.
 *
 * This is a deliberately minimal, launch-safe placeholder. Atomux is a
 * standalone terminal app built on the host-agnostic iTermux runtime, and its
 * own process is the permanent host of that runtime.
 *
 * The real Compose UI and the wrapped terminal surface are added in later
 * phases. For now this screen only proves the app launches and can initialize
 * the native runtime without crashing, even when no bootstrap payload is
 * packaged yet. proot is intentionally NOT wired in here — it is an optional
 * plugin, never a launch-time dependency of the app.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = runCatching {
            val runtime = iTermux.initialize(
                this,
                config = iTermuxConfig(prootEnabled = false),
            )
            buildString {
                append("Runtime initialized.\n")
                append("package: ")
                append(runtime.identity.packageName)
                append("\nprefix: ")
                append(runtime.paths.prefixDir)
                append("\nbootstrapRequired: ")
                append(runtime.isBootstrapRequired)
                append("\nbootstrapPayloadPackaged: ")
                append(runtime.isBootstrapPayloadPackaged)
            }
        }.getOrElse { error ->
            "Runtime not ready yet: ${error.message ?: error::class.java.simpleName}"
        }

        val message = buildString {
            append("Atomux\n\n")
            append("Standalone Android terminal built on the iTermux runtime.\n")
            append("This is an early launch-safe placeholder — the Compose UI ")
            append("and terminal surface arrive in later phases.\n\n")
            append(status)
        }

        setContentView(
            TextView(this).apply {
                text = message
                textSize = 16f
                setPadding(48, 48, 48, 48)
            }
        )
    }
}
