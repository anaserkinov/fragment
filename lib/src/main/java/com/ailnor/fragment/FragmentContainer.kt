/* 
 * Copyright Erkinjanov Anaskhan, 12/02/2022.
 */

package com.ailnor.fragment

import android.animation.*
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.*
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.math.MathUtils
import androidx.core.view.children
import com.ailnor.core.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FragmentContainer(context: Context) : FrameLayout(context) {

    private var frameAnimationFinishRunnable: Runnable? = null
    private var inAnimation = false
    private var touching = false
    private var isSlideFinishing = false
    private var isKeyboardVisible = false
    private val rect = Rect()
    private val scrimPaint = Paint()
    private val layerShadowDrawable = resources.getDrawable(R.drawable.layer_shadow).mutate()
    private val layerShadowDrawable2 = resources.getDrawable(R.drawable.layer_shadow).mutate()
    private var innerTranslationX = 0f
        set(value) {
            field = value
            invalidate()
        }
    private var isSlidingLastFragment = false

    private var velocityTracker: VelocityTracker? = null
    private var startedTrackingX = 0
    private var startedTrackingY = 0
    private var startedTracking = false
    private var maybeStartedTracking = false
    private var beginTrackingSent = false
    private var startedTrackingPointerId = -1
    private var drawShadow = false

    companion object {
        const val FROM_RIGHT = 1
        const val FROM_LEFT = 2
        const val FROM_RIGHT_FLOATING = 3
    }

    private class Container(context: Context) : FrameLayout(context) {

        var weight = 1f
        var leftOffset = 0F
        private val headerShadowDrawable = R.drawable.header_shadow.drawable()

        fun setView(view: View, color: Int) {
            addView(view)
            setBackgroundColor(color)
        }

        fun updateParams(weight: Float, leftOffset: Float) {
            this.weight = weight
            this.leftOffset = leftOffset
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            var actionBarHeight = 0

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is ActionBar) {
                    child.measure(measureSpec_exactly(width), measureSpec_unspecified)
                    actionBarHeight = child.measuredHeight
                    break
                }
            }

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child !is ActionBar && child.visibility != View.GONE)
//                    child.measure(
//                        widthMeasureSpec,
//                        measureSpec_exactly(
//                            height - if (child.fitsSystemWindows)
//                                -Utilities.statusBarHeight
//                            else if (actionBarHeight != 0)
//                                actionBarHeight
//                            else
//                                (actionBarHeight + Utilities.statusBarHeight)
//                        )
//                    )
                    measureChildWithMargins(
                        child,
                        widthMeasureSpec,
                        0,
                        heightMeasureSpec,
                        actionBarHeight +
                                if (child.fitsSystemWindows || actionBarHeight != 0)
                                    0
                                else
                                    Utilities.statusBarHeight
                    )
            }

            setMeasuredDimension(
                width, height
            )
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val count = childCount
            var actionBarHeight = 0
            for (i in 0 until count) {
                val child = getChildAt(i)
                if (child is ActionBar) {
                    actionBarHeight = child.measuredHeight
                    child.layout(0, 0, measuredWidth, actionBarHeight)
                    break
                }
            }
            for (i in 0 until count) {
                val child = getChildAt(i)
                if (child !is ActionBar && child.visibility == View.VISIBLE) {
                    val layoutParams = child.layoutParams as LayoutParams
                    val topInset = if (child.fitsSystemWindows || actionBarHeight != 0)
                        0
                    else
                        Utilities.statusBarHeight
                    child.layout(
                        layoutParams.leftMargin,
                        layoutParams.topMargin + actionBarHeight + topInset,
                        layoutParams.leftMargin + child.measuredWidth,
                        layoutParams.topMargin + actionBarHeight + child.measuredHeight + topInset
                    )
                }
            }
        }

        override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
            return if (child is ActionBar) {
                super.drawChild(canvas, child, drawingTime)
            } else {
                var actionBarHeight = 0
                var actionBarY = 0
                val childCount = childCount
                for (a in 0 until childCount) {
                    val view = getChildAt(a)
                    if (view === child) {
                        continue
                    }
                    if (view is ActionBar && view.getVisibility() == VISIBLE && view.drawShadow) {
                        actionBarHeight = view.getMeasuredHeight()
                        actionBarY = view.getY().toInt()
                        break
                    }
                }
                val result = super.drawChild(canvas, child, drawingTime)
                if (actionBarHeight != 0) {
                    headerShadowDrawable.setBounds(
                        0,
                        actionBarY + actionBarHeight,
                        measuredWidth,
                        actionBarY + actionBarHeight + headerShadowDrawable.intrinsicHeight
                    )
                    headerShadowDrawable.draw(canvas)
                }
                result
            }
        }
    }

    private inner class GroupContainer(context: Context) : FrameLayout(context) {

        private var leftFrame = Container(context)
        private var rightFrame: Container? = null
        private var frame: Container? = null

        private var animationType = FROM_RIGHT

        init {
            addView(leftFrame)
        }

        fun addGroup(view: View, actionBar: ActionBar?, backgroundColor: Int) {
            rightFrame?.updateParams(0f, 0f)
            frame?.updateParams(0f, 0f)
            leftFrame.updateParams(1f, 0f)
            requestLayout()
            leftFrame.addView(view)
            leftFrame.setBackgroundColor(backgroundColor)
            if (actionBar != null)
                leftFrame.addView(actionBar)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val width = MeasureSpec.getSize(widthMeasureSpec)

            if (inAnimation) {
                var availableWidth = width
                if (animationType == FROM_RIGHT) {
                    leftFrame.measure(
                        measureSpec_exactly((leftFrame.weight * width).toInt()),
                        heightMeasureSpec
                    )
                    availableWidth -= leftFrame.measuredWidth
                    if (rightFrame != null) {
                        rightFrame!!.measure(
                            measureSpec_exactly(if (frame == null || frame!!.weight == 0f) availableWidth else (width * rightFrame!!.weight).toInt()),
                            heightMeasureSpec
                        )
                        availableWidth -= rightFrame!!.measuredWidth
                        frame?.measure(
                            measureSpec_exactly((width * frame!!.weight).toInt()),
                            heightMeasureSpec
                        )
                    }
                } else if (animationType == FROM_LEFT) {
                    if (frame?.parent != null) {
                        frame!!.measure(
                            measureSpec_exactly((width * frame!!.weight).toInt()),
                            heightMeasureSpec
                        )
                        availableWidth -= frame!!.measuredWidth
                    }
                    leftFrame.measure(
                        measureSpec_exactly(if (rightFrame!!.weight == 0f) availableWidth else (width * leftFrame.weight).toInt()),
                        heightMeasureSpec
                    )
                    availableWidth -= leftFrame.measuredWidth
                    rightFrame!!.measure(
                        measureSpec_exactly((width * rightFrame!!.weight).toInt()),
                        heightMeasureSpec
                    )
                } else {
                    frame!!.measure(
                        measureSpec_exactly((width * frame!!.weight).toInt()),
                        heightMeasureSpec
                    )
                    leftFrame.measure(
                        measureSpec_exactly((width * leftFrame.weight).toInt()),
                        heightMeasureSpec
                    )
                    rightFrame!!.measure(
                        measureSpec_exactly(width - leftFrame.measuredWidth),
                        heightMeasureSpec
                    )
                }
            } else {
                if (leftFrame.weight > 0.5f) {
                    leftFrame.measure(
                        measureSpec_exactly((width * leftFrame.weight).toInt()),
                        heightMeasureSpec
                    )
                    rightFrame?.measure(
                        measureSpec_exactly(0),
                        measureSpec_exactly(0)
                    )
                } else {
                    leftFrame.measure(
                        measureSpec_exactly((width * 0.35f).toInt()),
                        heightMeasureSpec
                    )
                    rightFrame?.measure(
                        measureSpec_exactly(width - leftFrame.measuredWidth),
                        heightMeasureSpec
                    )
                }
            }

            setMeasuredDimension(
                width,
                height
            )
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            var l = 0

            if (inAnimation) {
                if (animationType == FROM_RIGHT) {
                    l += leftFrame.leftOffset.toInt()
                    leftFrame.layout(
                        l, 0, l + leftFrame.measuredWidth, leftFrame.measuredHeight
                    )
                    l += leftFrame.measuredWidth
                    if (rightFrame != null) {
                        rightFrame!!.layout(
                            l, 0, l + rightFrame!!.measuredWidth, rightFrame!!.measuredHeight
                        )
                        l += rightFrame!!.measuredWidth
                        if (frame != null) {
                            frame!!.layout(
                                l, 0, l + frame!!.measuredWidth, frame!!.measuredHeight
                            )
                        }
                    }
                } else if (animationType == FROM_LEFT) {
                    if (frame?.parent != null) {
                        l += frame!!.leftOffset.toInt()
                        frame!!.layout(
                            l, 0, l + frame!!.measuredWidth, frame!!.measuredHeight
                        )
                        l += frame!!.measuredWidth
                    }
                    leftFrame.layout(
                        l, 0, l + leftFrame.measuredWidth, leftFrame.measuredHeight
                    )
                    l += leftFrame.measuredWidth
                    rightFrame!!.layout(
                        l, 0, l + rightFrame!!.measuredWidth, rightFrame!!.measuredHeight
                    )
                } else {
                    leftFrame.layout(
                        0, 0, leftFrame.measuredWidth, leftFrame.measuredHeight
                    )
                    rightFrame!!.layout(
                        leftFrame.measuredWidth, 0, measuredWidth, rightFrame!!.measuredHeight
                    )
                    frame!!.layout(
                        frame!!.leftOffset.toInt(),
                        0,
                        frame!!.leftOffset.toInt() + frame!!.measuredWidth,
                        frame!!.measuredHeight
                    )
                }
            } else {
                if (leftFrame.weight > 0.5f) {
                    val leftOffset = leftFrame.leftOffset.toInt()
                    leftFrame.layout(
                        leftOffset, 0, leftOffset + leftFrame.measuredWidth, measuredHeight
                    )
                    rightFrame?.layout(
                        0, 0, 0, 0
                    )
                } else {
                    leftFrame.layout(
                        0, 0, leftFrame.measuredWidth, measuredHeight
                    )
                    rightFrame?.layout(
                        leftFrame.measuredWidth, 0, measuredWidth, measuredHeight
                    )
                }
            }

            val rootView = rootView
            getWindowVisibleDisplayFrame(rect)
            val usableViewHeight: Int =
                rootView.height - (if (rect.top != 0) Utilities.statusBarHeight else 0) - Utilities.getViewInset(
                    rootView
                )
            isKeyboardVisible = usableViewHeight - (rect.bottom - rect.top) > 0
            if (waitingForKeyboardCloseRunnable != null && isKeyboardVisible) {
//                cancelRunOnUIThread(waitingForKeyboardCloseRunnable)
                waitingForKeyboardCloseRunnable!!.run()
                waitingForKeyboardCloseRunnable = null
            }
        }

        override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
            val result = super.drawChild(canvas, child, drawingTime)

            if (indexOfChild(child) == childCount - 1) {
                children.forEach {
                    drawChildShadow(canvas, it as Container)
                }
            }

            return result
        }

        private fun drawChildShadow(canvas: Canvas, child: Container) {
            if (child.weight <= 0.35)
                return
            val drawable = if (child === rightFrame)
                layerShadowDrawable
            else
                layerShadowDrawable2
            val widthOffset = width - child.left
            val alpha = MathUtils.clamp(widthOffset / dp(20f), 0f, 1f)
            drawable.setBounds(
                child.left - drawable.intrinsicWidth,
                child.top,
                child.left,
                child.bottom
            )
            drawable.alpha = (0xff * alpha).toInt()
            drawable.draw(canvas)
        }

        fun isSplit() = isSlidingLastFragment || leftFrame.weight != 1f

        fun prepareForMove() {
            inAnimation = true
            touching = true
            animationType = FROM_LEFT
            isSlidingLastFragment =
                fragmentStack.size < 3 || fragmentStack[fragmentStack.size - 2].groupId != fragmentStack[fragmentStack.size - 3].groupId
            if (isSlidingLastFragment)
                return
            oldFragment = fragmentStack[fragmentStack.size - 2]
            if (frame == null)
                frame = Container(context)
            addView(frame)
            frame!!.updateParams(0.20f, -measuredWidth / 5f)

            val fragment = fragmentStack[fragmentStack.size - 3]
            var screenView = fragment.savedView
            if (screenView != null) {
                val parent = screenView.parent as? ViewGroup
                if (parent != null) {
                    fragment.onRemoveFromParent()
                    parent.removeView(screenView)
                }
            } else
                screenView = fragment.createView(context)

            if (fragment.actionBar != null && fragment.requiredActionBar.shouldAddToContainer) {
                val parent = fragment.requiredActionBar.parent as? ViewGroup
                parent?.removeView(fragment.actionBar)
                frame!!.addView(fragment.requiredActionBar)
            }

            frame!!.setView(screenView, fragment.backgroundColor)

            fragment.onPreResume()
            fragment.onResume()
        }

        fun translateX(dx: Int) {

            val per = min(dx / (measuredWidth * 0.65f), 1f)

            oldFragment?.actionBar?.drawableRotation = per

            if (isSlidingLastFragment)
                leftFrame.weight = 0.35f + per * 0.65f
            else {
                frame!!.updateParams(
                    0.20f + 0.15f * per,
                    -measuredWidth * 0.20f * (1 - per)
                )
                leftFrame.weight =
                    0.35f + 0.30f * per
            }
            this.requestLayout()
        }

        fun finishTranslation(velX: Float, velY: Float) {
            startedTracking = false

            animationType = FROM_LEFT
            var thisInAnimation = true

            val leftFrameWeight = leftFrame.weight

            if (isSlidingLastFragment) {
                val backAnimation =
                    (leftFrame.weight - 0.35) < (0.65f / 3) && (velX < 3500 || velX < velY)

                startAnimation(
                    if (backAnimation) {
                        val leftFrameWeightDist = leftFrameWeight - 0.35f
                        object : Animation() {
                            init {
                                duration = (200 * leftFrameWeight).toLong()
                                setAnimationListener(object : AnimationListener {
                                    override fun onAnimationStart(animation: Animation?) {
                                    }

                                    override fun onAnimationEnd(animation: Animation?) {
                                        inAnimation = false
                                        touching = false
                                        thisInAnimation = false
                                        leftFrame.updateParams(0.35f, 0f)
                                        rightFrame!!.updateParams(0.65f, 0f)
                                        requestLayout()
                                        if (frameAnimationFinishRunnable != null)
                                            post(frameAnimationFinishRunnable)
                                    }

                                    override fun onAnimationRepeat(animation: Animation?) {
                                    }
                                })
                            }

                            override fun applyTransformation(
                                interpolatedTime: Float,
                                t: Transformation?
                            ) {
                                if (thisInAnimation) {
                                    leftFrame.weight =
                                        leftFrameWeight - leftFrameWeightDist * interpolatedTime
                                    requestLayout()
                                }
                            }
                        }
                    } else {
                        val leftFrameWeightDist = 1f - leftFrameWeight
                        object : Animation() {
                            init {
                                duration = (200 * leftFrameWeight).toLong()
                                setAnimationListener(object : AnimationListener {
                                    override fun onAnimationStart(animation: Animation?) {
                                    }

                                    override fun onAnimationEnd(animation: Animation?) {
                                        inAnimation = false
                                        touching = false
                                        thisInAnimation = false
                                        leftFrame.updateParams(1f, 0f)
                                        requestLayout()
                                        finishFragment(fragmentStack[fragmentStack.size - 1])
                                        fragmentStack[fragmentStack.size - 1].onGetFirstInStack()
                                        if (frameAnimationFinishRunnable != null)
                                            post(frameAnimationFinishRunnable)
                                    }

                                    override fun onAnimationRepeat(animation: Animation?) {
                                    }
                                })
                            }

                            override fun applyTransformation(
                                interpolatedTime: Float,
                                t: Transformation?
                            ) {
                                if (thisInAnimation) {
                                    leftFrame.weight =
                                        leftFrameWeight + leftFrameWeightDist * interpolatedTime
                                    requestLayout()
                                }
                            }
                        }
                    }
                )
            } else {
                val backAnimation =
                    (leftFrame.weight - 0.35f) < (0.65f / 3) && (velX < 3500 || velX < velY)

                oldFragment = null
                val frameWeight = frame!!.weight
                val frameLeftOffset = frame!!.leftOffset

                val leftFragmentActionBar = fragmentStack[fragmentStack.size - 2].actionBar

                startAnimation(
                    if (backAnimation) {
                        val frameWeightDist = frameWeight - 0.20f
                        val frameLeftOffsetDist = -(measuredWidth / 5) - frameLeftOffset
                        val leftFrameWeightDist = leftFrameWeight - 0.35f
                        val leftFragmentActionBarDist =
                            leftFragmentActionBar?.drawableCurrentRotation
                        object : Animation() {
                            init {
                                duration = (200 * leftFrameWeight / 0.65f).toLong()
                                setAnimationListener(object : AnimationListener {
                                    override fun onAnimationStart(animation: Animation?) {
                                    }

                                    override fun onAnimationEnd(animation: Animation?) {
                                        inAnimation = false
                                        touching = false
                                        thisInAnimation = false
                                        removeViewInLayout(frame!!)
                                        leftFragmentActionBar?.drawableRotation = 0f
                                        leftFrame.updateParams(0.35f, 0f)
                                        rightFrame!!.updateParams(0.65f, 0f)
                                        requestLayout()
                                        pauseFragment(fragmentStack[fragmentStack.size - 3], true)
                                        if (frameAnimationFinishRunnable != null)
                                            post(frameAnimationFinishRunnable)
                                    }

                                    override fun onAnimationRepeat(animation: Animation?) {
                                    }
                                })
                            }

                            override fun applyTransformation(
                                interpolatedTime: Float,
                                t: Transformation?
                            ) {
                                if (thisInAnimation) {
                                    frame!!.updateParams(
                                        frameWeight - frameWeightDist * interpolatedTime,
                                        frameLeftOffset + frameLeftOffsetDist * interpolatedTime
                                    )
                                    leftFrame.weight =
                                        leftFrameWeight - leftFrameWeightDist * interpolatedTime
                                    leftFragmentActionBar?.drawableRotation =
                                        leftFragmentActionBarDist!! - leftFragmentActionBarDist * interpolatedTime
                                    requestLayout()
                                }
                            }
                        }
                    } else {
                        val frameWeightDist = 0.35f - frameWeight
                        val leftFrameWeightDist = 0.65f - leftFrameWeight
                        object : Animation() {
                            init {
                                duration = (200 * leftFrameWeight / 0.65f).toLong()
                                setAnimationListener(object : AnimationListener {
                                    override fun onAnimationStart(animation: Animation?) {
                                    }

                                    override fun onAnimationEnd(animation: Animation?) {
                                        val temp = rightFrame
                                        rightFrame = leftFrame
                                        leftFrame = frame!!
                                        frame = temp
                                        inAnimation = false
                                        touching = false
                                        thisInAnimation = false
                                        removeViewInLayout(frame!!)
                                        leftFragmentActionBar?.drawableRotation = 1f
                                        leftFrame.updateParams(0.35f, 0f)
                                        rightFrame!!.updateParams(0.65f, 0f)
                                        requestLayout()
                                        finishFragment(fragmentStack[fragmentStack.size - 1])
                                        fragmentStack[fragmentStack.size - 1].onGetFirstInStack()
                                        resumeFragment(fragmentStack[fragmentStack.size - 2], false)
                                        if (frameAnimationFinishRunnable != null)
                                            post(frameAnimationFinishRunnable)
                                    }

                                    override fun onAnimationRepeat(animation: Animation?) {
                                    }
                                })
                            }

                            override fun applyTransformation(
                                interpolatedTime: Float,
                                t: Transformation?
                            ) {
                                if (thisInAnimation) {
                                    frame!!.updateParams(
                                        frameWeight + frameWeightDist * interpolatedTime,
                                        frameLeftOffset * (1 - interpolatedTime)
                                    )
                                    leftFrame.weight =
                                        leftFrameWeight + leftFrameWeightDist * interpolatedTime
                                    leftFragmentActionBar?.drawableRotation =
                                        leftFragmentActionBar!!.drawableCurrentRotation * (1 - interpolatedTime) + interpolatedTime
                                    requestLayout()
                                }
                            }
                        }
                    }
                )
            }
            isSlidingLastFragment = false
        }

        //region NEXT
        fun nextScreen(view: View, actionBar: ActionBar?, forceWithoutAnimation: Boolean) {
            if (forceWithoutAnimation) {
                leftFrame.updateParams(0.35f, 0f)
                if (rightFrame == null) {
                    rightFrame = Container(context)
                    rightFrame!!.weight = 0.65f
                    addView(rightFrame)
                } else
                    rightFrame!!.updateParams(0.65f, 0f)
                if (oldFragment != null) {
                    oldFragment!!.actionBar?.drawableRotation = 0f
                    oldFragment!!.onPrePause()
                    pauseFragment(oldFragment!!, true)
                    oldFragment = null
                    val temp = leftFrame
                    leftFrame = rightFrame!!
                    rightFrame = temp
                }
                rightFrame!!.setView(view, newFragment!!.backgroundColor)
                if (actionBar != null)
                    rightFrame!!.addView(actionBar)
                newFragment!!.actionBar?.drawableRotation = 1f
                newFragment!!.onPreResume()
                resumeFragment(newFragment!!, true)
                newFragment = null
            } else if (oldFragment == null)
                startFirstNextAnimation(view, actionBar)
            else
                startNextAnimation(view, actionBar)

        }

        private fun startFirstNextAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            var thisInAnimation = true
            animationType = FROM_RIGHT

            if (rightFrame == null) {
                rightFrame = Container(context)
                addView(rightFrame)
            } else
                bringChildToFront(rightFrame)
            rightFrame!!.updateParams(0.65f, 0f)
            rightFrame!!.setView(view, newFragment!!.backgroundColor)
            if (actionBar != null)
                rightFrame!!.addView(actionBar)

            newFragment!!.actionBar?.drawableRotation = 1f
            newFragment!!.onPreResume()
            startAnimation(
                object : Animation() {
                    init {
                        duration = 200
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                inAnimation = false
                                thisInAnimation = false
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame!!.updateParams(0.65f, 0f)
                                requestLayout()
                                resumeFragment(newFragment!!, true)
                                newFragment = null
                                if (frameAnimationFinishRunnable != null)
                                    post(frameAnimationFinishRunnable)
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (thisInAnimation) {
                            leftFrame.weight = 1f - interpolatedTime * 0.65f
                            requestLayout()
                        }
                    }
                }
            )
        }

        private fun startNextAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            var thisInAnimation = true
            animationType = FROM_RIGHT

            if (frame == null)
                frame = Container(context)
            addView(frame)
            frame!!.updateParams(0.65f, 0f)

            frame!!.setView(view, newFragment!!.backgroundColor)
            if (actionBar != null)
                frame!!.addView(actionBar)


            val currentFragmentActionBar = fragmentStack[fragmentStack.size - 2].actionBar
            oldFragment!!.onPrePause()

            newFragment!!.actionBar?.drawableRotation = 1f
            newFragment!!.onPreResume()

            startAnimation(
                object : Animation() {
                    init {
                        duration = 200
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = leftFrame
                                leftFrame = rightFrame!!
                                rightFrame = frame
                                frame = temp
                                inAnimation = false
                                thisInAnimation = false
                                removeViewInLayout(frame!!)
                                currentFragmentActionBar?.drawableRotation = 0f
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame!!.updateParams(0.65f, 0f)
                                requestLayout()
                                pauseFragment(oldFragment!!, true)
                                oldFragment = null
                                resumeFragment(newFragment!!, true)
                                newFragment = null
                                if (frameAnimationFinishRunnable != null)
                                    post(frameAnimationFinishRunnable)
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (thisInAnimation) {
                            leftFrame.updateParams(
                                0.35f - (0.15f) * interpolatedTime,
                                -measuredWidth * 0.20f * interpolatedTime
                            )
                            rightFrame!!.weight =
                                0.65f - (0.30f) * interpolatedTime
                            currentFragmentActionBar?.drawableRotation = 1f - interpolatedTime
                            requestLayout()
                        }
                    }
                }
            )
        }
        //endregion

        //region PREVIOUS
        fun previousScreen(view: View?, actionBar: ActionBar?, forceWithoutAnimation: Boolean) {
            if (forceWithoutAnimation) {
                if (oldFragment != null) {
                    oldFragment!!.onPrePause()
                    finishFragment(oldFragment!!)
                }

                if (view == null) {
                    leftFrame.updateParams(1f, 0f)
                    newFragment!!.onGetFirstInStack()
                    newFragment = null
                } else if (oldFragment != null) {
                    val temp = rightFrame
                    rightFrame = leftFrame
                    leftFrame = temp!!
                    rightFrame!!.setView(view, newFragment!!.backgroundColor)
                    if (actionBar != null)
                        rightFrame!!.addView(actionBar)

                    newFragment!!.onPreResume()
                    resumeFragment(newFragment!!, false)
                    newFragment = null

                    newFragment2!!.actionBar?.drawableRotation = 1f
                    newFragment2!!.onGetFirstInStack()
                    newFragment2 = null
                } else {
                    val temp = rightFrame
                    rightFrame = leftFrame
                    leftFrame = temp!!
                    rightFrame!!.weight = 0.65f
                    leftFrame.weight = 0.35f

                    leftFrame.addView(view)
                    if (actionBar != null)
                        leftFrame.addView(actionBar)

                    newFragment!!.onPreResume()
                    resumeFragment(newFragment!!, false)
                    newFragment = null
                }
                oldFragment = null
                requestLayout()
            } else if (oldFragment == null)
                startPreviousAnimationFromFullScreen(view!!, actionBar)
            else if (view == null)
                startLastPreviousAnimation()
            else
                startPreviousAnimation(view, actionBar)
        }

        private fun startLastPreviousAnimation() {
            inAnimation = true
            var thisInAnimation = true
            animationType = FROM_LEFT

            oldFragment!!.onPrePause()
            startAnimation(
                object : Animation() {
                    init {
                        duration = 200
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                inAnimation = false
                                thisInAnimation = false
                                leftFrame.updateParams(1f, 0f)
                                requestLayout()
                                finishFragment(oldFragment!!)
                                oldFragment = null
                                newFragment!!.onGetFirstInStack()
                                newFragment = null
                                if (frameAnimationFinishRunnable != null)
                                    post(frameAnimationFinishRunnable)
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (thisInAnimation) {
                            leftFrame.weight =
                                0.35f + interpolatedTime * 0.65f
                            requestLayout()
                        }
                    }
                }
            )
        }

        private fun startPreviousAnimationFromFullScreen(view: View, actionBar: ActionBar?) {
            inAnimation = true
            var thisInAnimation = true
            animationType = FROM_LEFT

            if (frame == null)
                frame = Container(context)
            addView(frame)
            frame!!.updateParams(0.20f, -measuredWidth * 0.20f)
            frame!!.setView(view, newFragment2!!.backgroundColor)
            if (actionBar != null)
                frame!!.addView(actionBar)

            newFragment2!!.onPreResume()
            startAnimation(
                object : Animation() {
                    init {
                        duration = 200
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {}

                            override fun onAnimationEnd(animation: Animation?) {
                                inAnimation = false
                                thisInAnimation = false
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = frame!!
                                frame = temp
                                removeViewInLayout(frame)
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame!!.updateParams(0.65f, 0f)
                                requestLayout()
                                newFragment!!.actionBar?.drawableRotation = 1f
                                newFragment = null
                                resumeFragment(newFragment2!!, false)
                                newFragment2 = null
                                if (frameAnimationFinishRunnable != null)
                                    post(frameAnimationFinishRunnable)
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (thisInAnimation) {
                            frame!!.updateParams(
                                0.20f + 0.15f * interpolatedTime,
                                -measuredWidth * 0.20f * (1 - interpolatedTime)
                            )
                            newFragment!!.actionBar?.drawableRotation = interpolatedTime
                            requestLayout()
                        }
                    }
                }
            )
        }

        private fun startPreviousAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            var thisInAnimation = true
            animationType = FROM_LEFT

            if (frame == null)
                frame = Container(context)
            frame!!.updateParams(0.20f, -measuredWidth * 0.20f)
            frame!!.setView(view, newFragment2!!.backgroundColor)
            addView(frame)
            if (actionBar != null)
                frame!!.addView(actionBar)

            oldFragment!!.onPrePause()

            newFragment!!.onPreResume()
            newFragment2!!.actionBar?.drawableRotation = 0f
            newFragment2!!.onPreResume()
            startAnimation(
                object : Animation() {
                    init {
                        duration = 200
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {}

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = frame!!
                                frame = temp
                                inAnimation = false
                                thisInAnimation = false
                                removeViewInLayout(frame!!)
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame!!.updateParams(0.65f, 0f)
                                requestLayout()
                                finishFragment(oldFragment!!)
                                oldFragment = null
                                newFragment!!.actionBar?.drawableRotation = 1f
                                newFragment!!.onGetFirstInStack()
                                newFragment = null
                                resumeFragment(newFragment2!!, false)
                                newFragment2 = null
                                if (frameAnimationFinishRunnable != null)
                                    post(frameAnimationFinishRunnable)
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (thisInAnimation) {
                            frame!!.updateParams(
                                0.20f + 0.15f * interpolatedTime,
                                -measuredWidth * 0.20f * (1 - interpolatedTime)
                            )
                            leftFrame.weight =
                                0.35f + 0.30f * interpolatedTime
                            newFragment!!.actionBar?.drawableRotation = interpolatedTime
                            requestLayout()
                        }
                    }
                }
            )
        }
        //endregion

        //region LEFT
        fun openLeft(view: View, actionBar: ActionBar?) {
            startOpenLeftAnimation(view, actionBar)
        }

        private fun startOpenLeftAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            var thisInAnimation = true
            animationType = FROM_LEFT

            if (rightFrame == null) {
                rightFrame = Container(context)
                addView(rightFrame)
            }
            rightFrame!!.updateParams(0.20f, -measuredWidth * 0.20f)

            rightFrame!!.setView(view, newFragment!!.backgroundColor)
            if (actionBar != null)
                rightFrame!!.addView(actionBar)

            newFragment!!.onPreResume()
            startAnimation(
                object : Animation() {
                    init {
                        duration = 200
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = temp!!
                                inAnimation = false
                                thisInAnimation = false
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame!!.updateParams(0.65f, 0f)
                                requestLayout()
                                resumeFragment(newFragment!!, false)
                                newFragment = null
                                if (frameAnimationFinishRunnable != null)
                                    post(frameAnimationFinishRunnable)
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                        if (thisInAnimation) {
                            rightFrame!!.updateParams(
                                0.20f + 0.15f * interpolatedTime,
                                -measuredWidth * 0.20f * (1 - interpolatedTime)
                            )
                            leftFrame.weight =
                                1f - 0.35f * interpolatedTime
                            requestLayout()
                        }
                    }
                }
            )
        }

        fun closeLeft() {
            startCloseLeftAnimation()
        }

        private fun startCloseLeftAnimation() {
            inAnimation = true
            var thisInAnimation = true
            animationType = FROM_RIGHT

            oldFragment!!.onPrePause()
            startAnimation(
                object : Animation() {
                    init {
                        duration = 200
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = temp!!
                                inAnimation = false
                                thisInAnimation = false
                                leftFrame.updateParams(1f, 0f)
                                rightFrame!!.updateParams(0f, 0f)
                                requestLayout()
                                pauseFragment(oldFragment!!, true)
                                oldFragment = null
                                if (frameAnimationFinishRunnable != null)
                                    post(frameAnimationFinishRunnable)
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                        if (thisInAnimation) {
                            leftFrame.updateParams(
                                0.35f - 0.15f * interpolatedTime,
                                -measuredWidth * 0.20f * interpolatedTime
                            )
                            rightFrame!!.weight =
                                0.65f + 0.35f * interpolatedTime
                        }
                    }
                }
            )
        }
        //endregion

        //region RIGHT
        fun replaceRight(view: View, actionBar: ActionBar?, forceWithoutAnimation: Boolean) {
            startReplaceAnimation(view, actionBar)
        }

        private fun startReplaceAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            var thisInAnimation = true
            animationType = FROM_RIGHT_FLOATING

            if (frame == null)
                frame = Container(context)
            frame!!.updateParams(0.65f, measuredWidth.toFloat())
            frame!!.setView(view, newFragment!!.backgroundColor)
            addView(frame)
            if (actionBar != null)
                frame!!.addView(actionBar)

            newFragment!!.actionBar?.drawableRotation = 1f
            newFragment!!.onPreResume()
            oldFragment!!.onPrePause()
            startAnimation(
                object : Animation() {
                    init {
                        duration = 200
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = frame!!
                                frame = temp
                                inAnimation = false
                                thisInAnimation = false
                                removeViewInLayout(frame!!)
                                rightFrame!!.leftOffset = 0f
                                requestLayout()

                                finishFragment(oldFragment!!)
                                oldFragment = null
                                resumeFragment(newFragment!!, true)
                                newFragment = null
                                if (frameAnimationFinishRunnable != null)
                                    post(frameAnimationFinishRunnable)
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (thisInAnimation) {
                            frame!!.leftOffset =
                                measuredWidth - measuredWidth * 0.65f * interpolatedTime
                            requestLayout()
                        }
                    }
                }
            )
        }
        //endregion

        //region MODAL
        fun showAsModal(view: View, actionBar: ActionBar?, forceWithoutAnimation: Boolean){
            if (forceWithoutAnimation){
                leftFrame.updateParams(0.65f, 0f)
                if (rightFrame == null) {
                    rightFrame = Container(context)
                    rightFrame!!.weight = 0.35f
                    addView(rightFrame)
                } else
                    rightFrame!!.updateParams(0.35f, 0f)
            } else
                startModelShowingAnimation(view, actionBar)
        }

        fun startModelShowingAnimation(view: View, actionBar: ActionBar?){

        }
        //endregion
    }


    private var waitingForKeyboardCloseRunnable: Runnable? = null
