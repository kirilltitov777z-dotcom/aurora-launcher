package com.aurora.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Монохромный слой для тематических значков у приложений, которые его не дали
 * (как «тематические значки для всех приложений» в Android 16).
 *
 * Рисунок ищется так:
 *  - есть сплошная «плашка» (квадрат, круг) — рисунок всё, что заметно отличается от её цвета;
 *  - плашки нет (логотип на прозрачном) — берётся силуэт логотипа, а мелкие детали другого цвета
 *    внутри него вырезаются (трубка в пузыре WhatsApp, буква на кнопке);
 * После этого рисунок обрезается по своим границам и вписывается в одинаковую для всех значков область,
 * чтобы сгенерированные значки были того же размера, что и системные монохромные.
 */
object MonoSynth {
    /** Доля стороны значка, которую занимает рисунок; у системных монохромных значков примерно столько же. */
    private const val GLYPH = 0.52f

    /** [fg] — передний слой adaptive-иконки на прозрачном, [full] — значок целиком. */
    fun fromAdaptive(fg: Bitmap, full: Bitmap): Bitmap? {
        if (isIllustration(full)) return null
        val s = fg.width
        val px = IntArray(s * s)
        fg.getPixels(px, 0, s, 0, 0, s, s)
        var opaque = 0
        for (p in px) if (Color.alpha(p) > 160) opaque++
        // Передний слой закрывает почти весь значок — рисунок ищем на значке целиком, как на плашке
        val mask = if (opaque > s * s * 0.72f) fromPlate(full) ?: silhouette(full) else silhouette(fg)
        return mask?.takeUnless(::isBlob)
    }

