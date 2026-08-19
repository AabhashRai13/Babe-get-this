// PebbleListeningIndicator.kt
// The "pebble" mascot recording animation: blinking face inside the recorder
// circle, breathing scale, pulsing sound-rings, floating hearts. Pure vector
// Canvas drawing — no assets, ~zero APK weight.
//
// USAGE (shown only while recording — compose it when the state is Recording):
//   PebbleListeningIndicator(modifier = Modifier.size(200.dp))
// The visible circle is 107/240 of the canvas; the rest is headroom for the
// pulsing rings and floating hearts.

package com.babegetthis.android.core.voice.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

// ---------- palette (matches the app-icon mascot) ----------
private val Slate = Color(0xFF5E7B87)
private val Cream = Color(0xFFF3EDE1)
private val Pupil = Color(0xFF17242E)
private val BlushPink = Color(0xFFE89B9B)

@Composable
fun PebbleListeningIndicator(modifier: Modifier = Modifier) {
    // One InfiniteTransition drives every looping value below — like a single
    // vsync-synced AnimationController in Flutter with several Tweens on it.
    val infiniteTransition = rememberInfiniteTransition(label = "pebbleListening")

    // Three expanding sound-rings, staggered so one is always mid-pulse.
    // (animateFloat is @Composable, so this local helper must be too.)
    @Composable
    fun ringProgress(delayMillis: Int): State<Float> = infiniteTransition.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(2400, easing = LinearOutSlowInEasing),
            RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMillis)
        ),
        label = "ring$delayMillis"
    )
    val firstRing = ringProgress(0)
    val secondRing = ringProgress(800)
    val thirdRing = ringProgress(1600)

    val breatheScale = infiniteTransition.animateFloat(
        1f, 1.06f,
        infiniteRepeatable(tween(1200, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val bobOffsetY = infiniteTransition.animateFloat(
        2f, -3f,
        infiniteRepeatable(tween(1100, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    // Blink: eyes open for most of a 3.4s cycle, quick squash near the end.
    val blinkScaleY = infiniteTransition.animateFloat(
        1f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 3400
                1f at 0; 1f at 3094; 0.12f at 3196; 1f at 3400
            },
            RepeatMode.Restart
        ),
        label = "blink"
    )
    @Composable
    fun heartProgress(delayMillis: Int): State<Float> = infiniteTransition.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(3200, easing = LinearEasing),
            RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMillis)
        ),
        label = "heart$delayMillis"
    )
    val firstHeart = heartProgress(0)
    val secondHeart = heartProgress(1600)

    Canvas(modifier.size(240.dp)) {
        // Design space is 240x240 with the circle (diameter 107) centered;
        // scale uniformly to whatever size the caller gave us.
        val designScale = minOf(size.width, size.height) / 240f
        withTransform({
            translate(
                (size.width - 240f * designScale) / 2f,
                (size.height - 240f * designScale) / 2f,
            )
            scale(designScale, designScale, pivot = Offset.Zero)
        }) {
            val circleCenter = Offset(120f, 120f)
            val circleRadius = 107f / 2f

            // pulsing rings, fading out as they expand
            for (ring in listOf(firstRing.value, secondRing.value, thirdRing.value)) {
                if (ring > 0f && ring < 1f) drawCircle(
                    Slate.copy(alpha = 0.4f * (1f - ring)),
                    radius = circleRadius * (1f + 1.05f * ring),
                    center = circleCenter,
                    style = Stroke(2f)
                )
            }

            // floating hearts
            drawFloatingHeart(firstHeart.value, x = 74f, baseY = 44f, heartWidth = 18f, color = Slate)
            drawFloatingHeart(secondHeart.value, x = 148f, baseY = 52f, heartWidth = 13f, color = BlushPink)

            // the circle breathes gently while listening
            withTransform({ scale(breatheScale.value, breatheScale.value, circleCenter) }) {
                drawCircle(Slate, circleRadius, circleCenter)
            }

            // ---- mascot face, bobbing with the breathing circle ----
            withTransform({
                scale(breatheScale.value, breatheScale.value, circleCenter)
                translate(0f, bobOffsetY.value)
            }) {
                val faceTop = circleCenter.y - 23f

                // Brows: path "M4 14 Q 20 2 36 12" in a 40x18 box, scaled to 22x10.
                val browScale = 22f / 40f
                val browStroke = Stroke(5f * browScale, cap = StrokeCap.Round)
                fun drawBrow(left: Float, flip: Boolean) {
                    drawPath(Path().apply {
                        if (!flip) {
                            moveTo(left + 4f * browScale, faceTop + 14f * browScale)
                            quadraticBezierTo(left + 20f * browScale, faceTop + 2f * browScale, left + 36f * browScale, faceTop + 12f * browScale)
                        } else {
                            moveTo(left + 36f * browScale, faceTop + 14f * browScale)
                            quadraticBezierTo(left + 20f * browScale, faceTop + 2f * browScale, left + 4f * browScale, faceTop + 12f * browScale)
                        }
                    }, Cream, style = browStroke)
                }
                drawBrow(circleCenter.x - 8f - 22f, flip = false)
                drawBrow(circleCenter.x + 8f, flip = true)

                // Eyes: diameter 20, gap 10, just below the brows.
                val eyeTop = faceTop + 12f
                val eyeDiameter = 20f
                for (side in listOf(-1f, 1f)) {
                    val eyeCenter = Offset(
                        circleCenter.x + side * (5f + eyeDiameter / 2f),
                        eyeTop + eyeDiameter / 2f,
                    )
                    withTransform({ scale(1f, blinkScaleY.value, eyeCenter) }) {
                        drawCircle(Color.White, eyeDiameter / 2f, eyeCenter)
                        val pupilRadius = 0.48f * eyeDiameter / 2f
                        val pupilCenter = Offset(eyeCenter.x, eyeCenter.y - 0.04f * eyeDiameter / 2f)
                        drawCircle(Pupil, pupilRadius, pupilCenter)
                        // tiny white highlight, up-left of the pupil center
                        drawCircle(
                            Color.White,
                            0.34f * pupilRadius,
                            Offset(pupilCenter.x - 0.34f * pupilRadius, pupilCenter.y - 0.42f * pupilRadius),
                        )
                    }
                }

                // Blush: 11x7 soft ovals slightly overlapping the eye row bottom.
                val blushCenterY = eyeTop + eyeDiameter + 1f
                for (side in listOf(-1f, 1f)) {
                    val blushCenter = Offset(circleCenter.x + side * (17f + 5.5f), blushCenterY)
                    withTransform({ scale(1f, 7f / 11f, blushCenter) }) {
                        drawCircle(
                            Brush.radialGradient(
                                0.55f to BlushPink.copy(alpha = 0.9f), 1f to BlushPink.copy(alpha = 0f),
                                center = blushCenter, radius = 5.5f,
                            ),
                            radius = 5.5f, center = blushCenter,
                        )
                    }
                }

                // Smile: path "M5 5 Q 20 18 35 5" in a 40x20 box, scaled to 20x10.
                val smileScale = 20f / 40f
                val smileLeft = circleCenter.x - 10f
                val smileTop = blushCenterY + 5f
                drawPath(Path().apply {
                    moveTo(smileLeft + 5f * smileScale, smileTop + 5f * smileScale)
                    quadraticBezierTo(smileLeft + 20f * smileScale, smileTop + 18f * smileScale, smileLeft + 35f * smileScale, smileTop + 5f * smileScale)
                }, Cream, style = Stroke(5f * smileScale, cap = StrokeCap.Round))
            }
        }
    }
}

