package com.dsemu.drastic

object DraSticBridge {
    init {
        // Carga primero el motor de CPU y luego el núcleo gráfico principal
        System.loadLibrary("drastic_cpu")
        System.loadLibrary("drastic_arm64")
    }

    // Funciones nativas del motor C++
    external fun initCore(path: String): Boolean
    external fun loadRom(romPath: String): Boolean
    external fun updateFrame(keyMask: Int)
}
