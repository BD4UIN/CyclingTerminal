package com.example.cyclingterminal.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * 罗盘表盘旋转、顶部指针固定的罗盘：
 * - 刻度盘与「北东南西」方位随朝向一起转动（跨 0°/360° 走最短路径）；
 * - 顶部橙色指针永远朝上，作为固定的“车头方向”基准；
 * - 四个方位文字始终保持正向（不随表盘歪斜）。
 *
 * heading 为当前朝向角：0°=正北，顺时针增加。
 */
@Composable
fun CompassRose(heading: Float, modifier: Modifier = Modifier) {
    val rose by rememberShortestPathAngle(heading)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            if (radius <= 0f) return@Canvas

            // 内盘底色，让罗盘从卡片背景中微微浮起
            drawCircle(color = primary.copy(alpha = 0.05f), radius = radius * 0.98f, center = center)

            // 外圈双环（固定）
            drawCircle(
                color = onSurface.copy(alpha = 0.18f),
                radius = radius,
                center = center,
                style = Stroke(width = radius * 0.012f),
            )
            drawCircle(
                color = primary.copy(alpha = 0.32f),
                radius = radius * 0.965f,
                center = center,
                style = Stroke(width = radius * 0.006f),
            )

            // 刻度：每 5° 一格，每 30° 主刻度，随表盘旋转
            rotate(degrees = -rose, pivot = center) {
                for (i in 0 until 72) {
                    val rad = Math.toRadians((i * 5).toDouble())
                    val isMajor = i % 6 == 0
                    val innerR = radius * if (isMajor) 0.82f else 0.88f
                    val outerR = radius * 0.95f
                    val s = sin(rad).toFloat()
                    val c = cos(rad).toFloat()
                    drawLine(
                        color = if (isMajor) onSurface.copy(alpha = 0.75f) else onSurface.copy(alpha = 0.28f),
                        start = Offset(center.x + innerR * s, center.y - innerR * c),
                        end = Offset(center.x + outerR * s, center.y - outerR * c),
                        strokeWidth = if (isMajor) radius * 0.017f else radius * 0.007f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // 方位文字：位置随表盘转动，但文字本身保持正向可读
            val labelPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = radius * 0.185f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val fm = labelPaint.fontMetrics
            val labelRadius = radius * 0.655f
            val directions = listOf(0f to "北", 90f to "东", 180f to "南", 270f to "西")
            for ((deg, label) in directions) {
                val eff = Math.toRadians((deg - rose).toDouble())
                val x = center.x + labelRadius * sin(eff).toFloat()
                val labelCenterY = center.y - labelRadius * cos(eff).toFloat()
                labelPaint.color = if (deg == 0f) {
                    primary.toArgb()
                } else {
                    onSurface.copy(alpha = 0.88f).toArgb()
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    labelCenterY - (fm.ascent + fm.descent) / 2f,
                    labelPaint,
                )
            }

            // 顶部固定指针：箭头朝上，作为车头方向基准，永远不转
            val halfW = radius * 0.10f
            val tipY = center.y - radius * 0.50f
            val neckY = center.y - radius * 0.08f
            val arrow = Path().apply {
                moveTo(center.x, tipY)
                lineTo(center.x + halfW, neckY)
                lineTo(center.x - halfW, neckY)
                close()
            }
            drawPath(arrow, color = primary)
            val tail = Path().apply {
                moveTo(center.x + halfW, neckY)
                lineTo(center.x - halfW, neckY)
                lineTo(center.x, center.y + radius * 0.26f)
                close()
            }
            drawPath(tail, color = primary.copy(alpha = 0.22f))

            // 中心轴
            drawCircle(color = onSurface.copy(alpha = 0.85f), radius = radius * 0.034f, center = center)
            drawCircle(color = primary, radius = radius * 0.018f, center = center)
        }
    }
}

/** 角度最短路径动画：保证跨 0°/360° 时沿最近方向旋转 */
@Composable
private fun rememberShortestPathAngle(target: Float): State<Float> {
    val angle = remember { Animatable(target) }
    LaunchedEffect(target) {
        var diff = target - angle.value
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        angle.animateTo(
            angle.value + diff,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    return angle.asState()
}
