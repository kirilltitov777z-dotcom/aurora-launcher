package com.aurora.launcher.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал сбоев лаунчера. Падение пишет стек в файл до того, как процесс умрёт, а с Android 11
 * добавляются причины прошлых завершений от системы: ANR, нехватка памяти, падение в нативном коде.
 * Отчёт виден в «О программе», его можно скопировать или отправить.
 */
object CrashLog {
    private const val FILE = "crash_log.txt"
    private const val MAX_BYTES = 64 * 1024

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                prepend(app, "=== ${stamp(System.currentTimeMillis())} · поток ${thread.name}\n${android.util.Log.getStackTraceString(e)}\n")
                app.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putBoolean("unseen", true).commit()
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Новое — сверху, старое обрезается. */
    @Synchronized
    private fun prepend(app: Context, entry: String) {
        val f = File(app.filesDir, FILE)
        val old = if (f.exists()) f.readText() else ""
        f.writeText((entry + old).take(MAX_BYTES))
    }

    /** Одна и та же заметка пишется не чаще раза за запуск процесса. */
    private val noted = HashSet<String>()

    /** Заметка о неполадке, которая не уронила лаунчер (например, виджет не смог начать слушать обновления). */
    fun note(ctx: Context, text: String, e: Throwable? = null) {
        runCatching {
            synchronized(noted) { if (!noted.add(text)) return }
            val trace = e?.let { "\n" + android.util.Log.getStackTraceString(it) }.orEmpty()
            prepend(ctx.applicationContext, "--- ${stamp(System.currentTimeMillis())} · $text$trace\n")
        }
    }

    /** Лаунчер упал с прошлого просмотра отчёта. Флаг снимается при чтении. */
    fun takeUnseen(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE)
        val v = p.getBoolean("unseen", false)
        if (v) p.edit().putBoolean("unseen", false).apply()
        return v
    }

    private fun stamp(t: Long) = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date(t))

    /** Короткие строки о прошлых завершениях процесса, которые не были обычным закрытием. */
    fun exits(ctx: Context): List<String> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        return runCatching {
            val am = ctx.getSystemService(ActivityManager::class.java)
            val since = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).getLong("clearedAt", 0L)
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 12).filter { it.timestamp > since }.mapNotNull { e ->
                val reason = when (e.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "сбой (Java)"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "сбой (native)"
                    ApplicationExitInfo.REASON_ANR -> "не отвечал (ANR)"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "выгружен: мало памяти"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "выгружен: много ресурсов"
                    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "ошибка запуска"
                    ApplicationExitInfo.REASON_SIGNALED -> "убит сигналом ${e.status}"
                    ApplicationExitInfo.REASON_OTHER -> "убит системой"
                    else -> return@mapNotNull null // обновление, остановка пользователем и т.п.
                }
                val mem = if (e.pss > 0) " · ${e.pss / 1024} МБ" else ""
                "${stamp(e.timestamp)} — $reason$mem" + (e.description?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            }
        }.getOrDefault(emptyList())
    }

    /** Трасса последнего ANR или нативного падения, если система её сохранила. */
    private fun lastTrace(ctx: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            val am = ctx.getSystemService(ActivityManager::class.java)
            val since = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).getLong("clearedAt", 0L)
            val e = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 12).firstOrNull {
                // Скобки обязательны: без них «timestamp > since» действует только на ANR,
                // и очищенный отчёт снова показывал старые нативные падения
                it.timestamp > since &&
                    (it.reason == ApplicationExitInfo.REASON_ANR || it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE)
            } ?: return null
            e.traceInputStream?.use { it.readBytes().decodeToString() }?.take(12 * 1024)
        }.getOrNull()
    }

    fun stored(ctx: Context): String? = runCatching { File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun hasAnything(ctx: Context): Boolean = stored(ctx) != null || exits(ctx).isNotEmpty()

    /** Отчёт собирается в фоне: обход кэша значков может читать тысячи файлов и подвесил бы интерфейс. */
    suspend fun report(ctx: Context, version: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        buildString {
            append("Aurora Launcher ").append(version).append('\n')
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · ").append(Permissions.vendorSkin).append('\n')
            append("SDK ").append(Build.VERSION.SDK_INT).append(" · ").append(Build.DISPLAY).append("\n\n")
            runCatching { append("Хранилище:\n").append(StorageInfo.format(StorageInfo.measure(ctx))).append("\n\n") }
            val ex = exits(ctx)
            if (ex.isNotEmpty()) {
                append("Завершения процесса:\n")
                ex.forEach { append("• ").append(it).append('\n') }
                append('\n')
            }
            stored(ctx)?.let { append("Сбои:\n").append(it).append('\n') }
            lastTrace(ctx)?.let { append("Последняя трасса ANR/native:\n").append(it) }
        }
    }

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, FILE).delete() }
        ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putLong("clearedAt", System.currentTimeMillis()).apply()
    }
}
