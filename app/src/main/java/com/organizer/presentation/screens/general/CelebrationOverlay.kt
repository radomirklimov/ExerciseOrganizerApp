package com.organizer.presentation.screens.general

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Text shown by [CelebrationOverlay].
 *
 * Deliberately not a data class: every instance is a separate celebration
 * event. With value equality Compose would drop a second trigger whose title
 * and subtitle match the previous one (adding the same exercise to the same
 * workout twice), so the effect would only play once.
 */
class CelebrationMessage(
    val title: String,
    val subtitle: String? = null,
)

/**
 * How long the pieces keep falling. The card hides after three seconds; the
 * pieces continue a little longer and faint out on their own.
 */
private const val ConfettiDurationSeconds = 6f

/** Last stretch of [ConfettiDurationSeconds] used to fade every piece out. */
private const val ConfettiGlobalFadeStartSeconds = 5.2f

private const val ToastVisibleMillis = 3_000L

/**
 * Success feedback drawn on top of a screen: a card that slides down from the
 * top edge and a shower of small, shiny green pieces that fall slowly and
 * fade away.
 *
 * Pass a new [message] to trigger the effect and set it back to null when the
 * card is dismissed. The layer never consumes touches outside the card, so
 * the screen underneath stays usable.
 */
@Composable
fun CelebrationOverlay(
    message: CelebrationMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bumped for every new message. The confetti and the auto-hide timer use
    // it as a key, so they keep working after the message is cleared and a
    // repeated message restarts the effect from scratch.
    var triggerToken by remember { mutableIntStateOf(0) }
    // Kept while the card slides out, when `message` is already null.
    var visibleMessage by remember { mutableStateOf<CelebrationMessage?>(null) }
    // Compared by identity, not by value: adding the same exercise to the same
    // workout twice is still a new event. The referential policy is what makes
    // the new instance stick even if its text is unchanged.
    var lastTrigger by remember {
        mutableStateOf<CelebrationMessage?>(null, referentialEqualityPolicy())
    }

    SideEffect {
        val incoming = message
        if (incoming != null && incoming !== lastTrigger) {
            lastTrigger = incoming
            visibleMessage = incoming
            triggerToken++
        }
    }

    LaunchedEffect(triggerToken) {
        if (triggerToken == 0) return@LaunchedEffect
        delay(ToastVisibleMillis)
        onDismiss()
    }

    Box(modifier = modifier.fillMaxSize()) {
        ConfettiLayer(token = triggerToken)

        AnimatedVisibility(
            visible = message != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .padding(horizontal = 16.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f),
                ),
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(durationMillis = 300),
            ) + fadeOut(animationSpec = tween(durationMillis = 300)),
        ) {
            // While visible the current message is used directly; the retained
            // copy keeps the card rendered during the slide-out animation.
            val shown = message ?: visibleMessage
            shown?.let { CelebrationToast(message = it, onDismiss = onDismiss) }
        }
    }
}

@Composable
private fun CelebrationToast(message: CelebrationMessage, onDismiss: () -> Unit) {
    val colors = AppTheme.colors

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.successBorder),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.successSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = message.subtitle
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.success,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Confetti
// ---------------------------------------------------------------------------

private enum class ConfettiShape { Rect, Diamond }

/** Jewel-toned greens with a few near-white sparkles. */
private val ConfettiColors = listOf(
    Color(0xFF10B981),
    Color(0xFF22C55E),
    Color(0xFF4ADE80),
    Color(0xFF34D399),
    Color(0xFFA7F3D0),
    Color(0xFF059669),
    Color(0xFFBBF7D0),
)

/**
 * One piece of confetti described analytically in fractions of the canvas:
 * every value is derived from its age, so nothing has to be mutated per
 * frame and no temporary objects are created while animating.
 */
private data class ConfettiParticle(
    val startX: Float,
    val startY: Float,
    val size: Dp,
    val fallSpeed: Float,
    val driftSpeed: Float,
    val swayWidth: Float,
    val swaySpeed: Float,
    val swayPhase: Float,
    val rotationX: Float,
    val rotationY: Float,
    val rotationSpeedX: Float,
    val rotationSpeedY: Float,
    val glintPhase: Float,
    val glintSpeed: Float,
    val fadeRate: Float,
    val fadeStartAge: Float,
    val delay: Float,
    val color: Color,
    val shape: ConfettiShape,
)

