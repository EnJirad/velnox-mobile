package com.velnox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// §7.1 Squircle radius tokens: the shape language of Velnox.
// xs 7 · sm 10 · default 14 · lg 18
val SquircleXs = RoundedCornerShape(7.dp)
val SquircleSm = RoundedCornerShape(10.dp)
val Squircle = RoundedCornerShape(14.dp)
val SquircleLg = RoundedCornerShape(18.dp)

// rounded-full is reserved for real circles: avatar, dot, spinner, progress, switch thumb.
val Circle = RoundedCornerShape(percent = 50)

val VelnoxShapes = Shapes(
    extraSmall = SquircleXs,
    small = SquircleSm,
    medium = Squircle,
    large = SquircleLg,
    extraLarge = SquircleLg,
)
