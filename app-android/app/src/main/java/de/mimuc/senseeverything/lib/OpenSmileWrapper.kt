package de.mimuc.senseeverything.lib

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class OpenSmileWrapper(context: Context) {
    private val context: Context? = null
    private val openSmile: OpenSmileExamples? = null

    init {
        cacheAll()
    }

    /**
     * copies a file to a given destination
     * @param filename the file to be copied
     * @param dst destination directory (default: cacheDir)
     */
    private fun cacheAsset(filename: String, dst: String) {
        if (context == null) return
        val pathname = "$dst/$filename"
        val outfile = File(pathname).apply { parentFile?.mkdirs() }
        context.assets.open(filename).use { inputStream ->
            FileOutputStream(outfile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    private fun cacheAll() {
        if (context == null) return
        thread(start = true) { OpenSmileExamples.openSmileAssets.map { cacheAsset(it, context.cacheDir.absolutePath) } }
    }

    private fun runOpenSmile() {
        if (openSmile == null) return
        openSmile.build("EXTSINK", this)
    }
}