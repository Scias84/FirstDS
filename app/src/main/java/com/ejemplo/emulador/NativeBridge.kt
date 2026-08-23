package com.ejemplo.emulador

import java.nio.ByteBuffer

object NativeBridge {
    init {
        System.loadLibrary("emulatorkernel")
    }

    external fun nativeInit(systemPath: String): Boolean
    external fun nativeLoadRom(romPath: String): String
    external fun nativeRunFrame(keyMask: Int, touchX: Int, touchY: Int, isTouching: Boolean)
    external fun nativeGetTopBuffer(): ByteBuffer?
    external fun nativeGetBottomBuffer(): ByteBuffer?
}
