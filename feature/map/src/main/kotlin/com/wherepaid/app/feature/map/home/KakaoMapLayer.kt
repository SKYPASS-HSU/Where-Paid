package com.wherepaid.app.feature.map.home

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.MapAuthException
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.OrderingType
import com.wherepaid.app.core.designsystem.component.MapPinBitmaps
import com.wherepaid.app.core.designsystem.component.PinBitmap
import com.wherepaid.app.core.designsystem.theme.WherePaidTheme
import com.wherepaid.app.core.model.LatLng
import com.kakao.vectormap.LatLng as KakaoLatLng

private const val TAG = "KakaoMapLayer"

/** 카카오맵 SDK 초기화. Application.onCreate에서 한 번. 키가 비어 있으면 하지 않고 false. */
fun initKakaoMap(context: Context, nativeAppKey: String): Boolean {
    if (nativeAppKey.isBlank()) return false
    KakaoMapSdk.init(context, nativeAppKey)
    return true
}

/** 화면의 줌 버튼이 지도를 움직이게 하는 손잡이. */
@Stable
class MapController {
    internal var map: KakaoMap? by mutableStateOf(null)

    fun zoomIn() = map?.moveCamera(CameraUpdateFactory.zoomIn(), CameraAnimation.from(200))

    fun zoomOut() = map?.moveCamera(CameraUpdateFactory.zoomOut(), CameraAnimation.from(200))
}

@Composable
fun rememberMapController(): MapController = remember { MapController() }

/** 지도를 못 그리는 이유. 지도 자리에 회색 바탕과 함께 보여 준다. */
private sealed interface MapProblem {
    data object NoKey : MapProblem
    data class Auth(val code: Int) : MapProblem
    data class Other(val message: String?) : MapProblem
}

/**
 * 카카오맵 + 핀. 공식 Compose API가 없어 [MapView]를 감싼다.
 * - 화면 수명주기에 맞춰 resume/pause를 부른다 (공식 문서: 안 하면 알 수 없는 크래시)
 * - [cameraKey]가 바뀔 때(달을 바꿀 때) 핀이 모두 보이게 카메라를 맞춘다. 그 사이 사용자가 움직인 화면은 건드리지 않는다
 * - [contentPaddingTopDp]만큼 위쪽은 카드에 가려지므로 카메라를 맞출 때 피한다
 */
@Composable
fun KakaoMapLayer(
    pins: List<MapPin>,
    cameraKey: Any,
    controller: MapController,
    onPinClick: (MapPin) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingTopDp: Int = 0,
) {
    if (!KakaoMapSdk.isInitialized()) {
        MapUnavailable(MapProblem.NoKey, modifier)
        return
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    var problem by remember { mutableStateOf<MapProblem?>(null) }
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val bitmaps = remember(context) { MapPinBitmaps(context) }
    val pinCache = remember(bitmaps) { mutableMapOf<String, PinBitmap>() }
    var fittedKey by remember { mutableStateOf<Any?>(null) }

    val mapView = remember(context) {
        MapView(context).apply {
            start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {
                        controller.map = null
                    }

                    override fun onMapError(error: Exception) {
                        Log.w(TAG, "map error", error)
                        problem = if (error is MapAuthException) MapProblem.Auth(error.errorCode) else MapProblem.Other(error.message)
                    }
                },
                object : KakaoMapReadyCallback() {
                    override fun getPosition(): KakaoLatLng = DEFAULT_CENTER.toKakao()

                    override fun getZoomLevel(): Int = DEFAULT_ZOOM

                    override fun onMapReady(map: KakaoMap) {
                        map.setOnLabelClickListener { _, _, label ->
                            (label.tag as? MapPin)?.let { currentOnPinClick(it) }
                            true
                        }
                        controller.map = map
                    }
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.resume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish() // 탭을 떠나면 지도 엔진을 내린다
            controller.map = null
        }
    }

    val map = controller.map
    LaunchedEffect(map, pins) {
        val layer = map?.pinLayer() ?: return@LaunchedEffect
        layer.removeAll()
        pins.forEach { pin ->
            val bitmap = pinCache.getOrPut(pin.cacheKey()) {
                when (pin) {
                    is MapPin.Store -> bitmaps.storePin(pin.amount, pin.name, pin.diameterDp)
                    is MapPin.Ambiguous -> bitmaps.ambiguousPin()
                }
            }
            val style = LabelStyle.from(bitmap.bitmap).setAnchorPoint(0.5f, bitmap.anchorY).setApplyDpScale(false)
            layer.addLabel(
                LabelOptions.from(pin.latLng.toKakao())
                    .setStyles(LabelStyles.from(style))
                    .setTag(pin)
                    .setClickable(true)
                    // 큰 핀이 작은 핀 위에, 느낌표 핀은 맨 위에
                    .setRank(if (pin is MapPin.Store) pin.total else Long.MAX_VALUE),
            )
        }
    }

    LaunchedEffect(map, cameraKey, pins.isNotEmpty()) {
        if (map == null || pins.isEmpty() || fittedKey == cameraKey) return@LaunchedEffect
        val points = pins.map { it.latLng.toKakao() }.toTypedArray()
        if (points.size == 1) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(points.first(), SINGLE_PIN_ZOOM))
        } else {
            val padding = with(density) { (FIT_PADDING_DP + contentPaddingTopDp / 2).dp.roundToPx() }
            map.moveCamera(CameraUpdateFactory.fitMapPoints(points, padding))
        }
        fittedKey = cameraKey
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        problem?.let { MapUnavailable(it, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun MapUnavailable(problem: MapProblem, modifier: Modifier = Modifier) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    val reason = when (problem) {
        MapProblem.NoKey -> "local.properties에 kakao.nativeAppKey를 넣어 주세요"
        is MapProblem.Auth -> when (problem.code) {
            401, 403 -> "카카오 콘솔에 패키지명과 키 해시가 등록됐는지 확인해 주세요 (${problem.code})"
            429 -> "지도 사용량 한도를 넘었어요 (429)"
            else -> "지도 인증에 실패했어요 (${problem.code})"
        }
        is MapProblem.Other -> "잠시 후 다시 열어 주세요"
    }
    Box(modifier.fillMaxSize().background(colors.mapBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("지도를 불러오지 못했어요", style = type.body15Strong, color = colors.textSecondary)
            Text(reason, style = type.caption12, color = colors.textSecondary)
        }
    }
}

private fun KakaoMap.pinLayer(): LabelLayer? {
    val manager = labelManager ?: return null
    return manager.getLayer(PIN_LAYER_ID) ?: manager.addLayer(
        LabelLayerOptions.from(PIN_LAYER_ID)
            // 핀끼리 겹쳐도 숨기지 않는다 (매장 핀은 크기가 금액이라 모두 보여야 한다)
            .setCompetitionType(CompetitionType.None)
            .setOrderingType(OrderingType.Rank),
    )
}

private fun MapPin.cacheKey(): String = when (this) {
    is MapPin.Store -> "store|$amount|$name|$diameterDp"
    is MapPin.Ambiguous -> "ambiguous"
}

private fun LatLng.toKakao(): KakaoLatLng = KakaoLatLng.from(latitude, longitude)

private const val PIN_LAYER_ID = "wherepaid-pins"

/** 결제가 없을 때 처음 보여 줄 곳: 서울시청. */
private val DEFAULT_CENTER = LatLng(37.5665, 126.9780)
private const val DEFAULT_ZOOM = 15
private const val SINGLE_PIN_ZOOM = 16
private const val FIT_PADDING_DP = 72
