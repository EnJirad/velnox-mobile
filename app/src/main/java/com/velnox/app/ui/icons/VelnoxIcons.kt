package com.velnox.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Velnox icon set.
 *
 * Stroke geometry is taken from Lucide (ISC License, https://lucide.dev),
 * each glyph re-authored with the 24dp viewport, 2dp stroke, round caps and
 * round joins that make Lucide recognisable. Rendering them as ImageVector
 * keeps the app free of an icon-font dependency and keeps every glyph tintable
 * from the design tokens.
 *
 * Rule from the design system: never use emoji for iconography.
 */
object VelnoxIcons {

    // ---------------------------------------------------------------- helpers

    private fun stroke(
        name: String,
        pathData: List<String>,
        strokeWidth: Float = 2f,
        fill: Boolean = false,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                fill = if (fill) SolidColor(Color.Black) else null,
                stroke = if (fill) null else SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    private fun filled(name: String, pathData: List<String>): ImageVector =
        stroke(name = name, pathData = pathData, fill = true)

    // ------------------------------------------------------------ navigation

    val Search: ImageVector by lazy {
        stroke(
            "lucide-search",
            listOf(
                "M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
                "m21 21l-4.3-4.3",
            ),
        )
    }

    val Home: ImageVector by lazy {
        stroke(
            "lucide-house",
            listOf(
                "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
                "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            ),
        )
    }

    val ShoppingBag: ImageVector by lazy {
        stroke(
            "lucide-shopping-bag",
            listOf(
                "M6 6h15l-1.967 12.047A2 2 0 0 1 17.055 20H8.945a2 2 0 0 1-1.978-1.953L5 3H2",
            ),
        )
    }

    val Heart: ImageVector by lazy {
        stroke(
            "lucide-heart",
            listOf(
                "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
            ),
        )
    }

    val HeartFill: ImageVector by lazy {
        filled(
            "lucide-heart-fill",
            listOf(
                "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
            ),
        )
    }

    val User: ImageVector by lazy {
        stroke(
            "lucide-user",
            listOf(
                "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
                "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
            ),
        )
    }

    val Grid: ImageVector by lazy {
        stroke(
            "lucide-layout-grid",
            listOf(
                "M3 3h7v7h-7z",
                "M14 3h7v7h-7z",
                "M14 14h7v7h-7z",
                "M3 14h7v7h-7z",
            ),
        )
    }

    val ShoppingCart: ImageVector by lazy {
        stroke(
            "lucide-shopping-cart",
            listOf(
                "M6 6h15l-1.967 12.047A2 2 0 0 1 17.055 20H8.945a2 2 0 0 1-1.978-1.953L5 3H2",
                "M9 9h.01",
                "M18 9h.01",
            ),
        )
    }

    // ------------------------------------------------------------ home pieces

    val Bell: ImageVector by lazy {
        stroke(
            "lucide-bell",
            listOf(
                "M10.268 21a2 2 0 0 0 3.464 0",
                "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
            ),
        )
    }

    val Sliders: ImageVector by lazy {
        stroke(
            "lucide-sliders-horizontal",
            listOf(
                "M10 5H3",
                "M12 19H3",
                "M14 3v4",
                "M16 17v4",
                "M21 12h-9",
                "M21 19h-5",
                "M21 5h-7",
                "M8 10v4",
                "M8 12H3",
            ),
        )
    }

    val ChevronRight: ImageVector by lazy {
        stroke("lucide-chevron-right", listOf("m9 18l6-6l-6-6"))
    }

    val ChevronLeft: ImageVector by lazy {
        stroke("lucide-chevron-left", listOf("m15 18l-6-6l6-6"))
    }

    val ArrowRight: ImageVector by lazy {
        stroke(
            "lucide-arrow-right",
            listOf("M5 12h14", "m12 5l7 7l-7 7"),
        )
    }

    val Maximize: ImageVector by lazy {
        stroke(
            "lucide-maximize",
            listOf(
                "M8 3H5a2 2 0 0 0-2 2v3",
                "M21 8V5a2 2 0 0 0-2-2h-3",
                "M3 16v3a2 2 0 0 0 2 2h3",
                "M16 21h3a2 2 0 0 0 2-2v-3",
            ),
        )
    }

    val Star: ImageVector by lazy {
        filled(
            "lucide-star",
            listOf(
                "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
            ),
        )
    }

    val Truck: ImageVector by lazy {
        stroke(
            "lucide-truck",
            listOf(
                "M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2",
                "M15 18H9",
                "M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14",
                "M17 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
                "M7 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            ),
        )
    }

    val ShieldCheck: ImageVector by lazy {
        stroke(
            "lucide-shield-check",
            listOf(
                "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
                "m9 12l2 2l4-4",
            ),
        )
    }

    val EyeOff: ImageVector by lazy {
        stroke(
            "lucide-eye-off",
            listOf(
                "M10.73 5.08a10.74 10.74 0 0 1 11.2 6.57 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-1.44 2.49",
                "M14.08 14.16a3 3 0 0 1-4.24-4.24",
                "M17.48 17.5a10.75 10.75 0 0 1-15.42-5.15 1 1 0 0 1 0-.7 10.75 10.75 0 0 1 4.45-5.14",
                "m2 2l20 20",
            ),
        )
    }

    val Wallet: ImageVector by lazy {
        stroke(
            "lucide-wallet",
            listOf(
                "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
                "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
            ),
        )
    }

    val Sparkles: ImageVector by lazy {
        stroke(
            "lucide-sparkles",
            listOf(
                "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z",
                "M20 3v4",
                "M22 5h-4",
                "M4 17v2",
                "M5 18H3",
            ),
        )
    }

    val Tag: ImageVector by lazy {
        stroke(
            "lucide-tag",
            listOf(
                "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
                "M7.5 7.5h.01",
            ),
        )
    }

    val Headphones: ImageVector by lazy {
        stroke(
            "lucide-headphones",
            listOf(
                "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3",
            ),
        )
    }

    val Shirt: ImageVector by lazy {
        stroke(
            "lucide-shirt",
            listOf(
                "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
                "M9 3h6l-1 4h-4z",
            ),
        )
    }

    val Laptop: ImageVector by lazy {
        stroke(
            "lucide-monitor-smartphone",
            listOf(
                "M18 8V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h8",
                "M10 19v-3.96 3.15",
                "M7 19h5",
                "M16 12h2a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2",
            ),
        )
    }

    val Coffee: ImageVector by lazy {
        stroke(
            "lucide-coffee",
            listOf(
                "M10 2v2",
                "M14 2v2",
                "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1",
                "M6 2v2",
            ),
        )
    }

    val Clock: ImageVector by lazy {
        stroke(
            "lucide-clock",
            listOf(
                "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2z",
                "M12 6v6l4 2",
            ),
        )
    }

    // ------------------------------------------------------ phase 2 additions

    val MessageCircle: ImageVector by lazy {
        stroke(
            "lucide-message-circle",
            listOf("M7.9 20A9 9 0 1 0 4 16.1L2 22z"),
        )
    }

    val Package: ImageVector by lazy {
        stroke(
            "lucide-package",
            listOf(
                "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z",
                "M3.3 7 12 12l8.7-5",
                "M12 22V12",
            ),
        )
    }

    val Cog: ImageVector by lazy {
        stroke(
            "lucide-settings",
            listOf(
                "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
                "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
            ),
        )
    }

    val MapPin: ImageVector by lazy {
        stroke(
            "lucide-map-pin",
            listOf(
                "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
                "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            ),
        )
    }

    val Globe: ImageVector by lazy {
        stroke(
            "lucide-globe",
            listOf(
                "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
                "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z",
                "M2 12h20",
            ),
        )
    }

    val Moon: ImageVector by lazy {
        stroke("lucide-moon", listOf("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z"))
    }

    val Lock: ImageVector by lazy {
        stroke(
            "lucide-lock",
            listOf(
                "M5 11h14v10H5z",
                "M8 11V7a4 4 0 0 1 8 0v4",
            ),
        )
    }

    val HelpCircle: ImageVector by lazy {
        stroke(
            "lucide-circle-help",
            listOf(
                "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z",
                "M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3",
                "M12 17h.01",
            ),
        )
    }

    val FileText: ImageVector by lazy {
        stroke(
            "lucide-file-text",
            listOf(
                "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7z",
                "M14 2v4a2 2 0 0 0 2 2h4",
                "M16 13H8",
                "M16 17H8",
            ),
        )
    }

    val Info: ImageVector by lazy {
        stroke(
            "lucide-info",
            listOf(
                "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z",
                "M12 16v-4",
                "M12 8h.01",
            ),
        )
    }

    val LogOut: ImageVector by lazy {
        stroke(
            "lucide-log-out",
            listOf(
                "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
                "M16 17l5-5l-5-5",
                "M21 12H9",
            ),
        )
    }

    val Plus: ImageVector by lazy {
        stroke("lucide-plus", listOf("M12 5v14", "M5 12h14"))
    }

    val Minus: ImageVector by lazy {
        stroke("lucide-minus", listOf("M5 12h14"))
    }

    val RotateCcw: ImageVector by lazy {
        stroke(
            "lucide-rotate-ccw",
            listOf(
                "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
                "M3 3v5h5",
            ),
        )
    }

    val Check: ImageVector by lazy {
        stroke("lucide-check", listOf("m20 6L9 17l-5-5"))
    }

    val Pencil: ImageVector by lazy {
        stroke(
            "lucide-pencil",
            listOf("M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z"),
        )
    }

    val CreditCard: ImageVector by lazy {
        stroke(
            "lucide-credit-card",
            listOf(
                "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
                "M2 10h20",
            ),
        )
    }

    val ChevronDown: ImageVector by lazy {
        stroke("lucide-chevron-down", listOf("m6 9l6 6l6-6"))
    }

    val ChevronUp: ImageVector by lazy {
        stroke("lucide-chevron-up", listOf("m18 15l-6-6l-6 6"))
    }

    val Close: ImageVector by lazy {
        stroke("lucide-x", listOf("M18 6L6 18", "M6 6l12 12"))
    }

    val Calendar: ImageVector by lazy {
        stroke(
            "lucide-calendar",
            listOf(
                "M8 2v4",
                "M16 2v4",
                "M3 6h18v15H3z",
                "M3 11h18",
            ),
        )
    }

    val Trash: ImageVector by lazy {
        stroke(
            "lucide-trash-2",
            listOf(
                "M3 6h18",
                "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
                "M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2",
                "M10 11v6",
                "M14 11v6",
            ),
        )
    }

    val ProductVariants: ImageVector by lazy {
        stroke(
            "lucide-product-variants",
            listOf(
                "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
                "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
                "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
                "M14 17.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0",
            ),
        )
    }

    val Camera: ImageVector by lazy {
        stroke(
            "lucide-camera",
            listOf(
                "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
                "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            ),
        )
    }

    val Send: ImageVector by lazy {
        stroke(
            "lucide-send",
            listOf(
                "M22 2L11 13",
                "M22 2l-7 20-4-9-9-4 20-7z",
            ),
        )
    }

    val Crosshair: ImageVector by lazy {
        stroke(
            "lucide-locate-fixed",
            listOf(
                "M2 12h3",
                "M19 12h3",
                "M12 2v3",
                "M12 19v3",
                "M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
                "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            ),
        )
    }

    val Briefcase: ImageVector by lazy {
        stroke(
            "lucide-briefcase",
            listOf(
                "M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16",
                "M2 8a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8z",
            ),
        )
    }

    val Image: ImageVector by lazy {
        stroke(
            "lucide-image",
            listOf(
                "M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5z",
                "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
                "m21 15l-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
            ),
        )
    }

    val Share2: ImageVector by lazy {
        stroke(
            "lucide-share-2",
            listOf(
                "M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
                "M6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
                "M18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
                "M8.59 13.51l6.83 3.98",
                "M15.41 6.51l-6.82 3.98",
            ),
        )
    }

    val Smile: ImageVector by lazy {
        stroke(
            "lucide-smile",
            listOf(
                "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2z",
                "M8 14s1.5 2 4 2 4-2 4-2",
                "M9 9h.01",
                "M15 9h.01",
            ),
        )
    }

    val Video: ImageVector by lazy {
        stroke(
            "lucide-video",
            listOf(
                "M16 16l4 2V6l-4 2z",
                "M2 6a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z",
            ),
        )
    }

    val Play: ImageVector by lazy {
        filled(
            "lucide-play",
            listOf(
                "M6 4l14 8-14 8z",
            ),
        )
    }

    val Paperclip: ImageVector by lazy {
        stroke(
            "lucide-paperclip",
            listOf(
                "M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48",
            ),
        )
    }
}
