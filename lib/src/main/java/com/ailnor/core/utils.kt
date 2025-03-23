/* 
 * Copyright Erkinjanov Anaskhan, 14/02/2022.
 */

package com.ailnor.core

import android.R
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RoundRectShape
import android.graphics.drawable.shapes.Shape
import android.os.Build
import android.util.StateSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.core.view.setMargins
import com.ailnor.core.Theme.alpha
import com.ailnor.core.Theme.maskPaint
import java.lang.reflect.Method
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val MATCH_PARENT = -1
const val WRAP_CONTENT = -2

private var StateListDrawable_getStateDrawableMethod: Method? = null

fun dp(value: Int) =
    if (value == 0)
        0
    else
        ceil(AndroidUtilities.density * value).toInt()

fun dp(value: Float) =
    if (value == 0F)
        0F
    else
        ceil(AndroidUtilities.density * value)

fun dp2(value: Int) =
    if (value == 0)
        0
    else
        floor(AndroidUtilities.density * value).toInt()


fun dp2(value: Float) =
    if (value == 0F)
        0F
    else
        AndroidUtilities.density * value

fun dpr(value: Float): Int {
    return if (value == 0f) {
        0
    } else (AndroidUtilities.density * value).roundToInt()
}

val measureSpec_unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
fun measureSpec_at_most(atMost: Int) =
    View.MeasureSpec.makeMeasureSpec(atMost, View.MeasureSpec.AT_MOST)

fun measureSpec_exactly(exactly: Int) =
    View.MeasureSpec.makeMeasureSpec(exactly, View.MeasureSpec.EXACTLY)

fun groupLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT
) = ViewGroup.LayoutParams(width, height)

fun marginLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    marginLeft: Int = 0,
    marginTop: Int = 0,
    marginRight: Int = 0,
    marginBottom: Int = 0,
): ViewGroup.MarginLayoutParams {
    val layoutParams = ViewGroup.MarginLayoutParams(width, height)
    layoutParams.setMargins(
        marginLeft,
        marginTop,
        marginRight,
        marginBottom
    )
    return layoutParams
}

fun marginLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    margin: Int = 0
): ViewGroup.MarginLayoutParams {
    val layoutParams = ViewGroup.MarginLayoutParams(width, height)
    layoutParams.setMargins(
        margin,
        margin,
        margin,
        margin
    )
    return layoutParams
}

fun frameLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT
) = FrameLayout.LayoutParams(width, height)

fun frameLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    gravity: Int
) = FrameLayout.LayoutParams(width, height, gravity)

fun frameLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    gravity: Int = Gravity.CENTER,
    margin: Int = 0
): FrameLayout.LayoutParams {
    val frameLayoutParams = FrameLayout.LayoutParams(width, height, gravity)
    frameLayoutParams.setMargins(margin, margin, margin, margin)
    return frameLayoutParams
}

fun frameLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    gravity: Int = Gravity.CENTER,
    marginLeft: Int = 0,
    marginTop: Int = 0,
    marginRight: Int = 0,
    marginBottom: Int = 0,
): FrameLayout.LayoutParams {
    val frameLayoutParams = FrameLayout.LayoutParams(width, height, gravity)
    frameLayoutParams.setMargins(marginLeft, marginTop, marginRight, marginBottom)
    return frameLayoutParams
}

fun linearLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    weight: Float = 0F,
): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, height, weight)

fun linearLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    weight: Float = 0F,
    gravity: Int = Gravity.START,
    margin: Int = 0
): LinearLayout.LayoutParams {
    val layoutParams = LinearLayout.LayoutParams(width, height, weight)
    layoutParams.gravity = gravity
    layoutParams.setMargins(margin)

    return layoutParams
}

fun linearLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    weight: Float = 0F,
    marginLeft: Int = 0,
    marginTop: Int = 0,
    marginRight: Int = 0,
    marginBottom: Int = 0,
    gravity: Int = Gravity.START
): LinearLayout.LayoutParams {
    val layoutParams = LinearLayout.LayoutParams(width, height, weight)
    layoutParams.setMargins(
        marginLeft,
        marginTop,
        marginRight,
        marginBottom
    )
    layoutParams.gravity = gravity

    return layoutParams
}