/** One shower of confetti, started by one celebration trigger. */
private class ConfettiBurst(
    val particles: List<ConfettiParticle>,
) {
    /** Frame time of the first frame this shower was drawn on; 0 until then. */
    var startNanos: Long = 0L

    fun ageSeconds(nowNanos: Long): Float =
        if (startNanos == 0L) -1f
        else (nowNanos - startNanos) / 1_000_000_000f

    fun isRunning(nowNanos: Long): Boolean =
        startNanos != 0L && ageSeconds(nowNanos) < ConfettiDurationSeconds
}

/** Upper bound for showers falling at the same time. */
private const val MaxConcurrentBursts = 3

@Composable
private fun ConfettiLayer(token: Int, modifier: Modifier = Modifier) {
    // Every trigger adds a shower instead of replacing the one that is still in
    // the air. A repeated celebration therefore starts over smoothly: the old
    // pieces keep drifting down while the new ones enter from the top.
    val bursts = remember { mutableStateListOf<ConfettiBurst>() }

    // Absolute frame time. Restarting the frame loop for a new trigger must not
    // reset the age of showers that are already falling.
    var clockNanos by remember { mutableLongStateOf(0L) }

    val burst = remember(token) {
        if (token == 0) null
        else ConfettiBurst(createConfetti(Random(token * 7919 + 17)))
    }

    LaunchedEffect(token) {
        val newBurst = burst ?: return@LaunchedEffect
        bursts += newBurst
        while (bursts.size > MaxConcurrentBursts) {
            bursts.removeAt(0)
        }

        while (true) {
            val frameNanos = withFrameNanos { it }
            clockNanos = frameNanos
            if (newBurst.startNanos == 0L) {
                newBurst.startNanos = frameNanos
            }
            if (bursts.none { it.isRunning(frameNanos) }) break
        }

        // All showers have finished. A new trigger restarts this effect before
        // it gets here, in which case the older bursts are kept.
        bursts.clear()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val now = clockNanos
        if (now == 0L) return@Canvas

        bursts.forEach { burst ->
            val time = burst.ageSeconds(now)
            if (time < 0f || time >= ConfettiDurationSeconds) return@forEach

            val globalAlpha = (
                (ConfettiDurationSeconds - time) /
                    (ConfettiDurationSeconds - ConfettiGlobalFadeStartSeconds)
                ).coerceIn(0f, 1f)

            drawConfetti(particles = burst.particles, elapsed = time, globalAlpha = globalAlpha)
        }
    }
}

private fun createConfetti(random: Random): List<ConfettiParticle> {
    val particles = ArrayList<ConfettiParticle>(210)

    fun wave(count: Int, delay: Float) {
        repeat(count) {
            val swaySpeed = random.nextFloat() * 1.4f + 0.9f
            val startY = -random.nextFloat() * 0.30f - 0.03f
            val fallSpeed = random.nextFloat() * 0.06f + 0.06f
            // Pieces start to faint shortly after they enter and are gone in a
            // couple of seconds, so the burst does not outstay the toast.
            val fadeRate = random.nextFloat() * 0.30f + 0.42f
            val timeToFade = random.nextFloat() * 1.2f + 1.3f

            particles += ConfettiParticle(
                startX = random.nextFloat(),
                startY = startY,
                size = (random.nextFloat() * 5f + 4f).dp,
                fallSpeed = fallSpeed,
                driftSpeed = (random.nextFloat() - 0.5f) * 0.02f,
                swayWidth = random.nextFloat() * 0.03f + 0.012f,
                swaySpeed = swaySpeed,
                swayPhase = random.nextFloat() * (Math.PI * 2).toFloat(),
                rotationX = random.nextFloat() * (Math.PI * 2).toFloat(),
                rotationY = random.nextFloat() * (Math.PI * 2).toFloat(),
                rotationSpeedX = (random.nextFloat() - 0.5f) * 3.6f,
                rotationSpeedY = (random.nextFloat() - 0.5f) * 4.8f,
                glintPhase = random.nextFloat() * (Math.PI * 2).toFloat(),
                glintSpeed = random.nextFloat() * 3.2f + 1.6f,
                fadeRate = fadeRate,
                fadeStartAge = timeToFade,
                delay = delay,
                color = ConfettiColors[random.nextInt(ConfettiColors.size)],
                shape = if (random.nextFloat() > 0.4f) ConfettiShape.Rect else ConfettiShape.Diamond,
            )
        }
    }

    // Three overlapping bursts keep the fall continuous instead of dumping
    // every piece on screen at once.
    wave(count = 95, delay = 0f)
    wave(count = 65, delay = 0.3f)
    wave(count = 50, delay = 0.7f)
    return particles
}