//    private val delayedOpenAnimationRunnable: Runnable? = null

    private var containerView = GroupContainer(context)
    private var containerViewBack = GroupContainer(context)
    private var currentAnimationSet: AnimatorSet? = null

    //    private var interpolator = FastOutLinearInInterpolator()
    private var currentGroupId = 0
    val fragmentsCount: Int
        get() = fragmentStack.size
    var clearable = false

    private var oldFragment: Fragment? = null
    private var newFragment: Fragment? = null
    private var newFragment2: Fragment? = null

    val parentActivity: AppCompatActivity = context as AppCompatActivity

    private val fragmentStack = arrayListOf<Fragment>()

    init {
        containerViewBack.visibility = View.GONE
        addView(containerViewBack)
        addView(containerView)

        fitsSystemWindows = true
        setOnApplyWindowInsetsListener { v: View, insets: WindowInsets ->
//            if (Build.VERSION.SDK_INT >= 30) {
//                val newKeyboardVisibility =
//                    insets.isVisible(WindowInsets.Type.ime())
//                val imeHeight =
//                    insets.getInsets(WindowInsets.Type.ime()).bottom
//                if (keyboardVisibility != newKeyboardVisibility || imeHeight != imeHeight) {
//                    keyboardVisibility = newKeyboardVisibility
//                    imeHeight = imeHeight
//                    requestLayout()
//                }
//            }
//            if (AndroidUtilities.statusBarHeight !== insets.systemWindowInsetTop) {
//                drawerLayoutContainer.requestLayout()
//            }
            val newTopInset = insets.systemWindowInsetTop
            if (newTopInset != 0 && Utilities.statusBarHeight != newTopInset) {
                Utilities.statusBarHeight = newTopInset
            }
//            if (Build.VERSION.SDK_INT >= 28) {
//                val cutout = insets.displayCutout
//                hasCutout = cutout != null && cutout.boundingRects.size != 0
//            }
            invalidate()
            if (Build.VERSION.SDK_INT >= 30) {
                return@setOnApplyWindowInsetsListener WindowInsets.CONSUMED
            } else {
                return@setOnApplyWindowInsetsListener insets.consumeSystemWindowInsets()
            }
        }
        systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

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

        if (Utilities.isLandscape && containerView.isSplit()) {
            containerView.prepareForMove()
        } else if (fragmentStack.size > 1) {
            containerViewBack.translationX = 0f
            containerViewBack.alpha = 1f
            containerViewBack.visibility = View.VISIBLE

            newFragment = fragmentStack[fragmentStack.size - 2]
            val newFragment2: Fragment?
            var leftView: View? = null
            var leftActionBar: ActionBar? = null
            if (Utilities.isLandscape && fragmentStack.size > 2 && fragmentStack[fragmentStack.size - 3].groupId == newFragment!!.groupId) {
                newFragment2 = fragmentStack[fragmentStack.size - 3]
                leftView = newFragment2.savedView
                if (leftView == null)
                    leftView = newFragment2.createView(context)
                else
                    (leftView.parent as? ViewGroup)?.removeView(leftView)
                leftActionBar = newFragment2.actionBar
                if (leftActionBar != null && leftActionBar.shouldAddToContainer)
                    (leftActionBar.parent as? ViewGroup)?.removeView(leftActionBar)
            } else
                newFragment2 = null

            var rightView = newFragment!!.savedView
            if (rightView == null)
                rightView = newFragment!!.createView(context)
            else
                (rightView.parent as? ViewGroup)?.removeView(rightView)

            var rightActionBar: ActionBar? = newFragment!!.actionBar
            if (rightActionBar != null && rightActionBar.shouldAddToContainer)
                (rightActionBar.parent as? ViewGroup)?.removeView(rightActionBar)
            else
                rightActionBar = null


            if (leftView == null) {
                containerViewBack.addGroup(rightView, rightActionBar, newFragment!!.backgroundColor)
                newFragment!!.onPreResume()
                newFragment!!.resume()
                newFragment = null
            } else {
                containerViewBack.addGroup(leftView, leftActionBar, newFragment2!!.backgroundColor)
                containerViewBack.nextScreen(rightView, rightActionBar, true)
                newFragment2.onPreResume()
                resumeFragment(newFragment2, false)
            }
        }
    }

    private fun onSlideAnimationEnd(backAnimation: Boolean) {
        if (backAnimation) {
            if (fragmentStack.size > 1)
                pauseFragment(
                    fragmentStack[fragmentStack.size - 2],
                    !fragmentStack[fragmentStack.size - 1].popup
                )
        } else {
            val fragment = fragmentStack[fragmentStack.size - 1]
            if (fragmentStack.size == 1 || fragment.groupId != fragmentStack[fragmentStack.size - 2].groupId)
                currentGroupId--

            finishFragment(fragment)

            val temp = containerView
            containerView = containerViewBack
            containerViewBack = temp
            bringChildToFront(containerView)

            if (!fragmentStack.isEmpty())
                fragmentStack[fragmentStack.size - 1].onGetFirstInStack()
        }
        if (!fragmentStack.isEmpty() && !fragmentStack[fragmentStack.size - 1].popup)
            containerViewBack.visibility = GONE
        containerView.translationX = 0f
        startedTracking = false
        isSlideFinishing = false
        innerTranslationX = 0f
    }


    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (!isSlideFinishing && inAnimation == touching) {
            if (fragmentStack.size > 1 || clearable) {
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
                            val currentFragment = fragmentStack[fragmentStack.size - 1]
                            if (currentFragment.canBeginSlide() && findScrollingChild(
                                    this,
                                    ev.x,
                                    ev.y
                                ) == null
                            )
                                prepareForMoving(ev.x)
                            else
                                maybeStartedTracking = false
                        } else if (startedTracking) {
                            if (!beginTrackingSent) {
                                hideKeyboard()
                                fragmentStack[fragmentStack.size - 1].onBeginSlide()
                                beginTrackingSent = true
                            }
                            if (Utilities.isLandscape && containerView.isSplit())
                                containerView.translateX(dx)
                            else {
                                containerView.translationX = dx.toFloat()
                                innerTranslationX = dx.toFloat()
                            }
                        }
                    } else if (ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_POINTER_UP)) {
                        if (velocityTracker == null)
                            velocityTracker = VelocityTracker.obtain()
                        velocityTracker!!.computeCurrentVelocity(1000)
                        val currentFragment = fragmentStack[fragmentStack.size - 1]
                        if (!startedTracking && currentFragment.isSwipeBackEnabled(ev)) {
                            val velX = velocityTracker!!.xVelocity
                            val velY = velocityTracker!!.yVelocity
                            if (velX >= 3500 && velX > abs(velY) && currentFragment.canBeginSlide()) {
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

                            if (Utilities.isLandscape && containerView.isSplit()) {
                                containerView.finishTranslation(velX, velY)
                            } else {
                                val x = containerView.x
                                val backAnimation =
                                    x < containerView.measuredWidth / 3f && (velX < 3500 || velX < velY)
                                val distToMove: Float = if (backAnimation)
                                    x
                                else
                                    containerView.measuredWidth - x

                                if (backAnimation) {
                                    if (fragmentStack.size > 1)
                                        fragmentStack[fragmentStack.size - 2].onPrePause()
                                } else
                                    fragmentStack[fragmentStack.size - 1].onPrePause()

                                val duration = max(
                                    (200 * distToMove / containerView.measuredWidth).toLong(),
                                    50
                                )
                                val animatorSet = AnimatorSet()
                                if (backAnimation)
                                    animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(
                                            containerView,
                                            View.TRANSLATION_X,
                                            0f
                                        ).setDuration(duration),
                                        ObjectAnimator.ofFloat(
                                            this,
                                            "innerTranslationX",
                                            innerTranslationX,
                                            0f
                                        )
                                            .setDuration(duration)
                                    )
                                else
                                    animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(
                                            containerView,
                                            View.TRANSLATION_X,
                                            containerView.measuredWidth.toFloat()
                                        ).setDuration(duration),
                                        ObjectAnimator.ofFloat(
                                            this,
                                            "innerTranslationX",
                                            innerTranslationX,
                                            containerView.measuredWidth.toFloat()
                                        ).setDuration(duration)
                                    )
                                animatorSet.addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator?) {
                                        onSlideAnimationEnd(backAnimation)
                                    }
                                })
                                isSlideFinishing = true
                                animatorSet.start()
                            }
                        } else {
                            maybeStartedTracking = false
                        }
                        if (velocityTracker != null) {
                            velocityTracker!!.recycle()
                            velocityTracker = null
                        }
                    }
                }
            }
            return startedTracking
        }

        return false
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val width = width - paddingLeft - paddingRight
        val translationX = (innerTranslationX + paddingRight).toInt()
        val clipLeft: Int
        val clipRight: Int

        if (child === containerViewBack) {
            clipRight = if (inAnimation || !drawShadow)
                translationX + dp(1)
            else
                width
            clipLeft = paddingLeft
        } else {
            clipRight = width + paddingLeft
            clipLeft = translationX
        }

        val restoreCount = canvas.save()
        if (!isSlideFinishing && !inAnimation)
            canvas.clipRect(clipLeft, 0, clipRight, height)
        val result = super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(restoreCount)

        if (translationX != 0 || drawShadow) {
            val widthOffset = width - translationX
            if (child === containerView && !drawShadow) {
                val alpha = MathUtils.clamp(widthOffset / dp(20f), 0f, 1f)
                layerShadowDrawable.setBounds(
                    translationX - layerShadowDrawable.intrinsicWidth,
                    child.top,
                    translationX,
                    child.bottom
                )
                layerShadowDrawable.alpha = (0xff * alpha).toInt()
                layerShadowDrawable.draw(canvas)
            } else if (child === containerViewBack) {
                val opacity = MathUtils.clamp(widthOffset / width.toFloat(), 0f, 0.8f)
                scrimPaint.color = Color.argb((0x99 * opacity).toInt(), 0x00, 0x00, 0x00)
                canvas.drawRect(
                    clipLeft.toFloat(),
                    0f,
                    clipRight.toFloat(),
                    height.toFloat(),
                    scrimPaint
                )
            }
        }

        return result
    }

    private fun resumeFragment(fragment: Fragment, inFirst: Boolean) {
        drawShadow = fragment.popup
        fragment.onBecomeFullyVisible()
        fragment.resume()
        if (inFirst)
            fragment.onGetFirstInStack()
    }

    private fun pauseFragment(fragment: Fragment, hideView: Boolean) {
//        fragment.onBecomeFullyHidden()
        fragment.pause()
        if (fragment.savedView != null && hideView) {
            val parent = fragment.savedView?.parent as? ViewGroup
            if (parent != null) {
                fragment.onRemoveFromParent()
                try {
                    parent.removeViewInLayout(fragment.savedView)
                } catch (e: Exception) {
                    try {
                        parent.removeView(fragment.savedView)
                    } catch (e2: Exception) {
                    }
                }
            }

            if (fragment.actionBar != null && fragment.requiredActionBar.shouldAddToContainer) {
                val actionBarParent = fragment.requiredActionBar.parent as? ViewGroup
                actionBarParent?.removeViewInLayout(fragment.actionBar)
            }
        }
    }

    private fun finishFragment(fragment: Fragment) {
        //        fragment.onBecomeFullyHidden()
        fragment.pause()
        if (fragment.savedView != null) {
            val parent = fragment.savedView?.parent as? ViewGroup
            if (parent != null) {
                fragment.onRemoveFromParent()
                try {
                    parent.removeViewInLayout(fragment.savedView)
                } catch (e: Exception) {
                    try {
                        parent.removeView(fragment.savedView)
                    } catch (e2: Exception) {
                    }
                }
            }

            if (fragment.actionBar != null && fragment.requiredActionBar.shouldAddToContainer) {
                val actionBarParent = fragment.requiredActionBar.parent as? ViewGroup
                actionBarParent?.removeViewInLayout(fragment.actionBar)
            }
        }
        fragment.onFragmentDestroy()
        fragment.parentLayout = null
        fragmentStack.remove(fragment)
    }

    fun presentFragmentGroup(
        screen: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1
    ): Boolean = presentFragment(screen, true, removeLast, forceWithoutAnimation, uniqueWith)


    fun presentFragment(
        fragment: Fragment,
        newGroup: Boolean = false,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1
    ): Boolean {
        if (!inAnimation)
            return presentFragmentInternal(
                fragment,
                newGroup,
                removeLast,
                false,
                forceWithoutAnimation,
                uniqueWith
            )
        else if (frameAnimationFinishRunnable == null)
            frameAnimationFinishRunnable = Runnable {
                frameAnimationFinishRunnable = null
                presentFragmentInternal(
                    fragment,
                    newGroup,
                    removeLast,
                    false,
                    forceWithoutAnimation,
                    uniqueWith
                )
            }
        return false
    }

    fun presentFragmentAsPopUp(
        fragment: Fragment,
        uniqueWith: Int = -1
    ): Boolean {
        fragment.popup = true
        if (!inAnimation)
            return presentFragmentInternal(
                fragment,
                true,
                false,
                false,
                true,
                uniqueWith
            )
        else if (frameAnimationFinishRunnable == null)
            frameAnimationFinishRunnable = Runnable {
                frameAnimationFinishRunnable = null
                presentFragmentInternal(
                    fragment,
                    true,
                    false,
                    false,
                    true,
                    uniqueWith
                )
            }
        return false
    }

    private fun presentFragmentInternal(
        fragment: Fragment,
        newGroup: Boolean,
        removeLast: Boolean,
        innerGroup: Boolean,
        forceWithoutAnimation: Boolean,
        uniqueWith: Int
    ): Boolean {
        if (!fragment.onFragmentCreate() || uniqueWith != -1 && fragmentStack.any { it.fragmentId == uniqueWith })
            return false

        if (parentActivity.currentFocus != null && fragment.hideKeyboardOnShow())
            parentActivity.currentFocus.hideKeyboard()

        if (newGroup)
            currentGroupId++
        fragment.groupId = currentGroupId
        if (innerGroup)
            fragment.innerGroupId = currentGroupId + 1
        fragment.parentLayout = this

        drawShadow = fragment.popup

        var screenView = fragment.savedView
        if (screenView != null) {
            val parent = screenView.parent as? ViewGroup
            if (parent != null) {
                fragment.onRemoveFromParent()
                parent.removeView(screenView)
            }
        } else
            screenView = fragment.createView(context)

        if (fragment.actionBar != null && fragment.requiredActionBar.shouldAddToContainer) {
            val parent = fragment.requiredActionBar.parent as? ViewGroup
            parent?.removeView(fragment.actionBar)
            containerViewBack.addGroup(
                screenView,
                fragment.requiredActionBar,
                fragment.backgroundColor
            )
        } else
            containerViewBack.addGroup(screenView, null, fragment.backgroundColor)

        fragmentStack.add(fragment)

        val temp = containerView
        containerView = containerViewBack
        containerViewBack = temp
        bringChildToFront(containerView)

        if (forceWithoutAnimation) {
            containerView.translationX = 0f
            containerView.alpha = 1f
            if (!fragment.popup)
                containerViewBack.visibility = GONE
            containerView.visibility = View.VISIBLE
            if (fragmentStack.size > 1) {
                val oldFragment = fragmentStack[fragmentStack.size - 2]
                oldFragment.onPrePause()
                if (removeLast)
                    finishFragment(oldFragment)
                else
                    pauseFragment(oldFragment, !fragment.popup)
            }
            fragment.onPreResume()
            resumeFragment(fragment, true)
        } else {
            inAnimation = true
            containerView.translationX = measuredWidth * 0.5f
            containerView.alpha = 0f
            containerView.visibility = VISIBLE

            currentAnimationSet = AnimatorSet()
            currentAnimationSet!!.duration = 200
            val alphaAnimation = ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.8f, 1.0f)
            val translationXAnimation = ObjectAnimator.ofFloat(
                containerView,
                View.TRANSLATION_X,
                containerView.measuredWidth * 0.3f,
                0f
            )

            fragment.onPreResume()
            if (fragmentStack.size > 1)
                fragmentStack[fragmentStack.size - 2].onPrePause()

            currentAnimationSet!!.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {

                }

                override fun onAnimationEnd(animation: Animator?) {
                    if (fragmentStack.size > 1) {
                        val oldFragment = fragmentStack[fragmentStack.size - 2]
                        if (removeLast)
                            finishFragment(oldFragment)
                        else
                            pauseFragment(oldFragment, !fragment.popup)
                    }
                    resumeFragment(fragment, true)
                    if (!fragment.popup)
                        containerViewBack.visibility = GONE
                    inAnimation = false
                    if (frameAnimationFinishRunnable != null)
                        post(frameAnimationFinishRunnable)
                }

                override fun onAnimationCancel(animation: Animator?) {

                }

                override fun onAnimationRepeat(animation: Animator?) {

                }

            })

            currentAnimationSet!!.playTogether(translationXAnimation, alphaAnimation)
            currentAnimationSet!!.start()
        }

        return true
    }

    fun nextFragmentInnerGroup(
        fragment: Fragment,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1
    ): Boolean {
        val removeLast = if (fragmentStack.size > 1) {
            fragmentStack[fragmentStack.size - 1].innerGroupId == currentGroupId + 1
        } else
            false

        if (!inAnimation)
            return nextFragmentInnerGroupInternal(
                fragment,
                removeLast,
                forceWithoutAnimation,
                uniqueWith
            )
        else if (frameAnimationFinishRunnable == null)
            frameAnimationFinishRunnable = Runnable {
                frameAnimationFinishRunnable = null

                nextFragmentInnerGroupInternal(
                    fragment,
                    removeLast,
                    forceWithoutAnimation,
                    uniqueWith
                )
            }
        return false
    }

    private fun nextFragmentInnerGroupInternal(
        fragment: Fragment,
        removeLast: Boolean,
        forceWithoutAnimation: Boolean,
        uniqueWith: Int
    ): Boolean {
        return if (!Utilities.isLandscape)
            presentFragmentInternal(
                fragment,
                false,
                removeLast,
                true,
                forceWithoutAnimation,
                uniqueWith
            )
        else
            nextScreenInternal(fragment, removeLast, true, forceWithoutAnimation)
    }

    fun nextScreen(
        fragment: Fragment,
        removeLast: Boolean,
        forceWithoutAnimation: Boolean = false
    ): Boolean {
        if (!Utilities.isLandscape)
            return presentFragment(fragment, false, removeLast, forceWithoutAnimation)
        else if (!inAnimation)
            return nextScreenInternal(fragment, removeLast, false, forceWithoutAnimation)
        else if (frameAnimationFinishRunnable == null)
            frameAnimationFinishRunnable = Runnable {
                frameAnimationFinishRunnable = null
                nextScreenInternal(fragment, removeLast, false, forceWithoutAnimation)
            }
        return false
    }

    private fun nextScreenInternal(
        fragment: Fragment,
        removeLast: Boolean,
        innerGroup: Boolean,
        forceWithoutAnimation: Boolean
    ): Boolean {
        if (!fragment.onFragmentCreate()) {
            return false
        }

        fragment.groupId = currentGroupId
        if (innerGroup)
            fragment.innerGroupId = currentGroupId + 1
        fragment.parentLayout = this
        var screenView = fragment.savedView
        if (screenView != null) {
            val parent = screenView.parent as? ViewGroup
            if (parent != null) {
                fragment.onRemoveFromParent()
                parent.removeView(screenView)
            }
        } else
            screenView = fragment.createView(context)

        newFragment = fragment

        oldFragment = if (fragmentStack.size > 1) {
            if (removeLast)
                fragmentStack[fragmentStack.size - 1]
            else if (fragmentStack[fragmentStack.size - 2].groupId == currentGroupId && containerView.isSplit())
                fragmentStack[fragmentStack.size - 2]
            else
                null
        } else
            null

        fragmentStack.add(fragment)

        if (fragment.actionBar != null && fragment.requiredActionBar.shouldAddToContainer) {
            val parent = fragment.requiredActionBar.parent as? ViewGroup
            parent?.removeView(fragment.actionBar)
            if (removeLast)
                containerView.replaceRight(screenView, fragment.actionBar, forceWithoutAnimation)
            else
                containerView.nextScreen(screenView, fragment.actionBar, forceWithoutAnimation)
        } else if (removeLast)
            containerView.replaceRight(screenView, null, forceWithoutAnimation)
        else
            containerView.nextScreen(screenView, null, forceWithoutAnimation)
        return true
    }

    fun presentAsSheet(screen: Fragment) {
        screen.parentLayout = this
        screen.groupId = -2
        fragmentStack.add(screen)
        BottomSheet(screen).show(parentActivity.supportFragmentManager, "Sheet")
    }

    fun addFragmentToStack(screen: Fragment, newGroup: Boolean = true): Boolean {
        return addFragmentToStack(screen, newGroup, -1)
    }

    fun addFragmentToStack(screen: Fragment, newGroup: Boolean, position: Int): Boolean {
        if (!screen.onFragmentCreate()) {
            return false
        }
        if (newGroup)
            currentGroupId++
        screen.groupId = currentGroupId
        screen.parentLayout = this
        if (position == -1) {
            if (fragmentStack.isNotEmpty()) {
                val previousFragment: Fragment = fragmentStack[fragmentStack.size - 1]
                previousFragment.pause()
                if (previousFragment.actionBar != null && previousFragment.requiredActionBar.shouldAddToContainer) {
                    val parent = previousFragment.requiredActionBar.parent as? ViewGroup
                    parent?.removeView(previousFragment.actionBar)
                }
                if (previousFragment.savedView != null) {
                    val parent = previousFragment.savedView?.parent as? ViewGroup
                    if (parent != null) {
                        previousFragment.onRemoveFromParent()
                        parent.removeView(previousFragment.savedView)
                    }
                }
            }
            fragmentStack.add(screen)
        } else {
            fragmentStack.add(position, screen)
        }
        return true
    }

    fun closeLastFragment(fragment: Fragment, animated: Boolean = true) {
        if (!inAnimation)
            closeLastFragmentInternal(
                animated,
                fragmentStack.indexOf(fragment) != fragmentStack.size - 1
            )
        else if (frameAnimationFinishRunnable == null)
            frameAnimationFinishRunnable = Runnable {
                frameAnimationFinishRunnable = null
                closeLastFragmentInternal(
                    animated,
                    fragmentStack.indexOf(fragment) != fragmentStack.size - 1
                )
            }
    }

    fun closeLastFragment(animated: Boolean = true, openPrevious: Boolean = true) {
        if (!inAnimation)
            closeLastFragmentInternal(animated, openPrevious)
        else if (frameAnimationFinishRunnable == null)
            frameAnimationFinishRunnable = Runnable {
                frameAnimationFinishRunnable = null
                closeLastFragmentInternal(animated, openPrevious)
            }
    }

    // Not completed, always close last fragment in stack !!!
    fun closeFragment(fragmentId: Int, animated: Boolean = true): Boolean {
        val fragmentIndex = fragmentStack.indexOfFirst { it.fragmentId == fragmentId }
        if (fragmentIndex == -1)
            return false
        if (!inAnimation)
            closeLastFragmentInternal(animated, fragmentIndex != fragmentStack.size - 1)
        else if (frameAnimationFinishRunnable == null)
            frameAnimationFinishRunnable = Runnable {
                frameAnimationFinishRunnable = null
                closeLastFragmentInternal(animated, fragmentIndex != fragmentStack.size - 1)
            }
        return true
    }


    private fun closeLastFragmentInternal(animated: Boolean, openPrevious: Boolean) {
        val _oldFragment = fragmentStack[fragmentStack.size - 1]

        if (_oldFragment.groupId == -2) {
            (parentActivity.supportFragmentManager.findFragmentByTag("Sheet") as? BottomSheetDialogFragment)?.dismissAllowingStateLoss()
        } else if (fragmentStack.size != 1 || clearable) {
            val groupRemoved: Boolean

            if (fragmentStack.size > 1 && fragmentStack[fragmentStack.size - 2].groupId == currentGroupId) {
                groupRemoved = false
                newFragment = fragmentStack[fragmentStack.size - 2]
                if (Utilities.isLandscape) {
                    oldFragment = _oldFragment
                    if (fragmentStack.size == 2 && fragmentStack[fragmentStack.size - 2].groupId == currentGroupId ||
                        fragmentStack.size > 2 && fragmentStack[fragmentStack.size - 3].groupId == currentGroupId
                    ) {
                        val preScreen = if (!openPrevious) {
                            if (!containerView.isSplit()) {
                                oldFragment = null
                                newFragment = fragmentStack[fragmentStack.size - 1]
                                fragmentStack[fragmentStack.size - 2]
                            } else {
                                containerView.previousScreen(null, null, !animated)
                                return
                            }
                        } else if (fragmentStack.size > 2)
                            fragmentStack[fragmentStack.size - 3]
                        else {
                            containerView.previousScreen(null, null, !animated)
                            return
                        }
                        var preView = preScreen.savedView
                        newFragment2 = preScreen
                        if (preView != null) {
                            val parent = preView.parent as? ViewGroup
                            parent?.removeView(preView)
                        } else
                            preView = preScreen.createView(context)
                        if (preScreen.actionBar != null && preScreen.requiredActionBar.shouldAddToContainer) {
                            val parent = preScreen.requiredActionBar.parent as? ViewGroup
                            parent?.removeView(preScreen.actionBar)
                        }
                        containerView.previousScreen(preView, preScreen.actionBar, !animated)
                    } else
                        containerView.previousScreen(null, null, !animated)
                    return
                }
            } else
                groupRemoved = true

            newFragment = if (fragmentStack.size > 1)
                fragmentStack[fragmentStack.size - 2]
            else
                null

            var leftView: View? = null
            var leftActionBar: ActionBar? = null

            if (Utilities.isLandscape && fragmentStack.size > 2 && fragmentStack[fragmentStack.size - 3].groupId == newFragment!!.groupId) {
                newFragment2 = fragmentStack[fragmentStack.size - 3]
                leftView = newFragment2!!.savedView
                if (leftView == null)
                    leftView = newFragment2!!.createView(context)
                else
                    (leftView.parent as? ViewGroup)?.removeView(leftView)
                leftActionBar = newFragment2!!.actionBar
                if (leftActionBar != null && leftActionBar.shouldAddToContainer)
                    (leftActionBar.parent as? ViewGroup)?.removeView(leftActionBar)
            }

            val temp = containerView
            containerView = containerViewBack
            containerViewBack = temp

            if (newFragment != null) {
                var rightView: View? = newFragment!!.savedView

                if (rightView == null)
                    rightView = newFragment!!.createView(context)
                else
                    (rightView.parent as? ViewGroup)?.removeView(rightView)

                var rightActionBar = newFragment!!.actionBar
                if (rightActionBar != null && rightActionBar.shouldAddToContainer)
                    (rightActionBar.parent as? ViewGroup)?.removeView(rightActionBar)
                else
                    rightActionBar = null

                if (leftView == null) {
                    containerView.addGroup(
                        rightView,
                        rightActionBar,
                        newFragment!!.backgroundColor
                    )
                    newFragment!!.onPreResume()
                } else {
                    containerView.addGroup(
                        leftView,
                        leftActionBar,
                        newFragment2!!.backgroundColor
                    )
                    containerView.nextScreen(rightView, rightActionBar, true)
                    newFragment2!!.onPreResume()
                }

                containerView.translationX = 0f
                containerView.alpha = 1f
                containerView.visibility = VISIBLE

            }

            _oldFragment.onPrePause()
            if (animated) {
                inAnimation = true

                currentAnimationSet = AnimatorSet()
                currentAnimationSet!!.duration = 200
                val alphaAnimation = ObjectAnimator.ofFloat(containerViewBack, View.ALPHA, 1f, 0f)
                val translationXAnimation = ObjectAnimator.ofFloat(
                    containerViewBack,
                    View.TRANSLATION_X,
                    0f,
                    containerViewBack.measuredWidth * 0.3f
                )

                val _newFragment = newFragment
                val _newFragment2 = newFragment2

                currentAnimationSet!!.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator?) {
                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        containerViewBack.visibility = View.GONE
                        bringChildToFront(containerView)
                        if (leftView == null) {
                            if (_newFragment != null)
                                resumeFragment(_newFragment, true)
                        } else
                            resumeFragment(_newFragment2!!, false)
                        finishFragment(_oldFragment)
                        if (groupRemoved && !fragmentStack.isEmpty())
                            currentGroupId = fragmentStack[fragmentStack.size - 1].groupId
                        inAnimation = false
                        if (frameAnimationFinishRunnable != null)
                            post(frameAnimationFinishRunnable)
                    }

                    override fun onAnimationCancel(animation: Animator?) {

                    }

                    override fun onAnimationRepeat(animation: Animator?) {

                    }

                })

                currentAnimationSet!!.playTogether(translationXAnimation, alphaAnimation)
                currentAnimationSet!!.start()
            } else {
                finishFragment(_oldFragment)
                if (newFragment != null) {
                    if (groupRemoved)
                        currentGroupId = fragmentStack[fragmentStack.size - 1].groupId
                    containerViewBack.visibility = View.GONE
                    bringChildToFront(containerView)
                    if (leftView == null) {
                        resumeFragment(newFragment!!, true)
                        newFragment = null
                    } else {
                        resumeFragment(newFragment2!!, false)
                        newFragment2 = null
                    }
                }
            }
        } else {
            currentGroupId = 0
            val lastFragment = fragmentStack[0]
            lastFragment.onPrePause()
            finishFragment(lastFragment)
            fragmentStack.clear()
        }
    }

    fun popFragmentRange(
        start: Int,
        end: Int
    ) {
        var end = end
        var start = start
        val step = if (start < end)
            1
        else
            -1
        while (start != end) {
            val currentScreen = fragmentStack[start]
            if (currentScreen.groupId == -2) {
                (parentActivity.supportFragmentManager.findFragmentByTag("Sheet") as? BottomSheetDialogFragment)?.dismissAllowingStateLoss()
                fragmentStack.removeAt(start)
            } else {
                currentScreen.onPrePause()
                finishFragment(currentScreen)
            }
            if (step < 0)
                start += step
            else
                end--
        }
    }

    fun popScreensFromStack(count: Int, removeLatest: Boolean) {
        var index = fragmentStack.size - 2
        val lastIndex = index - count
        if (index > 0)
            while (index > lastIndex) {
                removeScreenFromStack(index, false)
                index--
            }
        if (removeLatest)
            closeLastFragment(true)
    }


    fun removeAllFragments() {
        if (!inAnimation)
            while (fragmentStack.size != 0) {
                removeScreenFromStack(fragmentStack.size - 1, true)
            }
        else if (frameAnimationFinishRunnable == null)
            frameAnimationFinishRunnable = Runnable {
                frameAnimationFinishRunnable = null
                while (fragmentStack.size != 0) {
                    removeScreenFromStack(fragmentStack.size - 1, true)
                }
            }

    }

    private fun removeScreenFromStack(index: Int, updateGroupId: Boolean) {
        if (index >= fragmentStack.size) {
            return
        }
        removeScreenFromStackInternal(fragmentStack[index], updateGroupId)
    }

    fun removeScreenFromStack(fragment: Fragment) {
        removeScreenFromStackInternal(fragment, true)
    }

    private fun removeScreenFromStackInternal(fragment: Fragment, updateGroupId: Boolean) {
        if (fragment.groupId != -2)
            fragment.pause()
        fragment.onFragmentDestroy()
        fragment.parentLayout = null
        fragmentStack.remove(fragment)
        if (fragment.groupId != -2 && updateGroupId)
            currentGroupId = if (fragmentStack.size >= 1) {
                fragmentStack[fragmentStack.size - 1].onGetFirstInStack()
                fragmentStack[fragmentStack.size - 1].groupId
            } else
                0
    }

    fun isAddedToStack(fragment: Fragment): Boolean = fragmentStack.contains(fragment)

    fun indexOf(fragment: Fragment) = fragmentStack.indexOf(fragment)