fun relativeLayoutParams(
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT,
    leftMargin: Int = 0,
    topMargin: Int = 0,
    rightMargin: Int = 0,
    bottomMargin: Int = 0,
    alignParent: Int = -1,
    alignRelative: Int = -1,
    anchorRelative: Int = -1
): RelativeLayout.LayoutParams {
    val layoutParams: RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(width, height)
    if (alignParent >= 0)
        layoutParams.addRule(alignParent)
    if (alignRelative >= 0 && anchorRelative >= 0)
        layoutParams.addRule(alignRelative, anchorRelative)
    layoutParams.leftMargin = dp(leftMargin)
    layoutParams.topMargin = dp(topMargin)
    layoutParams.rightMargin = dp(rightMargin)
    layoutParams.bottomMargin = dp(bottomMargin)
    return layoutParams
}

// Drawables

fun makeRippleDrawable(
    @ColorInt rippleColor: Int = Theme.platinum.alpha(70),
    @ColorInt backgroundColor: Int = Theme.transparent,
    @ColorInt disabledBackgroundColor: Int = backgroundColor,
    cornerRadius: Float = dp(4f),
    elevation: Float = 0F
) = makeRippleDrawable(
    rippleColor,
    backgroundColor,
    disabledBackgroundColor,
    cornerRadius,
    cornerRadius,
    cornerRadius,
    cornerRadius,
    elevation
)

fun makeRippleDrawable(
    @ColorInt rippleColor: Int = Theme.platinum.alpha(70),
    @ColorInt backgroundColor: Int = Theme.transparent,
    @ColorInt disabledBackgroundColor: Int = backgroundColor,
    topLeftRadius: Float = dp(4f),
    topRightRadius: Float = dp(4f),
    bottomLeftRadius: Float = dp(4f),
    bottomRightRadius: Float = dp(4f),
    elevataion: Float = 0F
): Drawable {
    return makeRippleDrawable(
        rippleColor,
        backgroundColor,
        disabledBackgroundColor,
        floatArrayOf(
            topLeftRadius,
            topLeftRadius,
            topRightRadius,
            topRightRadius,
            bottomRightRadius,
            bottomRightRadius,
            bottomLeftRadius,
            bottomLeftRadius
        ),
        elevataion
    )
}

fun makeRippleDrawable(
    @ColorInt rippleColor: Int = Theme.platinum.alpha(70),
    @ColorInt backgroundColor: Int = Theme.transparent,
    @ColorInt disabledBackgroundColor: Int = backgroundColor,
    cornerRadii: FloatArray,
    elevataion: Float = 0F
): Drawable {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

        val content: GradientDrawable?
        val mask: ShapeDrawable?

        if (backgroundColor == Theme.transparent) {
            content = null
            mask = ShapeDrawable(RoundRectShape(cornerRadii, null, null))
            mask.colorFilter = PorterDuffColorFilter(rippleColor, PorterDuff.Mode.SRC_IN)
        } else {
            content = GradientDrawable()
            content.cornerRadii = cornerRadii
            content.color = ColorStateList(
                arrayOf(
                    intArrayOf(R.attr.state_activated),
                    intArrayOf(R.attr.state_enabled),
                    intArrayOf(-R.attr.state_enabled)
                ),
                intArrayOf(
                    backgroundColor,
                    backgroundColor,
                    disabledBackgroundColor
                )
            )
            mask = null
        }

        val rippleDrawable = RippleDrawable(
            ColorStateList(
                arrayOf(
                    StateSet.WILD_CARD
                ),
                intArrayOf(
                    rippleColor
                )
            ),
            content,
            mask
        )

        rippleDrawable

    } else {

        val shapePressed = ShapeDrawable(RoundRectShape(cornerRadii, null, null))
        shapePressed.colorFilter =
            PorterDuffColorFilter(rippleColor, PorterDuff.Mode.SRC_IN)

        val shapeDefault = GradientDrawable().also {
            it.cornerRadii = cornerRadii
            it.color = ColorStateList(
                arrayOf(
                    intArrayOf(R.attr.state_activated),
                    intArrayOf(R.attr.state_enabled),
                    intArrayOf(-R.attr.state_enabled)
                ),
                intArrayOf(
                    backgroundColor,
                    backgroundColor,
                    disabledBackgroundColor
                )
            )
        }

        val stateListDrawable = StateListDrawable()
        stateListDrawable.addState(
            intArrayOf(
                R.attr.state_pressed,
                R.attr.state_enabled
            ), shapePressed
        )
        stateListDrawable.addState(intArrayOf(), shapeDefault)
        stateListDrawable
    }
}


