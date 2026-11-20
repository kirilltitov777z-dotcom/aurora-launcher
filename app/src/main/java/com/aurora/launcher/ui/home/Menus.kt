@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.aurora.launcher.ui.home

import com.aurora.launcher.ui.theme.accentColors
import com.aurora.launcher.ui.theme.LauncherThemes
import com.aurora.launcher.ui.theme.SeedColors
import com.aurora.launcher.ui.theme.rememberThemeSwitch
import com.aurora.launcher.data.ColorSource
import android.content.pm.ShortcutInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import com.aurora.launcher.ui.components.staggerIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.aurora.launcher.LauncherViewModel
import com.aurora.launcher.R
import com.aurora.launcher.data.AppInfo
import com.aurora.launcher.data.HomeItem
import com.aurora.launcher.data.ItemType
import com.aurora.launcher.data.ShortcutEntry
import com.aurora.launcher.ui.LocalHaptics
import com.aurora.launcher.ui.LocalFx
import com.aurora.launcher.ui.LocalRepo
import com.aurora.launcher.ui.LocalSettings
import com.aurora.launcher.ui.components.HapticGroup
import com.aurora.launcher.ui.components.HapticKind
import com.aurora.launcher.ui.components.GlassPanel
import com.aurora.launcher.ui.components.ThemedSurface
import com.aurora.launcher.ui.components.accentGlow
import kotlin.math.roundToInt

/** Размещает содержимое рядом с [anchor] (координаты окна): под ним, а если не помещается — над ним. */
@Composable
fun AnchoredBox(anchor: Rect, content: @Composable () -> Unit) {
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.first().measure(
            Constraints(maxWidth = constraints.maxWidth - 2 * margin, maxHeight = constraints.maxHeight - 2 * margin)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (anchor.center.x - placeable.width / 2f).roundToInt()
                .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val below = anchor.bottom + gap + placeable.height <= constraints.maxHeight - margin * 4
            val y = (if (below) anchor.bottom + gap else anchor.top - gap - placeable.height).roundToInt().coerceAtLeast(margin)
            placeable.place(x, y)
        }
    }
}

/** Меню вырастает из элемента, к которому привязано, и сворачивается обратно; [target] == null — закрыто. */
@Composable
fun <T : Any> AnimatedAnchoredMenu(target: T?, anchor: (T) -> Rect, screen: Size, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf<T?>(null) }
    if (target != null) last = target
    val shown = last ?: return
    val a = anchor(shown)
    val origin = TransformOrigin((a.center.x / screen.width).coerceIn(0f, 1f), (a.center.y / screen.height).coerceIn(0f, 1f))
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = target != null,
        // Раскрывается из значка с лёгкой пружиной; закрывается коротко и без отскока — к нему уже не вернутся
        enter = scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f, transformOrigin = origin) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(androidx.compose.animation.core.tween(160, easing = androidx.compose.animation.core.FastOutLinearInEasing), targetScale = 0.86f, transformOrigin = origin) +
            fadeOut(androidx.compose.animation.core.tween(140)),
    ) {
        AnchoredBox(a) { content(shown) }
    }
}

/** Пункт меню. [custom] — произвольная строка вместо обычного пункта (например, изменение размера виджета). */
private class MenuEntry(
    val text: String = "",
    val icon: ImageVector? = null,
    val image: ImageBitmap? = null,
    val destructive: Boolean = false,
    val custom: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit = {},
)

/**
 * Меню Material 3 Expressive: пункты разбиты на группы, группы стоят отдельными контейнерами с зазором,
 * у крайних групп внешние углы крупнее. Пункты — обычные значки и подписи, без цветных подложек.
 */
