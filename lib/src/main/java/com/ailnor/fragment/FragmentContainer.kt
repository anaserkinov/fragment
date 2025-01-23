/* 
 * Copyright Erkinjanov Anaskhan, 12/02/2022.
 */

package com.ailnor.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.math.MathUtils
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import com.ailnor.core.AndroidUtilities
import com.ailnor.core.MATCH_PARENT
import com.ailnor.core.dp
import com.ailnor.core.drawable
import com.ailnor.core.hideKeyboard
import com.ailnor.core.measureSpec_exactly
import com.ailnor.core.measureSpec_unspecified
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FragmentContainer(context: Context) : FrameLayout(context) {

    val frameAnimationFinishRunnable = ArrayDeque<Runnable>()
    var inAnimation = false
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
    var startedTracking = false
    private var maybeStartedTracking = false
    private var beginTrackingSent = false
    private var startedTrackingPointerId = -1
    private var drawShadow = false
    private var touchStartedOnSplitted = false
    private var cancelSlide = false

    companion object {
        private const val FROM_RIGHT = 1
        private const val FROM_LEFT = 2
        private const val FROM_RIGHT_FLOATING = 3

        var ANIMATION_DURATION = 200L
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
            var height = 0
            var actionBarHeight = 0

            var topInset = 0

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
                if (child !is ActionBar && child.visibility != View.GONE) {
//                    child.measure(
//                        widthMeasureSpec,
//                        measureSpec_exactly(
//                            height - if (child.fitsSystemWindows)
//                                -AndroidUtilities.statusBarHeight
//                            else if (actionBarHeight != 0)
//                                actionBarHeight
//                            else
//                                (actionBarHeight + AndroidUtilities.statusBarHeight)
//                        )
//                    )
                    topInset = if (child.fitsSystemWindows || actionBarHeight != 0)
                        0
                    else
                        AndroidUtilities.statusBarHeight

                    measureChildWithMargins(
                        child,
                        widthMeasureSpec,
                        0,
                        heightMeasureSpec,
                        (if (i >= 1 + if (actionBarHeight != 0) 1 else 0)
                            0
                        else
                            actionBarHeight) + topInset
                    )
                    height = child.measuredHeight
                }
            }

            setMeasuredDimension(
                width, height + actionBarHeight + topInset
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
                    var topInset = if (child.fitsSystemWindows || actionBarHeight != 0)
                        0
                    else
                        AndroidUtilities.statusBarHeight
                    if (i >= 1 + if (actionBarHeight != 0) 1 else 0) {

                    } else
                        topInset += actionBarHeight
                    child.layout(
                        layoutParams.leftMargin,
                        layoutParams.topMargin + topInset,
                        layoutParams.leftMargin + child.measuredWidth,
                        layoutParams.topMargin + child.measuredHeight + topInset
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
                if (childCount < 3)
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
        private var rightFrame = Container(context)
        private var frame: Container? = null

        private var animationType = FROM_RIGHT

        init {
            addView(leftFrame)
            addView(rightFrame)
        }

        fun addGroup(view: View, actionBar: ActionBar?, backgroundColor: Int) {
            rightFrame.updateParams(0f, 0f)
            frame?.updateParams(0f, 0f)
            leftFrame.updateParams(1f, 0f)
            leftFrame.addView(view)
            leftFrame.setBackgroundColor(backgroundColor)
            if (actionBar != null)
                leftFrame.addView(actionBar)
        }

        fun addDialog(view: View, frameIndex: Int) {
            if (frameIndex == 0)
                leftFrame.addView(view)
            else
                rightFrame.addView(view)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)

            if (inAnimation) {
                var availableWidth = width
                if (animationType == FROM_RIGHT) {
                    leftFrame.measure(
                        measureSpec_exactly((leftFrame.weight * width).toInt()),
                        heightMeasureSpec
                    )
                    availableWidth -= leftFrame.measuredWidth
                    rightFrame.measure(
                        measureSpec_exactly(if (frame == null || frame!!.weight == 0f) availableWidth else (width * rightFrame.weight).toInt()),
                        heightMeasureSpec
                    )
                    availableWidth -= rightFrame.measuredWidth
                    frame?.measure(
                        measureSpec_exactly((width * frame!!.weight).toInt()),
                        heightMeasureSpec
                    )
                } else if (animationType == FROM_LEFT) {
                    if (frame?.parent != null) {
                        frame!!.measure(
                            measureSpec_exactly((width * frame!!.weight).toInt()),
                            heightMeasureSpec
                        )
                        availableWidth -= frame!!.measuredWidth
                    }
                    leftFrame.measure(
                        measureSpec_exactly(if (rightFrame.weight == 0f) availableWidth else (width * leftFrame.weight).toInt()),
                        heightMeasureSpec
                    )
                    availableWidth -= leftFrame.measuredWidth
                    rightFrame.measure(
                        measureSpec_exactly((width * rightFrame.weight).toInt()),
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
                    rightFrame.measure(
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
                    rightFrame.measure(
                        measureSpec_exactly(0),
                        measureSpec_exactly(0)
                    )
                } else {
                    leftFrame.measure(
                        measureSpec_exactly((width * 0.35f).toInt()),
                        heightMeasureSpec
                    )
                    rightFrame.measure(
                        measureSpec_exactly(width - leftFrame.measuredWidth),
                        heightMeasureSpec
                    )
                }
            }

            setMeasuredDimension(
                width,
                leftFrame.measuredHeight
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
                    rightFrame.layout(
                        l, 0, l + rightFrame.measuredWidth, rightFrame.measuredHeight
                    )
                    l += rightFrame.measuredWidth
                    if (frame != null) {
                        frame!!.layout(
                            l, 0, l + frame!!.measuredWidth, frame!!.measuredHeight
                        )
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
                    rightFrame.layout(
                        l, 0, l + rightFrame.measuredWidth, rightFrame.measuredHeight
                    )
                } else {
                    leftFrame.layout(
                        0, 0, leftFrame.measuredWidth, leftFrame.measuredHeight
                    )
                    rightFrame.layout(
                        leftFrame.measuredWidth, 0, measuredWidth, rightFrame.measuredHeight
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
                        leftOffset,
                        0,
                        leftOffset + leftFrame.measuredWidth,
                        leftFrame.measuredHeight
                    )
                    rightFrame.layout(
                        0, 0, 0, 0
                    )
                } else {
                    leftFrame.layout(
                        0, 0, leftFrame.measuredWidth, leftFrame.measuredHeight
                    )
                    rightFrame.layout(
                        leftFrame.measuredWidth, 0, measuredWidth, rightFrame.measuredHeight
                    )
                }
            }

            val rootView = rootView
            getWindowVisibleDisplayFrame(rect)
            val usableViewHeight: Int =
                rootView.height - (if (rect.top != 0) AndroidUtilities.statusBarHeight else 0) - AndroidUtilities.getViewInset(
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
                fragmentsStack.size < 3 || fragmentsStack[fragmentsStack.size - 2].groupId != fragmentsStack[fragmentsStack.size - 3].groupId
            if (isSlidingLastFragment)
                return
            oldFragment = fragmentsStack[fragmentsStack.size - 2]
            if (frame == null)
                frame = Container(context)
            addView(frame)
            frame!!.updateParams(0.20f, -measuredWidth / 5f)

            val fragment = fragmentsStack[fragmentsStack.size - 3]
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
            onAnimationProgressChanged(per, false)

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
                                duration = (ANIMATION_DURATION * leftFrameWeight).toLong()
                                setAnimationListener(object : AnimationListener {
                                    override fun onAnimationStart(animation: Animation?) {
                                    }

                                    override fun onAnimationEnd(animation: Animation?) {
                                        touching = false
                                        thisInAnimation = false
                                        leftFrame.updateParams(0.35f, 0f)
                                        rightFrame.updateParams(0.65f, 0f)
                                        requestLayout()
                                        inAnimation = false
                                        runStackedRunnable()
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
                        removingFragmentInAnimation++
                        val leftFrameWeightDist = 1f - leftFrameWeight
                        object : Animation() {
                            init {
                                duration = (ANIMATION_DURATION * leftFrameWeight).toLong()
                                setAnimationListener(object : AnimationListener {
                                    override fun onAnimationStart(animation: Animation?) {
                                    }

                                    override fun onAnimationEnd(animation: Animation?) {
                                        touching = false
                                        thisInAnimation = false
                                        leftFrame.updateParams(1f, 0f)
                                        requestLayout()
                                        fragmentsStack[fragmentsStack.size - 1].pause()
                                        finishFragment(fragmentsStack[fragmentsStack.size - 1])
                                        fragmentsStack[fragmentsStack.size - 1].onGetFirstInStack()
                                        inAnimation = false
                                        runStackedRunnable()
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

                val leftFragmentActionBar = fragmentsStack[fragmentsStack.size - 2].actionBar

                startAnimation(
                    if (backAnimation) {
                        val frameWeightDist = frameWeight - 0.20f
                        val frameLeftOffsetDist = -(measuredWidth / 5) - frameLeftOffset
                        val leftFrameWeightDist = leftFrameWeight - 0.35f
                        val leftFragmentActionBarDist =
                            leftFragmentActionBar?.drawableCurrentRotation
                        object : Animation() {
                            init {
                                duration = (ANIMATION_DURATION * leftFrameWeight / 0.65f).toLong()
                                setAnimationListener(object : AnimationListener {
                                    override fun onAnimationStart(animation: Animation?) {
                                    }

                                    override fun onAnimationEnd(animation: Animation?) {
                                        touching = false
                                        thisInAnimation = false
                                        removeViewInLayout(frame!!)
                                        leftFragmentActionBar?.drawableRotation = 0f
                                        leftFrame.updateParams(0.35f, 0f)
                                        rightFrame.updateParams(0.65f, 0f)
                                        requestLayout()
                                        fragmentsStack[fragmentsStack.size - 3].pause()
                                        pauseFragment(fragmentsStack[fragmentsStack.size - 3], true)
                                        inAnimation = false
                                        runStackedRunnable()
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
                        removingFragmentInAnimation++
                        val frameWeightDist = 0.35f - frameWeight
                        val leftFrameWeightDist = 0.65f - leftFrameWeight
                        object : Animation() {
                            init {
                                duration = (ANIMATION_DURATION * leftFrameWeight / 0.65f).toLong()
                                setAnimationListener(object : AnimationListener {
                                    override fun onAnimationStart(animation: Animation?) {
                                    }

                                    override fun onAnimationEnd(animation: Animation?) {
                                        val temp = rightFrame
                                        rightFrame = leftFrame
                                        leftFrame = frame!!
                                        frame = temp
                                        touching = false
                                        thisInAnimation = false
                                        removeViewInLayout(frame!!)
                                        leftFragmentActionBar?.drawableRotation = 1f
                                        leftFrame.updateParams(0.35f, 0f)
                                        rightFrame.updateParams(0.65f, 0f)
                                        requestLayout()
                                        fragmentsStack[fragmentsStack.size - 1].pause()
                                        finishFragment(fragmentsStack[fragmentsStack.size - 1])
                                        fragmentsStack[fragmentsStack.size - 1].onGetFirstInStack()
                                        resumeFragment(
                                            fragmentsStack[fragmentsStack.size - 2],
                                            false
                                        )
                                        inAnimation = false
                                        runStackedRunnable()
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
                rightFrame.updateParams(0.65f, 0f)
                if (oldFragment != null) {
                    oldFragment!!.actionBar?.drawableRotation = 0f
                    oldFragment!!.onPrePause()
                    oldFragment!!.pause()
                    pauseFragment(oldFragment!!, true)
                    oldFragment = null
                    val temp = leftFrame
                    leftFrame = rightFrame
                    rightFrame = temp
                }
                rightFrame.setView(view, newFragment!!.backgroundColor)
                if (actionBar != null)
                    rightFrame.addView(actionBar)
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

            bringChildToFront(rightFrame)
            rightFrame.updateParams(0.65f, 0f)
            rightFrame.setView(view, newFragment!!.backgroundColor)
            if (actionBar != null)
                rightFrame.addView(actionBar)

            newFragment!!.actionBar?.drawableRotation = 1f
            newFragment!!.onPreResume()
            startAnimation(
                object : Animation() {
                    init {
                        duration = ANIMATION_DURATION
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                thisInAnimation = false
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame.updateParams(0.65f, 0f)
                                requestLayout()
                                resumeFragment(newFragment!!, true)
                                newFragment = null
                                inAnimation = false
                                runStackedRunnable()
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


            val currentFragmentActionBar = fragmentsStack[fragmentsStack.size - 2].actionBar
            oldFragment!!.onPrePause()

            newFragment!!.actionBar?.drawableRotation = 1f
            newFragment!!.onPreResume()

            startAnimation(
                object : Animation() {
                    init {
                        duration = ANIMATION_DURATION
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = leftFrame
                                leftFrame = rightFrame
                                rightFrame = frame!!
                                frame = temp
                                thisInAnimation = false
                                removeViewInLayout(frame!!)
                                currentFragmentActionBar?.drawableRotation = 0f
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame.updateParams(0.65f, 0f)
                                requestLayout()
                                oldFragment!!.pause()
                                pauseFragment(oldFragment!!, true)
                                oldFragment = null
                                resumeFragment(newFragment!!, true)
                                newFragment = null
                                inAnimation = false
                                runStackedRunnable()
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
                            rightFrame.weight =
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
                    removingFragmentInAnimation++
                    oldFragment!!.pause()
                    finishFragment(oldFragment!!)
                }

                if (view == null) {
                    leftFrame.updateParams(1f, 0f)
                    newFragment!!.onGetFirstInStack()
                    newFragment = null
                } else if (oldFragment != null) {
                    val temp = rightFrame
                    rightFrame = leftFrame
                    leftFrame = temp
                    rightFrame.setView(view, newFragment!!.backgroundColor)
                    if (actionBar != null)
                        rightFrame.addView(actionBar)

                    newFragment!!.onPreResume()
                    resumeFragment(newFragment!!, false)
                    newFragment = null

                    newFragment2!!.actionBar?.drawableRotation = 1f
                    newFragment2!!.onGetFirstInStack()
                    newFragment2 = null
                } else {
                    val temp = rightFrame
                    rightFrame = leftFrame
                    leftFrame = temp
                    rightFrame.weight = 0.65f
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
            removingFragmentInAnimation++
            var thisInAnimation = true
            animationType = FROM_LEFT

            oldFragment!!.onPrePause()
            startAnimation(
                object : Animation() {
                    init {
                        duration = ANIMATION_DURATION
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                thisInAnimation = false
                                leftFrame.updateParams(1f, 0f)
                                requestLayout()
                                oldFragment!!.pause()
                                finishFragment(oldFragment!!)
                                oldFragment = null
                                newFragment!!.onGetFirstInStack()
                                newFragment = null
                                inAnimation = false
                                runStackedRunnable()
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
                        duration = ANIMATION_DURATION
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {}

                            override fun onAnimationEnd(animation: Animation?) {
                                thisInAnimation = false
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = frame!!
                                frame = temp
                                removeViewInLayout(frame)
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame.updateParams(0.65f, 0f)
                                requestLayout()
                                newFragment!!.actionBar?.drawableRotation = 1f
                                newFragment = null
                                resumeFragment(newFragment2!!, false)
                                newFragment2 = null
                                inAnimation = false
                                runStackedRunnable()
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
            removingFragmentInAnimation++
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
                        duration = ANIMATION_DURATION
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {}

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = frame!!
                                frame = temp
                                thisInAnimation = false
                                removeViewInLayout(frame!!)
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame.updateParams(0.65f, 0f)
                                requestLayout()
                                oldFragment!!.pause()
                                finishFragment(oldFragment!!)
                                oldFragment = null
                                newFragment!!.actionBar?.drawableRotation = 1f
                                newFragment!!.onGetFirstInStack()
                                newFragment = null
                                resumeFragment(newFragment2!!, false)
                                newFragment2 = null
                                inAnimation = false
                                runStackedRunnable()
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

            rightFrame.updateParams(0.20f, -measuredWidth * 0.20f)

            rightFrame.setView(view, newFragment!!.backgroundColor)
            if (actionBar != null)
                rightFrame.addView(actionBar)

            newFragment!!.onPreResume()
            startAnimation(
                object : Animation() {
                    init {
                        duration = ANIMATION_DURATION
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = temp
                                thisInAnimation = false
                                leftFrame.updateParams(0.35f, 0f)
                                rightFrame.updateParams(0.65f, 0f)
                                requestLayout()
                                resumeFragment(newFragment!!, false)
                                newFragment = null
                                inAnimation = false
                                runStackedRunnable()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                        if (thisInAnimation) {
                            rightFrame.updateParams(
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
                        duration = ANIMATION_DURATION
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = temp
                                thisInAnimation = false
                                leftFrame.updateParams(1f, 0f)
                                rightFrame.updateParams(0f, 0f)
                                requestLayout()
                                oldFragment!!.pause()
                                pauseFragment(oldFragment!!, true)
                                oldFragment = null
                                inAnimation = false
                                runStackedRunnable()
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
                            rightFrame.weight =
                                0.65f + 0.35f * interpolatedTime
                        }
                    }
                }
            )
        }
        //endregion

        //region RIGHT
        fun replaceRight(view: View, actionBar: ActionBar?, forceWithoutAnimation: Boolean) {
            if (forceWithoutAnimation) {
                removingFragmentInAnimation++
                if (frame == null)
                    frame = Container(context)
                frame!!.updateParams(0.65f, 0f)
                frame!!.setView(view, newFragment!!.backgroundColor)
                if (actionBar != null)
                    frame!!.addView(actionBar)
                addView(frame)
                newFragment!!.actionBar?.drawableRotation = 1f
                newFragment!!.onPreResume()
                oldFragment!!.onPrePause()

                val temp = rightFrame
                rightFrame = frame!!
                frame = temp
                removeViewInLayout(frame!!)
                requestLayout()

                oldFragment!!.pause()
                finishFragment(oldFragment!!)
                oldFragment = null
                resumeFragment(newFragment!!, true)
                newFragment = null
            } else
                startReplaceAnimation(view, actionBar)
        }

        private fun startReplaceAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            removingFragmentInAnimation++
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
                        duration = ANIMATION_DURATION
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = frame!!
                                frame = temp
                                thisInAnimation = false
                                removeViewInLayout(frame!!)
                                rightFrame.leftOffset = 0f
                                requestLayout()

                                oldFragment!!.pause()
                                finishFragment(oldFragment!!)
                                oldFragment = null
                                resumeFragment(newFragment!!, true)
                                newFragment = null
                                inAnimation = false
                                runStackedRunnable()
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
        fun showAsModal(view: View, actionBar: ActionBar?, forceWithoutAnimation: Boolean) {
            if (forceWithoutAnimation) {
                leftFrame.updateParams(0.65f, 0f)
                rightFrame.updateParams(0.35f, 0f)
            } else
                startModelShowingAnimation(view, actionBar)
        }

        fun startModelShowingAnimation(view: View, actionBar: ActionBar?) {

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
    private var removingFragmentInAnimation = 0
    var removingFragmentInWait = 0
        private set
    val fragmentsCount: Int
        get() = fragmentsStack.size
    val fragmentCountInAnimation: Int
        get() = fragmentsStack.size - removingFragmentInAnimation
    var clearable = false
    private var resumed = false

    private var oldFragment: Fragment? = null
    private var newFragment: Fragment? = null
    private var newFragment2: Fragment? = null

    val parentActivity: AppCompatActivity = context as AppCompatActivity

    private val fragmentsStack = arrayListOf<Fragment>()

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
            if (newTopInset != 0 && AndroidUtilities.statusBarHeight != newTopInset) {
                AndroidUtilities.statusBarHeight = newTopInset
            }
//            if (Build.VERSION.SDK_INT >= 28) {
//                val cutout = insets.displayCutout
//                hasCutout = cutout != null && cutout.boundingRects.size != 0
//            }
            setPadding(0, 0, 0, insets.systemWindowInsetBottom)
            invalidate()
            if (Build.VERSION.SDK_INT >= 30) {
                return@setOnApplyWindowInsetsListener WindowInsets.CONSUMED
            } else {
                return@setOnApplyWindowInsetsListener insets.consumeSystemWindowInsets()
            }
        }
        systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

    }


    private fun prepareForMoving(x: Float) {
        maybeStartedTracking = false
        startedTracking = true
        startedTrackingX = x.toInt()
        beginTrackingSent = false

        if (AndroidUtilities.isLandscape && containerView.isSplit()) {
            touchStartedOnSplitted = true
            containerView.prepareForMove()
        } else if (fragmentsStack.size > 1) {
            touchStartedOnSplitted = false
            containerViewBack.translationX = 0f
            containerViewBack.alpha = 1f
            containerViewBack.visibility = View.VISIBLE

            newFragment = fragmentsStack[fragmentsStack.size - 2]
            val newFragment2: Fragment?
            var leftView: View? = null
            var leftActionBar: ActionBar? = null
            if (AndroidUtilities.isLandscape && fragmentsStack.size > 2 && fragmentsStack[fragmentsStack.size - 3].groupId == newFragment!!.groupId) {
                newFragment2 = fragmentsStack[fragmentsStack.size - 3]
                leftView = newFragment2.savedView
                if (leftView == null)
                    leftView = newFragment2.createView(context)
                else
                    (leftView.parent as? ViewGroup)?.removeView(leftView)
                leftActionBar = newFragment2.actionBar
                if (leftActionBar != null && leftActionBar.shouldAddToContainer)
                    (leftActionBar.parent as? ViewGroup)?.removeView(leftActionBar)
                else
                    leftActionBar = null
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
        inAnimation = false
        if (backAnimation) {
            if (fragmentsStack.size > 1) {
                val index = if (!touchStartedOnSplitted && containerView.isSplit())
                    fragmentsStack.size - 3
                else
                    fragmentsStack.size - 2
                fragmentsStack[index].pause()
                pauseFragment(
                    fragmentsStack[index],
                    !fragmentsStack[index + 1].isPopup
                )
            }
        } else {
            removingFragmentInAnimation++
            if (!touchStartedOnSplitted && containerView.isSplit()) {
                removingFragmentInAnimation++
                fragmentsStack[fragmentsStack.size - 1].pause()
                finishFragment(fragmentsStack[fragmentsStack.size - 1])

                val fragment = fragmentsStack[fragmentsStack.size - 1]
                if (fragmentsStack.size == 1 || fragment.groupId != fragmentsStack[fragmentsStack.size - 2].groupId)
                    currentGroupId--

                fragment.pause()
                finishFragment(fragment)
            } else {
                val fragment = fragmentsStack[fragmentsStack.size - 1]
                if (fragmentsStack.size == 1 || fragment.groupId != fragmentsStack[fragmentsStack.size - 2].groupId)
                    currentGroupId--

                fragment.pause()
                finishFragment(fragment)
            }

            val temp = containerView
            containerView = containerViewBack
            containerViewBack = temp
            bringChildToFront(containerView)

            if (!fragmentsStack.isEmpty())
                fragmentsStack[fragmentsStack.size - 1].onGetFirstInStack()
        }
        if (!fragmentsStack.isEmpty() && (!fragmentsStack[fragmentsStack.size - 1].isPopup))
            containerViewBack.visibility = GONE
        containerView.translationX = 0f
        startedTracking = false
        isSlideFinishing = false
        innerTranslationX = 0f
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return isSlideFinishing || onTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        onTouchEvent(null)
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }


    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (!isSlideFinishing && inAnimation == touching) {
            if (fragmentsStack.size > 1 || (clearable && fragmentsStack.size != 0)) {
                if (ev == null) {
                    startedTrackingX = 0
                    startedTrackingY = 0
                    startedTracking = false
                    beginTrackingSent = false
                    maybeStartedTracking = false
                    cancelSlide = false
                    if (velocityTracker != null) {
                        velocityTracker!!.recycle()
                        velocityTracker = null
                    }
                    return false
                } else {
                    if (ev.action == MotionEvent.ACTION_DOWN && !cancelSlide) {
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
                    } else if (ev.action == MotionEvent.ACTION_MOVE && !cancelSlide && ev.getPointerId(
                            0
                        ) == startedTrackingPointerId
                    ) {
                        if (velocityTracker == null)
                            velocityTracker = VelocityTracker.obtain()
                        val dx = max(0, (ev.x - startedTrackingX).toInt())
                        val dy = abs(ev.y - startedTrackingY)
                        velocityTracker!!.addMovement(ev)

                        if (maybeStartedTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(
                                0.4f,
                                true
                            ) && abs(dx) / 3 > dy
                        ) {
                            val currentFragment = fragmentsStack[fragmentsStack.size - 1]
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
                                fragmentsStack[fragmentsStack.size - 1].onBeginSlide()
                                beginTrackingSent = true
                            }
                            if (AndroidUtilities.isLandscape && touchStartedOnSplitted)
                                containerView.translateX(dx)
                            else {
                                containerView.translationX = dx.toFloat()
                                innerTranslationX = dx.toFloat()
                                onAnimationProgressChanged(
                                    min(dx / measuredWidth.toFloat(), 1f),
                                    false
                                )
                            }
                        }
                    } else if (cancelSlide || ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_POINTER_UP)) {
                        cancelSlide = false
                        if (velocityTracker == null)
                            velocityTracker = VelocityTracker.obtain()
                        velocityTracker!!.computeCurrentVelocity(1000)
                        val currentFragment = fragmentsStack[fragmentsStack.size - 1]
                        if (!startedTracking && currentFragment.isSwipeBackEnabled(ev)) {
                            val velX = velocityTracker!!.xVelocity
                            val velY = velocityTracker!!.yVelocity
                            if (velX >= 3500 && velX > abs(velY) && currentFragment.canBeginSlide() && findScrollingChild(
                                    this,
                                    ev.x,
                                    ev.y
                                ) == null
                            ) {
                                prepareForMoving(ev.x)
                                inAnimation = false
                                if (!beginTrackingSent) {
                                    hideKeyboard()
                                    beginTrackingSent = true
                                }
                            }
                        }
                        if (startedTracking) {
                            val velX = velocityTracker!!.xVelocity
                            val velY = velocityTracker!!.yVelocity

                            if (AndroidUtilities.isLandscape && touchStartedOnSplitted) {
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
                                    if (fragmentsStack.size > 1) {
                                        if (!touchStartedOnSplitted && containerView.isSplit())
                                            fragmentsStack[fragmentsStack.size - 3].onPrePause()
                                        else
                                            fragmentsStack[fragmentsStack.size - 2].onPrePause()
                                    }
                                } else
                                    fragmentsStack[fragmentsStack.size - 1].onPrePause()

                                val duration = max(
                                    (ANIMATION_DURATION * distToMove / containerView.measuredWidth).toLong(),
                                    min(50, ANIMATION_DURATION)
                                )
                                val animatorSet = AnimatorSet()
                                val innerTranslationXAnimation: ObjectAnimator
                                val translationXAnimation: ObjectAnimator
                                if (backAnimation) {
                                    innerTranslationXAnimation = ObjectAnimator.ofFloat(
                                        this,
                                        "innerTranslationX",
                                        innerTranslationX,
                                        0f
                                    ).setDuration(duration)
                                    translationXAnimation = ObjectAnimator.ofFloat(
                                        containerView,
                                        View.TRANSLATION_X,
                                        0f
                                    ).setDuration(duration)
                                    translationXAnimation.addUpdateListener {
                                        onAnimationProgressChanged(
                                            1f - containerView.translationX / containerView.measuredWidth,
                                            true
                                        )
                                    }
                                } else {
                                    innerTranslationXAnimation = ObjectAnimator.ofFloat(
                                        this,
                                        "innerTranslationX",
                                        innerTranslationX,
                                        containerView.measuredWidth.toFloat()
                                    ).setDuration(duration)
                                    translationXAnimation = ObjectAnimator.ofFloat(
                                        containerView,
                                        View.TRANSLATION_X,
                                        containerView.measuredWidth.toFloat()
                                    ).setDuration(duration)
                                    translationXAnimation.addUpdateListener {
                                        onAnimationProgressChanged(
                                            containerView.translationX / containerView.measuredWidth,
                                            false
                                        )
                                    }
                                }
                                animatorSet.playTogether(
                                    translationXAnimation,
                                    innerTranslationXAnimation
                                )
                                animatorSet.addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
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
        drawShadow = fragment.isPopup
        fragment.onBecomeFullyVisible()
        fragment.resume()
        if (inFirst)
            fragment.onGetFirstInStack()
    }

    private fun pauseFragment(fragment: Fragment, hideView: Boolean) {
//        fragment.onBecomeFullyHidden()
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
        clearViews(fragment)
        fragment.onFragmentDestroy()
        fragment.parentLayout = null
        fragmentsStack.remove(fragment)
        removingFragmentInAnimation--
    }

    private fun clearViews(fragment: Fragment) {
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
    }

    fun presentFragmentGroup(
        screen: Fragment,
        parentFragmentId: Int = -1,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1,
        synchronized: Boolean = true
    ) = presentFragment(
        screen,
        parentFragmentId,
        true,
        removeLast,
        forceWithoutAnimation,
        uniqueWith,
        synchronized
    )

    fun presentFragment(
        fragment: Fragment,
        parentFragmentId: Int,
        newGroup: Boolean = false,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1,
        synchronized: Boolean = true
    ) {
        cancelSlide = true
        if (synchronized) {
            stackRunnable {
                presentFragmentInternal(
                    fragment,
                    parentFragmentId,
                    newGroup,
                    removeLast,
                    false,
                    forceWithoutAnimation,
                    uniqueWith
                )
            }
            runStackedRunnable()
        } else
            presentFragmentInternal(
                fragment,
                parentFragmentId,
                newGroup,
                removeLast,
                false,
                forceWithoutAnimation,
                uniqueWith
            )
    }

    fun presentFragmentAsPopUp(
        fragment: Fragment,
        parentFragmentId: Int,
        uniqueWith: Int = -1
    ): Boolean {
        fragment.isPopup = true
        cancelSlide = true
        stackRunnable {
            presentFragmentInternal(
                fragment,
                parentFragmentId,
                true,
                false,
                false,
                true,
                uniqueWith
            )
        }
        runStackedRunnable()
        return false
    }

    private fun presentFragmentInternal(
        fragment: Fragment,
        parentFragmentId: Int,
        newGroup: Boolean,
        removeLast: Boolean,
        innerGroup: Boolean,
        forceWithoutAnimation: Boolean,
        uniqueWith: Int
    ): Boolean {
        fragment.parentFragmentId = parentFragmentId
        if (!fragment.onFragmentCreate() || uniqueWith != -1 && fragmentsStack.any { it.fragmentId == uniqueWith })
            return false

        if (parentActivity.currentFocus != null && fragment.hideKeyboardOnShow())
            parentActivity.currentFocus.hideKeyboard()

        if (newGroup)
            currentGroupId++
        fragment.groupId = currentGroupId
        if (innerGroup)
            fragment.innerGroupId = currentGroupId + 1
        fragment.parentLayout = this

        drawShadow = fragment.isPopup

        var fragmentView = fragment.savedView
        if (fragmentView != null) {
            val parent = fragmentView.parent as? ViewGroup
            if (parent != null) {
                fragment.onRemoveFromParent()
                parent.removeView(fragmentView)
            }
        } else
            fragmentView = fragment.createView(context)

        if (fragment.isDialog) {
            fragmentsStack.add(fragment)
            containerView.addDialog(fragmentView, 0)
            fragment.onPreResume()
            resumeFragment(fragment, true)
        } else {
            if (fragment.actionBar != null && fragment.requiredActionBar.shouldAddToContainer) {
                val parent = fragment.requiredActionBar.parent as? ViewGroup
                parent?.removeView(fragment.actionBar)
                containerViewBack.addGroup(
                    fragmentView,
                    fragment.requiredActionBar,
                    fragment.backgroundColor
                )
            } else
                containerViewBack.addGroup(fragmentView, null, fragment.backgroundColor)


            fragmentsStack.add(fragment)

            val temp = containerView
            containerView = containerViewBack
            containerViewBack = temp
            bringChildToFront(containerView)

            if (forceWithoutAnimation) {
                containerView.translationX = 0f
                containerView.alpha = 1f
                if (!fragment.isPopup)
                    containerViewBack.visibility = GONE
                containerView.visibility = View.VISIBLE
                if (fragmentsStack.size > 1) {
                    val oldFragment = fragmentsStack[fragmentsStack.size - 2]
                    var oldFragment2 = if (fragmentsStack.size > 2) {
                        val f = fragmentsStack[fragmentsStack.size - 3]
                        if (f.groupId == oldFragment.groupId || oldFragment.groupId == -2)
                            f
                        else
                            null
                    } else
                        null
                    if (removeLast) {
                        if (oldFragment.groupId == -2) {
                            if (oldFragment2?.groupId == -2)
                                oldFragment2 = null
                            fragmentsStack.forEach {
                                if (it.groupId == -2) {
                                    (parentActivity.supportFragmentManager.findFragmentByTag(
                                        it.fragmentId.toString()
                                    ) as? BottomSheetDialogFragment)?.let {
                                        removingFragmentInAnimation++
                                        it.dismissAllowingStateLoss()
                                    }
                                }
                            }
                        } else {
                            removingFragmentInAnimation++
                            oldFragment.onPrePause()
                            oldFragment.pause()
                            finishFragment(oldFragment)
                        }
                    } else {
                        oldFragment.onPrePause()
                        oldFragment.pause()
                        pauseFragment(oldFragment, !fragment.isPopup)
                    }
                    if (oldFragment2 != null) {
                        oldFragment2.onPrePause()
                        oldFragment2.pause()
                        pauseFragment(oldFragment2, !fragment.isPopup)
                    }
                }
                fragment.onPreResume()
                resumeFragment(fragment, true)
            } else {
                var oldFragment: Fragment? = null
                var oldFragment2: Fragment? = null

                inAnimation = true

                containerView.translationX = measuredWidth * 0.3f
                containerView.alpha = 0f
                containerView.visibility = VISIBLE

                currentAnimationSet = AnimatorSet()
                currentAnimationSet!!.duration = ANIMATION_DURATION
                val alphaAnimation = ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.8f, 1.0f)
                val translationXAnimation = ObjectAnimator.ofFloat(
                    containerView,
                    View.TRANSLATION_X,
                    containerView.measuredWidth * 0.3f,
                    0f
                )

                alphaAnimation.addUpdateListener {
                    onAnimationProgressChanged(it.animatedFraction, true)
                }

                fragment.onPreResume()
                if (fragmentsStack.size > 1) {
                    oldFragment = fragmentsStack[fragmentsStack.size - 2]
                    oldFragment2 = if (fragmentsStack.size > 2) {
                        val f = fragmentsStack[fragmentsStack.size - 3]
                        if (f.groupId == oldFragment.groupId || oldFragment.groupId == -2)
                            f
                        else
                            null
                    } else
                        null

                    if (removeLast) {
                        if (oldFragment.groupId == -2) {
                            oldFragment = null
                            if (oldFragment2?.groupId == -2)
                                oldFragment2 = null
                            fragmentsStack.forEach {
                                if (it.groupId == -2) {
                                    (parentActivity.supportFragmentManager.findFragmentByTag(
                                        it.fragmentId.toString()
                                    ) as? BottomSheetDialogFragment)?.let {
                                        removingFragmentInAnimation++
                                        it.dismissAllowingStateLoss()
                                    }
                                }
                            }
                        } else {
                            removingFragmentInAnimation++
                        }
                    }
                    oldFragment?.onPrePause()
                    oldFragment2?.onPreResume()
                }

                currentAnimationSet!!.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {

                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (oldFragment != null) {
                            if (removeLast) {
                                if (oldFragment.groupId != -2) {
                                    oldFragment.pause()
                                    finishFragment(oldFragment)
                                }
                            } else {
                                oldFragment.pause()
                                pauseFragment(oldFragment, !fragment.isPopup)
                            }
                        }
                        if (oldFragment2 != null) {
                            oldFragment2.pause()
                            pauseFragment(oldFragment2, !fragment.isPopup)
                        }
                        resumeFragment(fragment, true)
                        if (!fragment.isPopup)
                            containerViewBack.visibility = GONE
                        inAnimation = false
                        runStackedRunnable()
                    }

                    override fun onAnimationCancel(animation: Animator) {

                    }

                    override fun onAnimationRepeat(animation: Animator) {

                    }

                })

                currentAnimationSet!!.playTogether(translationXAnimation, alphaAnimation)
                currentAnimationSet!!.start()
            }
        }

        return true
    }

    fun nextFragmentInnerGroup(
        fragment: Fragment,
        parentFragmentId: Int,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1
    ): Boolean {
        val removeLast = if (fragmentsStack.size > 1) {
            fragmentsStack[fragmentsStack.size - 1].innerGroupId == currentGroupId + 1
        } else
            false
        cancelSlide = true
        stackRunnable {
            nextFragmentInnerGroupInternal(
                fragment,
                parentFragmentId,
                removeLast,
                forceWithoutAnimation,
                uniqueWith
            )
        }
        runStackedRunnable()
        return false
    }

    private fun nextFragmentInnerGroupInternal(
        fragment: Fragment,
        parentFragmentId: Int,
        removeLast: Boolean,
        forceWithoutAnimation: Boolean,
        uniqueWith: Int
    ): Boolean {
        return if (!AndroidUtilities.isLandscape)
            presentFragmentInternal(
                fragment,
                parentFragmentId,
                false,
                removeLast,
                true,
                forceWithoutAnimation,
                uniqueWith
            )
        else
            nextFragmentInternal(
                fragment,
                parentFragmentId,
                removeLast,
                true,
                forceWithoutAnimation
            )
    }

    fun nextFragment(
        fragment: Fragment,
        parentFragmentId: Int,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false
    ): Boolean {
        cancelSlide = true
        stackRunnable {
            if (!AndroidUtilities.isLandscape)
                presentFragment(
                    fragment,
                    parentFragmentId,
                    false,
                    removeLast,
                    forceWithoutAnimation
                )
            else
                nextFragmentInternal(
                    fragment,
                    parentFragmentId,
                    removeLast,
                    false,
                    forceWithoutAnimation
                )
        }
        runStackedRunnable()
        return false
    }

    private fun nextFragmentInternal(
        fragment: Fragment,
        parentFragmentId: Int,
        removeLast: Boolean,
        innerGroup: Boolean,
        forceWithoutAnimation: Boolean
    ): Boolean {
        fragment.parentFragmentId = parentFragmentId
        if (!fragment.onFragmentCreate()) {
            return false
        }
        var removeLast = removeLast

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

        oldFragment = if (fragmentsStack.size > 1) {
            if (removeLast)
                fragmentsStack[fragmentsStack.size - 1]
            else if (fragmentsStack[fragmentsStack.size - 2].groupId == currentGroupId && containerView.isSplit())
                fragmentsStack[fragmentsStack.size - 2]
            else
                null
        } else
            null

        if (oldFragment?.groupId == -2) {
            oldFragment!!.pause()
            oldFragment!!.finishFragment(false, false)
            removeLast = false
            oldFragment = if (fragmentsStack.size > 1) {
                if (removeLast)
                    fragmentsStack[fragmentsStack.size - 1]
                else if (fragmentsStack[fragmentsStack.size - 2].groupId == currentGroupId && containerView.isSplit())
                    fragmentsStack[fragmentsStack.size - 2]
                else
                    null
            } else
                null
        }

        fragmentsStack.add(fragment)

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

    fun presentFragmentAsSheet(
        fragment: Fragment,
        parentFragmentId: Int,
        fullScreen: Boolean = false,
        height: Int = MATCH_PARENT
    ) {
        fragment.parentLayout = this
        fragment.groupId = -2
        fragment.parentFragmentId = parentFragmentId
        fragmentsStack.add(fragment)
        BottomSheet2(fragment, fullScreen, height).show(
            parentActivity.supportFragmentManager,
            fragment.fragmentId.toString()
        )
    }

    fun showAsListSheet(
        fragment: Fragment,
        fullScreen: Boolean = false
    ): Array<FragmentContainer?>? {
        val actionBarLayout = arrayOf<FragmentContainer?>(FragmentContainer(context))
        val bottomSheet: BottomSheet = object : BottomSheet(parentActivity, true) {
            init {
                isFullscreen = fullScreen
                actionBarLayout[0]!!.addFragmentToStack(fragment, true, 0)
                actionBarLayout[0]!!.showLastFragment()
                actionBarLayout[0]!!.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)
                containerView = actionBarLayout[0]
                setApplyBottomPadding(false)
                setOnDismissListener { dialog -> fragment.onFragmentDestroy() }
            }

            override fun canDismissWithSwipe(): Boolean {
                return false
            }

            override fun onBackPressed() {
                if (actionBarLayout[0] == null || actionBarLayout[0]!!.fragmentsCount <= 1) {
                    super.onBackPressed()
                } else {
                    actionBarLayout[0]?.onBackPressed()
                }
            }

            override fun dismiss() {
                super.dismiss()
                actionBarLayout[0] = null
            }
        }
        fragment.setParentDialog(bottomSheet)
        bottomSheet.show()
        return actionBarLayout
    }

    fun showAsSheet(fragment: Fragment, fullScreen: Boolean = false): Array<FragmentContainer?>? {
        val actionBarLayout = arrayOf<FragmentContainer?>(FragmentContainer(context))
        val bottomSheet: BottomSheet = object : BottomSheet(parentActivity, true) {
            init {
                isFullscreen = fullScreen
                actionBarLayout[0]!!.addFragmentToStack(fragment, true, 0)
                actionBarLayout[0]!!.showLastFragment()
                actionBarLayout[0]!!.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)
                containerView = actionBarLayout[0]
                setApplyBottomPadding(false)
                setOnDismissListener { dialog -> fragment.onFragmentDestroy() }
            }

            override fun canDismissWithSwipe(): Boolean {
                return false
            }

            override fun onBackPressed() {
                if (actionBarLayout[0] == null || actionBarLayout[0]!!.fragmentsCount <= 1) {
                    super.onBackPressed()
                } else {
                    actionBarLayout[0]?.onBackPressed()
                }
            }

            override fun dismiss() {
                super.dismiss()
                actionBarLayout[0] = null
            }
        }
        fragment.setParentDialog(bottomSheet)
        bottomSheet.show()
        return actionBarLayout
    }

    fun addFragmentToStack(
        fragment: Fragment,
        parentFragmentId: Int,
        newGroup: Boolean = true
    ): Boolean {
        fragment.parentFragmentId = parentFragmentId
        return addFragmentToStack(fragment, newGroup, -1)
    }

    fun addFragmentToStack(fragment: Fragment, newGroup: Boolean, position: Int): Boolean {
        if (!fragment.onFragmentCreate()) {
            return false
        }
        if (newGroup)
            currentGroupId++
        fragment.groupId = currentGroupId
        fragment.parentLayout = this

        if (position == -1 || position == fragmentsStack.size) {
            if (fragmentsStack.isNotEmpty()) {
                val previousFragment: Fragment = fragmentsStack[fragmentsStack.size - 1]
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

            val screenView = fragment.savedView
            if (screenView != null) {
                val parent = screenView.parent as? ViewGroup
                if (parent != null) {
                    fragment.onRemoveFromParent()
                    parent.removeView(screenView)
                }
            } else
                fragment.createView(context)

        }

        if (position == -1) {
            fragmentsStack.add(fragment)
        } else {
            fragmentsStack.add(position, fragment)
        }

        return true
    }

    fun closeLastFragment(
        fragment: Fragment,
        animated: Boolean = true,
        synchronized: Boolean = true
    ) {
        cancelSlide = true
        if (synchronized) {
            stackRunnable {
                closeLastFragmentInternal(
                    animated,
                    fragmentsStack.indexOf(fragment) != fragmentsStack.size - 1
                )
            }
            runStackedRunnable()
        } else
            closeLastFragmentInternal(
                animated,
                fragmentsStack.indexOf(fragment) != fragmentsStack.size - 1
            )
    }

    fun closeLastFragment(
        animated: Boolean = true,
        openPrevious: Boolean = true,
        synchronized: Boolean = true
    ) {
        cancelSlide = true
        if (synchronized) {
            removingFragmentInWait++
            stackRunnable {
                removingFragmentInWait--
                closeLastFragmentInternal(animated, openPrevious)
            }
            runStackedRunnable()
        } else {
            closeLastFragmentInternal(animated, openPrevious)
        }
    }

    // Not completed, always close last fragment in stack !!!
    fun closeFragment(fragmentId: Int, animated: Boolean = true): Boolean {
        cancelSlide = true
//        val contain = fragmentStack.indexOfFirst { it.fragmentId == fragmentId } != -1
        stackRunnable {
            val fragmentIndex = fragmentsStack.indexOfFirst { it.fragmentId == fragmentId }
            if (fragmentIndex == -1)
                return@stackRunnable
            if (fragmentIndex == fragmentsStack.size - 1)
                closeLastFragmentInternal(animated, false)
            else
                removeScreenFromStack(fragmentsStack[fragmentIndex])
        }
        runStackedRunnable()
        return true
    }


    private fun closeLastFragmentInternal(animated: Boolean, openPrevious: Boolean) {
        if (fragmentsStack.size == 0)
            return
        val _oldFragment = fragmentsStack[fragmentsStack.size - 1]
        _oldFragment.isFinishing = true

        if (_oldFragment.groupId == -2) {
            if (animated)
                removingFragmentInAnimation++
            else
                fragmentsStack.remove(_oldFragment)
            (parentActivity.supportFragmentManager.findFragmentByTag(_oldFragment.fragmentId.toString()) as? BottomSheetDialogFragment)?.dismissAllowingStateLoss()
            val offset = if (animated) 1 else 0
            if (fragmentsStack.size > offset) {
                val previous = fragmentsStack[fragmentsStack.size - 1 - offset]
                if (previous.isPaused) {
                    var view: View? = previous.savedView

                    if (view == null)
                        view = previous.createView(context)
                    else
                        (view.parent as? ViewGroup)?.removeView(view)

                    var rightActionBar = previous.actionBar
                    if (rightActionBar != null && rightActionBar.shouldAddToContainer)
                        (rightActionBar.parent as? ViewGroup)?.removeView(rightActionBar)
                    else
                        rightActionBar = null

                    previous.onPreResume()

                    containerView.addGroup(
                        view,
                        rightActionBar,
                        previous.backgroundColor
                    )

                    containerView.translationX = 0f
                    containerView.alpha = 1f
                    containerView.visibility = VISIBLE
                }
            }
        } else if (fragmentsStack.size != 1 || clearable) {
            val groupRemoved: Boolean

            if (fragmentsStack.size > 1 && fragmentsStack[fragmentsStack.size - 2].groupId == currentGroupId) {
                groupRemoved = false
                newFragment = fragmentsStack[fragmentsStack.size - 2]
                if (AndroidUtilities.isLandscape) {
                    oldFragment = _oldFragment
                    if (fragmentsStack.size == 2 && fragmentsStack[fragmentsStack.size - 2].groupId == currentGroupId ||
                        fragmentsStack.size > 2 && fragmentsStack[fragmentsStack.size - 3].groupId == currentGroupId
                    ) {
                        val preScreen = if (!openPrevious) {
                            if (!containerView.isSplit()) {
                                oldFragment = null
                                newFragment = fragmentsStack[fragmentsStack.size - 1]
                                fragmentsStack[fragmentsStack.size - 2]
                            } else {
                                containerView.previousScreen(null, null, !animated)
                                return
                            }
                        } else if (fragmentsStack.size > 2)
                            fragmentsStack[fragmentsStack.size - 3]
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
                            containerView.previousScreen(preView, preScreen.actionBar, !animated)
                        } else
                            containerView.previousScreen(preView, null, !animated)
                    } else if (_oldFragment.isDialog) {
                        if (oldFragment != null) {
                            oldFragment!!.onPrePause()
                            removingFragmentInAnimation++
                            oldFragment!!.pause()
                            finishFragment(oldFragment!!)
                        }
                    } else
                        containerView.previousScreen(null, null, !animated)
                    return
                }
            } else
                groupRemoved = true

            removingFragmentInAnimation++

            newFragment = if (fragmentsStack.size > 1)
                fragmentsStack[fragmentsStack.size - 2]
            else
                null

            var leftView: View? = null
            var leftActionBar: ActionBar? = null

            if (fragmentsStack.size > 2) {
                if (!_oldFragment.isDialog && (AndroidUtilities.isLandscape && fragmentsStack[fragmentsStack.size - 3].groupId == newFragment!!.groupId || newFragment?.isDialog == true)) {
                    newFragment2 = fragmentsStack[fragmentsStack.size - 3]
                    leftView = newFragment2!!.savedView
                    if (leftView == null)
                        leftView = newFragment2!!.createView(context)
                    else
                        (leftView.parent as? ViewGroup)?.removeView(leftView)
                    leftActionBar = newFragment2!!.actionBar
                    if (leftActionBar != null && leftActionBar.shouldAddToContainer)
                        (leftActionBar.parent as? ViewGroup)?.removeView(leftActionBar)
                    else
                        leftActionBar = null
                }
            }

            if (!_oldFragment.isDialog) {
                val temp = containerView
                containerView = containerViewBack
                containerViewBack = temp
            }

            if (newFragment != null) {
                if (!_oldFragment.isDialog) {

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

                    newFragment!!.onPreResume()

                    if (leftView == null) {
                        containerView.addGroup(
                            rightView,
                            rightActionBar,
                            newFragment!!.backgroundColor
                        )
                    } else {
                        containerView.addGroup(
                            leftView,
                            leftActionBar,
                            newFragment2!!.backgroundColor
                        )
                        if (newFragment!!.isDialog)
                            containerView.addDialog(rightView, 0)
                        else
                            containerView.nextScreen(rightView, rightActionBar, true)
                        newFragment2!!.onPreResume()
                    }

                    containerView.translationX = 0f
                    containerView.alpha = 1f
                    containerView.visibility = VISIBLE
                }
            }

            _oldFragment.onPrePause()
            if (animated) {
                inAnimation = true

                currentAnimationSet = AnimatorSet()
                currentAnimationSet!!.duration = ANIMATION_DURATION
                val alphaAnimation = ObjectAnimator.ofFloat(containerViewBack, View.ALPHA, 1f, 0f)
                val translationXAnimation = ObjectAnimator.ofFloat(
                    containerViewBack,
                    View.TRANSLATION_X,
                    0f,
                    containerViewBack.measuredWidth * 0.3f
                )

                val _newFragment = newFragment
                val _newFragment2 = newFragment2

                alphaAnimation.addUpdateListener {
                    onAnimationProgressChanged(it.animatedFraction, false)
                }
                currentAnimationSet!!.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        containerViewBack.visibility = View.GONE
                        bringChildToFront(containerView)
                        _oldFragment.pause()
                        if (_newFragment != null)
                            resumeFragment(_newFragment, true)
                        if (leftView != null) {
                            resumeFragment(_newFragment2!!, false)
                        }
                        finishFragment(_oldFragment)
                        if (groupRemoved && !fragmentsStack.isEmpty())
                            currentGroupId = fragmentsStack[fragmentsStack.size - 1].groupId
                        inAnimation = false
                        runStackedRunnable()
                    }

                    override fun onAnimationCancel(animation: Animator) {

                    }

                    override fun onAnimationRepeat(animation: Animator) {

                    }

                })

                currentAnimationSet!!.playTogether(translationXAnimation, alphaAnimation)
                currentAnimationSet!!.start()
            } else {
                containerViewBack.visibility = View.GONE
                bringChildToFront(containerView)
                _oldFragment.pause()
                if (newFragment != null) {
                    resumeFragment(newFragment!!, true)
                    newFragment = null
                }
                if (leftView != null) {
                    resumeFragment(newFragment2!!, false)
                    newFragment2 = null
                }
                finishFragment(_oldFragment)
                if (groupRemoved && !fragmentsStack.isEmpty())
                    currentGroupId = fragmentsStack[fragmentsStack.size - 1].groupId
            }
        } else {
            currentGroupId = 0
            val lastFragment = fragmentsStack[0]
            lastFragment.onPrePause()
            removingFragmentInAnimation++
            lastFragment.pause()
            finishFragment(lastFragment)
            fragmentsStack.clear()
        }
    }

    // [start] inclusive
    // [end] exclusive
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
            val currentScreen = fragmentsStack[start]
            removingFragmentInAnimation++
            if (currentScreen.groupId == -2) {
                (parentActivity.supportFragmentManager.findFragmentByTag(currentScreen.fragmentId.toString()) as? BottomSheetDialogFragment)?.dismissAllowingStateLoss()
            } else {
                currentScreen.onPrePause()
                currentScreen.pause()
                finishFragment(currentScreen)
            }
            if (step < 0)
                start += step
            else
                end--
        }
    }

    fun popScreensFromStack(count: Int, removeLatest: Boolean) {
        var index = fragmentsStack.size - 2
        val lastIndex = index - count
        if (index >= 0)
            while (index > lastIndex) {
                removeScreenFromStack(index, false)
                index--
            }
        if (removeLatest)
            closeLastFragment(true)
    }

    fun clearActionStack() {
        frameAnimationFinishRunnable.clear()
    }

    fun removeAllFragments(synchronized: Boolean = true) {
        cancelSlide = true
        if (synchronized) {
            stackRunnable {
                removingFragmentInAnimation = 0
                while (fragmentsStack.size != 0) {
                    removeScreenFromStack(fragmentsStack.size - 1, false)
                }
            }
            runStackedRunnable()
        } else {
            removingFragmentInAnimation = 0
            while (fragmentsStack.size != 0) {
                removeScreenFromStack(fragmentsStack.size - 1, false)
            }
        }
    }

    private fun removeScreenFromStack(index: Int, updateGroupId: Boolean) {
        if (index >= fragmentsStack.size) {
            return
        }
        removeScreenFromStackInternal(fragmentsStack[index], updateGroupId)
    }

    fun removeScreenFromStack(fragment: Fragment) {
        removeScreenFromStackInternal(fragment, true)
    }

    private fun removeScreenFromStackInternal(fragment: Fragment, updateGroupId: Boolean) {
        fragment.onPrePause()
        fragment.pause()
        clearViews(fragment)
        fragment.onFragmentDestroy()
        fragment.parentLayout = null
        if (fragment.groupId == -2) {
            (parentActivity.supportFragmentManager.findFragmentByTag(fragment.fragmentId.toString()) as? BottomSheetDialogFragment)?.dismissAllowingStateLoss()
        }
        if (fragmentsStack.remove(fragment) && removingFragmentInAnimation != 0)
            removingFragmentInAnimation--
        if (updateGroupId)
            currentGroupId = if (fragmentsStack.size >= 1) {
                fragmentsStack[fragmentsStack.size - 1].onGetFirstInStack()
                fragmentsStack[fragmentsStack.size - 1].groupId
            } else
                0
        if (fragment.groupId == -2) {
            if (fragmentsStack.size > 0) {
                val previous = fragmentsStack[fragmentsStack.size - 1]
                if (previous.isPaused &&
                    (previous.viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                            || previous.viewLifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED)
                ) {
                    resumeFragment(previous, true)
                }
            }
        }
    }

    fun isAddedToStack(fragment: Fragment): Boolean = fragmentsStack.contains(fragment)

    fun indexOf(fragment: Fragment) = fragmentsStack.indexOf(fragment)

    fun showLastFragment() {
        if (fragmentsStack.isEmpty()) {
            return
        }
        bringToFront(fragmentsStack.size - 1)
    }

    private fun bringToFront(i: Int) {
        if (fragmentsStack.isEmpty()) {
            return
        }
        for (a in 0 until i) {
            val previousFragment = fragmentsStack[a]
            if (previousFragment.actionBar != null && previousFragment.actionBar!!.shouldAddToContainer)
                (previousFragment.actionBar!!.parent as ViewGroup?)?.removeView(previousFragment.actionBar)
            if (previousFragment.savedView != null && previousFragment.savedView!!.parent != null) {
                previousFragment.onPrePause()
                previousFragment.pause()
                pauseFragment(previousFragment, true)
            }
        }
        var previousFragment = fragmentsStack[i]
        previousFragment.parentLayout = this
        if (previousFragment.groupId == -2) {
            previousFragment.reCreate()
            (parentActivity.supportFragmentManager.findFragmentByTag(
                previousFragment.fragmentId.toString()
            ) as? BottomSheet2)?.dismissAllowingStateLoss()
            if (i != 0)
                previousFragment = fragmentsStack[i - 1]
            else
                return
        }

        var savedView = previousFragment.savedView
        if (savedView == null) {
            savedView = previousFragment.createView(parentActivity)
        } else {
            val parent = savedView.parent as ViewGroup?
            if (parent != null) {
                previousFragment.onRemoveFromParent()
                parent.removeView(savedView)
            }
        }
        if (previousFragment.actionBar != null && previousFragment.actionBar!!.shouldAddToContainer) {
            (previousFragment.actionBar!!.parent as ViewGroup?)?.removeView(previousFragment.actionBar)
            containerView.addGroup(
                savedView,
                previousFragment.actionBar,
                previousFragment.backgroundColor
            )
        } else
            containerView.addGroup(
                savedView,
                null,
                previousFragment.backgroundColor
            )
        previousFragment.onPreResume()
        resumeFragment(previousFragment, true)
    }

    fun rebuildAllFragmentViews(last: Boolean, showLastAfter: Boolean) {
        stackRunnable {
            var size: Int = fragmentsStack.size
            if (!last) {
                size--
            }
            for (a in 0 until size) {
                fragmentsStack[a].clearViews()
                fragmentsStack[a].parentLayout = this
            }
            if (showLastAfter) {
                showLastFragment()
            }
        }
        runStackedRunnable()
    }

    private fun onAnimationProgressChanged(progress: Float, opening: Boolean) {
        if (fragmentsStack.size > 0)
            fragmentsStack[fragmentsStack.size - 1].onTransitionAnimationProgress(opening, progress)
//        if (fragmentStack.size > 1)
//            fragmentStack[fragmentStack.size - 2].onTransitionAnimationProgress(opening, progress)
    }

//    override fun onConfigurationChanged(newConfig: Configuration?) {
//        super.onConfigurationChanged(newConfig)
//        if (screensStack.isNotEmpty()) {
//            screensStack.forEach {
//                it.onConfigurationChanged(newConfig)
//            }
//        }
//    }

    fun sendFirst(vararg data: Any?) {
        if (fragmentsStack.size > 0)
            fragmentsStack[fragmentsStack.size - 1].onReceive(*data)
    }

    fun send(fragmentId: Int, vararg data: Any?) {
        fragmentsStack.find {
            it.fragmentId == fragmentId
        }?.onReceive(*data)
    }

    fun send(toRight: Boolean, vararg data: Any?) {
        if (fragmentsStack.size >= 2)
            fragmentsStack[fragmentsStack.size - if (toRight) 1 else 2].onReceive(*data)
    }

    fun sendTo(shift: Int, vararg data: Any?) {
        if (fragmentsStack.size + shift in 0 until fragmentsStack.size)
            fragmentsStack[fragmentsStack.size + shift].onReceive(*data)
    }

    fun onResume() {
        resumed = true
        if (fragmentsStack.isNotEmpty()) {
            fragmentsStack[fragmentsStack.size - 1].let {
                it.resume()
                if (it.groupId == -2 && fragmentsStack.size > 1) {
                    for (i in fragmentsStack.size - 2..0) {
                        val fragment = fragmentsStack[i]
                        if (fragment.groupId != -2) {
                            fragment.resume()
                            return
                        }
                    }
                }
            }
        }
    }

    fun onPause() {
        resumed = false
        if (fragmentsStack.isNotEmpty()) {
            fragmentsStack[fragmentsStack.size - 1].let {
                it.pause()
                if (it.groupId == -2 && fragmentsStack.size > 1) {
                    for (i in fragmentsStack.size - 2..0) {
                        val fragment = fragmentsStack[i]
                        fragment.onPrePause()
                        if (fragment.groupId != -2) {
                            fragment.pause()
                            return
                        }
                    }
                }
            }
        }
    }

    fun getFragmentStack(): List<Fragment> = fragmentsStack

    fun onOrientationChanged() {
        if (fragmentsStack.isNotEmpty()) {
            var cont = true
            val _fragments = fragmentsStack.toList()
            val size = _fragments.size
            for (i in size - 1 downTo 0) {
                val fragment = _fragments[i]
                if (fragment.savedView != null)
                    fragment.onOrientationChanged()
                if (cont && fragment.groupId == -2) {
                    fragment.finishFragment(false, false)
                } else
                    cont = false
            }
            if (fragmentsStack.size > 1) {
                val preScreen = fragmentsStack[fragmentsStack.size - 2]
                if (preScreen.groupId == fragmentsStack[fragmentsStack.size - 1].groupId) {
                    if (AndroidUtilities.isLandscape) {
                        newFragment = preScreen
                        fragmentsStack[fragmentsStack.size - 1].actionBar?.drawableRotation = 1f
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
                        fragmentsStack[fragmentsStack.size - 1].actionBar?.drawableRotation = 0f
                        containerView.closeLeft()
                    }
                }
            }
        }
    }

    fun startActivityForResult(
        intent: Intent,
        requestCode: Int
    ) {
        parentActivity.startActivityForResult(intent, requestCode)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        fragmentsStack.forEach {
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
            fragmentsStack.forEach {
                it.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
    }

    fun onBackPressed(): Boolean {
        if (fragmentsStack.isEmpty())
            return false
        if (!inAnimation && !startedTracking && frameAnimationFinishRunnable.size == 0) {
            val lastFragment: Fragment = fragmentsStack[fragmentsStack.size - 1]
            if (!lastFragment.onBackPressed()) {
                if (fragmentsStack.isNotEmpty()) {
                    if (fragmentsStack.size == 1)
                        return false
                    closeLastFragment(true)
                    return fragmentsStack.isNotEmpty()
                }
            } else
                return true
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

    private fun stackRunnable(runnable: Runnable) {
        frameAnimationFinishRunnable.addLast(runnable)
    }

    private fun runStackedRunnable() {
        if (!inAnimation && !startedTracking && frameAnimationFinishRunnable.size != 0) {
            removeCallbacks(checkRunnable)
            post(checkRunnable)
        }
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!inAnimation && !startedTracking && frameAnimationFinishRunnable.size != 0) {
                post(frameAnimationFinishRunnable.removeFirst())
                post(this)
            }
        }
    }

    fun removingFragment() {
        removingFragmentInAnimation++
    }


}