fun makeCircleRippleDrawable(
    @ColorInt rippleColor: Int = Theme.platinum.alpha(70),
    @ColorInt backgroundColor: Int = Theme.transparent,
    @ColorInt disabledColor: Int = backgroundColor,
    elevataion: Float = 0F
): Drawable {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

        val content: GradientDrawable?
        val mask: GradientDrawable?

        if (backgroundColor == Theme.transparent) {
            content = null
            mask = GradientDrawable()
            mask.setColor(rippleColor)
            mask.shape = GradientDrawable.OVAL
        } else {
            content = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.color = ColorStateList(
                    arrayOf(
                        intArrayOf(R.attr.state_activated),
                        intArrayOf(R.attr.state_enabled),
                        intArrayOf(-R.attr.state_enabled)
                    ),
                    intArrayOf(
                        backgroundColor,
                        backgroundColor,
                        disabledColor
                    )
                )
            }
            mask = null
        }

        RippleDrawable(
            ColorStateList(
                arrayOf(
                    intArrayOf(R.attr.state_pressed),
                    intArrayOf(R.attr.state_focused),
                    intArrayOf(R.attr.state_activated)
                ),
                intArrayOf(
                    rippleColor,
                    rippleColor,
                    rippleColor
                )
            ),
            content,
            mask
        )
    } else {

        val shapePressed = GradientDrawable()
        shapePressed.shape = GradientDrawable.OVAL
        shapePressed.setColor(rippleColor)

        val shapeDefault = GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            it.color = ColorStateList(
                arrayOf(
                    intArrayOf(R.attr.state_activated),
                    intArrayOf(R.attr.state_enabled),
                    intArrayOf(-R.attr.state_enabled)
                ),
                intArrayOf(
                    backgroundColor,
                    backgroundColor,
                    disabledColor
                )
            )
        }

        val stateListDrawable = StateListDrawable()
        stateListDrawable.addState(
            intArrayOf(
                R.attr.state_pressed,
                R.attr.state_enabled
            ), shapePressed
        )
        stateListDrawable.addState(intArrayOf(), shapeDefault)
        stateListDrawable
    }
}


fun makeRoundedDrawable(
    @ColorInt color: Int = Theme.colorPrimary,
    cornerRadiusTL: Float = 0F,
    cornerRadiusTR: Float = 0F,
    cornerRadiusBL: Float = 0F,
    cornerRadiusBR: Float = 0F
): Drawable {
    val shapeDrawable = ShapeDrawable(
        RoundRectShape(
            floatArrayOf(
                cornerRadiusTL,
                cornerRadiusTL,
                cornerRadiusTR,
                cornerRadiusTR,
                cornerRadiusBR,
                cornerRadiusBR,
                cornerRadiusBL,
                cornerRadiusBL
            ),
            null,
            null
        )
    )
    shapeDrawable.colorFilter =
        PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

    return shapeDrawable
}

