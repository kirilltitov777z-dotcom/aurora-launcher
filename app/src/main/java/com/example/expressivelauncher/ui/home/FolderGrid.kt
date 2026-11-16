package com.example.expressivelauncher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expressivelauncher.data.HomeItem
import kotlin.math.ceil
import kotlin.math.min

/**
 * Геометрия папки на столе — одна для отрисовки и для касаний.
 *
 * Обычная папка 1×1 — значок как у приложения с подписью снизу. Большая папка занимает свою сетку
 * (2×2, 3×2, 1×2 и т. д., как виджет) и показывает приложения крупными плитками: по плитке приложение
 * запускается сразу, не открывая папку. Если приложений больше, чем плиток, последняя плитка — «ещё»:
 * миниатюра с четырьмя оставшимися значками, по ней открывается сама папка.
 */

/** Отступ плитки от краёв ячеек папки. */
internal const val FOLDER_INSET_DP = 4f

/** Крупный значок занимает эту долю клетки; остаток клетки — зазор между значками. */
private const val ICON_IN_CELL = 0.92f

/**
 * Поле плитки вокруг сетки: доля меньшей стороны. По макету большая папка — тёмный контейнер,
 * внутри которого светлые плитки приложений почти вплотную к краю, с узкими равными зазорами.
 */
private const val FOLDER_PAD_SHARE = 0.07f

/** Предел роста значка, чтобы у папки с одним приложением он не распух на всю плитку. */
private const val ICON_MAX_SHARE = 0.62f

/** Сколько миниатюр в плитке «ещё». */
const val FOLDER_OVERFLOW_MINI = 4

/** Сетка крупных значков внутри плитки большой папки. */
class FolderGrid(val cols: Int, val rows: Int) {
    val count: Int get() = cols * rows

    /** Поле от края плитки до сетки. */
    fun pad(size: Size): Float = min(size.width, size.height) * FOLDER_PAD_SHARE

    /** Область сетки внутри плитки. */
    fun inner(size: Size): Rect {
        val p = pad(size)
        return Rect(p, p, (size.width - p).coerceAtLeast(p + 1f), (size.height - p).coerceAtLeast(p + 1f))
    }

    fun cell(size: Size): Size {
        val i = inner(size)
        return Size(i.width / cols, i.height / rows)
    }

    /** Сторона крупного значка: квадрат, вписанный в клетку. Один значок на всю папку не раздуваем. */
    fun iconSide(size: Size): Float {
        val c = cell(size)
        val fit = min(c.width, c.height) * ICON_IN_CELL
        return if (count == 1) min(fit, min(size.width, size.height) * ICON_MAX_SHARE) else fit
    }

    /** Квадрат значка в клетке [index]. */
    fun rect(size: Size, index: Int): Rect {
        val i = inner(size)
        val c = cell(size)
        val side = iconSide(size)
        val col = index % cols
        val row = index / cols
        val x = i.left + col * c.width + (c.width - side) / 2f
        val y = i.top + row * c.height + (c.height - side) / 2f
        return Rect(x, y, x + side, y + side)
    }
}

/** Что рисовать в плитке: [grid], сколько крупных значков ([big]) и есть ли плитка «ещё». */
class FolderContents(val grid: FolderGrid, val big: Int, val overflow: Boolean)

/**
 * Раскладка содержимого большой папки. У квадратной 2×2 плитки раскладка по макету: 4–5 приложений
 * помещаются в сетку 2×2, 6 и больше — в 3×3. Плитка «ещё» появляется, только когда приложений больше,
 * чем клеток: папка из шести приложений показывает все шесть, а не пять и пустую миниатюру.
 * У остальных размеров (широкая/высокая папка) клетки задают предел: у широкой на столбец больше,
 * у высокой — на строку.
 */
fun folderContents(item: HomeItem): FolderContents {
    val w = item.w.coerceAtLeast(1)
    val h = item.h.coerceAtLeast(1)
    val n = item.children.size
    if (w == h && w >= 2) {
        val cols = if (n >= 6) 3 else 2
        val cap = cols * cols
        // Не всё помещается: последняя клетка — плитка «ещё», остальные заняты приложениями
        if (n > cap) return FolderContents(FolderGrid(cols, cols), big = cap - 1, overflow = true)
        // Все помещаются: сетку не «оптимизируем», иначе шесть приложений встают в два столбца и уезжают влево
        val grid = when (n) {
            0, 1 -> FolderGrid(1, 1)
            2 -> FolderGrid(2, 1)
            else -> FolderGrid(cols, cols)
        }
        return FolderContents(grid, big = n, overflow = false)
    }
    val maxCols = (if (w >= h) w + 1 else w).coerceIn(1, 5)
    val maxRows = (if (h > w) h + 1 else h).coerceIn(1, 5)
    if (n > maxCols * maxRows) {
        // Не всё помещается: последняя клетка — плитка «ещё»
        return FolderContents(FolderGrid(maxCols, maxRows), big = maxCols * maxRows - 1, overflow = true)
    }
    return FolderContents(fit(n, maxCols, maxRows), big = n, overflow = false)
}

