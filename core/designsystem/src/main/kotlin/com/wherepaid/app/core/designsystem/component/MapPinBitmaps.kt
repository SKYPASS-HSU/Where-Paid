package com.wherepaid.app.core.designsystem.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.core.content.res.ResourcesCompat
import com.wherepaid.app.core.designsystem.R
import com.wherepaid.app.core.designsystem.theme.WherePaidColors
import com.wherepaid.app.core.designsystem.theme.WherePaidTypography
import kotlin.math.ceil
import kotlin.math.max

/**
 * 지도 SDK에 넘길 핀 그림 (S-02 "Pin / 스타벅스", "Pin / 확인 필요").
 * 지도 라이브러리는 Compose를 그리지 못해 비트맵으로 만든다. 색과 글자는 디자인 시스템 토큰을 그대로 쓴다.
 *
 * 핀 = 원(지름 [diameterDp], 흰 테두리 3, 그림자) + 4dp 아래 알약 이름표.
 * [PinBitmap.anchorY]는 원 중심의 세로 위치(0~1)라 지도 좌표가 원 중심에 온다.
 */
class MapPinBitmaps(
    context: Context,
    private val colors: WherePaidColors = WherePaidColors(),
    private val type: WherePaidTypography = WherePaidTypography(),
) {
    private val density = context.resources.displayMetrics.density
    private val scaledDensity = density * context.resources.configuration.fontScale
    private val bold: Typeface = ResourcesCompat.getFont(context, R.font.pretendard_bold) ?: Typeface.DEFAULT_BOLD
    private val semibold: Typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold) ?: Typeface.DEFAULT_BOLD

    /** 매장 핀: 주황 원 안에 금액, 아래 흰 이름표. [selected]면 테두리가 진한 색 (S-03 핀 상세). */
    fun storePin(amount: String, name: String, diameterDp: Float, selected: Boolean = false): PinBitmap = pin(
        diameterDp = diameterDp,
        fill = colors.primary,
        border = if (selected) colors.inverseSurface else colors.background,
        centerText = amount,
        centerStyle = if (diameterDp >= LARGE_TEXT_MIN_DIAMETER_DP) type.pinAmountLarge else type.pinAmountSmall,
        label = name,
        labelFill = colors.background,
        labelText = colors.textPrimary,
        labelShadow = true,
    )

    /** 느낌표 핀: 노랑 원 34 + "어디였나요?" 이름표. */
    fun ambiguousPin(): PinBitmap = pin(
        diameterDp = AMBIGUOUS_DIAMETER_DP,
        fill = colors.warning,
        border = colors.background,
        centerText = "!",
        centerStyle = type.pinMark,
        label = "어디였나요?",
        labelFill = colors.warningContainer,
        labelText = colors.onWarningContainer,
        labelShadow = false,
    )

    private fun pin(
        diameterDp: Float,
        fill: Color,
        border: Color,
        centerText: String,
        centerStyle: TextStyle,
        label: String,
        labelFill: Color,
        labelText: Color,
        labelShadow: Boolean,
    ): PinBitmap {
        val shadowPad = px(8f) // 그림자가 잘리지 않게
        val diameter = px(diameterDp)
        val labelPaint = textPaint(type.label11, semibold, labelText)
        val labelWidth = labelPaint.measureText(label) + px(8f) * 2
        val labelHeight = textHeight(labelPaint) + px(3f) * 2
        val width = max(diameter, labelWidth) + shadowPad * 2
        val height = shadowPad + diameter + px(4f) + labelHeight + shadowPad
        val bitmap = Bitmap.createBitmap(ceil(width).toInt(), ceil(height).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2
        val cy = shadowPad + diameter / 2

        // 원: 그림자(0 4 12 rgba(0,0,0,0.18)) → 테두리 → 채움
        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = border.toArgb()
            setShadowLayer(px(6f), 0f, px(4f), android.graphics.Color.argb(46, 0, 0, 0))
        }
        canvas.drawCircle(cx, cy, diameter / 2, circle)
        canvas.drawCircle(cx, cy, diameter / 2 - px(3f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.toArgb() })
        drawCentered(canvas, centerText, textPaint(centerStyle, bold, Color.White), cx, cy)

        // 이름표
        val top = shadowPad + diameter + px(4f)
        val rect = RectF(cx - labelWidth / 2, top, cx + labelWidth / 2, top + labelHeight)
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelFill.toArgb()
            if (labelShadow) setShadowLayer(px(2f), 0f, px(1f), android.graphics.Color.argb(26, 0, 0, 0))
        }
        canvas.drawRoundRect(rect, px(8f), px(8f), pill)
        drawCentered(canvas, label, labelPaint, cx, rect.centerY())

        return PinBitmap(bitmap, anchorY = cy / height)
    }

    private fun textPaint(style: TextStyle, face: Typeface, color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = style.fontSize.value * scaledDensity
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
    }

    private fun textHeight(paint: Paint): Float = paint.fontMetrics.let { it.descent - it.ascent }

    private fun drawCentered(canvas: Canvas, text: String, paint: Paint, cx: Float, cy: Float) {
        val metrics = paint.fontMetrics
        canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) / 2, paint)
    }

    private fun px(dp: Float) = dp * density

    companion object {
        const val AMBIGUOUS_DIAMETER_DP = 34f
        const val LARGE_TEXT_MIN_DIAMETER_DP = 56f
    }
}

/** 핀 그림과 지도 좌표가 놓일 세로 위치(0 = 위, 1 = 아래). 가로는 항상 가운데. */
class PinBitmap(val bitmap: Bitmap, val anchorY: Float)
