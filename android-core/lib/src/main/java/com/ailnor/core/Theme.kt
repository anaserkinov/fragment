package com.ailnor.core

import android.R
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.util.StateSet
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange
import androidx.core.graphics.ColorUtils

object Theme {

    var key_listSelector: String = "key_listSelector"

    var key_actionBarDefault: String = "key_actionBarDefault"

    var key_windowBackgroundWhiteBlackText: String = "key_windowBackgroundWhiteBlackText"

    var key_fastScrollText: String = "key_fastScrollText"

    var key_fastScrollActive: String = "key_fastScrollActive"

    var key_fastScrollInactive: String = "key_fastScrollInactive"

    var key_windowBackgroundWhite: String = "key_windowBackgroundWhite"

    @ColorInt
    const val transparent = 0x0

    @ColorInt
    const val white = -0x1

    @ColorInt
    const val black = -0x1000000

    @ColorInt
    const val red = -0x10000

    @ColorInt
    const val green = -0xff0100

    @ColorInt
    const val yellow = -0x100

    @ColorInt
    const val grey_400 = -0x3C3C3D

    @ColorInt
    const val grey_600 = -0x919192

    @ColorInt
    const val dark_charcoal = -0xcccccd

    @ColorInt
    const val anti_flash_white = -0xf0d0b

    @ColorInt
    const val cultured = -0x121213

    @ColorInt
    const val battleship_grey = -0x79797a

    @ColorInt
    const val platinum = -0x211d19


    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val defaultChatDrawables = HashMap<String, Drawable>()
    private val defaultChatPaints = HashMap<String, Paint>()


    fun init(
        colorPrimary: Int,
        colorOnPrimary: Int,
        colorSecondary: Int,
        colorBackground: Int,
        colorOnBackground: Int,
        colorSurface: Int,
        colorOnSurface: Int,
        appIcon64: Int,
        appIcon128: Int
    ) {

        Theme.colorPrimary = colorPrimary
        Theme.colorOnPrimary = colorOnPrimary
        Theme.colorSecondary = colorSecondary
        Theme.colorBackground = colorBackground
        Theme.colorOnBackground = colorOnBackground
        Theme.colorSurface = colorSurface
        Theme.colorOnSurface = colorOnSurface
        Theme.appIcon64 = appIcon64
        Theme.appIcon128 = appIcon128

        selectedPaint.color = Theme.colorPrimary
        selectedPaint
    }

    fun Int.alpha(@IntRange(from = 0L, to = 100L) factor: Int): Int {
        return ((factor * 255 / 100) shl 24) or (this and 0x00ffffff)
    }

    fun Int.darken(factor: Float) = ColorUtils.blendARGB(
        this, black, factor
    )
    fun Int.lighten(factor: Float) = ColorUtils.blendARGB(
        this, white, factor
    )

    @DrawableRes
    var appIcon64 = 0
        private set
    @DrawableRes
    var appIcon128 = 0
        private set
    @ColorInt
    var colorPrimary = 0x0
        private set
    @ColorInt
    var colorOnPrimary = 0x0
        private set
    @ColorInt
    var colorSecondary = 0x0
        private set
    @ColorInt
    var colorBackground = 0x0
        private set
    @ColorInt
    var colorOnBackground = 0x0
        private set
    @ColorInt
    var colorSurface = 0x0
        private set
    @ColorInt
    var colorOnSurface = 0x0
        private set

    interface ResourcesProvider{
        fun getColor(key: String): Int

        fun getDrawable(drawableKey: String?): Drawable? {
            return null
        }

        fun getPaint(paintKey: String?): Paint? {
            return null
        }
    }

    fun getColor(value: String): Int{
        return transparent
    }

    fun getThemeDrawable(drawableKey: String?): Drawable? {
        return defaultChatDrawables[drawableKey]
    }

    fun getThemePaint(paintKey: String?): Paint? {
        return defaultChatPaints[paintKey]
    }