fun makeRoundedDrawable(
    @ColorInt color: Int = Theme.colorPrimary,
    cornerRadius: Float,
    elevataion: Float = 0F
): Drawable {
    val outerRadii = FloatArray(8)
    Arrays.fill(outerRadii, cornerRadius)
    return ShapeDrawable(RoundRectShape(outerRadii, null, null)).also {
        it.colorFilter =
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}

fun makeCircleDrawable(
    @ColorInt color: Int = Theme.colorPrimary,
    elevataion: Float = 0F
): Drawable {
    return ShapeDrawable(OvalShape()).also {
        it.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}

fun makeOutlinedDrawable(
    @ColorInt strokeColor: Int = Theme.colorPrimary,
    @ColorInt backgroundColor: Int = Theme.transparent,
    cornerRadius: Float = 0F,
    stroke: Int = dp(1)
): Drawable {
    val outerRadii = FloatArray(8)
    Arrays.fill(outerRadii, cornerRadius)
    val drawable = GradientDrawable()
    drawable.cornerRadii = outerRadii
    if (backgroundColor != Theme.transparent)
        drawable.color = ColorStateList.valueOf(backgroundColor)
    drawable.setStroke(stroke, strokeColor)
    return drawable
}


// From telegram

fun setCombinedDrawableColor(combinedDrawable: Drawable?, color: Int, isIcon: Boolean) {
    if (combinedDrawable !is CombinedDrawable) {
        return
    }
    val drawable: Drawable = if (isIcon) {
        combinedDrawable.icon
    } else {
        combinedDrawable.background
    }
    if (drawable is ColorDrawable) {
        drawable.color = color
    } else {
        drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
    }
}

fun createSimpleSelectorCircleDrawable(size: Int, defaultColor: Int, pressedColor: Int): Drawable {
    val ovalShape = OvalShape()
    ovalShape.resize(size.toFloat(), size.toFloat())
    val defaultDrawable = ShapeDrawable(ovalShape)
    defaultDrawable.paint.color = defaultColor
    val pressedDrawable = ShapeDrawable(ovalShape)
    pressedDrawable.paint.color = -0x1
    val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(pressedColor))
    return RippleDrawable(colorStateList, defaultDrawable, pressedDrawable)
}

fun createRoundRectDrawable(rad: Float, defaultColor: Int): ShapeDrawable {
    return createRoundRectDrawable(rad, rad, defaultColor)
}

fun createRoundRectDrawable(topRad: Float, bottomRad: Float, defaultColor: Int): ShapeDrawable {
    return createRoundRectDrawable(floatArrayOf(
        topRad, topRad, topRad, topRad,
        bottomRad, bottomRad, bottomRad, bottomRad
    ), defaultColor)
}

fun createRoundRectDrawable(radii: FloatArray, defaultColor: Int): ShapeDrawable {
    val defaultDrawable = ShapeDrawable(
        RoundRectShape(
            radii, null, null
        )
    )
    defaultDrawable.paint.color = defaultColor
    return defaultDrawable
}


fun createSimpleSelectorRoundRectDrawable(
    rad: Int,
    defaultColor: Int,
    pressedColor: Int,
    maskColor: Int = pressedColor
): Drawable {
    val defaultDrawable = ShapeDrawable(
        RoundRectShape(
            floatArrayOf(
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat()
            ), null, null
        )
    )
    defaultDrawable.paint.color = defaultColor
    val pressedDrawable = ShapeDrawable(
        RoundRectShape(
            floatArrayOf(
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat(),
                rad.toFloat()
            ), null, null
        )
    )
    pressedDrawable.paint.color = maskColor
    val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(pressedColor))
    return RippleDrawable(colorStateList, defaultDrawable, pressedDrawable)
}

fun createSelectorDrawableFromDrawables(normal: Drawable?, pressed: Drawable?): Drawable {
    val stateListDrawable = StateListDrawable()
//    stateListDrawable.addState(intArrayOf(R.attr.state_pressed), pressed)
    stateListDrawable.addState(intArrayOf(R.attr.state_selected), pressed)
    stateListDrawable.addState(StateSet.WILD_CARD, normal)
    return stateListDrawable
}

fun getRoundRectSelectorDrawable(corners: Int = dp(3), color: Int): Drawable {
    val maskDrawable = createRoundRectDrawable(corners.toFloat(), -0x1)
    val colorStateList = ColorStateList(
        arrayOf(StateSet.WILD_CARD), intArrayOf(
            color and 0x00ffffff or 0x19000000
        )
    )
    return RippleDrawable(colorStateList, null, maskDrawable)
}

