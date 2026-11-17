package com.jevon.personalos.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Lucide outline subset. ISC license bundled in res/raw/lucide_license.txt.
 * Source: github.com/lucide-icons/lucide/icons; 24-unit grid, 1.75-unit rounded strokes.
 * Native vectors only: no icon font, raster assets, or runtime library.
 */
internal object AppIcons {
    val Clock by lazy { outline("Clock", false,
        "M22 12A10 10 0 1 1 2 12A10 10 0 1 1 22 12",
        "M12 6v6l4 2",
    ) }
    val Today by lazy { outline("Today", false,
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    ) }
    val AccountBalanceWallet by lazy { outline("AccountBalanceWallet", false,
        "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
        "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
    ) }
    val CalendarMonth by lazy { outline("CalendarMonth", false,
        "M8 2v3",
        "M16 2v3",
        "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z",
        "M3 9h18",
        "M8 13h.01",
        "M12 13h.01",
        "M16 13h.01",
        "M8 17h.01",
        "M12 17h.01",
        "M16 17h.01",
    ) }
    val MenuBook by lazy { outline("MenuBook", true,
        "M12 5v16",
        // SVG permits packed arc flags; Compose's parser needs explicit flag separators.
        "M20.001 19A2 2 0 0 0 22 17V5a2 2 0 0 0 -1.999 -2L16 3.002A5 5 0 0 0 12 5a5 5 0 0 0 -4 -2H4a2 2 0 0 0 -2 2v12a2 2 0 0 0 1.999 2H8a5 5 0 0 1 4 2 5 5 0 0 1 4 -2z",
    ) }
    val Add by lazy { outline("Add", false,
        "M5 12h14",
        "M12 5v14",
    ) }
    val ArrowBack by lazy { outline("ArrowBack", true,
        "m12 19-7-7 7-7",
        "M19 12H5",
    ) }
    val Code by lazy { outline("Code", false,
        "m16 18 6-6-6-6",
        "m8 6-6 6 6 6",
    ) }
    val Settings by lazy { outline("Settings", false,
        "M14 17H5",
        "M19 7h-9",
        "M14 17A3 3 0 1 0 20 17A3 3 0 1 0 14 17Z",
        "M4 7A3 3 0 1 0 10 7A3 3 0 1 0 4 7Z",
    ) }
    val Autorenew by lazy { outline("Autorenew", false,
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    ) }
    val NorthEast by lazy { outline("NorthEast", false,
        "M7 7h10v10",
        "M7 17 17 7",
    ) }
    val SouthWest by lazy { outline("SouthWest", false,
        "M17 7 7 17",
        "M17 17H7V7",
    ) }
    val SwapHoriz by lazy { outline("SwapHoriz", false,
        "M8 3 4 7l4 4",
        "M4 7h16",
        "m16 21 4-4-4-4",
        "M20 17H4",
    ) }
    val ChevronLeft by lazy { outline("ChevronLeft", false,
        "m15 18-6-6 6-6",
    ) }
    val ChevronRight by lazy { outline("ChevronRight", false,
        "m9 18 6-6-6-6",
    ) }
    val ExpandMore by lazy { outline("ExpandMore", false,
        "m6 9 6 6 6-6",
    ) }
    val Label by lazy { outline("Label", false,
        "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
        "M7 7.5A0.5 0.5 0 1 0 8 7.5A0.5 0.5 0 1 0 7 7.5Z",
    ) }
    val Payments by lazy { outline("Payments", false,
        "M4 6H20A2 2 0 0 1 22 8V16A2 2 0 0 1 20 18H4A2 2 0 0 1 2 16V8A2 2 0 0 1 4 6Z",
        "M10 12A2 2 0 1 0 14 12A2 2 0 1 0 10 12Z",
        "M6 12h.01M18 12h.01",
    ) }
    val AccountBalance by lazy { outline("AccountBalance", false,
        "M10 18v-7",
        "M11.119 2.205a2 2 0 0 1 1.762 0l7.84 3.846A.5.5 0 0 1 20.5 7h-17a.5.5 0 0 1-.22-.949z",
        "M14 18v-7",
        "M18 18v-7",
        "M3 22h18",
        "M6 18v-7",
    ) }
    val TrendingUp by lazy { outline("TrendingUp", false,
        "M16 7h6v6",
        "m22 7-8.5 8.5-5-5L2 17",
    ) }
    val CheckCircle by lazy { outline("CheckCircle", false,
        "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12Z",
        "m16 9-5.5 5.5L8 12",
    ) }
    val RadioButtonUnchecked by lazy { outline("RadioButtonUnchecked", false,
        "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12Z",
    ) }
    val SportsEsports by lazy { outline("SportsEsports", false,
        "M6 11L10 11",
        "M8 9L8 13",
        "M15 12L15.01 12",
        "M18 10L18.01 10",
        "M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z",
    ) }
    val Movie by lazy { outline("Movie", false,
        "m12.296 3.464 3.02 3.956",
        "M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z",
        "M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        "m6.18 5.276 3.1 3.899",
    ) }

    private fun outline(name: String, mirror: Boolean, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
            paths.forEach { data -> addPath(PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
        }.build()
}
