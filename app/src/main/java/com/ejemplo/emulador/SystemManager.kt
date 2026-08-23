package com.ejemplo.emulador

import android.content.Context
import java.io.File

class SystemManager(private val context: Context) {

    val systemDir: File get() = File(context.filesDir, "system").apply { if (!exists()) mkdirs() }
    val backupDir: File get() = File(context.filesDir, "backup").apply { if (!exists()) mkdirs() }
    val savestatesDir: File get() = File(context.filesDir, "savestates").apply { if (!exists()) mkdirs() }
    val cheatsDir: File get() = File(context.filesDir, "cheats").apply { if (!exists()) mkdirs() }

    fun initializeDirectories() {
        systemDir
        backupDir
        savestatesDir
        cheatsDir
    }

    fun getSaveFilePath(romName: String): String {
        val baseName = romName.substringBeforeLast(".")
        return File(backupDir, "$baseName.dsv").absolutePath
    }

    fun getStateFilePath(romName: String, slot: Int): String {
        val baseName = romName.substringBeforeLast(".")
        return File(savestatesDir, "$baseName.state$slot").absolutePath
    }
}