// One floating heart: rises 78px while fading in then out; scale grows 0.5 → 1.05.
// The shape is a 40x40 design-space heart scaled by heartWidth/40.
private fun DrawScope.drawFloatingHeart(
    progress: Float,
    x: Float,
    baseY: Float,
    heartWidth: Float,
    color: Color,
) {
    if (progress <= 0f || progress >= 1f) return
    val alpha =
        if (progress < 0.18f) (progress / 0.18f) * 0.95f
        else 0.95f * (1f - (progress - 0.18f) / 0.82f)
    val heartScale = 0.5f + 0.55f * progress
    val y = baseY - 78f * progress
    val unit = heartWidth / 40f
    val heartCenter = Offset(x + heartWidth / 2f, y + heartWidth * 0.45f)
    withTransform({ scale(heartScale, heartScale, heartCenter) }) {
        drawPath(Path().apply {
            moveTo(x + 20f * unit, y + 34f * unit)
            cubicTo(x + 7f * unit, y + 24f * unit, x + 2f * unit, y + 17f * unit, x + 2f * unit, y + 10.5f * unit)
            cubicTo(x + 2f * unit, y + 5f * unit, x + 6f * unit, y + 2f * unit, x + 10.5f * unit, y + 2f * unit)
            cubicTo(x + 14.5f * unit, y + 2f * unit, x + 18f * unit, y + 4.5f * unit, x + 20f * unit, y + 8f * unit)
            cubicTo(x + 22f * unit, y + 4.5f * unit, x + 25.5f * unit, y + 2f * unit, x + 29.5f * unit, y + 2f * unit)
            cubicTo(x + 34f * unit, y + 2f * unit, x + 38f * unit, y + 5f * unit, x + 38f * unit, y + 10.5f * unit)
            cubicTo(x + 38f * unit, y + 17f * unit, x + 33f * unit, y + 24f * unit, x + 20f * unit, y + 34f * unit)
            close()
        }, color.copy(alpha = alpha))
    }
}