//    override fun onConfigurationChanged(newConfig: Configuration?) {
//        super.onConfigurationChanged(newConfig)
//        if (screensStack.isNotEmpty()) {
//            screensStack.forEach {
//                it.onConfigurationChanged(newConfig)
//            }
//        }
//    }

    fun send(toRight: Boolean, vararg data: Any?) {
        fragmentStack[fragmentStack.size - if (toRight) 1 else 2].onReceive(*data)
    }

    fun send(current: Fragment, step: Int, vararg data: Any?) {
        fragmentStack[fragmentStack.indexOf(current) + step].onReceive(*data)
    }


    fun onResume() {
        if (fragmentStack.isNotEmpty())
            fragmentStack[fragmentStack.size - 1].resume()
    }

    fun onPause() {
        if (fragmentStack.isNotEmpty())
            fragmentStack[fragmentStack.size - 1].pause()
    }

    fun onOrientationChanged() {
        if (fragmentStack.isNotEmpty()) {
            fragmentStack.toList().forEach {
                it.onOrientationChanged()
            }
            if (fragmentStack.size > 1) {
                val preScreen = fragmentStack[fragmentStack.size - 2]
                if (preScreen.groupId == fragmentStack[fragmentStack.size - 1].groupId) {
                    if (Utilities.isLandscape) {
                        newFragment = preScreen
                        fragmentStack[fragmentStack.size - 1].actionBar?.drawableRotation = 1f
                        var screenView = preScreen.savedView
                        if (screenView != null) {
                            val parent = screenView.parent as? ViewGroup
                            if (parent != null) {
                                preScreen.onRemoveFromParent()
                                parent.removeView(screenView)
                            }
                        } else
                            screenView = preScreen.createView(context)

                        if (preScreen.actionBar != null && preScreen.requiredActionBar.shouldAddToContainer) {
                            val parent = preScreen.requiredActionBar.parent as? ViewGroup
                            parent?.removeView(preScreen.actionBar)
                            containerView.openLeft(screenView, preScreen.actionBar)
                        } else
                            containerView.openLeft(screenView, null)
                    } else {
                        oldFragment = preScreen
                        fragmentStack[fragmentStack.size - 1].actionBar?.drawableRotation = 0f
                        containerView.closeLeft()
                    }
                }
            }
        }
    }

    fun startActivityForResult(
        intent: Intent?,
        requestCode: Int
    ) {
        parentActivity.startActivityForResult(intent, requestCode)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        fragmentStack.forEach {
            it.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun requestPermissions(
        permissions: Array<out String>, requestCode: Int
    ) {
        ActivityCompat.requestPermissions(parentActivity, permissions, requestCode)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (grantResults.isNotEmpty())
            fragmentStack.forEach {
                it.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
    }

    fun onBackPressed(): Boolean {
        if (fragmentStack.isEmpty())
            return false
        val lastFragment: Fragment = fragmentStack[fragmentStack.size - 1]
        if (!lastFragment.onBackPressed()) {
            if (fragmentStack.isNotEmpty()) {
                if (fragmentStack.size == 1)
                    return false
                closeLastFragment(true)
                return fragmentStack.isNotEmpty()
            }
        } else
            return true
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