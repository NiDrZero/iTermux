package com.darkian.itermux.core

// INTERNAL-TERMUX MODIFIED - merge carefully

/**
 * Resolves the packaged bootstrap variant for a device ABI list.
 */
object iTermuxBootstrapResolver {
    fun resolve(
        supportedAbis: List<String>,
        config: iTermuxConfig = iTermuxConfig(),
    ): iTermuxBootstrapVariant? {
        if (supportedAbis.isEmpty()) {
            return null
        }

        return supportedAbis.firstNotNullOfOrNull { abi ->
            config.bootstrapVariants.firstOrNull { candidate ->
                candidate.abi.equals(abi, ignoreCase = true)
            }
        }
    }

    /**
     * Atomux targets 64-bit ARM only (Snapdragon and other arm64 SoCs).
     * armeabi-v7a and x86_64 were intentionally dropped: proroot ships an
     * arm64-v8a launcher exclusively, so no other ABI can host a session.
     * Callers that need a different ABI (e.g. tests) can still supply their
     * own variants via [iTermuxConfig.bootstrapVariants].
     */
    fun defaultVariants(): List<iTermuxBootstrapVariant> {
        return listOf(
            iTermuxBootstrapVariant(
                abi = "arm64-v8a",
                assetPath = "itermux/bootstrap/arm64-v8a/bootstrap.tar.xz",
            ),
        )
    }
}

data class iTermuxBootstrapVariant(
    val abi: String,
    val assetPath: String,
)
