package com.voxgest.app.avatar

import android.animation.Animator
import android.animation.ValueAnimator
import android.os.Handler
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.PI
import kotlin.math.sin

class AvatarAnimator(
    private val handler: Handler,
    private val onPose: (AvatarPose) -> Unit
) {
    private val pose = AvatarPose()
    private val idlePose = AvatarPose()
    private val targetPose = AvatarPose()
    private val letterPose = FingerPose()
    private val interpolator = AccelerateDecelerateInterpolator()
    private var activeAnimator: ValueAnimator? = null
    private var generation = 0

    fun playIdle() {
        generation += 1
        val localGeneration = generation
        activeAnimator?.cancel()
        pose.resetToIdle()
        activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = IDLE_LOOP_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = null
            addUpdateListener { animator ->
                if (localGeneration != generation) return@addUpdateListener
                applyIdle(animator.animatedFraction)
                onPose(pose)
            }
            start()
        }
    }

    fun playClip(clip: AvatarClip, onFinished: (() -> Unit)? = null) {
        if (clip == AvatarClip.IDLE) {
            playIdle()
            onFinished?.invoke()
            return
        }
        generation += 1
        val localGeneration = generation
        activeAnimator?.cancel()
        activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = clip.durationMs
            interpolator = this@AvatarAnimator.interpolator
            addUpdateListener { animator ->
                if (localGeneration != generation) return@addUpdateListener
                applyClip(clip, animator.animatedFraction)
                onPose(pose)
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit
                override fun onAnimationRepeat(animation: Animator) = Unit
                override fun onAnimationCancel(animation: Animator) = Unit
                override fun onAnimationEnd(animation: Animator) {
                    if (localGeneration == generation) onFinished?.invoke()
                }
            })
            start()
        }
    }

    fun playSequence(clips: List<AvatarClip>, gapMs: Long = 200L) {
        generation += 1
        val localGeneration = generation
        activeAnimator?.cancel()
        if (clips.isEmpty()) {
            playIdle()
            return
        }
        fun playAt(index: Int) {
            if (localGeneration != generation) return
            if (index >= clips.size) {
                playIdle()
                return
            }
            playClip(clips[index]) {
                if (localGeneration == generation) {
                    handler.postDelayed({ playAt(index + 1) }, gapMs)
                }
            }
        }
        playAt(0)
    }

    fun stop() {
        generation += 1
        activeAnimator?.cancel()
        activeAnimator = null
        pose.resetToIdle()
        onPose(pose)
    }

    fun release() {
        stop()
    }

    private fun applyIdle(fraction: Float) {
        pose.resetToIdle()
        pose.chestBreath = 1f + 0.02f * sin((fraction * 3f * TWO_PI).toDouble()).toFloat().coerceAtLeast(0f)
        pose.headTilt = -2f * kotlin.math.cos((fraction * 2f * TWO_PI).toDouble()).toFloat()
    }

    private fun applyClip(clip: AvatarClip, fraction: Float) {
        idlePose.resetToIdle()
        targetPose.resetToIdle()
        when (clip) {
            AvatarClip.HELLO -> hello(fraction)
            AvatarClip.THANKYOU -> thankYou(fraction)
            AvatarClip.WATER -> water(fraction)
            AvatarClip.EAT -> eat(fraction)
            AvatarClip.MY -> my(fraction)
            AvatarClip.NAME -> name(fraction)
            AvatarClip.YOUR -> your(fraction)
            AvatarClip.WHAT -> what(fraction)
            AvatarClip.YOU -> you(fraction)
            AvatarClip.OKAY -> okay(fraction)
            is AvatarClip.LETTER -> letter(clip.letter, fraction)
            AvatarClip.IDLE -> applyIdle(fraction)
        }
    }

    private fun hello(t: Float) {
        raisedRight(-120f, 160f, 0f)
        targetPose.rightFingers.setAll(0f)
        val wave = if (t in 0.25f..0.78f) sin(((t - 0.25f) / 0.53f * 4f * TWO_PI).toDouble()).toFloat() * 20f else 0f
        targetPose.rightHand.angleDeg = wave
        blendInOut(t)
    }

    private fun thankYou(t: Float) {
        raisedRight(-80f + 35f * middlePhase(t), 118f, -18f)
        targetPose.rightFingers.setAll(0.05f)
        targetPose.rightHandOffsetY = 0.05f * middlePhase(t)
        blendInOut(t)
    }

    private fun water(t: Float) {
        val bounce = sin((t * 6f * PI).toDouble()).toFloat() * 8f
        raisedRight(-72f + bounce, 132f, -8f)
        targetPose.rightFingers.setAll(0.6f, thumb = 0.7f)
        targetPose.rightFingers.indexCurl = 0f
        targetPose.rightFingers.middleCurl = 0f
        targetPose.rightFingers.ringCurl = 0f
        targetPose.rightFingers.spread = 0.22f
        blendInOut(t)
    }

    private fun eat(t: Float) {
        val pull = sin((t * 4f * PI).toDouble()).toFloat() * 18f
        raisedRight(-80f, 125f - pull, -8f)
        targetPose.rightFingers.setAll(0.55f, thumb = 0.55f)
        blendInOut(t)
    }

    private fun my(t: Float) {
        raisedRight(-46f, 124f, 54f)
        targetPose.rightFingers.setAll(0.02f)
        targetPose.rightHandOffsetX = -0.08f
        blendHold(t, 0.25f, 0.65f)
    }

    private fun name(t: Float) {
        raisedRight(-50f, 120f, -2f)
        targetPose.leftUpperArm.angleDeg = 214f
        targetPose.leftForearm.angleDeg = -116f
        targetPose.leftHand.angleDeg = -8f
        targetPose.rightFingers.setAll(0.3f, thumb = 0.7f)
        targetPose.rightFingers.indexCurl = 0f
        targetPose.rightFingers.middleCurl = 0f
        targetPose.leftFingers.copyFrom(targetPose.rightFingers)
        targetPose.rightHandOffsetY = if (t < 0.5f) tapPulse(t * 2f) else tapPulse((t - 0.5f) * 2f)
        blendInOut(t)
    }

    private fun your(t: Float) {
        raisedRight(-8f, 18f, 0f)
        targetPose.rightFingers.setAll(0f)
        blendHold(t, 0.2f, 0.72f)
    }

    private fun what(t: Float) {
        raisedRight(28f, 8f, 90f)
        targetPose.rightFingers.setAll(0f)
        targetPose.rightHand.angleDeg += sin((t * 4f * PI).toDouble()).toFloat() * 15f
        blendInOut(t)
    }

    private fun you(t: Float) {
        raisedRight(-4f, 8f, 0f)
        pointIndex(targetPose.rightFingers)
        blendHold(t, 0.16f, 0.72f)
    }

    private fun okay(t: Float) {
        raisedRight(-42f, 78f, 8f)
        targetPose.rightFingers.setAll(0f, thumb = 0.5f)
        targetPose.rightFingers.indexCurl = 0.5f
        blendHold(t, 0.22f, 0.68f)
    }

    private fun letter(letter: Char, t: Float) {
        raisedRight(-60f, 92f, 0f)
        AvatarClip.configureLetter(letter, letterPose)
        targetPose.rightFingers.copyFrom(letterPose)
        when (letter.uppercaseChar()) {
            'J' -> targetPose.rightHand.angleDeg = -30f + 60f * t
            'Z' -> {
                pointIndex(targetPose.rightFingers)
                when {
                    t < 0.33f -> {
                        targetPose.rightHandOffsetX = t / 0.33f * 0.12f
                    }
                    t < 0.66f -> {
                        val p = (t - 0.33f) / 0.33f
                        targetPose.rightHandOffsetX = 0.12f - p * 0.18f
                        targetPose.rightHandOffsetY = p * 0.08f
                    }
                    else -> {
                        val p = (t - 0.66f) / 0.34f
                        targetPose.rightHandOffsetX = -0.06f + p * 0.18f
                        targetPose.rightHandOffsetY = 0.08f
                    }
                }
            }
            'P' -> targetPose.rightHand.angleDeg = 85f
            'Q' -> targetPose.rightHand.angleDeg = 65f
            'G', 'H' -> targetPose.rightHand.angleDeg = 72f
        }
        blendHold(t, 0.18f, 0.78f)
    }

    private fun raisedRight(upper: Float, forearm: Float, hand: Float) {
        targetPose.rightUpperArm.angleDeg = upper
        targetPose.rightForearm.angleDeg = forearm
        targetPose.rightHand.angleDeg = hand
    }

    private fun pointIndex(fingers: FingerPose) {
        fingers.setAll(0.9f, thumb = 0.7f)
        fingers.indexCurl = 0f
    }

    private fun blendInOut(t: Float) {
        val amount = when {
            t < 0.22f -> t / 0.22f
            t > 0.82f -> 1f - ((t - 0.82f) / 0.18f)
            else -> 1f
        }.coerceIn(0f, 1f)
        lerpPose(idlePose, targetPose, amount, pose)
    }

    private fun blendHold(t: Float, inEnd: Float, outStart: Float) {
        val amount = when {
            t < inEnd -> t / inEnd
            t > outStart -> 1f - ((t - outStart) / (1f - outStart))
            else -> 1f
        }.coerceIn(0f, 1f)
        lerpPose(idlePose, targetPose, amount, pose)
    }

    private fun middlePhase(t: Float): Float {
        return when {
            t < 0.25f -> 0f
            t > 0.80f -> 1f
            else -> (t - 0.25f) / 0.55f
        }.coerceIn(0f, 1f)
    }

    private fun tapPulse(t: Float): Float {
        return sin((t.coerceIn(0f, 1f) * PI).toDouble()).toFloat() * 0.07f
    }

    private fun lerpPose(from: AvatarPose, to: AvatarPose, t: Float, out: AvatarPose) {
        out.rightUpperArm.angleDeg = lerp(from.rightUpperArm.angleDeg, to.rightUpperArm.angleDeg, t)
        out.rightForearm.angleDeg = lerp(from.rightForearm.angleDeg, to.rightForearm.angleDeg, t)
        out.rightHand.angleDeg = lerp(from.rightHand.angleDeg, to.rightHand.angleDeg, t)
        out.leftUpperArm.angleDeg = lerp(from.leftUpperArm.angleDeg, to.leftUpperArm.angleDeg, t)
        out.leftForearm.angleDeg = lerp(from.leftForearm.angleDeg, to.leftForearm.angleDeg, t)
        out.leftHand.angleDeg = lerp(from.leftHand.angleDeg, to.leftHand.angleDeg, t)
        lerpFingers(from.rightFingers, to.rightFingers, t, out.rightFingers)
        lerpFingers(from.leftFingers, to.leftFingers, t, out.leftFingers)
        out.headTilt = lerp(from.headTilt, to.headTilt, t)
        out.chestBreath = lerp(from.chestBreath, to.chestBreath, t)
        out.rightHandOffsetX = lerp(from.rightHandOffsetX, to.rightHandOffsetX, t)
        out.rightHandOffsetY = lerp(from.rightHandOffsetY, to.rightHandOffsetY, t)
        out.leftHandOffsetX = lerp(from.leftHandOffsetX, to.leftHandOffsetX, t)
        out.leftHandOffsetY = lerp(from.leftHandOffsetY, to.leftHandOffsetY, t)
    }

    private fun lerpFingers(from: FingerPose, to: FingerPose, t: Float, out: FingerPose) {
        out.thumbCurl = lerp(from.thumbCurl, to.thumbCurl, t)
        out.indexCurl = lerp(from.indexCurl, to.indexCurl, t)
        out.middleCurl = lerp(from.middleCurl, to.middleCurl, t)
        out.ringCurl = lerp(from.ringCurl, to.ringCurl, t)
        out.pinkyCurl = lerp(from.pinkyCurl, to.pinkyCurl, t)
        out.thumbAngle = lerp(from.thumbAngle, to.thumbAngle, t)
        out.spread = lerp(from.spread, to.spread, t)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        private const val IDLE_LOOP_MS = 6000L
        private const val TWO_PI = (PI * 2.0).toFloat()
    }
}
