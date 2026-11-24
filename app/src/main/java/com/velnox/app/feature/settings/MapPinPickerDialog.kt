package com.velnox.app.feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private const val DEFAULT_LAT = 13.7563
private const val DEFAULT_LNG = 100.5018
private const val GPS_FALLBACK_LAT = 13.7466
private const val GPS_FALLBACK_LNG = 100.5347

enum class MapLayerType {
    Street,
    Satellite,
}

data class AddressGeocodeResult(
    val addressLine: String? = null,
    val subDistrict: String? = null,
    val district: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val fullDisplayName: String? = null,
)

data class MapSearchResult(
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
)

internal object MapTileMath {
    const val TILE_SIZE = 256.0
    const val MIN_ZOOM = 4.0
    const val MAX_TILE_ZOOM = 18
    const val MAX_ZOOM = 21.0
    const val DEFAULT_STREET_ZOOM = 16.5
    const val ROOFTOP_ZOOM = 18.0

    fun latLngToMercator(lat: Double, lng: Double, zoom: Double): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val x = ((lng + 180.0) / 360.0) * n * TILE_SIZE
        val latClamped = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(latClamped)
        val y = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0) * n * TILE_SIZE
        return x to y
    }

    fun mercatorToLatLng(x: Double, y: Double, zoom: Double): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val totalPixels = n * TILE_SIZE
        val lng = (x / totalPixels) * 360.0 - 180.0
        val clampedLng = lng.coerceIn(-180.0, 180.0)
        val mercatorY = 0.5 - (y / totalPixels)
        val latRad = Math.atan(Math.sinh(mercatorY * 2.0 * Math.PI))
        val lat = Math.toDegrees(latRad).coerceIn(-85.05112878, 85.05112878)
        return lat to clampedLng
    }

    fun getTileUrl(x: Int, y: Int, z: Int, layerType: MapLayerType): String {
        return when (layerType) {
            MapLayerType.Street ->
                "https://a.basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png"
            MapLayerType.Satellite ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        }
    }
}

private object MapGeocodeClient {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchPlaces(query: String, context: Context): List<MapSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val results = mutableListOf<MapSearchResult>()

        // 1. Android Geocoder attempt
        runCatching {
            val geocoder = Geocoder(context, Locale("th", "TH"))
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocationName(trimmed, 5)
            if (!list.isNullOrEmpty()) {
                for (addr in list) {
                    val title = addr.featureName ?: addr.thoroughfare ?: addr.subLocality ?: trimmed
                    val subtitle = listOfNotNull(
                        addr.subLocality,
                        addr.locality,
                        addr.adminArea,
                        addr.postalCode,
                    ).distinct().joinToString(" ")
                    results.add(
                        MapSearchResult(
                            title = title,
                            subtitle = subtitle.ifBlank { title },
                            lat = addr.latitude,
                            lng = addr.longitude,
                        ),
                    )
                }
            }
        }