    fun isCurrentThemeDark(): Boolean{
        return false
    }

    object AdaptiveRipple {
        const val RADIUS_TO_BOUNDS = -1f
        const val RADIUS_OUT_BOUNDS = -2f
        private val defaultBackgroundColorKey: String =
            key_windowBackgroundWhite

        fun circle(backgroundColorKey: String): Drawable {
            return circle(
                getColor(backgroundColorKey),
                RADIUS_TO_BOUNDS
            )
        }

        fun circle(backgroundColorKey: String, radius: Float): Drawable {
            return circle(getColor(backgroundColorKey), radius)
        }

        @JvmOverloads
        fun circle(
            backgroundColor: Int = getColor(
                defaultBackgroundColorKey
            ), radius: Float = RADIUS_TO_BOUNDS
        ): Drawable {
            return createCircle(calcRippleColor(backgroundColor), radius)
        }

        fun filledCircle(backgroundColorKey: String): Drawable {
            return filledCircle(
                null,
                getColor(backgroundColorKey),
                RADIUS_TO_BOUNDS
            )
        }

        fun filledCircle(background: Drawable?, backgroundColorKey: String): Drawable {
            return filledCircle(
                background,
                getColor(backgroundColorKey),
                RADIUS_TO_BOUNDS
            )
        }

        fun filledCircle(backgroundColorKey: String, radius: Float): Drawable {
            return filledCircle(
                null,
                getColor(backgroundColorKey),
                radius
            )
        }

        fun filledCircle(
            background: Drawable?,
            backgroundColorKey: String,
            radius: Float
        ): Drawable {
            return filledCircle(
                background,
                getColor(backgroundColorKey),
                radius
            )
        }

        fun filledCircle(backgroundColor: Int): Drawable {
            return filledCircle(null, backgroundColor, RADIUS_TO_BOUNDS)
        }

        fun filledCircle(backgroundColor: Int, radius: Float): Drawable {
            return filledCircle(null, backgroundColor, radius)
        }

        @JvmOverloads
        fun filledCircle(
            background: Drawable? = null,
            backgroundColor: Int = getColor(
                defaultBackgroundColorKey
            ),
            radius: Float = RADIUS_TO_BOUNDS
        ): Drawable {
            return createCircle(background, calcRippleColor(backgroundColor), radius)
        }

        fun rect(backgroundColorKey: String): Drawable {
            return rect(getColor(backgroundColorKey))
        }

        fun rect(backgroundColorKey: String, vararg radii: Float): Drawable {
            return rect(getColor(backgroundColorKey), *radii)
        }

        @JvmOverloads
        fun rect(
            backgroundColor: Int = getColor(
                defaultBackgroundColorKey
            )
        ): Drawable {
            return rect(backgroundColor, 0f)
        }

        fun rect(backgroundColor: Int, vararg radii: Float): Drawable {
            return createRect(0, calcRippleColor(backgroundColor), *radii)
        }

        fun filledRect(): Drawable {
            return filledRect(
                getColor(defaultBackgroundColorKey),
                0f
            )
        }

        fun filledRect(background: Drawable?): Drawable {
            val backgroundColor =
                if (background is ColorDrawable) background.color else getColor(
                    defaultBackgroundColorKey
                )
            return filledRect(background, backgroundColor, 0f)
        }

        fun filledRect(backgroundColorKey: String): Drawable {
            return filledRect(getColor(backgroundColorKey))
        }

        fun filledRect(background: Drawable?, backgroundColorKey: String): Drawable {
            return filledRect(
                background,
                getColor(backgroundColorKey)
            )
        }

        fun filledRect(backgroundColorKey: String, vararg radii: Float): Drawable {
            return filledRect(getColor(backgroundColorKey), *radii)
        }

        fun filledRect(
            background: Drawable?,
            backgroundColorKey: String,
            vararg radii: Float
        ): Drawable {
            return filledRect(
                background,
                getColor(backgroundColorKey),
                *radii
            )
        }

