package com.velnox.app.feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.velnox.app.R
import com.velnox.app.ui.components.IconActionButton
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

private const val DEFAULT_LAT = 13.7563
private const val DEFAULT_LNG = 100.5018
private const val GPS_FALLBACK_LAT = 13.7466
private const val GPS_FALLBACK_LNG = 100.5347

/**
 * Interactive Map Pin Picker modal with a pan-able procedural street & rooftop
 * canvas, a center-locked pin marker ("เลื่อนหมุดไปติดบนหลังคาบ้าน"), a GPS
 * Current Location action, and coordinate confirmation.
 */
@Composable
internal fun MapPinPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (latitude: Double, longitude: Double) -> Unit,
) {
    val context = LocalContext.current
    var centerLat by remember { mutableDoubleStateOf(initialLatitude ?: DEFAULT_LAT) }
    var centerLng by remember { mutableDoubleStateOf(initialLongitude ?: DEFAULT_LNG) }
    var zoom by remember { mutableFloatStateOf(1.15f) }
    var isDragging by remember { mutableStateOf(false) }
    var gpsNotice by remember { mutableStateOf<String?>(null) }

    val pinLift by animateDpAsState(
        targetValue = if (isDragging) (-12).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "pinLift",
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f || isSystemInDarkTheme()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(580.dp)
                .clip(SquircleLg),
            shape = SquircleLg,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.MapPin,
                        contentDescription = null,
                        tint = VelnoxBrand,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.address_map_picker_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconActionButton(
                        icon = VelnoxIcons.Close,
                        contentDescription = stringResource(R.string.common_close),
                        onClick = onDismiss,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Interactive Map Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(zoom) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    gpsNotice = null
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // Panning right moves the map east, so the pin moves west.
                                    val degPerPx = 0.000018 / zoom.toDouble()
                                    centerLat = (centerLat + dragAmount.y * degPerPx)
                                        .coerceIn(-85.0, 85.0)
                                    centerLng = (centerLng - dragAmount.x * degPerPx)
                                        .coerceIn(-180.0, 180.0)
                                },
                            )
                        },
                ) {
                    StreetMapCanvas(
                        latitude = centerLat,
                        longitude = centerLng,
                        zoom = zoom,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Top floating instruction pill ("เลื่อนหมุดไปติดบนหลังคาบ้าน")
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                            .shadow(4.dp, Circle)
                            .clip(Circle)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            .border(1.dp, VelnoxBrand.copy(alpha = 0.35f), Circle)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.MapPin,
                            contentDescription = null,
                            tint = VelnoxBrand,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = gpsNotice ?: stringResource(R.string.address_map_instruction),
                            style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Exact center-locked pin marker + target dot
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Target anchor dot directly under pin tip
                        Box(
                            modifier = Modifier
                                .size(if (isDragging) 14.dp else 10.dp)
                                .clip(Circle)
                                .background(VelnoxBrand.copy(alpha = 0.25f))
                                .border(1.5.dp, VelnoxBrand, Circle),
                        )
                        // Pin teardrop whose bottom tip sits at the exact center
                        CenterRoofPin(
                            modifier = Modifier
                                .offset(y = (-22).dp + pinLift),
                        )
                    }

                    // Floating Zoom + GPS Current Location controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Column(
                            modifier = Modifier
                                .shadow(3.dp, SquircleSm)
                                .clip(SquircleSm)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .velnoxClickable(role = Role.Button) {
                                        zoom = (zoom + 0.25f).coerceAtMost(2.2f)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = VelnoxIcons.Plus,
                                    contentDescription = "Zoom in",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.width(38.dp),
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .velnoxClickable(role = Role.Button) {
                                        zoom = (zoom - 0.25f).coerceAtLeast(0.65f)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = VelnoxIcons.Minus,
                                    contentDescription = "Zoom out",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        // Current Location (GPS) button
                        Row(
                            modifier = Modifier
                                .shadow(4.dp, Circle)
                                .clip(Circle)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, VelnoxBrand.copy(alpha = 0.45f), Circle)
                                .velnoxClickable(role = Role.Button) {
                                    val deviceLoc = resolveLastKnownLocation(context)
                                    centerLat = deviceLoc?.first ?: GPS_FALLBACK_LAT
                                    centerLng = deviceLoc?.second ?: GPS_FALLBACK_LNG
                                    zoom = 1.35f
                                    gpsNotice = context.getString(R.string.address_map_gps_updated)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = VelnoxIcons.Crosshair,
                                contentDescription = stringResource(R.string.address_map_current_location),
                                tint = VelnoxBrand,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.address_map_current_location),
                                style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Footer: Live coordinates + Confirm button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleSm)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.MapPin,
                            contentDescription = null,
                            tint = VelnoxBrand,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.address_map_coordinates_label),
                                style = Text2xs,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatCoordinates(centerLat, centerLng),
                                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(Squircle)
                            .background(VelnoxBrand)
                            .velnoxClickable(role = Role.Button) {
                                onConfirm(
                                    roundCoordinate(centerLat),
                                    roundCoordinate(centerLng),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.address_map_confirm),
                            style = TextXs.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterRoofPin(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 36.dp, height = 46.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headRadius = w * 0.42f
        val headCenterY = headRadius + 2f

        val pinPath = Path().apply {
            moveTo(cx, h)
            cubicTo(
                cx - headRadius * 0.9f, h * 0.62f,
                cx - headRadius, headCenterY + headRadius * 0.35f,
                cx - headRadius, headCenterY,
            )
            cubicTo(
                cx - headRadius, headCenterY - headRadius,
                cx + headRadius, headCenterY - headRadius,
                cx + headRadius, headCenterY,
            )
            cubicTo(
                cx + headRadius, headCenterY + headRadius * 0.35f,
                cx + headRadius * 0.9f, h * 0.62f,
                cx, h,
            )
            close()
        }

        // Crisp white outline for contrast over streets & rooftops
        drawPath(path = pinPath, color = Color.White, style = Stroke(width = 5f))
        drawPath(path = pinPath, color = VelnoxBrand)
        // Inner white dot
        drawCircle(
            color = Color.White,
            radius = headRadius * 0.38f,
            center = Offset(cx, headCenterY),
        )
    }
}

@Composable
private fun StreetMapCanvas(
    latitude: Double,
    longitude: Double,
    zoom: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isDark) Color(0xFF17202E) else Color(0xFFEEF2F6)
    val blockColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val roofColor = if (isDark) Color(0xFF283548) else Color(0xFFD5DFEB)
    val roofHighlight = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val parkColor = if (isDark) Color(0xFF13382C) else Color(0xFFD1FAE5)
    val waterColor = if (isDark) Color(0xFF132E47) else Color(0xFFBAE6FD)
    val minorRoadColor = if (isDark) Color(0xFF334155) else Color(0xFFFFFFFF)
    val majorRoadColor = if (isDark) Color(0xFF475569) else Color(0xFFFEF3C7)
    val majorRoadBorder = if (isDark) Color(0xFF64748B) else Color(0xFFF59E0B).copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        drawRect(color = bgColor)

        val tileSize = 150f * zoom
        // Convert lat/lng to continuous world pixels
        val worldX = ((longitude - DEFAULT_LNG) / 0.000018 * zoom.toDouble()).toFloat()
        val worldY = (-(latitude - DEFAULT_LAT) / 0.000018 * zoom.toDouble()).toFloat()

        val startTileX = floor((worldX - size.width / 2f) / tileSize).toInt() - 1
        val endTileX = floor((worldX + size.width / 2f) / tileSize).toInt() + 1
        val startTileY = floor((worldY - size.height / 2f) / tileSize).toInt() - 1
        val endTileY = floor((worldY + size.height / 2f) / tileSize).toInt() + 1

        val offsetX = size.width / 2f - worldX
        val offsetY = size.height / 2f - worldY

        // Pass 1: City blocks, parks, water canals, and building rooftops
        for (tx in startTileX..endTileX) {
            for (ty in startTileY..endTileY) {
                val left = tx * tileSize + offsetX
                val top = ty * tileSize + offsetY
                val inset = tileSize * 0.09f
                val hash = abs(tx * 73856093 xor ty * 19349663)

                when {
                    hash % 11 == 0 -> {
                        // Park green space
                        drawRoundRect(
                            color = parkColor,
                            topLeft = Offset(left + inset, top + inset),
                            size = Size(tileSize - inset * 2, tileSize - inset * 2),
                            cornerRadius = CornerRadius(12f * zoom, 12f * zoom),
                        )
                    }
                    hash % 13 == 0 -> {
                        // Canal / water feature
                        drawRoundRect(
                            color = waterColor,
                            topLeft = Offset(left + inset, top + inset),
                            size = Size(tileSize - inset * 2, tileSize - inset * 2),
                            cornerRadius = CornerRadius(16f * zoom, 16f * zoom),
                        )
                    }
                    else -> {
                        // Urban block + detailed rooftops
                        drawRoundRect(
                            color = blockColor,
                            topLeft = Offset(left + inset, top + inset),
                            size = Size(tileSize - inset * 2, tileSize - inset * 2),
                            cornerRadius = CornerRadius(8f * zoom, 8f * zoom),
                        )
                        val pad = tileSize * 0.16f
                        val bW = (tileSize - pad * 2 - 8f * zoom) / 2f
                        val bH = (tileSize - pad * 2 - 8f * zoom) / 2f
                        if (bW > 6f && bH > 6f) {
                            val positions = listOf(
                                Offset(left + pad, top + pad),
                                Offset(left + pad + bW + 8f * zoom, top + pad),
                                Offset(left + pad, top + pad + bH + 8f * zoom),
                                Offset(left + pad + bW + 8f * zoom, top + pad + bH + 8f * zoom),
                            )
                            positions.forEachIndexed { idx, pos ->
                                if ((hash + idx) % 5 != 0) {
                                    drawRoundRect(
                                        color = if ((hash + idx) % 2 == 0) roofColor else roofHighlight,
                                        topLeft = pos,
                                        size = Size(bW, bH),
                                        cornerRadius = CornerRadius(4f * zoom, 4f * zoom),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pass 2: Minor street grid
        val minorWidth = 8f * zoom
        for (tx in startTileX..endTileX + 1) {
            val x = tx * tileSize + offsetX
            drawLine(
                color = minorRoadColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = minorWidth,
            )
        }
        for (ty in startTileY..endTileY + 1) {
            val y = ty * tileSize + offsetY
            drawLine(
                color = minorRoadColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = minorWidth,
            )
        }

        // Pass 3: Major arterial avenues (every 3rd tile)
        val majorWidth = 15f * zoom
        for (tx in startTileX..endTileX + 1) {
            if (Math.floorMod(tx, 3) == 0) {
                val x = tx * tileSize + offsetX
                drawLine(
                    color = majorRoadBorder,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = majorWidth + 3f,
                    cap = StrokeCap.Butt,
                )
                drawLine(
                    color = majorRoadColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = majorWidth,
                    cap = StrokeCap.Butt,
                )
            }
        }
        for (ty in startTileY..endTileY + 1) {
            if (Math.floorMod(ty, 3) == 0) {
                val y = ty * tileSize + offsetY
                drawLine(
                    color = majorRoadBorder,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = majorWidth + 3f,
                    cap = StrokeCap.Butt,
                )
                drawLine(
                    color = majorRoadColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = majorWidth,
                    cap = StrokeCap.Butt,
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun resolveLastKnownLocation(context: Context): Pair<Double, Double>? {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null

    return runCatching {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val best = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        best?.let { it.latitude to it.longitude }
    }.getOrNull()
}

internal fun roundCoordinate(value: Double): Double =
    kotlin.math.round(value * 1_000_000.0) / 1_000_000.0

internal fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