private fun DrawScope.drawConfetti(
    particles: List<ConfettiParticle>,
    elapsed: Float,
    globalAlpha: Float,
) {
    particles.forEach { particle ->
        val age = elapsed - particle.delay
        if (age <= 0f) return@forEach

        val y = particle.startY + particle.fallSpeed * age
        if (y < -0.1f || y > 1.05f) return@forEach

        val alpha = if (age <= particle.fadeStartAge) {
            1f
        } else {
            1f - particle.fadeRate * (age - particle.fadeStartAge)
        } * globalAlpha
        if (alpha <= 0.02f) return@forEach

        // Integral of the sine sway: a smooth left-right oscillation that
        // starts where the piece is instead of jumping sideways.
        val sway = particle.swayWidth / particle.swaySpeed *
            (cos(particle.swayPhase) - cos(particle.swayPhase + particle.swaySpeed * age))
        val x = particle.startX + particle.driftSpeed * age + sway
        if (x < -0.1f || x > 1.1f) return@forEach

        val sidePx = particle.size.toPx()
        val center = Offset(x = x * size.width, y = y * size.height)

        // Occasional specular highlight: the piece catches the light and turns
        // whiter. The aura is drawn in the piece's own shape, so it flips and
        // moves with it instead of reading as a floating bubble.
        val glint = ((sin(particle.glintPhase + particle.glintSpeed * age) - 0.72f) / 0.28f)
            .coerceIn(0f, 1f)

        val color = lerp(particle.color, Color.White, 0.45f * glint).copy(alpha = alpha)
        val aura = particle.color.copy(alpha = alpha * 0.14f * glint)
        val scaleX = flipScale(cos(particle.rotationX + particle.rotationSpeedX * age))
        val scaleY = flipScale(cos(particle.rotationY + particle.rotationSpeedY * age))

        translate(left = center.x, top = center.y) {
            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero) {
                when (particle.shape) {
                    ConfettiShape.Rect -> {
                        val width = sidePx
                        val height = sidePx * 1.5f
                        if (glint > 0f) {
                            drawRect(
                                color = aura,
                                topLeft = Offset(x = -width, y = -height),
                                size = Size(width * 2f, height * 2f),
                            )
                        }
                        drawRect(
                            color = color,
                            topLeft = Offset(x = -width / 2f, y = -height / 2f),
                            size = Size(width, height),
                        )
                        if (glint > 0.5f) {
                            drawRect(
                                color = Color.White.copy(alpha = alpha * 0.9f * glint),
                                topLeft = Offset(x = -width / 4f, y = -height / 2f),
                                size = Size(width / 2f, height / 2f),
                            )
                        }
                    }

                    ConfettiShape.Diamond -> {
                        // A square rotated 45 degrees is a diamond and needs no
                        // Path allocation.
                        rotate(degrees = 45f, pivot = Offset.Zero) {
                            if (glint > 0f) {
                                drawRect(
                                    color = aura,
                                    topLeft = Offset(x = -sidePx * 1.5f, y = -sidePx * 1.5f),
                                    size = Size(sidePx * 3f, sidePx * 3f),
                                )
                            }
                            drawRect(
                                color = color,
                                topLeft = Offset(x = -sidePx, y = -sidePx),
                                size = Size(sidePx * 2f, sidePx * 2f),
                            )
                        }
                        if (glint > 0.5f) {
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * glint),
                                radius = sidePx * 0.5f,
                                center = Offset.Zero,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Edge-on pieces become a single line while flipping. A tiny minimum keeps
 * the drawing matrix valid without changing how the flip reads.
 */
private fun flipScale(value: Float): Float =
    if (abs(value) < 0.04f) 0.04f else value
