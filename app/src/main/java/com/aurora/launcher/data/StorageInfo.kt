package com.aurora.launcher.data

import android.content.Context
import java.io.File

/** Сколько места занимают данные лаунчера. Нужно в «О программе» и в отчёте: по нему видно, что именно разрослось. */
object StorageInfo {
    class Part(val name: String, val bytes: Long)

    /** Считает размер папок: вызывать не в главном потоке. */
    fun measure(ctx: Context): List<Part> {
        val files = ctx.filesDir
        val parts = ArrayList<Part>()
        parts += Part("cache", size(ctx.cacheDir))
        parts += Part("icon cache", size(File(ctx.cacheDir, ICON_CACHE_DIR)))
        parts += Part("files", size(files))
        for (name in listOf("icon_themes", "icon_overrides")) parts += Part(name, size(File(files, name)))
        parts += Part("wallpaper", File(files, "launcher_wallpaper.img").takeIf { it.exists() }?.length() ?: 0L)
        ctx.getExternalFilesDir(null)?.let { parts += Part("external", size(it)) }
        return parts
    }

    fun format(parts: List<Part>): String = parts.joinToString("\n") { "${it.name}: ${human(it.bytes)}" }

    fun human(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%d KB".format(bytes shr 10)
        else -> "$bytes B"
    }

    private fun size(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        var total = 0L
        val stack = ArrayDeque<File>().apply { add(f) }
        while (stack.isNotEmpty()) {
            for (c in stack.removeLast().listFiles().orEmpty()) if (c.isDirectory) stack.add(c) else total += c.length()
        }
        return total
    }
}