fun createSelectorWithBackgroundDrawable(
    backgroundColor: Int,
    color: Int,
    disabledBackgroundColor: Int = backgroundColor,
    topRad: Float = 0f,
    bottomRad: Float = 0F
): Drawable {
    if (topRad == 0f && bottomRad == 0F) {
        val maskDrawable: Drawable = ColorDrawable(backgroundColor)
        val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color))
        return if (backgroundColor == disabledBackgroundColor)
            RippleDrawable(colorStateList, ColorDrawable(backgroundColor), maskDrawable)
        else
            RippleDrawable(
                colorStateList,
                GradientDrawable().also {
                    it.color = ColorStateList(
                        arrayOf(
                            intArrayOf(R.attr.state_activated),
                            intArrayOf(R.attr.state_enabled),
                            intArrayOf(-R.attr.state_enabled)
                        ),
                        intArrayOf(
                            backgroundColor,
                            backgroundColor,
                            disabledBackgroundColor
                        )
                    )
                },
                maskDrawable
            )
    } else {
        val rads = floatArrayOf(
            topRad,
            topRad,
            topRad,
            topRad,
            bottomRad,
            bottomRad,
            bottomRad,
            bottomRad
        )
        val maskDrawable = ShapeDrawable(
            RoundRectShape(rads, null, null)
        ).also {
            it.paint.color = backgroundColor
        }
        val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color))
        return if (backgroundColor == disabledBackgroundColor) {
            RippleDrawable(
                colorStateList,
                ShapeDrawable(
                    RoundRectShape(rads, null, null)
                ).also {
                    it.paint.color = backgroundColor
                }, maskDrawable
            )
        } else
            RippleDrawable(
                colorStateList,
                GradientDrawable().also {
                    it.color = ColorStateList(
                        arrayOf(
                            intArrayOf(R.attr.state_activated),
                            intArrayOf(R.attr.state_enabled),
                            intArrayOf(-R.attr.state_enabled)
                        ),
                        intArrayOf(
                            backgroundColor,
                            backgroundColor,
                            disabledBackgroundColor
                        )
                    )
                    it.cornerRadii = floatArrayOf(
                        topRad,
                        topRad,
                        topRad,
                        topRad,
                        bottomRad,
                        bottomRad,
                        bottomRad,
                        bottomRad
                    )
                },
                maskDrawable
            )
    }
}

fun getSelectorDrawable(color: Int = Theme.platinum.alpha(70), whiteBackground: Boolean = false): Drawable {
    return if (whiteBackground) {
        getSelectorDrawable(color, Theme.green)
    } else {
        createSelectorDrawable(color, RIPPLE_MASK_ALL)
    }
}

fun getSelectorDrawable(color: Int, backgroundColor: Int): Drawable {
    return if (backgroundColor != 0) {
        val maskDrawable: Drawable = ColorDrawable(-0x1)
        val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color))
        RippleDrawable(
            colorStateList,
            ColorDrawable(backgroundColor),
            maskDrawable
        )
    } else
        createSelectorDrawable(color, RIPPLE_MASK_ALL)
}

const val RIPPLE_MASK_CIRCLE_20DP = 1
const val RIPPLE_MASK_ALL = 2
const val RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE = 3
const val RIPPLE_MASK_CIRCLE_TO_BOUND_CORNER = 4
const val RIPPLE_MASK_CIRCLE_AUTO = 5
const val RIPPLE_MASK_ROUNDRECT_6DP = 7

fun createSelectorDrawable(
    color: Int = Theme.platinum.alpha(70),
    maskType: Int = RIPPLE_MASK_ALL,
    radius: Int = -1
): Drawable {
    var maskDrawable: Drawable? = null
    if ((maskType == RIPPLE_MASK_CIRCLE_20DP || maskType == RIPPLE_MASK_CIRCLE_AUTO) && Build.VERSION.SDK_INT >= 23) {
        maskDrawable = null
    } else if (
        maskType == RIPPLE_MASK_CIRCLE_20DP
        || maskType == RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE
        || maskType == RIPPLE_MASK_CIRCLE_TO_BOUND_CORNER
        || maskType == RIPPLE_MASK_CIRCLE_AUTO
        || maskType == 6
        || maskType == RIPPLE_MASK_ROUNDRECT_6DP
    ) {
        maskPaint.color = -0x1
        maskDrawable = object : Drawable() {
            var rect: RectF? = null
            override fun draw(canvas: Canvas) {
                val bounds = bounds
                if (maskType == RIPPLE_MASK_ROUNDRECT_6DP) {
                    if (rect == null) {
                        rect = RectF()
                    }
                    rect!!.set(bounds)
                    canvas.drawRoundRect(
                        rect!!,
                        dp(6f),
                        dp(6f),
                        maskPaint
                    )
                } else {
                    val rad = when (maskType) {
                        RIPPLE_MASK_CIRCLE_20DP, 6 -> {
                            dp(20)
                        }
                        RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE -> {
                            bounds.width().coerceAtLeast(bounds.height()) / 2
                        }
                        else -> {
                            ceil(sqrt(((bounds.left - bounds.centerX()) * (bounds.left - bounds.centerX()) + (bounds.top - bounds.centerY()) * (bounds.top - bounds.centerY())).toDouble()))
                                .toInt()
                        }
                    }
                    canvas.drawCircle(
                        bounds.centerX().toFloat(),
                        bounds.centerY().toFloat(),
                        rad.toFloat(),
                        maskPaint
                    )
                }
            }

            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            override fun getOpacity(): Int {
                return PixelFormat.UNKNOWN
            }
        }
    } else if (maskType == RIPPLE_MASK_ALL) {
        maskDrawable = ColorDrawable(-0x1)
    }
    val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color))
    val rippleDrawable = RippleDrawable(colorStateList, null, maskDrawable)
    if (Build.VERSION.SDK_INT >= 23) {
        if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
            rippleDrawable.radius = if (radius <= 0) dp(20) else radius
        } else if (maskType == RIPPLE_MASK_CIRCLE_AUTO) {
            rippleDrawable.radius = RippleDrawable.RADIUS_AUTO
        }
    }
    return rippleDrawable
}

