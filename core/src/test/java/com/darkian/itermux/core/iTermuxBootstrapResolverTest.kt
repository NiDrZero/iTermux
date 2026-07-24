package com.darkian.itermux.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class iTermuxBootstrapResolverTest {
    @Test
    fun resolvesArm64VariantInDevicePreferenceOrder() {
        // Snapdragon/arm64 devices report arm64-v8a first; a legacy 32-bit ABI
        // may follow but is no longer a packaged variant.
        val variant = iTermuxBootstrapResolver.resolve(
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            config = iTermuxConfig(),
        )

        checkNotNull(variant)
        assertEquals("arm64-v8a", variant.abi)
        assertEquals("itermux/bootstrap/arm64-v8a/bootstrap.tar.xz", variant.assetPath)
    }

    @Test
    fun returnsNullWhenOnlyDroppedAbisArePresent() {
        // x86_64 and armeabi-v7a were intentionally dropped; a device offering
        // only those must resolve to no variant (host surfaces UNSUPPORTED_ABI).
        val variant = iTermuxBootstrapResolver.resolve(
            supportedAbis = listOf("x86_64", "armeabi-v7a"),
            config = iTermuxConfig(),
        )

        assertNull(variant)
    }

    @Test
    fun returnsNullWhenNoSupportedVariantMatches() {
        val variant = iTermuxBootstrapResolver.resolve(
            supportedAbis = listOf("x86", "mips"),
            config = iTermuxConfig(),
        )

        assertNull(variant)
    }
}
