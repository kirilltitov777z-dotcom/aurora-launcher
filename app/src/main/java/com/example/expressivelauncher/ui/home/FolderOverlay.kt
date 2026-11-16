@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.expressivelauncher.ui.home

import com.example.expressivelauncher.ui.components.accentGlow
import com.example.expressivelauncher.data.displayLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.expressivelauncher.LauncherViewModel
import com.example.expressivelauncher.R
import com.example.expressivelauncher.data.AppInfo
import com.example.expressivelauncher.data.HomeItem
import com.example.expressivelauncher.ui.LocalSettings
import com.example.expressivelauncher.ui.LocalFx
import com.example.expressivelauncher.ui.components.AppIconImage
import com.example.expressivelauncher.ui.components.AppLabel
import com.example.expressivelauncher.ui.components.bouncyClickable
import com.example.expressivelauncher.ui.components.staggerIn
import com.example.expressivelauncher.ui.theme.LocalThemeSpec
import com.example.expressivelauncher.ui.theme.accentColors
import kotlin.math.min

/**
 * Папка «вытекает» из своей плитки ([source] в координатах окна) и втягивается в неё при закрытии:
 * форма тянется от плитки к панели и обратно, содержимое проявляется по ходу, чтобы не растягиваться.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FolderOverlay(
    folder: HomeItem,
    appMap: Map<String, AppInfo>,
    vm: LauncherViewModel,
    source: Rect,
    open: Boolean,
    onClosed: () -> Unit,
    onDismiss: () -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
) {
    val settings = LocalSettings.current
    val spec = LocalThemeSpec.current
    val cs = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Expressive: папка — насыщенная панель акцента, как док и меню стола; у стекла и Lite свой цвет
    val accent = if (spec.accentChrome) accentColors() else null
    val fg = accent?.onPanel ?: cs.onSurface
    val apps = folder.children.mapNotNull(appMap::get)
    val default = stringResource(R.string.folder_default_name)
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    var showSettings by remember(folder.id) { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val blur = settings.blurBackground && LocalFx.current.blur
    // Lite: никаких полупрозрачных слоёв поверх обоев — папка просто сплошная панель
    val lite = spec.lite
    val large = folder.largeFolder
    // Большая папка открывается шире и показывает больше значков в строке
    val gridCols = folder.folderColumns.takeIf { it in 3..5 } ?: 3
    // Крупные значки: чем меньше столбцов, тем крупнее плитка приложения в открытой папке
    val iconBase = when (gridCols) { 3 -> 76f; 4 -> 66f; else -> 58f }
    val iconSize = (iconBase * settings.iconScale).dp
    val iconSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { iconSize.toPx() }
    val iconTopPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
    // Lite — единое умеренное скругление всех панелей; Expressive и стекло — крупные «таблетки»
    val panelShape = androidx.compose.foundation.shape.RoundedCornerShape(spec.uniformCorner ?: 44.dp)

    // Прогресс формы: 0 — папка совпадает со своей плиткой, 1 — раскрыта
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(open) {
        progress.animateTo(
            if (open) 1f else 0f,
            if (open) androidx.compose.animation.core.tween(
                340, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0.15f, 1f),
            ) else androidx.compose.animation.core.tween(
                380, easing = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.6f, 1f),
            ),
        )
        // Форма втянулась в плитку — только теперь окно можно убирать из композиции
        if (!open) onClosed()
    }
    // Куда панель встанет при раскрытии — измеряем, раскладка от анимации не зависит
    var target by remember { mutableStateOf<Rect?>(null) }
    val panelColor = accent?.panel ?: cs.surfaceContainerHigh
    val targetRadius = with(density) { (spec.uniformCorner ?: 44.dp).toPx() }
    // У плитки своё, более круглое скругление; по ходу роста форма переходит к скруглению панели
    val sourceRadius = min(source.width, source.height) * 0.3f
    val scrim = if (lite) 0f else if (blur) 0.18f else 0.28f
    // У темы со сплошной плиткой и панелью из-под неё можно «вытягивать» форму: цвета совпадают
    val solidTile = spec.accentChrome || spec.lite

    Box(Modifier.fillMaxSize()) {
        // Затемнение и «жидкая» форма — только рисование: содержимое папки не рекомпонуется каждый кадр
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        ) {
            val p = progress.value
            val dim = smoothStep(0.15f, 0.8f, p)
            if (scrim > 0f && dim > 0f) drawRect(Color.Black.copy(alpha = scrim * dim))
            val to = target
            // Форма тянется от прямоугольника плитки к прямоугольнику панели: растёт из папки, а не из центра экрана
            val rect = if (to != null && to.width > 0f && to.height > 0f) morphRect(source, to, p) else source
            // У самой плитки форма гаснет: её место занимает настоящая плитка папки, стык незаметен
            val blob = smoothStep(0f, 0.04f, p) * (1f - smoothStep(0.92f, 1f, p))
            if (blob > 0.001f) {
                drawRoundRect(
                    color = panelColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        (sourceRadius + (targetRadius - sourceRadius) * p.coerceIn(0f, 1f)).coerceAtLeast(0f),
                    ),
                    alpha = blob,
                )
                // Сама плитка папки остаётся на месте: панель вытекает из-под неё, как в референсе.
                // Только там, где плитка и панель одного цвета: у стеклянной плитки подложка другая
                if (solidTile) {
                    drawRoundRect(
                        color = panelColor,
                        topLeft = source.topLeft,
                        size = source.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(sourceRadius),
                        alpha = blob,
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            com.example.expressivelauncher.ui.components.GlassPanel(
                shape = panelShape,
                color = accent?.panel ?: cs.surfaceContainerHigh,
                glassAlpha = when {
                    // Папка в Expressive — матовое стекло на размытом столе: видно, что под ней размытие,
                    // а не сплошная тональная панель
                    spec.accentChrome -> 0.8f
                    else -> 0.68f
                },
                solidAlpha = 0.92f,
                contentColor = fg,
                // Стол под папкой уже размыт одним слоем: сквозь стекло видно его, обои не подкладываем
                overBlur = blur,
                modifier = Modifier.padding(20.dp).widthIn(max = if (large) 640.dp else 480.dp).fillMaxWidth()
                    .onGloballyPositioned { target = it.boundsInParent() }
                    // Содержимое не растягивается: оно проявляется, когда форма уже почти на месте
                    .graphicsLayer { alpha = smoothStep(0.65f, 0.95f, progress.value) }
                    .accentGlow(panelShape)
                    .clickable(interactionSource = null, indication = null) { },
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it.take(24); vm.renameFolder(folder.id, title) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmallEmphasized.copy(
                                color = fg, textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(cs.primary),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (title.isEmpty()) {
                                        Text(
                                            default, style = MaterialTheme.typography.headlineSmallEmphasized,
                                            color = fg.copy(alpha = 0.5f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        IconButton(onClick = { showSettings = !showSettings }) {
                            Icon(
                                Icons.Rounded.Tune, stringResource(R.string.folder_settings),
                                tint = fg.copy(alpha = if (showSettings) 1f else 0.75f),
                            )
                        }
                    }
                    // Настройки папки: размер (1×1 / 2×2), число значков в строке и название на столе
                    AnimatedVisibility(showSettings) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.folder_large), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge, color = fg,
                                )
                                Switch(
                                    checked = large,
                                    onCheckedChange = { vm.setFolderSize(folder.id, it) },
                                    enabled = !folder.inDock,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.folder_columns), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge, color = fg,
                                )
                                for (n in 3..5) {
                                    FilterChip(
                                        selected = gridCols == n,
                                        onClick = { vm.setFolderColumns(folder.id, n) },
                                        label = { Text("$n") },
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.folder_show_name), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge, color = fg,
                                )
                                Switch(
                                    checked = folder.folderLabel,
                                    onCheckedChange = { vm.setFolderLabel(folder.id, it) },
                                )
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridCols),
                        modifier = Modifier.heightIn(max = if (large) 600.dp else 480.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(apps, key = { _, a -> a.key }) { i, app ->
                            var menu by remember { mutableStateOf(false) }
                            Column(
                                Modifier
                                    .animateItem()
                                    .staggerIn(i, stepMs = 30L)
                                    .bouncyClickable({ r -> onLaunch(app, Rect(r.center.x - iconSizePx / 2f, r.top + iconTopPx, r.center.x + iconSizePx / 2f, r.top + iconTopPx + iconSizePx)) }, { menu = true })
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIconImage(app, iconSize)
                                AppLabel(app.displayLabel, Modifier.padding(top = 4.dp), color = fg)
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_take_out)) },
                                        onClick = { menu = false; vm.removeFromFolder(folder.id, app.key) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_app_info)) },
                                        onClick = { menu = false; onAppInfo(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Плавный шаг 0..1 для [x] в промежутке [from]..[to]. */
private fun smoothStep(from: Float, to: Float, x: Float): Float {
    val k = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

/**
 * Прямоугольник на пути от плитки [from] к панели [to]. [p] — прогресс пружины: он перелетает за единицу,
 * и панель на мгновение выходит чуть больше своего места — это и даёт «жидкий» отскок.
 */
private fun morphRect(from: Rect, to: Rect, p: Float): Rect {
    val k = p.coerceIn(-0.2f, 1.15f)
    fun l(a: Float, b: Float) = a + (b - a) * k
    return Rect(l(from.left, to.left), l(from.top, to.top), l(from.right, to.right), l(from.bottom, to.bottom))
}