fun createCircleSelectorDrawable(
    color: Int = Theme.platinum.alpha(70),
    leftInset: Int = 0,
    rightInset: Int = 0
): Drawable {
    maskPaint.setColor(-0x1)
    val maskDrawable: Drawable = object : Drawable() {
        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val rad = Math.max(bounds.width(), bounds.height()) / 2 + leftInset + rightInset
            canvas.drawCircle(
                (bounds.centerX() - leftInset + rightInset).toFloat(),
                bounds.centerY().toFloat(),
                rad.toFloat(),
                maskPaint
            )
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        override fun getOpacity(): Int {
            return PixelFormat.UNKNOWN
        }
    }
    val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color))
    return RippleDrawable(colorStateList, null, maskDrawable)
}

open class RippleRadMaskDrawable : Drawable {
    private val path = Path()
    private val rect = RectF()
    private val radii = FloatArray(8)

    constructor(top: Float, bottom: Float) {
        radii[3] = dp(top)
        radii[2] = radii[3]
        radii[1] = radii[2]
        radii[0] = radii[1]
        radii[7] = dp(bottom)
        radii[6] = radii[7]
        radii[5] = radii[6]
        radii[4] = radii[5]
    }

    constructor(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        radii[1] = dp(topLeft)
        radii[0] = radii[1]
        radii[3] = dp(topRight)
        radii[2] = radii[3]
        radii[5] = dp(bottomRight)
        radii[4] = radii[5]
        radii[7] = dp(bottomLeft)
        radii[6] = radii[7]
    }

    fun setRadius(top: Float, bottom: Float) {
        radii[3] = dp(top)
        radii[2] = radii[3]
        radii[1] = radii[2]
        radii[0] = radii[1]
        radii[7] = dp(bottom)
        radii[6] = radii[7]
        radii[5] = radii[6]
        radii[4] = radii[5]
        invalidateSelf()
    }

    fun setRadius(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        radii[1] = dp(topLeft)
        radii[0] = radii[1]
        radii[3] = dp(topRight)
        radii[2] = radii[3]
        radii[5] = dp(bottomRight)
        radii[4] = radii[5]
        radii[7] = dp(bottomLeft)
        radii[6] = radii[7]
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        path.addRoundRect(rect, radii, Path.Direction.CW)
        canvas.drawPath(path, maskPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int {
        return PixelFormat.UNKNOWN
    }
}

fun setMaskDrawableRad(rippleDrawable: Drawable?, top: Int, bottom: Int) {
    if (rippleDrawable is RippleDrawable) {
        val drawable = rippleDrawable
        val count = drawable.numberOfLayers
        for (a in 0 until count) {
            val layer = drawable.getDrawable(a)
            if (layer is RippleRadMaskDrawable) {
                drawable.setDrawableByLayerId(
                    R.id.mask, RippleRadMaskDrawable(
                        top.toFloat(),
                        bottom.toFloat()
                    )
                )
                break
            }
        }
    }
}

fun createRadSelectorDrawable(color: Int = Theme.platinum.alpha(70), topRad: Int, bottomRad: Int): Drawable {
    maskPaint.setColor(-0x1)
    val maskDrawable: Drawable = RippleRadMaskDrawable(
        topRad.toFloat(),
        bottomRad.toFloat()
    )
    val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color))
    return RippleDrawable(colorStateList, null, maskDrawable)
}

