package com.aurora.launcher.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aurora.launcher.R
import com.aurora.launcher.data.ColorSource
import com.aurora.launcher.data.LauncherStyle
import com.aurora.launcher.data.MotionStyle
import com.aurora.launcher.data.PageTransition
import com.aurora.launcher.data.Settings

/**
 * Описание темы лаунчера: всё, чем темы отличаются друг от друга. Экраны не проверяют, какая тема выбрана,
 * они читают токены отсюда ([LocalThemeSpec]) и итоговые настройки ([effective]).
 *
 * Линейка после переделки: Стекло, Импульс, Лайт, iOS, Nothing и «Своя тема».
 * Тёмная неоновая тема убрана: вместо неё — монохромная Nothing и конструктор своей темы.
 */
@Immutable
data class ThemeSpec(
    val style: LauncherStyle,
    val title: Int,
    /** Подпись в одну строку на карточке выбора. */
    val tagline: Int,
    val icon: ImageVector,
    /** Панели (док, папки, меню, поиск, настройки) — жидкое стекло: размытие, обводка и блик. */
    val glass: Boolean,
    /** Прозрачность светлой обводки по краю стекла; 0 — без обводки. */
    val outlineAlpha: Float,
    /** Сила блика-градиента сверху стекла; 0 — без блика. */
    val sheenAlpha: Float,
    /** Морфинг форм кнопок, карточек и значков при нажатии и выборе. */
    val morph: Boolean,
    /** Крупные выразительные заголовки (веса Emphasized). */
    val boldHeadlines: Boolean,
    /** Одинаковые скругления всех компонентов; null — шкала Expressive с множителем из настроек. */
    val uniformCorner: Dp?,
    /** Тени под панелями и меню. */
    val shadows: Boolean,
    /** Упрощённая графика: без размытия, без пружин, короткие fade/scale. */
    val lite: Boolean,
    /** Своя анимация «значок → окно» (LaunchPortal); иначе система раскрывает окно из значка. */
    val portalLaunch: Boolean,
    /** У темы есть выбор «динамические цвета из обоев / свой акцент». */
    val colorOptions: Boolean,
    /** Свои настройки темы на странице «Темы». */
    val options: List<ThemeOption>,
    /**
     * Док, строка поиска и меню стола — из фиксированных тонов акцента:
     * насыщенная панель с тональными элементами, одинаковая в светлой и тёмной теме.
     */
    val accentChrome: Boolean = false,
) {
    /**
     * Настройки, по которым рисуется интерфейс в этой теме. Выбор пользователя не теряется: тема лишь
     * перекрывает то, что противоречит её характеру, а при смене темы снова действуют его значения.
     * [animatorsOn] — системная настройка «Убрать анимации» выключена.
     */
    fun effective(s: Settings, animatorsOn: Boolean): Settings {
        val themed = when (style) {
            LauncherStyle.LIQUID_GLASS -> s.copy(
                iconShape = s.glassIconShape,
                themedIconColors = 0,
                themedIconFgColors = -1,
                blurBackground = true,
                blurStrength = s.blurStrength.coerceAtLeast(0.25f),
                dockBackground = true,
                dockOpacity = s.glassOpacity,
                drawerOpacity = (s.glassOpacity + 0.3f).coerceAtMost(0.9f),
                cornerScale = s.cornerScale * 1.3f,
                dockRadius = s.dockRadius.coerceAtLeast(32),
                motion = MotionStyle.EXPRESSIVE,
                animBounce = s.animBounce * 0.6f,
            )
            LauncherStyle.EXPRESSIVE -> s.copy(
                motion = MotionStyle.EXPRESSIVE,
                cornerScale = s.cornerScale.coerceAtLeast(1.15f),
            )
            LauncherStyle.LITE -> s.copy(
                iconShape = s.liteIconShape,
                blurBackground = false,
                motion = MotionStyle.STANDARD,
                animBounce = 0f,
                wakeWave = false,
                pageTransition = PageTransition.SLIDE,
                dockBackground = false,
                dockOpacity = 1f,
                dockRadius = 20,
                drawerOpacity = 1f,
                themedIconColors = 0,
                themedIconFgColors = -1,
            )
            // iOS: плотное матовое стекло, непрерывная маска значков, мягкие пружины, никакого свечения
            LauncherStyle.IOS -> s.copy(
                iconShape = s.iosIconShape,
                blurBackground = true,
                blurStrength = s.blurStrength.coerceAtLeast(0.5f),
                blurEverywhere = true,
                dockBackground = true,
                dockOpacity = 0.6f,
                dockRadius = 38,
                drawerOpacity = 0.88f,
                cornerScale = s.cornerScale * 1.35f,
                motion = MotionStyle.EXPRESSIVE,
                animBounce = s.animBounce * 0.85f,
                glowStrength = 0f,
                pageTransition = PageTransition.DEPTH,
            )
            // Nothing: чёткая монохромная графика, никакого размытия и пружин, короткие переходы
            LauncherStyle.NOTHING -> s.copy(
                iconShape = s.nothingIconShape,
                blurBackground = false,
                blurEverywhere = false,
                motion = MotionStyle.STANDARD,
                animBounce = 0f,
                wakeWave = false,
                pageTransition = PageTransition.FADE,
                dockBackground = true,
                dockOpacity = 1f,
                dockRadius = 12,
                drawerOpacity = 1f,
                glowStrength = 0f,
                themedIconColors = 0,
                themedIconFgColors = -1,
                colorSource = ColorSource.CUSTOM,
                seedColor = 0xFF9E9E9E.toInt(),
                paletteStyle = 7, // Monochrome
            )
            // Своя тема: все параметры задаёт пользователь на странице «Темы»
            LauncherStyle.CUSTOM -> s.copy(
                iconShape = s.customIconShape,
                blurBackground = s.customGlass,
                blurStrength = if (s.customGlass) s.blurStrength.coerceAtLeast(0.4f) else 0f,
                blurEverywhere = s.customGlass,
                dockBackground = s.customDock,
                dockOpacity = if (s.customDock) 0.78f else 1f,
                cornerScale = s.cornerScale * s.customCorner,
                motion = if (s.customExpressive) MotionStyle.EXPRESSIVE else MotionStyle.STANDARD,
                animBounce = if (s.customExpressive) s.animBounce else 0f,
                glowStrength = s.customGlow,
            )
        }
        return if (animatorsOn) themed else themed.copy(launchAnimation = false, wakeWave = false, pageTransition = PageTransition.SLIDE)
    }
}