        // 2. Nominatim fallback if Geocoder yielded no results
        if (results.isEmpty()) {
            runCatching {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=6&countrycodes=th&addressdetails=1&accept-language=th"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "VelnoxApp/1.0 (Android)")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val name = obj.optString("name").ifBlank {
                            obj.optString("display_name").split(",").firstOrNull().orEmpty()
                        }
                        val displayName = obj.optString("display_name")
                        val lat = obj.optDouble("lat", 0.0)
                        val lon = obj.optDouble("lon", 0.0)
                        if (lat != 0.0 && lon != 0.0) {
                            results.add(
                                MapSearchResult(
                                    title = name.ifBlank { trimmed },
                                    subtitle = displayName,
                                    lat = lat,
                                    lng = lon,
                                ),
                            )
                        }
                    }
                }
            }
        }
        results
    }

    suspend fun reverseGeocode(lat: Double, lng: Double, context: Context): AddressGeocodeResult? = withContext(Dispatchers.IO) {
        // 1. Android Geocoder attempt
        runCatching {
            val geocoder = Geocoder(context, Locale("th", "TH"))
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lng, 1)
            if (!list.isNullOrEmpty()) {
                val addr = list[0]
                val road = addr.thoroughfare ?: addr.subThoroughfare
                val subLoc = addr.subLocality
                val loc = addr.locality ?: addr.subAdminArea
                val admin = addr.adminArea
                val post = addr.postalCode
                val fullLine = addr.getAddressLine(0)
                return@withContext AddressGeocodeResult(
                    addressLine = road ?: fullLine?.split(",")?.firstOrNull()?.trim(),
                    subDistrict = subLoc,
                    district = loc,
                    province = admin,
                    postalCode = post,
                    fullDisplayName = fullLine ?: listOfNotNull(road, subLoc, loc, admin, post).joinToString(" "),
                )
            }
        }

        // 2. Nominatim reverse geocode fallback
        runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=18&addressdetails=1&accept-language=th"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VelnoxApp/1.0 (Android)")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrEmpty()) {
                val obj = JSONObject(body)
                val addressObj = obj.optJSONObject("address")
                val displayName = obj.optString("display_name")
                val road = addressObj?.optString("road")?.takeIf { it.isNotBlank() }
                    ?: addressObj?.optString("neighbourhood")?.takeIf { it.isNotBlank() }
                val quarter = addressObj?.optString("quarter")?.takeIf { it.isNotBlank() }
                    ?: addressObj?.optString("suburb")?.takeIf { it.isNotBlank() }
                val city = addressObj?.optString("city")?.takeIf { it.isNotBlank() }
                    ?: addressObj?.optString("province")?.takeIf { it.isNotBlank() }
                    ?: addressObj?.optString("state")?.takeIf { it.isNotBlank() }
                val postcode = addressObj?.optString("postcode")?.takeIf { it.isNotBlank() }
                val district = addressObj?.optString("district")?.takeIf { it.isNotBlank() }
                    ?: addressObj?.optString("city_district")?.takeIf { it.isNotBlank() }
                    ?: addressObj?.optString("suburb")?.takeIf { it.isNotBlank() }

                return@withContext AddressGeocodeResult(
                    addressLine = road,
                    subDistrict = quarter,
                    district = district,
                    province = city,
                    postalCode = postcode,
                    fullDisplayName = displayName,
                )
            }
        }
        null
    }
}

/**
 * Enhanced Interactive Map Pin Picker modal with real slippy raster map tiles
 * (Street & Satellite layers), instant place search & auto-complete, multi-touch
 * pan & pinch-to-zoom, live GPS location lock, rooftop pinpointing, and
 * real-time reverse geocoding with form auto-fill support.
 */
@Composable
internal fun MapPinPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (latitude: Double, longitude: Double) -> Unit,
) {
    MapPinPickerDialog(
        initialLatitude = initialLatitude,
        initialLongitude = initialLongitude,
        onDismiss = onDismiss,
        onConfirmWithDetails = { lat, lng, _, _ -> onConfirm(lat, lng) },
    )
}