@Composable
private fun ExpressiveMenu(groups: List<List<MenuEntry>>) {
    val filled = groups.filter { it.isNotEmpty() }
    val haptics = LocalHaptics.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 200.dp, max = 280.dp), verticalArrangement = Arrangement.spacedBy(MenuGroupGap)) {
        filled.forEachIndexed { gi, entries ->
            // Liquid Glass — стеклянные группы меню; в остальных темах сплошная поверхность по спецификации
            ThemedSurface(
                shape = menuGroupShape(gi, filled.size),
                color = cs.surfaceContainerLow,
                contentColor = cs.onSurface,
                shadowElevation = 3.dp,
                blurPanel = false,
                modifier = Modifier.fillMaxWidth().accentGlow(menuGroupShape(gi, filled.size)),
            ) {
                Column(Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
                    entries.forEachIndexed { i, e ->
                        if (e.custom != null) e.custom.invoke()
                        else {
                            val tint = if (e.destructive) cs.error else cs.onSurfaceVariant
                            DropdownMenuItem(
                                text = { Text(e.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { haptics.play(HapticKind.CLICK, HapticGroup.TOUCH); e.onClick() },
                                shape = menuItemShape(i, entries.size),
                                leadingIcon = {
                                    when {
                                        e.image != null -> Image(e.image, null, Modifier.size(24.dp).clip(CircleShape))
                                        e.icon != null -> Icon(e.icon, null, tint = tint)
                                    }
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = if (e.destructive) cs.error else cs.onSurface,
                                    leadingIconColor = tint,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Зазор между группами меню. */
private val MenuGroupGap = 4.dp

/** Группа меню: снаружи крупные скругления 24 dp, на стыках между группами — 10 dp. */
private fun menuGroupShape(index: Int, count: Int): RoundedCornerShape {
    val big = 24.dp; val small = 10.dp
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Пункт меню: подсветка нажатия повторяет форму группы, крайние пункты скруглены сильнее. */
private fun menuItemShape(index: Int, count: Int): RoundedCornerShape {
    val big = 20.dp; val small = 8.dp
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Ярлыки приложения (долгое нажатие): загружаются в фоне, до загрузки группы нет. */
@Composable
private fun shortcutEntries(app: AppInfo, onShortcut: (ShortcutInfo) -> Unit, onDismiss: () -> Unit): List<MenuEntry> {
    val repo = LocalRepo.current
    val shortcuts by produceState(emptyList<ShortcutEntry>(), app.key) { value = repo.shortcuts(app) }
    return shortcuts.map { s ->
        MenuEntry(s.label, icon = Icons.AutoMirrored.Rounded.OpenInNew, image = s.icon) { onShortcut(s.info); onDismiss() }
    }
}

/** Свой значок для одного приложения: сменить и вернуть обратно. */
@Composable
private fun iconOverrideEntries(app: AppInfo, onChangeIcon: (AppInfo) -> Unit, onDismiss: () -> Unit): List<MenuEntry> {
    val repo = LocalRepo.current
    return listOfNotNull(
        MenuEntry(stringResource(R.string.menu_customize), Icons.Rounded.Tune) { AppCustomize.app = app; onDismiss() },
        // «Сменить значок» и «Вернуть значок» — внутри «Настроить значок», в меню не дублируются
    )
}

/** Меню элемента на рабочем столе. */
@Composable
fun ItemMenu(
    item: HomeItem,
    app: AppInfo?,
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onLaunchShortcut: (ShortcutInfo) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    onSelect: (() -> Unit)? = null,
) {
    val repo = LocalRepo.current
    val shortcuts = if (item.type == ItemType.APP && app != null) shortcutEntries(app, onLaunchShortcut, onDismiss) else emptyList()
    val overrides = if (item.type == ItemType.APP && app != null) iconOverrideEntries(app, onChangeIcon, onDismiss) else emptyList()
    val main = buildList {
        when (item.type) {
            ItemType.APP -> if (app != null) {
                add(MenuEntry(stringResource(R.string.menu_app_info), Icons.Rounded.Info) { repo.openAppInfo(app, null); onDismiss() })
                if (!item.inDock) add(MenuEntry(stringResource(R.string.menu_to_dock), Icons.Rounded.Home) { vm.moveToDock(item.id); onDismiss() })
                addAll(overrides)
            }
            ItemType.FOLDER -> {
                add(MenuEntry(stringResource(R.string.menu_open), Icons.AutoMirrored.Rounded.OpenInNew) { onOpenFolder(item.id); onDismiss() })
                // Размер папки: обычная 1×1 или большая 2×2 с сеткой 3×3 в миниатюре (в доке — только обычная)
                if (!item.inDock) add(MenuEntry(
                    stringResource(if (item.largeFolder) R.string.folder_make_small else R.string.folder_make_large),
                    Icons.Rounded.Tune,
                ) { vm.setFolderSize(item.id, !item.largeFolder); onDismiss() })
            }
            ItemType.WIDGET -> {
                val w = stringResource(R.string.widget_width)
                val h = stringResource(R.string.widget_height)
                add(MenuEntry(custom = { ResizeRow(w, { vm.resizeWidget(item.id, -1, 0) }, { vm.resizeWidget(item.id, 1, 0) }) }))
                add(MenuEntry(custom = { ResizeRow(h, { vm.resizeWidget(item.id, 0, -1) }, { vm.resizeWidget(item.id, 0, 1) }) }))
            }
            ItemType.CLOCK -> Unit
        }
        if (onSelect != null && (item.type == ItemType.APP || item.type == ItemType.FOLDER)) {
            add(MenuEntry(stringResource(R.string.menu_select_more), Icons.Rounded.SelectAll) { onDismiss(); onSelect() })
        }
    }
    // Необратимые действия — отдельной группой в конце, цветом ошибки
    val danger = buildList {
        if (item.type == ItemType.APP && app != null && !app.isSystem) {
            add(MenuEntry(stringResource(R.string.menu_uninstall), Icons.Rounded.Delete, destructive = true) { repo.uninstall(app); onDismiss() })
        }
        add(MenuEntry(stringResource(R.string.menu_remove), Icons.Rounded.Close, destructive = true) { vm.removeItem(item.id); onDismiss() })
    }
    ExpressiveMenu(listOf(shortcuts, main, danger))
}

@Composable
private fun ResizeRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        FilledTonalIconButton(onClick = onMinus, shapes = IconButtonDefaults.shapes(), modifier = with(com.aurora.launcher.ui.components.GlassControls) { Modifier.control() }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = com.aurora.launcher.ui.components.glassContainer(MaterialTheme.colorScheme.secondaryContainer))) { Icon(Icons.Rounded.Remove, null) }
        Spacer(Modifier.size(4.dp))
        FilledTonalIconButton(onClick = onPlus, shapes = IconButtonDefaults.shapes(), modifier = with(com.aurora.launcher.ui.components.GlassControls) { Modifier.control() }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = com.aurora.launcher.ui.components.glassContainer(MaterialTheme.colorScheme.secondaryContainer))) { Icon(Icons.Rounded.Add, null) }
    }
}

/** Меню приложения в шторке. */
@Composable
fun DrawerAppMenu(
    app: AppInfo,
    alreadyOnHome: Boolean,
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
    onShortcut: (ShortcutInfo) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    onSelect: () -> Unit,
) {
    val repo = LocalRepo.current
    val shortcuts = shortcutEntries(app, onShortcut, onDismiss)
    val overrides = iconOverrideEntries(app, onChangeIcon, onDismiss)
    val main = buildList {
        if (!alreadyOnHome) add(MenuEntry(stringResource(R.string.menu_add_home), Icons.Rounded.Home) { vm.addApp(app); onAdded() })
        add(MenuEntry(stringResource(R.string.menu_select_more), Icons.Rounded.SelectAll) { onDismiss(); onSelect() })
        add(MenuEntry(stringResource(R.string.menu_hide), Icons.Rounded.VisibilityOff) {
            vm.updateSettings { it.copy(hiddenApps = it.hiddenApps + app.key) }; onDismiss()
        })
        addAll(overrides)
        add(MenuEntry(stringResource(R.string.menu_app_info), Icons.Rounded.Info) { repo.openAppInfo(app, null); onDismiss() })
    }
    val danger = if (!app.isSystem) {
        listOf(MenuEntry(stringResource(R.string.menu_uninstall), Icons.Rounded.Delete, destructive = true) { repo.uninstall(app); onDismiss() })
    } else emptyList()
    ExpressiveMenu(listOf(shortcuts, main, danger))
}

/** Сколько после открытия меню касание фона ещё не закрывает его. */
const val MENU_GUARD_MS = 350L

/**
 * Новое меню стола: три вкладки в одной панели — «Главное», «Оформление», «Действия».
 * Панель одинаково выглядит во всех темах и даёт быстрый доступ к настройкам без ухода в настройки.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedVisibilityScope.HomeMenu(
    vm: LauncherViewModel,
    actions: HomeActions,
    onDismiss: () -> Unit,
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    onThemes: () -> Unit,
    onSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val spec = com.aurora.launcher.ui.theme.LocalThemeSpec.current
    val openedAt = remember { android.os.SystemClock.uptimeMillis() }
    Box(
        Modifier.fillMaxSize().background(cs.scrim.copy(alpha = if (spec.accentChrome) 0.45f else 0.32f)).clickable(interactionSource = null, indication = null) {
            if (android.os.SystemClock.uptimeMillis() - openedAt > MENU_GUARD_MS) onDismiss()
        },
    )
    val slide = Modifier.animateEnterExit(
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
    )
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.BottomCenter) {
        TabbedHomeMenu(
            vm, actions, onWidgets, onWallpaper, onThemes, onSettings,
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp).widthIn(max = 520.dp).fillMaxWidth().then(slide),
        )
    }
}

@Composable
private fun TabbedHomeMenu(
    vm: LauncherViewModel,
    actions: HomeActions,
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    onThemes: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val spec = com.aurora.launcher.ui.theme.LocalThemeSpec.current
    val s by vm.settings.collectAsState()
    val switch = rememberThemeSwitch()
    var tab by remember { mutableStateOf(0) }
    val shape = RoundedCornerShape(32.dp)
    val panel = if (spec.accentChrome) accentColors().panel.copy(alpha = 0.97f) else cs.surfaceContainer
    ThemedSurface(
        shape = shape,
        color = panel,
        contentColor = cs.onSurface,
        shadowElevation = 3.dp,
        modifier = modifier.accentGlow(shape),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.home_menu_title), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Spacer(Modifier.size(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val labels = listOf(R.string.menu_tab_main, R.string.menu_tab_style, R.string.menu_tab_actions)
                labels.forEachIndexed { i, label ->
                    FilterChip(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = {
                            Text(
                                stringResource(label), style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "homeMenuTab") { t ->
                when (t) {
                    0 -> MenuMainTab(spec.glass, onWidgets, onWallpaper, onThemes, onSettings)
                    1 -> MenuStyleTab(vm, switch, s, onThemes)
                    2 -> MenuActionsTab(actions, onWidgets, onWallpaper, onThemes, onSettings)
                }
            }
        }
    }
}

/** Первая вкладка: четыре крупных действия, которые были в старом меню. */
@Composable
private fun MenuMainTab(
    glass: Boolean,
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    onThemes: () -> Unit,
    onSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HomeMenuTile(Icons.Rounded.Widgets, stringResource(R.string.home_widgets), cs.primary, cs.onPrimary,
            rest = 28.dp, pressed = 16.dp, emphasized = !glass, onClick = onWidgets,
            modifier = Modifier.weight(1f).fillMaxHeight().staggerIn(0))
        HomeMenuTile(Icons.Rounded.Wallpaper, stringResource(R.string.home_wallpaper), cs.secondaryContainer, cs.onSecondaryContainer,
            rest = 28.dp, pressed = 16.dp, emphasized = false, onClick = onWallpaper,
            modifier = Modifier.weight(1f).fillMaxHeight().staggerIn(1))
        HomeMenuTile(Icons.Rounded.Palette, stringResource(R.string.home_themes), cs.primaryContainer, cs.onPrimaryContainer,
            rest = 28.dp, pressed = 16.dp, emphasized = false, onClick = onThemes,
            modifier = Modifier.weight(1f).fillMaxHeight().staggerIn(2))
        HomeMenuTile(Icons.Rounded.Settings, stringResource(R.string.home_settings), cs.tertiaryContainer, cs.onTertiaryContainer,
            rest = 28.dp, pressed = 16.dp, emphasized = false, onClick = onSettings,
            modifier = Modifier.weight(1f).fillMaxHeight().staggerIn(3))
    }
}

/** Вторая вкладка: переключение темы, акцента и двух видимых переключателей прямо на столе. */
@Composable
private fun MenuStyleTab(
    vm: LauncherViewModel,
    switch: (apply: () -> Unit) -> Unit,
    s: com.aurora.launcher.data.Settings,
    onThemes: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.home_themes), style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (spec in LauncherThemes.all) {
                FilterChip(
                    selected = s.style == spec.style,
                    onClick = { switch { vm.updateSettings { it.copy(style = spec.style) } } },
                    label = { Text(stringResource(spec.title), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(spec.icon, null, Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(stringResource(R.string.menu_quick_accent), style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in SeedColors.take(10)) {
                val selected = s.seedColor == c && s.colorSource == ColorSource.CUSTOM
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                        .border(if (selected) 2.dp else 0.dp, if (selected) cs.onSurface else Color.Transparent, CircleShape)
                        .clickable { switch { vm.updateSettings { it.copy(seedColor = c, colorSource = ColorSource.CUSTOM) } } },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = s.themedIcons,
                onClick = { vm.updateSettings { it.copy(themedIcons = !s.themedIcons) } },
                label = { Text(stringResource(R.string.menu_theme_icons), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = s.showSearchPill,
                onClick = { vm.updateSettings { it.copy(showSearchPill = !s.showSearchPill) } },
                label = { Text(stringResource(R.string.menu_search_bar), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Третья вкладка: системные и лаунчерные действия без перехода в настройки. */
@Composable
private fun MenuActionsTab(
    actions: HomeActions,
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    onThemes: () -> Unit,
    onSettings: () -> Unit,
) {
    val items = listOf(
        Triple(Icons.Rounded.Lock, R.string.menu_action_lock, { actions.lockScreen() }),
        Triple(Icons.Rounded.Notifications, R.string.menu_action_notifications, { actions.expandNotifications() }),
        Triple(Icons.Rounded.Widgets, R.string.home_widgets, onWidgets),
        Triple(Icons.Rounded.Wallpaper, R.string.home_wallpaper, onWallpaper),
        Triple(Icons.Rounded.Palette, R.string.home_themes, onThemes),
        Triple(Icons.Rounded.Settings, R.string.home_settings, onSettings),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in items.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((icon, label, click) in row) {
                    MenuActionButton(icon, stringResource(label), click, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MenuActionButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    Surface(
        onClick = { haptics.play(HapticKind.CLICK, HapticGroup.TOUCH); onClick() },
        shape = RoundedCornerShape(18.dp),
        color = cs.secondaryContainer,
        contentColor = cs.onSecondaryContainer,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AccentMenuButton(icon: ImageVector, text: String, index: Int, count: Int, onClick: () -> Unit, modifier: Modifier) {
    val ac = accentColors()
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val haptics = LocalHaptics.current
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val outer = 28.dp; val inner = 8.dp; val pressedCorner = 36.dp
    val top by animateDpAsState(if (isPressed) pressedCorner else if (index == 0) outer else inner, spatial, label = "top")
    val bottom by animateDpAsState(if (isPressed) pressedCorner else if (index == count - 1) outer else inner, spatial, label = "bottom")
    Surface(
        onClick = { haptics.play(HapticKind.CLICK, HapticGroup.TOUCH); onClick() },
        shape = RoundedCornerShape(
            // Пружина может «перелететь» за ноль: отрицательные углы Compose не принимает, и это падение
            topStart = top.coerceAtLeast(0.dp), topEnd = top.coerceAtLeast(0.dp),
            bottomStart = bottom.coerceAtLeast(0.dp), bottomEnd = bottom.coerceAtLeast(0.dp),
        ),
        color = ac.field,
        contentColor = ac.onField,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().height(72.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Плитка меню: при нажатии углы сжимаются (морфинг формы Expressive). */
@Composable
private fun HomeMenuTile(
    icon: ImageVector, text: String,
    container: Color, onContainer: Color,
    rest: androidx.compose.ui.unit.Dp, pressed: androidx.compose.ui.unit.Dp, emphasized: Boolean,
    onClick: () -> Unit, modifier: Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val haptics = LocalHaptics.current
    val corner by animateDpAsState(if (isPressed) pressed else rest, MaterialTheme.motionScheme.fastSpatialSpec(), label = "tileCorner")
    val glass = com.aurora.launcher.ui.theme.LocalThemeSpec.current.glass
    // В Liquid Glass плитки — стекло поверх стеклянной панели меню: обои не подкладываются второй раз
    ThemedSurface(
        onClick = { haptics.play(HapticKind.CLICK, HapticGroup.TOUCH); onClick() },
        shape = RoundedCornerShape(corner.coerceAtLeast(0.dp)),
        color = container,
        contentColor = if (glass) MaterialTheme.colorScheme.onSurface else onContainer,
        interactionSource = source,
        overBlur = true,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 20.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(if (emphasized) 32.dp else 24.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text,
                style = if (emphasized) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