/** Настройки, которые показываются под выбором темы на странице «Темы». */
enum class ThemeOption { BLUR_STRENGTH, GLASS_OPACITY, GLASS_ICONS, DYNAMIC_COLOR, ACCENT, ANIM_INTENSITY, GLOW, BLUR_EVERYWHERE }

object LauncherThemes {
    val LiquidGlass = ThemeSpec(
        style = LauncherStyle.LIQUID_GLASS,
        title = R.string.theme_glass,
        tagline = R.string.theme_glass_tagline,
        icon = Icons.Rounded.BlurOn,
        glass = true,
        outlineAlpha = 0.34f,
        sheenAlpha = 0.16f,
        morph = true,
        boldHeadlines = false,
        uniformCorner = null,
        shadows = false,
        lite = false,
        portalLaunch = true,
        colorOptions = false,
        options = listOf(ThemeOption.BLUR_STRENGTH, ThemeOption.GLASS_OPACITY, ThemeOption.GLASS_ICONS),
    )

    val Expressive = ThemeSpec(
        style = LauncherStyle.EXPRESSIVE,
        title = R.string.theme_expressive,
        tagline = R.string.theme_expressive_tagline,
        icon = Icons.Rounded.AutoAwesome,
        glass = false,
        outlineAlpha = 0f,
        sheenAlpha = 0f,
        morph = true,
        boldHeadlines = true,
        uniformCorner = null,
        shadows = true,
        lite = false,
        portalLaunch = true,
        colorOptions = true,
        options = listOf(ThemeOption.DYNAMIC_COLOR, ThemeOption.ACCENT, ThemeOption.GLOW, ThemeOption.BLUR_EVERYWHERE, ThemeOption.ANIM_INTENSITY),
        accentChrome = true,
    )