    /**
     * Свой тематический (монохромный) слой приложения показывается как есть, даже залитый: так его задумал автор.
     * Раньше залитый слой переворачивался, и значок выглядел не так, как в стоковом лаунчере. Чистится только
     * «длинная тень» шаблонной иконки Android Studio.
     */
    fun fixSystem(mono: Bitmap): Bitmap {
        val s = mono.width
        if (mono.config != Bitmap.Config.ALPHA_8) return mono
        val stride = mono.rowBytes
        val raw = ByteArray(stride * mono.height)
        mono.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(raw))
        val a = ByteArray(s * s)
        for (y in 0 until s) System.arraycopy(raw, y * stride, a, y * s, s)
        return cutShadow(mono, a, s)
    }

    /**
     * Шаблонная иконка Android Studio отдаёт в монохромный слой передний слой целиком, вместе с «длинной тенью»:
     * на плашке она выглядит грязной диагональной полосой. Если полупрозрачная заливка занимает заметную часть
     * рисунка, оставляем только плотные места. Аккуратные двухтоновые глифы (полупрозрачны лишь мелкие детали
     * и края) не трогаем.
     */
    private fun cutShadow(mono: Bitmap, a: ByteArray, s: Int): Bitmap {
        var drawn = 0; var faint = 0
        for (v in a) {
            val x = v.toInt() and 0xFF
            if (x > 16) drawn++
            if (x in 17..150) faint++
        }
        if (drawn == 0 || faint < drawn * 0.3f) return mono
        val out = IntArray(s * s)
        for (i in a.indices) {
            val x = (a[i].toInt() and 0xFF) / 255f
            val k = ((x - 0.6f) / 0.3f).coerceIn(0f, 1f)
            out[i] = ((k * k * (3f - 2f * k)) * 255f).toInt() shl 24
        }
        return normalize(out, s) ?: mono
    }

    /** Обычная (не adaptive) иконка. */
    fun fromLegacy(bmp: Bitmap): Bitmap? {
        if (isIllustration(bmp)) return null
        return (fromPlate(bmp) ?: silhouette(bmp))?.takeUnless(::isBlob)
    }

    /**
     * Рисунок-иллюстрация (персонажи, сцены, фото): десятки цветов или много мелкой детали. Силуэт такого значка —
     * каша пятен; лучше перекрасить сам значок в цвета темы (это делает UI, когда монохромного слоя нет).
     * Пороги подобраны на реальных значках: у логотипов не больше ~16 заметных цветовых групп и спокойная яркость.
     */
    private fun isIllustration(src: Bitmap): Boolean {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val bins = IntArray(512)
        var n = 0
        var tv = 0.0
        var tvn = 0
        for (y in 0 until h) {
            var prev = -1f
            for (x in 0 until w) {
                val p = px[y * w + x]
                if (Color.alpha(p) <= 200) { prev = -1f; continue }
                n++
                bins[((Color.red(p) shr 5) shl 6) or ((Color.green(p) shr 5) shl 3) or (Color.blue(p) shr 5)]++
                val l = 0.3f * Color.red(p) + 0.59f * Color.green(p) + 0.11f * Color.blue(p)
                if (prev >= 0f) { tv += kotlin.math.abs(l - prev); tvn++ }
                prev = l
            }
        }
        if (n == 0) return false
        val groups = bins.count { it > n * 0.004f }
        val detail = if (tvn > 0) tv / tvn else 0.0
        return groups >= 20 || detail >= 10.0
    }

    /**
     * Бесформенное пятно: сплошной круг или квадрат без дыр и деталей (объектив целиком, плашка игры).
     * По такому значку приложение не узнать — тоже лучше перекрасить сам значок.
     */
    private fun isBlob(mask: Bitmap): Boolean {
        val s = mask.width
        val stride = mask.rowBytes
        val raw = ByteArray(stride * mask.height)
        mask.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(raw))
        val on = BooleanArray(s * s) { (raw[(it / s) * stride + it % s].toInt() and 0xFF) > 127 }
        var area = 0
        var l = s; var t = s; var r = -1; var b = -1
        var edge = 0
        for (y in 0 until s) for (x in 0 until s) {
            if (!on[y * s + x]) continue
            area++
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
            if (x == 0 || y == 0 || x == s - 1 || y == s - 1 ||
                !on[y * s + x - 1] || !on[y * s + x + 1] || !on[(y - 1) * s + x] || !on[(y + 1) * s + x]) edge++
        }
        if (area == 0) return true
        val fill = area.toFloat() / ((r - l + 1) * (b - t + 1))
        val contour = edge.toFloat() / (2 * ((r - l + 1) + (b - t + 1)))
        if (fill < 0.75f || contour >= 1f) return false
        // Дыры внутри (кольцо, буква «О») — это уже рисунок: фон, не связанный с краем, заметной площади
        val outside = BooleanArray(s * s)
        val stack = IntArray(s * s)
        var sp = 0
        fun push(k: Int) { if (!on[k] && !outside[k]) { outside[k] = true; stack[sp++] = k } }
        for (i in 0 until s) { push(i); push((s - 1) * s + i); push(i * s); push(i * s + s - 1) }
        while (sp > 0) {
            val j = stack[--sp]
            val x = j % s
            val y = j / s
            if (x > 0) push(j - 1)
            if (x < s - 1) push(j + 1)
            if (y > 0) push(j - s)
            if (y < s - 1) push(j + s)
        }
        var holes = 0
        for (i in on.indices) if (!on[i] && !outside[i]) holes++
        return holes < area * 0.01f
    }

    private class Bounds(val l: Int, val t: Int, val r: Int, val b: Int) {
        val w get() = r - l + 1
        val h get() = b - t + 1
    }

    private fun bounds(alpha: (Int) -> Int, s: Int, threshold: Int): Bounds? {
        var l = s; var t = s; var r = -1; var b = -1
        for (y in 0 until s) for (x in 0 until s) {
            if (alpha(y * s + x) > threshold) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
        }
        return if (r < 0) null else Bounds(l, t, r, b)
    }

    /** Значок на сплошной плашке: цвет плашки берём по кольцу у её края. null — плашки нет. */
    private fun fromPlate(src: Bitmap): Bitmap? {
        val s = src.width
        val px = IntArray(s * s)
        src.getPixels(px, 0, s, 0, 0, s, s)
        val box = bounds({ Color.alpha(px[it]) }, s, 200) ?: return null
        // Плашка — непрозрачная фигура почти на весь значок
        if (box.w < s * 0.5f || box.h < s * 0.5f) return null
        val cx = (box.l + box.r) / 2f
        val cy = (box.t + box.b) / 2f
        val rx = box.w / 2f * 0.86f
        val ry = box.h / 2f * 0.86f
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        val samples = IntArray(96)
        for (i in 0 until 96) {
            val a = i * Math.PI * 2 / 96
            val x = (cx + rx * kotlin.math.cos(a)).toInt().coerceIn(0, s - 1)
            val y = (cy + ry * kotlin.math.sin(a)).toInt().coerceIn(0, s - 1)
            val p = px[y * s + x]
            samples[i] = p
            if (Color.alpha(p) > 200) {
                r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
            }
        }
        if (n < 80) return null
        val br = (r / n).toInt(); val bg = (g / n).toInt(); val bb = (b / n).toInt()
        // Край пёстрый (градиент, фото) — сплошной плашки нет
        var spread = 0
        for (p in samples) if (Color.alpha(p) > 200 && diff(p, br, bg, bb) > 0.16f) spread++
        if (spread > 24) return null
        val vals = FloatArray(s * s)
        var covered = 0
        for (i in px.indices) {
            val p = px[i]
            val a = Color.alpha(p) / 255f
            val t = ((diff(p, br, bg, bb) - 0.09f) / 0.2f).coerceIn(0f, 1f)
            val v = a * t * t * (3f - 2f * t)
            if (v > 0.5f) covered++
            vals[i] = v
        }
        // Рисунок одного цвета с плашкой — различить нечего
        if (covered < s * s / 60) return null
        // Внутри рисунка мелкие детали другого цвета вырезаем, как у силуэта: иначе объектив камеры
        // со зрачком превращается в сплошной круг. Порог строже, чем у силуэта: разноцветные части логотипа на плашке
        // (точки Ассистента) — это рисунок, а не детали
        carve(px, vals, maxOther = 0.2f)
        return normalize(IntArray(s * s) { ((vals[it] * 255f).toInt() shl 24) }, s)
    }

    /** Логотип на прозрачном: силуэт, из которого вырезаны мелкие детали другого цвета. */
    private fun silhouette(src: Bitmap): Bitmap? {
        val s = src.width
        val px = IntArray(s * s)
        src.getPixels(px, 0, s, 0, 0, s, s)
        if (px.none { Color.alpha(it) > 200 }) return null
        // Полупрозрачные тени («длинная тень» у старых значков) в рисунок не попадают
        // Порог высокий: тень у шаблонного значка Android доходит до половины непрозрачности и давала грязную полосу
        val vals = FloatArray(s * s) { i ->
            val a = ((Color.alpha(px[i]) / 255f - 0.6f) / 0.3f).coerceIn(0f, 1f)
            a * a * (3f - 2f * a)
        }
        carve(px, vals)
        return normalize(IntArray(s * s) { ((vals[it] * 255f).toInt() shl 24) }, s)
    }

    /**
     * Вырезает из рисунка [vals] мелкие детали другого цвета (трубка в пузыре WhatsApp, зрачок объектива).
     * Основной цвет — средний по точкам рисунка, уточнённый по близким к нему (иначе пёстрый логотип даёт серую
     * кашу). Детали режем, только если их немного, и не режем, если от рисунка почти ничего не осталось бы:
     * у многоцветного логотипа (Chrome, Gmail) остаётся силуэт, а не пустота. Раньше Chrome выходил пустым.
     */
    private fun carve(px: IntArray, vals: FloatArray, maxOther: Float = 0.35f) {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (i in px.indices) if (vals[i] > 0.5f) { val p = px[i]; r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++ }
        if (n == 0) return
        var mr = (r / n).toInt(); var mg = (g / n).toInt(); var mb = (b / n).toInt()
        r = 0; g = 0; b = 0
        var m = 0
        for (i in px.indices) if (vals[i] > 0.5f && diff(px[i], mr, mg, mb) < 0.22f) { val p = px[i]; r += Color.red(p); g += Color.green(p); b += Color.blue(p); m++ }
        if (m > n / 4) { mr = (r / m).toInt(); mg = (g / m).toInt(); mb = (b / m).toInt() }
        var other = 0
        for (i in px.indices) if (vals[i] > 0.5f && diff(px[i], mr, mg, mb) > 0.3f) other++
        if (other < n / 40 || other > n * maxOther) return
        val carved = FloatArray(vals.size)
        var left = 0
        for (i in vals.indices) {
            var v = vals[i]
            if (v > 0f) {
                val t = ((diff(px[i], mr, mg, mb) - 0.2f) / 0.15f).coerceIn(0f, 1f)
                v *= 1f - t * t * (3f - 2f * t)
            }
            carved[i] = v
            if (v > 0.5f) left++
        }
        if (left < n * 0.3f) return
        carved.copyInto(vals)
    }

    /** Обрезает рисунок по границам и вписывает его по центру в одинаковую для всех значков область. */
    private fun normalize(mask: IntArray, s: Int): Bitmap? {
        val box = bounds({ mask[it] ushr 24 }, s, 40) ?: return null
        val src = Bitmap.createBitmap(mask, s, s, Bitmap.Config.ARGB_8888)
        val target = s * GLYPH
        // Вытянутые рисунки (надписи) вписываем по длинной стороне, но не крупнее полутора квадратных
        val k = target / max(box.w, box.h).toFloat() * if (box.w.toFloat() / box.h > 1.6f || box.h.toFloat() / box.w > 1.6f) 1.25f else 1f
        val w = box.w * k
        val h = box.h * k
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ALPHA_8)
        Canvas(out).drawBitmap(
            src,
            android.graphics.Rect(box.l, box.t, box.r + 1, box.b + 1),
            RectF((s - w) / 2f, (s - h) / 2f, (s + w) / 2f, (s + h) / 2f),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        src.recycle()
        return out
    }

    private fun diff(p: Int, r: Int, g: Int, b: Int): Float {
        val dr = Color.red(p) - r
        val dg = Color.green(p) - g
        val db = Color.blue(p) - b
        return sqrt((dr * dr + dg * dg + db * db) / 3f) / 255f
    }
}
