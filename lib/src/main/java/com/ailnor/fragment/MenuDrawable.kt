/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package com.ailnor.fragment

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import com.ailnor.core.Theme
import com.ailnor.core.Utilities
import com.ailnor.core.dp
import com.ailnor.core.dp2
import kotlin.math.abs

class MenuDrawable @JvmOverloads constructor(type: Int = TYPE_DEFAULT) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var reverseAngle = false
    private var lastFrameTime: Long = 0
    private val animationInProgress = false
    private var finalRotation = 0f
    var currentRotation = 0f
        private set
    private var currentAnimationTime = 0
    private var rotateToBack = true
    private val interpolator = DecelerateInterpolator()
    private var iconColor = 0
    private var backColor = 0
    private val rect = RectF()
    private var type: Int
    private var previousType: Int
    private var typeAnimationProgress: Float
    private var downloadRadOffset = 0f
    private var downloadProgress = 0f
    private var animatedDownloadProgress = 0f
    private var downloadProgressAnimationStart = 0f
    private var downloadProgressTime = 0f
    private var miniIcon = false
    private var alpha = 255
    fun setRotateToBack(value: Boolean) {
        rotateToBack = value
    }

    fun setRotation(rotation: Float, animated: Boolean) {
        lastFrameTime = 0
        if (currentRotation == 1f) {
            reverseAngle = true
        } else if (currentRotation == 0f) {
            reverseAngle = false
        }
        lastFrameTime = 0
        if (animated) {
            currentAnimationTime = if (currentRotation < rotation) {
                (currentRotation * 200).toInt()
            } else {
                ((1.0f - currentRotation) * 200).toInt()
            }
            lastFrameTime = SystemClock.elapsedRealtime()
            finalRotation = rotation
        } else {
            currentRotation = rotation
            finalRotation = currentRotation
        }
        invalidateSelf()
    }

    fun setType(value: Int, animated: Boolean) {
        if (type == value) {
            return
        }
        previousType = type
        type = value
        typeAnimationProgress = if (animated) {
            0.0f
        } else {
            1.0f
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val newTime = SystemClock.elapsedRealtime()
        val dt = newTime - lastFrameTime
        if (currentRotation != finalRotation) {
            if (lastFrameTime != 0L) {
                currentAnimationTime += dt.toInt()
                currentRotation = if (currentAnimationTime >= 200) {
                    finalRotation
                } else {
                    if (currentRotation < finalRotation) {
                        interpolator.getInterpolation(currentAnimationTime / 200.0f) * finalRotation
                    } else {
                        1.0f - interpolator.getInterpolation(currentAnimationTime / 200.0f)
                    }
                }
            }
            invalidateSelf()
        }
        if (typeAnimationProgress < 1.0f) {
            typeAnimationProgress += dt / 200.0f
            if (typeAnimationProgress > 1.0f) {
                typeAnimationProgress = 1.0f
            }
            invalidateSelf()
        }
        lastFrameTime = newTime
        canvas.save()
        canvas.translate(
            intrinsicWidth / 2 - dp(9f),
            (intrinsicHeight / 2).toFloat()
        )
        val endYDiff: Float
        val endXDiff: Float
        val startYDiff: Float
        val startXDiff: Float
        val color1 =
            if (iconColor == 0) Theme.black else iconColor
        var backColor1 =
            if (backColor == 0) Theme.black else backColor
        var diffUp = 0f
        var diffMiddle = 0f
        if (type == TYPE_DEFAULT) {
            if (previousType != TYPE_DEFAULT) {
                diffUp = dp(9) * (1.0f - typeAnimationProgress)
                diffMiddle = dp(7) * (1.0f - typeAnimationProgress)
            }
        } else {
            if (previousType == TYPE_DEFAULT) {
                diffUp = dp(9) * typeAnimationProgress * (1.0f - currentRotation)
                diffMiddle =
                    dp(7) * typeAnimationProgress * (1.0f - currentRotation)
            } else {
                diffUp = dp(9) * (1.0f - currentRotation)
                diffMiddle = dp(7) * (1.0f - currentRotation)
            }
        }
        if (rotateToBack) {
            canvas.rotate(
                currentRotation * if (reverseAngle) -180 else 180,
                dp(9f),
                0f
            )
            paint.color = color1
            paint.alpha = alpha
            canvas.drawLine(
                0f,
                0f,
                dp(18) - dp(3.0f) * currentRotation - diffMiddle,
                0f,
                paint
            )
            endYDiff =
                dp(5) * (1 - abs(currentRotation)) - dp(0.5f) * abs(
                    currentRotation
                )
            endXDiff =
                dp(18) - dp(2.5f) * abs(currentRotation)
            startYDiff =
                dp(5) + dp(2.0f) * abs(currentRotation)
            startXDiff = dp(7.5f) * abs(currentRotation)
        } else {
            canvas.rotate(
                currentRotation * if (reverseAngle) -225 else 135,
                dp(9f),
                0f
            )
            if (miniIcon) {
                paint.color = color1
                paint.alpha = alpha
                canvas.drawLine(
                    dp2(2) * (1 - abs(currentRotation)) + dp(
                        1
                    ) * currentRotation,
                    0f,
                    dp2(16) * (1f - currentRotation) + dp(17) * currentRotation - diffMiddle,
                    0f,
                    paint
                )
                endYDiff =
                    dp2(5) * (1 - abs(currentRotation)) - dp2(
                        0.5f
                    ) * abs(currentRotation)
                endXDiff =
                    dp2(16) * (1 - abs(currentRotation)) + dp2(
                        9
                    ) * abs(currentRotation)
                startYDiff = dp2(5) + dp2(3.0f) * abs(
                    currentRotation
                )
                startXDiff =
                    dp2(2) + dp2(7) * abs(currentRotation)
            } else {
                val color2: Int = Theme.black
                val backColor2: Int = Theme.black
                backColor1 =
                    Utilities.getOffsetColor(backColor1, backColor2, currentRotation, 1.0f)
                paint.color = Utilities.getOffsetColor(color1, color2, currentRotation, 1.0f)
                paint.alpha = alpha
                canvas.drawLine(
                    dp(1) * currentRotation,
                    0f,
                    dp(18) - dp(1) * currentRotation - diffMiddle,
                    0f,
                    paint
                )
                endYDiff =
                    dp(5) * (1 - abs(currentRotation)) - dp(
                        0.5f
                    ) * abs(currentRotation)
                endXDiff =
                    dp(18) - dp(9) * abs(currentRotation)
                startYDiff =
                    dp(5) + dp(3.0f) * abs(currentRotation)
                startXDiff = dp(9) * abs(currentRotation)
            }
        }
        if (miniIcon) {
            canvas.drawLine(startXDiff, -startYDiff, endXDiff, -endYDiff, paint)
            canvas.drawLine(startXDiff, startYDiff, endXDiff, endYDiff, paint)
        } else {
            canvas.drawLine(startXDiff, -startYDiff, endXDiff - diffUp, -endYDiff, paint)
            canvas.drawLine(startXDiff, startYDiff, endXDiff, endYDiff, paint)
        }
        if (type != TYPE_DEFAULT && currentRotation != 1.0f || previousType != TYPE_DEFAULT && typeAnimationProgress != 1.0f) {
            val cx: Float = dp(9f + 8f)
            val cy: Float = -dp(4.5f)
            var rad: Float = Utilities.density * 5.5f
            canvas.scale(1.0f - currentRotation, 1.0f - currentRotation, cx, cy)
            if (type == TYPE_DEFAULT) {
                rad *= 1.0f - typeAnimationProgress
            }
            backPaint.color = backColor1
            backPaint.alpha = alpha
            canvas.drawCircle(cx, cy, rad, paint)
            if (type == TYPE_UDPATE_AVAILABLE || previousType == TYPE_UDPATE_AVAILABLE) {
                backPaint.strokeWidth = Utilities.density * 1.66f
                if (previousType == TYPE_UDPATE_AVAILABLE) {
                    backPaint.alpha = (alpha * (1.0f - typeAnimationProgress)).toInt()
                } else {
                    backPaint.alpha = alpha
                }
                canvas.drawLine(cx, cy - dp(2), cx, cy, backPaint)
                canvas.drawPoint(cx, cy + dp(2.5f), backPaint)
            }
            if (type == TYPE_UDPATE_DOWNLOADING || previousType == TYPE_UDPATE_DOWNLOADING) {
                backPaint.strokeWidth = dp(2f)
                if (previousType == TYPE_UDPATE_DOWNLOADING) {
                    backPaint.alpha = (alpha * (1.0f - typeAnimationProgress)).toInt()
                } else {
                    backPaint.alpha = alpha
                }
                val arcRad = Math.max(4f, 360 * animatedDownloadProgress)
                rect[cx - dp(3), cy - dp(3), cx + dp(
                    3
                )] = cy + dp(3)
                canvas.drawArc(rect, downloadRadOffset, arcRad, false, backPaint)
                downloadRadOffset += 360 * dt / 2500.0f
                downloadRadOffset = MediaActionDrawable.getCircleValue(downloadRadOffset)
                val progressDiff = downloadProgress - downloadProgressAnimationStart
                if (progressDiff > 0) {
                    downloadProgressTime += dt.toFloat()
                    if (downloadProgressTime >= 200.0f) {
                        animatedDownloadProgress = downloadProgress
                        downloadProgressAnimationStart = downloadProgress
                        downloadProgressTime = 0f
                    } else {
                        animatedDownloadProgress =
                            downloadProgressAnimationStart + progressDiff * interpolator.getInterpolation(
                                downloadProgressTime / 200.0f
                            )
                    }
                }
                invalidateSelf()
            }
        }
        canvas.restore()
    }

    fun setUpdateDownloadProgress(value: Float, animated: Boolean) {
        if (!animated) {
            animatedDownloadProgress = value
            downloadProgressAnimationStart = value
        } else {
            if (animatedDownloadProgress > value) {
                animatedDownloadProgress = value
            }
            downloadProgressAnimationStart = animatedDownloadProgress
        }
        downloadProgress = value
        downloadProgressTime = 0f
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        if (this.alpha != alpha) {
            this.alpha = alpha
            paint.alpha = alpha
            backPaint.alpha = alpha
            invalidateSelf()
        }
    }

    override fun setColorFilter(cf: ColorFilter?) {}
    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun getIntrinsicWidth(): Int {
        return dp(24)
    }

    override fun getIntrinsicHeight(): Int {
        return dp(24)
    }

    fun setIconColor(iconColor: Int) {
        this.iconColor = iconColor
    }

    fun setBackColor(backColor: Int) {
        this.backColor = backColor
    }

    fun setRoundCap() {
        paint.strokeCap = Paint.Cap.ROUND
    }

    fun setMiniIcon(miniIcon: Boolean) {
        this.miniIcon = miniIcon
    }

    companion object {
        var TYPE_DEFAULT = 0
        var TYPE_UDPATE_AVAILABLE = 1
        var TYPE_UDPATE_DOWNLOADING = 2
    }

    init {
        paint.strokeWidth = dp(2f)
        backPaint.strokeWidth = Utilities.density * 1.66f
        backPaint.strokeCap = Paint.Cap.ROUND
        backPaint.style = Paint.Style.STROKE
        previousType = TYPE_DEFAULT
        this.type = type
        typeAnimationProgress = 1.0f
    }
}