        fun filledRect(backgroundColor: Int): Drawable {
            return createRect(backgroundColor, calcRippleColor(backgroundColor))
        }

        fun filledRect(backgroundColor: Int, vararg radii: Float): Drawable {
            return createRect(backgroundColor, calcRippleColor(backgroundColor), *radii)
        }

        fun filledRect(background: Drawable?, backgroundColor: Int, vararg radii: Float): Drawable {
            return createRect(background, calcRippleColor(backgroundColor), *radii)
        }

        fun createRect(rippleColor: Int, vararg radii: Float): Drawable {
            return createRect(0, rippleColor, *radii)
        }

        fun createRect(backgroundColor: Int, rippleColor: Int, vararg radii: Float): Drawable {
            var background: Drawable? = null
            if (backgroundColor != 0) {
                if (hasNonzeroRadii(*radii)) {
                    background = ShapeDrawable(
                        RoundRectShape(
                            calcRadii(*radii), null, null
                        )
                    )
                    background.paint.color =
                        backgroundColor
                } else {
                    background = ColorDrawable(backgroundColor)
                }
            }
            return createRect(
                background,
                rippleColor,
                *radii
            )
        }

        private fun createRect(
            background: Drawable?,
            rippleColor: Int,
            vararg radii: Float
        ): Drawable {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                var maskDrawable: Drawable? = null
                if (hasNonzeroRadii(*radii)) {
                    maskDrawable = ShapeDrawable(
                        RoundRectShape(
                            calcRadii(*radii), null, null
                        )
                    )
                    maskDrawable.paint.color = -0x1
                } else {
                    maskDrawable = ShapeDrawable(RectShape())
                    maskDrawable.paint.color = -0x1
                }
                RippleDrawable(
                    ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(rippleColor)),
                    background,
                    maskDrawable
                )
            } else {
                val stateListDrawable = StateListDrawable()
                val ripple: Drawable
                if (hasNonzeroRadii(*radii)) {
                    ripple = ShapeDrawable(
                        RoundRectShape(
                            calcRadii(*radii), null, null
                        )
                    )
                    ripple.paint.color = rippleColor
                } else {
                    ripple = ShapeDrawable(RectShape())
                    ripple.paint.color = rippleColor
                }
                val pressed: Drawable = LayerDrawable(arrayOf(background, ripple))
                stateListDrawable.addState(intArrayOf(R.attr.state_pressed), pressed)
                stateListDrawable.addState(intArrayOf(R.attr.state_selected), pressed)
                stateListDrawable.addState(StateSet.WILD_CARD, background)
                stateListDrawable
            }
        }

        private fun createCircle(rippleColor: Int): Drawable {
            return createCircle(0, rippleColor, RADIUS_TO_BOUNDS)
        }

        private fun createCircle(rippleColor: Int, radius: Float): Drawable {
            return createCircle(0, rippleColor, radius)
        }

        private fun createCircle(backgroundColor: Int, rippleColor: Int, radius: Float): Drawable {
            return createCircle(
                if (backgroundColor == 0) null else CircleDrawable(radius, backgroundColor),
                rippleColor,
                radius
            )
        }

        private fun createCircle(background: Drawable?, rippleColor: Int, radius: Float): Drawable {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                RippleDrawable(
                    ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(rippleColor)),
                    background,
                    CircleDrawable(radius)
                )
            } else {
                val stateListDrawable = StateListDrawable()
                val ripple: Drawable = CircleDrawable(radius, rippleColor)
                val pressed: Drawable = LayerDrawable(arrayOf(background, ripple))
                stateListDrawable.addState(intArrayOf(R.attr.state_pressed), pressed)
                stateListDrawable.addState(intArrayOf(R.attr.state_selected), pressed)
                stateListDrawable.addState(StateSet.WILD_CARD, background)
                stateListDrawable
            }
        }

        private fun calcRadii(vararg radii: Float): FloatArray {
            return if (radii.size == 0) {
                floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            } else if (radii.size == 1) {
                floatArrayOf(
                    dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(
                        radii[0]
                    ), dp(radii[0]), dp(radii[0])
                )
            } else if (radii.size == 2) {
                floatArrayOf(
                    dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[1]), dp(
                        radii[1]
                    ), dp(radii[1]), dp(radii[1])
                )
            } else if (radii.size == 3) {
                floatArrayOf(
                    dp(radii[0]), dp(radii[0]), dp(radii[1]), dp(radii[1]), dp(radii[2]), dp(
                        radii[2]
                    ), dp(radii[2]), dp(radii[2])
                )
            } else if (radii.size < 8) {
                floatArrayOf(
                    dp(radii[0]), dp(radii[0]), dp(radii[1]), dp(radii[1]), dp(radii[2]), dp(
                        radii[2]
                    ), dp(radii[3]), dp(radii[3])
                )
            } else {
                floatArrayOf(
                    dp(radii[0]), dp(radii[1]), dp(radii[2]), dp(radii[3]), dp(radii[4]), dp(
                        radii[5]
                    ), dp(radii[6]), dp(radii[7])
                )
            }
        }

        private fun hasNonzeroRadii(vararg radii: Float): Boolean {
            for (i in 0 until Math.min(8, radii.size)) {
                if (radii[i] > 0) {
                    return true
                }
            }
            return false
        }

        private var tempHSV: FloatArray? = null
        fun calcRippleColor(backgroundColor: Int): Int {
            if (tempHSV == null) {
                tempHSV = FloatArray(3)
            }
            Color.colorToHSV(backgroundColor, tempHSV)
            if (tempHSV!![1] > 0.01f) {
                // when saturation is too low, hue is ignored
                // so changing saturation at that point would reveal ignored hue (usually red, hue=0)
                tempHSV!![1] = Math.min(
                    1f,
                    Math.max(
                        0f,
                        tempHSV!![1] + if (isCurrentThemeDark()) .25f else -.25f
                    )
                )
                tempHSV!![2] = Math.min(
                    1f,
                    Math.max(
                        0f,
                        tempHSV!![2] + if (isCurrentThemeDark()) .05f else -.05f
                    )
                )
            } else {
                tempHSV!![2] = Math.min(
                    1f,
                    Math.max(
                        0f,
                        tempHSV!![2] + if (isCurrentThemeDark()) .1f else -.1f
                    )
                )
            }
            return Color.HSVToColor(127, tempHSV)
        }

        private class CircleDrawable : Drawable {
            private var paint: Paint
            private var radius: Float

            constructor(radius: Float) {
                this.radius = radius
                paint = maskPaint
            }

            constructor(radius: Float, paintColor: Int) {
                this.radius = radius
                paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = paintColor
            }

            override fun draw(canvas: Canvas) {
                val bounds = bounds
                val rad: Float
                rad = if (Math.abs(radius - RADIUS_TO_BOUNDS) < 0.01f) {
                    (Math.max(bounds.width(), bounds.height()) / 2).toFloat()
                } else if (Math.abs(radius - RADIUS_OUT_BOUNDS) < 0.01f) {
                    Math.ceil(Math.sqrt(((bounds.left - bounds.centerX()) * (bounds.left - bounds.centerX()) + (bounds.top - bounds.centerY()) * (bounds.top - bounds.centerY())).toDouble()))
                        .toInt().toFloat()
                } else {
                    dp(radius)
                }
                canvas.drawCircle(
                    bounds.centerX().toFloat(),
                    bounds.centerY().toFloat(),
                    rad,
                    paint
                )
            }

            override fun setAlpha(i: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}

            @Deprecated("")
            override fun getOpacity(): Int {
                return PixelFormat.TRANSPARENT
            }

            companion object {
                private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            }
        }
    }

}