    val Lite = ThemeSpec(
        style = LauncherStyle.LITE,
        title = R.string.theme_lite,
        tagline = R.string.theme_lite_tagline,
        icon = Icons.Rounded.Bolt,
        glass = false,
        outlineAlpha = 0f,
        sheenAlpha = 0f,
        morph = false,
        boldHeadlines = false,
        uniformCorner = 16.dp,
        shadows = false,
        lite = true,
        portalLaunch = false,
        colorOptions = true,
        options = listOf(ThemeOption.DYNAMIC_COLOR, ThemeOption.ACCENT),
    )

    /** iOS:continuous-маска значков, матовый док, мягкие пружины, спокойная палитра. */
    val Ios = ThemeSpec(
        style = LauncherStyle.IOS,
        title = R.string.theme_ios,
        tagline = R.string.theme_ios_tagline,
        icon = Icons.Rounded.Dashboard,
        glass = true,
        outlineAlpha = 0.3f,
        sheenAlpha = 0.12f,
        morph = true,
        boldHeadlines = false,
        uniformCorner = null,
        shadows = false,
        lite = false,
        portalLaunch = true,
        colorOptions = true,
        options = listOf(ThemeOption.DYNAMIC_COLOR, ThemeOption.ACCENT, ThemeOption.BLUR_STRENGTH, ThemeOption.GLASS_OPACITY, ThemeOption.GLASS_ICONS),
    )

    /** Nothing: монохром, точные формы, без размытия и подсветки. */
    val Nothing = ThemeSpec(
        style = LauncherStyle.NOTHING,
        title = R.string.theme_nothing,
        tagline = R.string.theme_nothing_tagline,
        icon = Icons.Rounded.DarkMode,
        glass = false,
        outlineAlpha = 0f,
        sheenAlpha = 0f,
        morph = false,
        boldHeadlines = false,
        uniformCorner = 12.dp,
        shadows = false,
        lite = false,
        portalLaunch = false,
        colorOptions = false,
        options = emptyList(),
    )

    /** Своя тема: токены собираются из настроек пользователя. */
    val Custom = ThemeSpec(
        style = LauncherStyle.CUSTOM,
        title = R.string.theme_custom,
        tagline = R.string.theme_custom_tagline,
        icon = Icons.Rounded.Tune,
        glass = true,
        outlineAlpha = 0.28f,
        sheenAlpha = 0.14f,
        morph = true,
        boldHeadlines = false,
        uniformCorner = null,
        shadows = false,
        lite = false,
        portalLaunch = true,
        colorOptions = true,
        options = listOf(ThemeOption.DYNAMIC_COLOR, ThemeOption.ACCENT, ThemeOption.BLUR_STRENGTH, ThemeOption.GLOW, ThemeOption.ANIM_INTENSITY),
    )

    val all = listOf(LiquidGlass, Expressive, Lite, Ios, Nothing, Custom)

    fun of(style: LauncherStyle): ThemeSpec = all.firstOrNull { it.style == style } ?: Ios

    /**
     * Токены «Своей темы» зависят от настроек: скругления, стекло и движение берутся из конструктора.
     * Остальные темы — фиксированные объекты.
     */
    fun resolve(s: Settings): ThemeSpec = if (s.style != LauncherStyle.CUSTOM) of(s.style) else Custom.copy(
        glass = s.customGlass,
        morph = s.customExpressive,
        boldHeadlines = s.customExpressive,
        outlineAlpha = if (s.customGlass) 0.28f else 0f,
        sheenAlpha = if (s.customGlass) 0.14f else 0f,
        shadows = s.customGlow > 0.4f,
        options = buildList {
            add(ThemeOption.DYNAMIC_COLOR); add(ThemeOption.ACCENT)
            if (s.customGlass) add(ThemeOption.BLUR_STRENGTH)
            if (s.customGlow > 0f) add(ThemeOption.GLOW)
            if (s.customExpressive) add(ThemeOption.ANIM_INTENSITY)
        },
    )
}

val LocalThemeSpec = compositionLocalOf { LauncherThemes.Ios }