/** Сетка под [n] значков: меньше пустых клеток, затем крупнее значок. */
private fun fit(n: Int, maxCols: Int, maxRows: Int): FolderGrid {
    if (n <= 0) return FolderGrid(1, 1)
    var best = FolderGrid(maxCols, maxRows)
    var bestWaste = Int.MAX_VALUE
    var bestSide = -1f
    for (c in 1..maxCols) {
        val r = ceil(n / c.toFloat()).toInt()
        if (r > maxRows) continue
        val waste = c * r - n
        val side = min(ICON_IN_CELL / c, ICON_IN_CELL / r)
        if (waste < bestWaste || (waste == bestWaste && side > bestSide)) {
            best = FolderGrid(c, r)
            bestWaste = waste
            bestSide = side
        }
    }
    return best
}

/** Сколько значков реально показано крупно (у плитки «ещё» — на один меньше клеток). */
fun folderBigCount(item: HomeItem): Int = folderContents(item).let { if (it.overflow) it.big else min(it.big, item.children.size) }

/** Прямоугольник плитки папки в координатах окна. */
fun folderTile(geometry: HomeGeometry, item: HomeItem, density: Density): Rect {
    val cell = geometry.itemBounds(item)
    val s = geometry.settings
    if (!item.largeFolder) {
        val side = with(density) { homeIconSize(geometry.cellW.toDp(), geometry.cellH.toDp(), s.iconScale, s.showHomeLabels).toPx() }
        val label = if (s.showHomeLabels) with(density) { 4.dp.toPx() + (16.sp * s.labelScale).toPx() } else 0f
        val top = cell.center.y - (side + label) / 2f
        return Rect(Offset(cell.center.x - side / 2f, top), Size(side, side))
    }
    val inset = with(density) { FOLDER_INSET_DP.dp.toPx() }
    val label = if (s.showHomeLabels && item.folderLabel) with(density) { 4.dp.toPx() + (16.sp * s.labelScale).toPx() } else 0f
    return Rect(
        cell.left + inset,
        cell.top + inset,
        cell.right - inset,
        cell.bottom - inset - label,
    )
}

/** Значок [index] в координатах окна: туда растёт окно приложения и куда оно возвращается. */
fun folderMiniRect(geometry: HomeGeometry, item: HomeItem, index: Int, density: Density): Rect {
    val tile = folderTile(geometry, item, density)
    val size = Size(tile.width, tile.height)
    val r = folderContents(item).grid.rect(size, index)
    return r.translate(tile.left, tile.top)
}

/**
 * Приложение под точкой [p] в большой папке: ключ и прямоугольник его плитки.
 * null — точка мимо крупных значков (плитка «ещё» и пустое место открывают саму папку).
 */
fun folderAppAt(geometry: HomeGeometry, item: HomeItem, p: Offset, density: Density): Pair<String, Rect>? {
    if (!item.largeFolder) return null
    val tile = folderTile(geometry, item, density)
    if (!tile.contains(p)) return null
    val size = Size(tile.width, tile.height)
    val contents = folderContents(item)
    val grid = contents.grid
    val inner = grid.inner(size)
    val cell = grid.cell(size)
    val local = p - tile.topLeft
    // Поле по краю папки — не «мимо значка»: там папка открывается, как и по плитке «ещё»
    if (local.x < inner.left || local.y < inner.top || local.x > inner.right || local.y > inner.bottom) return null
    val col = ((local.x - inner.left) / cell.width).toInt().coerceIn(0, grid.cols - 1)
    val row = ((local.y - inner.top) / cell.height).toInt().coerceIn(0, grid.rows - 1)
    val index = row * grid.cols + col
    // Точка в зазоре между клетками тоже считается попаданием: целиться в самый значок не нужно
    if (index >= contents.big) return null
    val key = item.children.getOrNull(index) ?: return null
    val rect = grid.rect(size, index).translate(tile.left, tile.top)
    return key to rect
}