fun createRadSelectorDrawable(
    color: Int = Theme.platinum.alpha(70),
    topLeftRad: Int,
    topRightRad: Int,
    bottomRightRad: Int,
    bottomLeftRad: Int
): Drawable {
    maskPaint.setColor(-0x1)
    val maskDrawable: Drawable = RippleRadMaskDrawable(
        topLeftRad.toFloat(),
        topRightRad.toFloat(), bottomRightRad.toFloat(), bottomLeftRad.toFloat()
    )
    val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color))
    return RippleDrawable(colorStateList, null, maskDrawable)
}

@SuppressLint("PrivateApi")
private fun getStateDrawable(drawable: Drawable, index: Int): Drawable? {
    return if (Build.VERSION.SDK_INT >= 29 && drawable is StateListDrawable) {
        drawable.getStateDrawable(index)
    } else {
        if (StateListDrawable_getStateDrawableMethod == null) {
            try {
                StateListDrawable_getStateDrawableMethod =
                    StateListDrawable::class.java.getDeclaredMethod(
                        "getStateDrawable",
                        Int::class.javaPrimitiveType
                    )
            } catch (ignore: Throwable) {
            }
        }
        if (StateListDrawable_getStateDrawableMethod == null) {
            return null
        }
        try {
            return StateListDrawable_getStateDrawableMethod!!.invoke(
                drawable,
                index
            ) as? Drawable
        } catch (ignore: Exception) {
        }
        null
    }
}

fun Drawable.setSelectorDrawableColor(backgroundColor: Int, disabledBackgroundColor: Int){
    val drawable = (this as RippleDrawable).getDrawable(0)
    if (drawable is GradientDrawable) {
        drawable.color = ColorStateList(
            arrayOf(
                intArrayOf(R.attr.state_activated),
                intArrayOf( R.attr.state_enabled),
                intArrayOf(-R.attr.state_enabled)
            ),
            intArrayOf(
                backgroundColor,
                backgroundColor,
                disabledBackgroundColor
            )
        )
    } else if (drawable is ColorDrawable){
        drawable.color = backgroundColor
    }
}

fun Drawable.setRippleDrawableColor(
    rippleColor: Int,
    backgroundColor: Int,
    disabledBackgroundColor: Int = backgroundColor
){
    this as RippleDrawable
    val drawable = this.getDrawable(0)
    this.setColor(ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(rippleColor)))
    if (drawable is GradientDrawable) {
        drawable.color = ColorStateList(
            arrayOf(
                intArrayOf(R.attr.state_activated),
                intArrayOf( R.attr.state_enabled),
                intArrayOf(-R.attr.state_enabled)
            ),
            intArrayOf(
                backgroundColor,
                backgroundColor,
                disabledBackgroundColor
            )
        )
    }
}

fun Drawable.setRippleDrawableRadius(radii: FloatArray){
    this as RippleDrawable
    val drawable = this.getDrawable(0)
    if (drawable is GradientDrawable) {
        drawable.cornerRadii = radii
    }
}

fun Drawable.setShapeDrawableShape(shape: Shape){
    this as ShapeDrawable
    this.shape = shape
}

fun Drawable.setShapeDrawableColor(color: Int){
    this as ShapeDrawable
    this.paint.color = color
}

fun Drawable.setSelectorDrawableColor(color: Int, selected: Boolean) {
    if (this is StateListDrawable) {
        try {
            var state: Drawable
            if (selected) {
                state = getStateDrawable(this, 0)!!
                if (state is ShapeDrawable) {
                    state.paint.color = color
                } else {
                    state.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
                }
                state = getStateDrawable(this, 1)!!
            } else {
                state = getStateDrawable(this, 2)!!
            }
            if (state is ShapeDrawable) {
                state.paint.color = color
            } else {
                state.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }
        } catch (ignore: Throwable) {
        }
    } else if (Build.VERSION.SDK_INT >= 21 && this is RippleDrawable) {
        val rippleDrawable = this
        if (selected) {
            rippleDrawable.setColor(ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color)))
        } else {
            if (rippleDrawable.numberOfLayers > 0) {
                val drawable1 = rippleDrawable.getDrawable(0)
                if (drawable1 is ShapeDrawable) {
                    drawable1.paint.color = color
                } else {
                    drawable1.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
                }
            }
        }
    }
}
