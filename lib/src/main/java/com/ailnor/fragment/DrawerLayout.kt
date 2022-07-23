/* 
 * Copyright Erkinjanov Anaskhan, 22/07/22.
 */

package com.ailnor.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.ailnor.core.Utilities
import com.ailnor.core.dp
import com.ailnor.core.hideKeyboard
import com.ailnor.core.measureSpec_exactly
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DrawerLayout(context: Context) : ViewGroup(context) {

    private var isSlideFinishing = false
    private var startedTrackingX = 0
    private var startedTrackingY = 0
    private var startedTracking = false
    private var maybeStartedTracking = false
    private var beginTrackingSent = false
    private var startedTrackingPointerId = -1
    private var innerTranslationX = -1f
        set(value) {
            field = value
            invalidate()
        }
    private var velocityTracker: VelocityTracker? = null
    private val rect = Rect()

    val navigationLayout = FrameLayout(context)


    override fun addView(child: View?) {
        super.addView(child)
        removeView(navigationLayout)
        super.addView(navigationLayout)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)

        getChildAt(0).measure(
            widthMeasureSpec,
            heightMeasureSpec
        )

        navigationLayout.measure(
            measureSpec_exactly(min((width * 0.7).toInt(), dp(250))),
            measureSpec_exactly(heightMeasureSpec)
        )

        if (innerTranslationX == -1f)
            innerTranslationX = (-navigationLayout.measuredWidth).toFloat()

                setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        getChildAt(0).layout(
            0,
            0,
            measuredWidth,
            measuredHeight
        )
        navigationLayout.layout(
            innerTranslationX.toInt(),
            0,
            (innerTranslationX + navigationLayout.measuredWidth).toInt(),
            measuredHeight
        )
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return isSlideFinishing || onTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        onTouchEvent(null)
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun prepareForMoving(x: Float) {
        maybeStartedTracking = false
        startedTracking = true
        startedTrackingX = x.toInt()
        beginTrackingSent = false
    }

    private fun onSlideAnimationEnd(backAnimation: Boolean) {
        startedTracking = false
        isSlideFinishing = false
        innerTranslationX = 0f
    }


    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (!isSlideFinishing) {
            if (ev == null) {
                startedTrackingX = 0
                startedTrackingY = 0
                startedTracking = false
                beginTrackingSent = false
                maybeStartedTracking = false
                if (velocityTracker != null) {
                    velocityTracker!!.recycle()
                    velocityTracker = null
                }
                return false
            } else {
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    maybeStartedTracking = true
                    beginTrackingSent = false
                    startedTrackingPointerId = ev.getPointerId(0)
                    startedTrackingX = ev.x.toInt()
                    startedTrackingY = ev.y.toInt()
                    if (velocityTracker == null)
                        velocityTracker = VelocityTracker.obtain()
                    else
                        velocityTracker!!.clear()
                    velocityTracker!!.addMovement(ev)
                } else if (ev.action == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    if (velocityTracker == null)
                        velocityTracker = VelocityTracker.obtain()
                    val dx = max(0, (ev.x - startedTrackingX).toInt())
                    val dy = abs(ev.y - startedTrackingY)
                    velocityTracker!!.addMovement(ev)

                    if (maybeStartedTracking && !startedTracking && dx >= Utilities.getPixelsInCM(
                            0.4f,
                            true
                        ) && abs(dx) / 3 > dy
                    ) {
                        if (findScrollingChild(this, ev.x, ev.y) == null)
                            prepareForMoving(ev.x)
                        else
                            maybeStartedTracking = false
                    } else if (startedTracking) {
                        if (!beginTrackingSent) {
                            hideKeyboard()
                            beginTrackingSent = true
                        }
//                        navigationLayout.translationX = dx.toFloat()
                        innerTranslationX = dx.toFloat()
                    } else if (ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_POINTER_UP)) {
                        if (velocityTracker == null)
                            velocityTracker = VelocityTracker.obtain()
                        velocityTracker!!.computeCurrentVelocity(1000)
                        if (!startedTracking) {
                            val velX = velocityTracker!!.xVelocity
                            val velY = velocityTracker!!.yVelocity
                            if (velX >= 3500 && velX > abs(velY)) {
                                prepareForMoving(ev.x)
                                if (!beginTrackingSent) {
                                    hideKeyboard()
                                    beginTrackingSent = true
                                }
                            }
                        }
                        if (startedTracking) {
                            val velX = velocityTracker!!.xVelocity
                            val velY = velocityTracker!!.yVelocity
                            val x = navigationLayout.measuredWidth + navigationLayout.x
                            val backAnimation = x < navigationLayout.measuredWidth / 3f && (velX < 3500 || velX < velY)
                            val distToMove: Float = if (backAnimation)
                                x
                            else
                                navigationLayout.measuredWidth - x

                            val duration = max(
                                (200 * distToMove / navigationLayout.measuredWidth).toLong(),
                                50
                            )
                            val animatorSet = AnimatorSet()
                            if (backAnimation)
                                animatorSet.playTogether(
//                                    ObjectAnimator.ofFloat(
//                                        navigationLayout,
//                                        View.TRANSLATION_X,
//                                        -navigationLayout.measuredWidth.toFloat()
//                                    ).setDuration(duration),
                                    ObjectAnimator.ofFloat(
                                        this,
                                        "innerTranslationX",
                                        innerTranslationX,
                                        -navigationLayout.measuredWidth.toFloat()
                                    ).setDuration(duration)
                                )
                            else
                                animatorSet.playTogether(
//                                    ObjectAnimator.ofFloat(
//                                        navigationLayout,
//                                        View.TRANSLATION_X,
//                                        0f
//                                    ).setDuration(duration),
                                    ObjectAnimator.ofFloat(
                                        this,
                                        "innerTranslationX",
                                        innerTranslationX,
                                        0f
                                    ).setDuration(duration)
                                )
                            animatorSet.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator?) {
                                    onSlideAnimationEnd(backAnimation)
                                }
                            })
                            isSlideFinishing = true
                            animatorSet.start()
                        } else {
                            maybeStartedTracking = false
                        }
                        if (velocityTracker != null) {
                            velocityTracker!!.recycle()
                            velocityTracker = null
                        }
                    }
                }
                return startedTracking
            }
        }
        return false
    }


    private fun findScrollingChild(parent: ViewGroup, x: Float, y: Float): View? {
        val n = parent.childCount
        for (i in 0 until n) {
            val child = parent.getChildAt(i)
            if (child.visibility != VISIBLE) {
                continue
            }
            child.getHitRect(rect)
            if (rect.contains(x.toInt(), y.toInt())) {
                if (child.canScrollHorizontally(-1)) {
                    return child
                } else if (child is ViewGroup) {
                    val v = findScrollingChild(child, x - rect.left, y - rect.top)
                    if (v != null) {
                        return v
                    }
                }
            }
        }
        return null
    }


}