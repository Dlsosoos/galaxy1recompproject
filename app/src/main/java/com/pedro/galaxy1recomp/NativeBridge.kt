package com.pedro.galaxy1recomp

object NativeBridge {
    var loaded = false
        private set

    init {
        try { System.loadLibrary("galaxyrecomp"); loaded = true } catch (_: Throwable) {}
    }

    external fun nativeVersion(): String
    external fun nativeStart(fd: Int, format: String, config: String, mods: String, cheats: String): Int
    external fun nativeExtract(fd: Int, keyHex: String, outDir: String): String
    external fun nativeStop()
}