@Composable
internal fun MapPinPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirmWithDetails: (
        latitude: Double,
        longitude: Double,
        geocoded: AddressGeocodeResult?,
        autoFill: Boolean,
    ) -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var centerLat by remember { mutableDoubleStateOf(initialLatitude ?: DEFAULT_LAT) }
    var centerLng by remember { mutableDoubleStateOf(initialLongitude ?: DEFAULT_LNG) }
    var zoom by remember { mutableDoubleStateOf(if (initialLatitude != null) 17.5 else MapTileMath.DEFAULT_STREET_ZOOM) }
    var layerType by remember { mutableStateOf(MapLayerType.Satellite) }
    var isDragging by remember { mutableStateOf(false) }
    var gpsNotice by remember { mutableStateOf<String?>(null) }
    var autoFillAddress by remember { mutableStateOf(true) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<MapSearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Reverse geocode state
    var detectedAddress by remember { mutableStateOf<AddressGeocodeResult?>(null) }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var reverseGeocodeJob by remember { mutableStateOf<Job?>(null) }

    // Debounced reverse geocoding when coordinates settle
    fun scheduleReverseGeocode(lat: Double, lng: Double) {
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = coroutineScope.launch {
            delay(500)
            isReverseGeocoding = true
            val res = MapGeocodeClient.reverseGeocode(lat, lng, context)
            detectedAddress = res
            isReverseGeocoding = false
        }
    }

    // Initial reverse geocode on launch
    LaunchedEffect(Unit) {
        scheduleReverseGeocode(centerLat, centerLng)
    }

    // GPS location provider function
    fun locateCurrentPosition() {
        val devLoc = resolveCurrentDeviceLocation(context)
        if (devLoc != null) {
            centerLat = devLoc.first
            centerLng = devLoc.second
            zoom = MapTileMath.ROOFTOP_ZOOM
            gpsNotice = context.getString(R.string.address_map_gps_updated)
            scheduleReverseGeocode(centerLat, centerLng)
        } else {
            gpsNotice = context.getString(R.string.address_map_permission_rationale)
        }
    }

    // Runtime location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            locateCurrentPosition()
        } else {
            gpsNotice = context.getString(R.string.address_map_permission_rationale)
        }
    }

    val pinLift by animateDpAsState(
        targetValue = if (isDragging) (-14).dp else 0.dp,
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
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(SquircleLg),
            shape = SquircleLg,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar: Title + Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
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

                // Interactive Address/Place Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .zIndex(20f),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, SquircleSm)
                                .clip(SquircleSm)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), SquircleSm)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = VelnoxIcons.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { newQuery ->
                                    searchQuery = newQuery
                                    searchJob?.cancel()
                                    if (newQuery.trim().length >= 2) {
                                        searchJob = coroutineScope.launch {
                                            delay(400)
                                            isSearching = true
                                            searchResults = MapGeocodeClient.searchPlaces(newQuery, context)
                                            isSearching = false
                                        }
                                    } else {
                                        searchResults = emptyList()
                                        isSearching = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        if (searchQuery.isNotBlank()) {
                                            searchJob?.cancel()
                                            searchJob = coroutineScope.launch {
                                                isSearching = true
                                                searchResults = MapGeocodeClient.searchPlaces(searchQuery, context)
                                                isSearching = false
                                            }
                                        }
                                    },
                                ),
                                cursorBrush = SolidColor(VelnoxBrand),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.address_map_search_placeholder),
                                            style = TextXs,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                    innerTextField()
                                },
                            )
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = VelnoxBrand,
                                )
                            } else if (searchQuery.isNotEmpty()) {
                                IconActionButton(
                                    icon = VelnoxIcons.Close,
                                    contentDescription = "Clear",
                                    onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        // Search Results Dropdown List
                        AnimatedVisibility(
                            visible = searchResults.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .shadow(6.dp, SquircleSm)
                                    .clip(SquircleSm),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 210.dp),
                                ) {
                                    items(searchResults) { result ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .velnoxClickable {
                                                    centerLat = result.lat
                                                    centerLng = result.lng
                                                    zoom = 17.5
                                                    searchQuery = ""
                                                    searchResults = emptyList()
                                                    keyboardController?.hide()
                                                    scheduleReverseGeocode(centerLat, centerLng)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = VelnoxIcons.MapPin,
                                                contentDescription = null,
                                                tint = VelnoxBrand,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = result.title,
                                                    style = TextXs.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = result.subtitle,
                                                    style = Text2xs,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Interactive Map Viewport (Multi-touch slippy raster tiles + vector canvas fallback)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                ) {
                    val density = LocalDensity.current.density
                    val wPx = constraints.maxWidth.toFloat()
                    val hPx = constraints.maxHeight.toFloat()

                    // Multi-touch gestures: Drag pan + Pinch zoom + Double tap zoom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        zoom = (zoom + 1.0).coerceAtMost(MapTileMath.MAX_ZOOM)
                                        scheduleReverseGeocode(centerLat, centerLng)
                                    },
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoomFactor, _ ->
                                    if (zoomFactor != 1f) {
                                        val zoomChange = ln(zoomFactor.toDouble()) / ln(2.0)
                                        zoom = (zoom + zoomChange).coerceIn(MapTileMath.MIN_ZOOM, MapTileMath.MAX_ZOOM)
                                    }
                                    if (pan.x != 0f || pan.y != 0f) {
                                        isDragging = true
                                        gpsNotice = null
                                        val n = 2.0.pow(zoom)
                                        val worldTotalPx = n * MapTileMath.TILE_SIZE
                                        val currWorldX = ((centerLng + 180.0) / 360.0) * worldTotalPx
                                        val latClamped = centerLat.coerceIn(-85.05112878, 85.05112878)
                                        val latRad = Math.toRadians(latClamped)
                                        val currWorldY = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0) * worldTotalPx

                                        val newWorldX = currWorldX - pan.x.toDouble()
                                        val newWorldY = currWorldY - pan.y.toDouble()

                                        val newLng = ((newWorldX / worldTotalPx) * 360.0 - 180.0).coerceIn(-180.0, 180.0)
                                        val mercY = 0.5 - (newWorldY / worldTotalPx)
                                        val newLat = Math.toDegrees(Math.atan(Math.sinh(mercY * 2.0 * Math.PI))).coerceIn(-85.05112878, 85.05112878)

                                        centerLat = newLat
                                        centerLng = newLng
                                        scheduleReverseGeocode(centerLat, centerLng)
                                    }
                                }
                            },
                    ) {
                        // Layer 1: Stylized vector backdrop (smooth fallback if tiles are loading or offline)
                        MapBackdropCanvas(
                            isDark = isDark,
                            layerType = layerType,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Layer 2: Slippy Map Raster Tiles (ArcGIS Satellite with Digital Magnification)
                        val effectiveTileZoom = zoom.toInt().coerceIn(MapTileMath.MIN_ZOOM.toInt(), MapTileMath.MAX_TILE_ZOOM)
                        val scale = 2.0.pow(zoom - effectiveTileZoom).toFloat()
                        val scaledTilePx = (MapTileMath.TILE_SIZE * scale).toFloat()
                        val numTiles = 1 shl effectiveTileZoom

                        val (centerMercX, centerMercY) = MapTileMath.latLngToMercator(centerLat, centerLng, effectiveTileZoom.toDouble())
                        val halfW = wPx / 2f
                        val halfH = hPx / 2f

                        val minTileX = floor((centerMercX - halfW / scale) / MapTileMath.TILE_SIZE).toInt()
                        val maxTileX = ceil((centerMercX + halfW / scale) / MapTileMath.TILE_SIZE).toInt()
                        val minTileY = floor((centerMercY - halfH / scale) / MapTileMath.TILE_SIZE).toInt().coerceIn(0, numTiles - 1)
                        val maxTileY = ceil((centerMercY + halfH / scale) / MapTileMath.TILE_SIZE).toInt().coerceIn(0, numTiles - 1)

                        for (tx in minTileX..maxTileX) {
                            for (ty in minTileY..maxTileY) {
                                val wrappedTx = ((tx % numTiles) + numTiles) % numTiles
                                val tileScreenX = halfW + ((tx * MapTileMath.TILE_SIZE - centerMercX) * scale).toFloat()
                                val tileScreenY = halfH + ((ty * MapTileMath.TILE_SIZE - centerMercY) * scale).toFloat()
                                val tileUrl = MapTileMath.getTileUrl(wrappedTx, ty, effectiveTileZoom, MapLayerType.Satellite)

                                key("$effectiveTileZoom-$wrappedTx-$ty") {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(tileUrl)
                                            .addHeader("User-Agent", "VelnoxApp/1.0 (Android)")
                                            .crossfade(150)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier
                                            .offset { IntOffset(tileScreenX.roundToInt(), tileScreenY.roundToInt()) }
                                            .size((scaledTilePx / density).dp),
                                    )
                                }
                            }
                        }

                        // Subtitle crosshairs on map center for pinpoint rooftop accuracy
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val crossLen = 14.dp.toPx()
                            val strokeColor = Color.White.copy(alpha = 0.5f)
                            drawLine(color = strokeColor, start = Offset(cx - crossLen, cy), end = Offset(cx + crossLen, cy), strokeWidth = 1.5f)
                            drawLine(color = strokeColor, start = Offset(cx, cy - crossLen), end = Offset(cx, cy + crossLen), strokeWidth = 1.5f)
                        }
                    }

                    // Top Floating Rooftop Instruction Pill
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp, start = 14.dp, end = 14.dp)
                            .shadow(4.dp, Circle)
                            .clip(Circle)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            .border(1.dp, VelnoxBrand.copy(alpha = 0.4f), Circle)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.MapPin,
                            contentDescription = null,
                            tint = VelnoxBrand,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = gpsNotice
                                ?: if (zoom < 16.0) stringResource(R.string.address_map_zoom_hint)
                                else stringResource(R.string.address_map_instruction),
                            style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Dynamic scaling pin: progressively scales down when zooming in deep
                    // so users can see exact house outlines and rooftops without obstruction
                    val pinScale = (1.0f - ((zoom.toFloat() - 15f) / 6f) * 0.55f).coerceIn(0.45f, 1.0f)
                    val pinHeight = 46.dp * pinScale
                    val anchorDotSize = (10.dp * pinScale).coerceAtLeast(6.dp)

                    // Exact center-locked pin marker + target dot
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Target anchor dot directly under pin tip
                        Box(
                            modifier = Modifier
                                .size(if (isDragging) anchorDotSize * 1.3f else anchorDotSize)
                                .clip(Circle)
                                .background(VelnoxBrand.copy(alpha = 0.25f))
                                .border(1.5.dp, VelnoxBrand, Circle),
                        )
                        // Pin teardrop whose bottom tip sits at the exact center
                        CenterRoofPin(
                            scale = pinScale,
                            modifier = Modifier.offset(y = (-pinHeight / 2f) + pinLift),
                        )
                    }

                    // Floating Controls: Zoom In / Out + Current Location (GPS)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        // Zoom in / out controls + current zoom badge
                        Column(
                            modifier = Modifier
                                .shadow(3.dp, SquircleSm)
                                .clip(SquircleSm)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .velnoxClickable(role = Role.Button) {
                                        zoom = (zoom + 1.0).coerceAtMost(MapTileMath.MAX_ZOOM)
                                        scheduleReverseGeocode(centerLat, centerLng)
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
                                        zoom = (zoom - 1.0).coerceAtLeast(MapTileMath.MIN_ZOOM)
                                        scheduleReverseGeocode(centerLat, centerLng)
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

                        // Current Location (GPS) button with permission check
                        Row(
                            modifier = Modifier
                                .shadow(4.dp, Circle)
                                .clip(Circle)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, VelnoxBrand.copy(alpha = 0.5f), Circle)
                                .velnoxClickable(role = Role.Button) {
                                    val hasFine = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val hasCoarse = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasFine || hasCoarse) {
                                        locateCurrentPosition()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            ),
                                        )
                                    }
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

                // Footer: Live reverse-geocoded address preview + Auto-fill option + Confirm button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Detected Address Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleSm)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.MapPin,
                            contentDescription = null,
                            tint = VelnoxBrand,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (isReverseGeocoding && detectedAddress == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = VelnoxBrand,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.address_map_detecting),
                                        style = Text2xs,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Text(
                                    text = detectedAddress?.fullDisplayName
                                        ?: stringResource(R.string.address_map_detected_location),
                                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "${stringResource(R.string.address_map_coordinates_label)}: ${formatCoordinates(centerLat, centerLng)}",
                                style = Text2xs,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Auto-fill address form checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .velnoxClickable { autoFillAddress = !autoFillAddress },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = autoFillAddress,
                            onCheckedChange = { autoFillAddress = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = VelnoxBrand,
                                checkmarkColor = Color.White,
                            ),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.address_map_autofill_checkbox),
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Primary Confirm Pin Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(Squircle)
                            .background(VelnoxBrand)
                            .velnoxClickable(role = Role.Button) {
                                onConfirmWithDetails(
                                    roundCoordinate(centerLat),
                                    roundCoordinate(centerLng),
                                    detectedAddress,
                                    autoFillAddress,
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
private fun MapBackdropCanvas(
    isDark: Boolean,
    layerType: MapLayerType,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (layerType == MapLayerType.Satellite) {
        Color(0xFF0F172A)
    } else if (isDark) {
        Color(0xFF1E293B)
    } else {
        Color(0xFFE2E8F0)
    }
    val gridColor = if (layerType == MapLayerType.Satellite) {
        Color(0xFF1E293B).copy(alpha = 0.5f)
    } else if (isDark) {
        Color(0xFF334155).copy(alpha = 0.4f)
    } else {
        Color(0xFFCBD5E1).copy(alpha = 0.6f)
    }

    Canvas(modifier = modifier) {
        drawRect(color = bgColor)
        val step = 32.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

@Composable
private fun CenterRoofPin(
    scale: Float = 1.0f,
    modifier: Modifier = Modifier,
) {
    val width = 36.dp * scale
    val height = 46.dp * scale
    Canvas(modifier = modifier.size(width = width, height = height)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headRadius = w * 0.42f
        val headCenterY = headRadius + (2f * scale)

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

        // Crisp white outline for contrast over satellite rooftops
        drawPath(path = pinPath, color = Color.White, style = Stroke(width = 4.5f * scale))
        drawPath(path = pinPath, color = VelnoxBrand)
        // Inner white dot
        drawCircle(
            color = Color.White,
            radius = headRadius * 0.38f,
            center = Offset(cx, headCenterY),
        )
    }
}

@SuppressLint("MissingPermission")
private fun resolveCurrentDeviceLocation(context: Context): Pair<Double, Double>? {
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
