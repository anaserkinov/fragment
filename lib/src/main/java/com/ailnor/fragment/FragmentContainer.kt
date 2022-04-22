/* 
 * Copyright Erkinjanov Anaskhan, 12/02/2022.
 */

package com.ailnor.fragment

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ailnor.core.*

class FragmentContainer(context: Context) : FrameLayout(context) {

    private var frameAnimationFinishRunnable: Runnable? = null
    private var inNextAnimation = false
    private var inPreviousAnimation = false
    private var inReplaceAnimation = false
    private var animationType = FROM_RIGHT
    private var inAnimation = false
    private var isKeyboardVisible = false
    private val rect = Rect()

    companion object {
        const val FROM_RIGHT = 1
        const val FROM_LEFT = 2
        const val FROM_RIGHT_FLOATING = 3
    }

    private class Container(context: Context) : FrameLayout(context) {

        init {
            setBackgroundColor(Theme.white)
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
                if (child !is ActionBar)
                    measureChildWithMargins(
                        child,
                        widthMeasureSpec,
                        0,
                        heightMeasureSpec,
                        actionBarHeight
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
                if (child !is ActionBar) {
                    val layoutParams = child.layoutParams as LayoutParams
                    child.layout(
                        layoutParams.leftMargin,
                        layoutParams.topMargin + actionBarHeight,
                        layoutParams.leftMargin + child.measuredWidth,
                        layoutParams.topMargin + actionBarHeight + child.measuredHeight
                    )
                }
            }
        }

    }

    private inner class GroupContainer(context: Context) : FrameLayout(context) {

        private var leftFrame = Container(context)
        private var rightFrame: Container? = null
        private var frame: Container? = null

        init {
            addView(leftFrame, Params(1f, 0))
        }

        fun addGroup(view: View, actionBar: ActionBar?) {
            (rightFrame?.layoutParams as? Params)?.update(0f, 0)
            (frame?.layoutParams as? Params)?.update(0f, 0)
            (leftFrame.layoutParams as Params).update(1f, 0)
            requestLayout()
            leftFrame.addView(view)
            if (actionBar != null)
                leftFrame.addView(actionBar)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val width = MeasureSpec.getSize(widthMeasureSpec)

            var availableWidth = width
            val leftFrameParams = leftFrame.layoutParams as Params
            if (inAnimation) {
                if (animationType == FROM_RIGHT) {
                    leftFrame.measure(
                        measureSpec_exactly((leftFrameParams.weight * width).toInt()),
                        heightMeasureSpec
                    )
                    availableWidth -= leftFrame.measuredWidth
                    if (rightFrame != null) {
                        val frameParams = frame?.layoutParams as? Params
                        val rightFrameParams = rightFrame!!.layoutParams as Params
                        rightFrame!!.measure(
                            measureSpec_exactly(if (frameParams == null || frameParams.weight == 0f) availableWidth else (width * rightFrameParams.weight).toInt()),
                            heightMeasureSpec
                        )
                        availableWidth -= rightFrame!!.measuredWidth
                        if (frameParams != null) {
                            frame!!.measure(
                                measureSpec_exactly((width * frameParams.weight).toInt()),
                                heightMeasureSpec
                            )
                        }
                    }
                } else if (animationType == FROM_LEFT) {
                    val frameParams = frame!!.layoutParams as Params
                    frame!!.measure(
                        measureSpec_exactly((width * frameParams.weight).toInt()),
                        heightMeasureSpec
                    )
                    availableWidth -= frame!!.measuredWidth
                    val rightFrameParams = rightFrame!!.layoutParams as Params
                    leftFrame.measure(
                        measureSpec_exactly(if (rightFrameParams.weight == 0f) availableWidth else (width * leftFrameParams.weight).toInt()),
                        heightMeasureSpec
                    )
                    availableWidth -= leftFrame.measuredWidth
                    rightFrame!!.measure(
                        measureSpec_exactly((width * rightFrameParams.weight).toInt()),
                        heightMeasureSpec
                    )
                } else {
                    val frameParams = frame!!.layoutParams as Params
                    frame!!.measure(
                        measureSpec_exactly((width * frameParams.weight).toInt()),
                        heightMeasureSpec
                    )
                    leftFrame.measure(
                        measureSpec_exactly((width * leftFrameParams.weight).toInt()),
                        heightMeasureSpec
                    )
                    rightFrame!!.measure(
                        measureSpec_exactly(width - leftFrame.measuredWidth),
                        heightMeasureSpec
                    )
                }
            } else {
                if (leftFrameParams.weight > 0.5f) {
                    leftFrame.measure(
                        widthMeasureSpec,
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
                    availableWidth -= leftFrame.measuredWidth
                    rightFrame?.measure(
                        measureSpec_exactly(availableWidth),
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

            val leftFrameParams = leftFrame.layoutParams as Params
            if (inAnimation) {
                if (animationType == FROM_RIGHT) {
                    l += leftFrameParams.leftOffset
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
                    val frameParams = frame!!.layoutParams as Params
                    l += frameParams.leftOffset
                    frame!!.layout(
                        l, 0, l + frame!!.measuredWidth, frame!!.measuredHeight
                    )
                    l += frame!!.measuredWidth
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
                    val frameParams = frame!!.layoutParams as Params
                    frame!!.layout(
                        frameParams.leftOffset,
                        0,
                        frameParams.leftOffset + frame!!.measuredWidth,
                        frame!!.measuredHeight
                    )
                }
            } else {
                if (leftFrameParams.weight > 0.5f) {
                    leftFrame.layout(
                        0, 0, measuredWidth, measuredHeight
                    )
                    rightFrame?.layout(
                        0, 0, 0, 0
                    )
                }else {
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


        fun nextScreen(view: View, actionBar: ActionBar?, forceWithoutAnimation: Boolean) {
            if (oldScreen == null) {
                if (forceWithoutAnimation) {
                    if (inNextAnimation)
                        frameAnimationFinishRunnable = Runnable {
                            nextWithoutAnimation(view, actionBar)
                            frameAnimationFinishRunnable = null
                        }
                    else
                        nextWithoutAnimation(view, actionBar)
                } else {
                    if (inNextAnimation)
                        frameAnimationFinishRunnable = Runnable {
                            startFirstNextAnimation(view, actionBar)
                            frameAnimationFinishRunnable = null
                        }
                    else
                        startFirstNextAnimation(view, actionBar)
                }

            } else {
                if (forceWithoutAnimation) {
                    if (inNextAnimation) {
                        frameAnimationFinishRunnable = Runnable {
                            nextWithoutAnimation(view, actionBar)
                            frameAnimationFinishRunnable = null
                        }
                    } else
                        nextWithoutAnimation(view, actionBar)
                } else {
                    if (frame == null) {
                        frame = Container(context)
                        frame!!.layoutParams = Params(0f, 0)
                    }
                    if (inNextAnimation) {
                        frameAnimationFinishRunnable = Runnable {
                            startNextAnimation(view, actionBar)
                            frameAnimationFinishRunnable = null
                        }
                    } else
                        startNextAnimation(view, actionBar)
                }
            }
        }

        private fun nextWithoutAnimation(view: View, actionBar: ActionBar?) {
            (leftFrame.layoutParams as Params).update(0.35f, 0)
            if (rightFrame == null) {
                rightFrame = Container(context)
                addView(rightFrame, Params(0.65f, 0))
            } else
                (rightFrame!!.layoutParams as Params).update(0.65f, 0)
            rightFrame!!.addView(view)
            if (actionBar != null)
                rightFrame!!.addView(actionBar)
            newScreen2?.resume()
            newScreen2 = null
            requestLayout()
        }

        private fun startFirstNextAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            inNextAnimation = true
            animationType = FROM_RIGHT
            if (rightFrame == null) {
                rightFrame = Container(context)
                addView(rightFrame, Params(0.65f, 0))
            } else
                (rightFrame!!.layoutParams as Params).update(0.65f, 0)
            rightFrame!!.addView(view)
            if (actionBar != null)
                rightFrame!!.addView(actionBar)
            startAnimation(
                object : Animation() {
                    init {
                        duration = 300
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                inAnimation = false
                                newScreen?.resume()
                                newScreen = null
                                if (frameAnimationFinishRunnable == null)
                                    inNextAnimation = false
                                else
                                    frameAnimationFinishRunnable!!.run()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (inAnimation) {
                            (leftFrame.layoutParams as Params).weight =
                                1f - interpolatedTime * 0.65f
                            requestLayout()
                        }
                    }
                }
            )
        }

        private fun startNextAnimation(view: View, actionBar: ActionBar?) {
            inNextAnimation = true
            inAnimation = true
            animationType = FROM_RIGHT
            (frame!!.layoutParams as Params).update(0.65f, 0)
            frame!!.addView(view)
            addView(frame)
            if (actionBar != null)
                frame!!.addView(actionBar)
            startAnimation(
                object : Animation() {
                    init {
                        duration = 300
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = leftFrame
                                leftFrame = rightFrame!!
                                rightFrame = frame
                                frame = temp
                                inAnimation = false
                                removeViewInLayout(frame!!)
                                presentScreenInternalRemoveOld(
                                    false,
                                    false,
                                    false,
                                    false,
                                    oldScreen
                                )
                                newScreen?.resume()
                                if (frameAnimationFinishRunnable == null)
                                    inNextAnimation = false
                                else
                                    frameAnimationFinishRunnable!!.run()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (inAnimation) {
                            (leftFrame.layoutParams as Params).update(
                                0.35f - (0.15f) * interpolatedTime,
                                -(measuredWidth * 0.20 * interpolatedTime).toInt()
                            )
                            (rightFrame!!.layoutParams as Params).weight =
                                0.65f - (0.30f) * interpolatedTime
                            requestLayout()
                        }
                    }
                }
            )
        }

        fun previousScreen(view: View?, actionBar: ActionBar?) {
            if (view == null) {
                if (inPreviousAnimation)
                    frameAnimationFinishRunnable = Runnable {
                        startLastPreviousAnimation()
                        frameAnimationFinishRunnable = null
                    }
                else
                    startLastPreviousAnimation()
            } else if (inPreviousAnimation) {
                frameAnimationFinishRunnable = Runnable {
                    startPreviousAnimation(view, actionBar)
                    frameAnimationFinishRunnable = null
                }
            } else {
                startPreviousAnimation(view, actionBar)
            }
        }

        private fun startLastPreviousAnimation() {
            inAnimation = true
            inPreviousAnimation = true
            animationType = FROM_RIGHT
            startAnimation(
                object : Animation() {
                    init {
                        duration = 300
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                inAnimation = false
                                presentScreenInternalRemoveOld(false, true, false, true, oldScreen)
                                newScreen?.resume()
                                if (frameAnimationFinishRunnable == null)
                                    inPreviousAnimation = false
                                else
                                    frameAnimationFinishRunnable!!.run()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (inAnimation) {
                            (leftFrame.layoutParams as Params).weight =
                                0.35f + interpolatedTime * 0.65f
                            requestLayout()
                        }
                    }
                }
            )
        }

        private fun startPreviousAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            inPreviousAnimation = true
            animationType = FROM_LEFT
            (frame!!.layoutParams as Params).update(0.20f, -(measuredWidth * 0.20f).toInt())
            frame!!.addView(view)
            addView(frame)
            if (actionBar != null)
                frame!!.addView(actionBar)
            startAnimation(
                object : Animation() {
                    init {
                        duration = 300
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = frame!!
                                frame = temp
                                inAnimation = false
                                removeViewInLayout(frame!!)
                                presentScreenInternalRemoveOld(false, true, false, true, oldScreen)
                                newScreen?.resume()
                                if (frameAnimationFinishRunnable == null)
                                    inPreviousAnimation = false
                                else
                                    frameAnimationFinishRunnable!!.run()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (inAnimation) {
                            (frame!!.layoutParams as Params).update(
                                0.20f + 0.15f * interpolatedTime,
                                -(measuredWidth * 0.20f * (1 - interpolatedTime)).toInt()
                            )
                            (leftFrame.layoutParams as Params).weight =
                                0.35f + 0.30f * interpolatedTime
                            requestLayout()
                        }
                    }
                }
            )
        }

        fun openLeft(view: View, actionBar: ActionBar?) {
            if (inAnimation)
                frameAnimationFinishRunnable = Runnable {
                    startOpenLeftAnimation(view, actionBar)
                    frameAnimationFinishRunnable = null
                }
            else
                startOpenLeftAnimation(view, actionBar)
        }

        private fun startOpenLeftAnimation(view: View, actionBar: ActionBar?) {
            inAnimation = true
            animationType = FROM_LEFT
            if (rightFrame == null) {
                rightFrame = Container(context)
                addView(rightFrame, Params(0f, 0))
            }
            if (frame == null) {
                frame = Container(context)
                frame!!.layoutParams = Params(0.20f, -(measuredWidth * 0.20f).toInt())
            } else
                (frame!!.layoutParams as Params).update(0.20f, -(measuredWidth * 0.20f).toInt())
            frame!!.addView(view)
            addView(frame)
            if (actionBar != null)
                frame!!.addView(actionBar)
            startAnimation(
                object : Animation() {
                    init {
                        duration = 300
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                (frame!!.layoutParams as Params).update(0.35f, 0)
                                (leftFrame.layoutParams as Params).update(0.65f, 0)
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = frame!!
                                frame = temp
                                inAnimation = false
                                removeViewInLayout(frame!!)
                                newScreen?.resume()
                                if (frameAnimationFinishRunnable != null)
                                    frameAnimationFinishRunnable!!.run()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                        if (inAnimation) {
                            (frame!!.layoutParams as Params).update(
                                0.20f + 0.15f * interpolatedTime,
                                -(measuredWidth * 0.20f * (1 - interpolatedTime)).toInt()
                            )
                            (leftFrame.layoutParams as Params).weight =
                                0.35f + 0.30f * interpolatedTime
                            requestLayout()
                        }
                    }
                }
            )
        }

        fun closeLeft() {
            if (inAnimation)
                frameAnimationFinishRunnable = Runnable {
                    startCloseLeftAnimation()
                    frameAnimationFinishRunnable = null
                }
            else
                startCloseLeftAnimation()
        }

        private fun startCloseLeftAnimation() {
            inAnimation = true
            animationType = FROM_RIGHT
            startAnimation(
                object : Animation() {
                    init {
                        duration = 300
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                (leftFrame.layoutParams as Params).update(0f, 0)
                                (rightFrame!!.layoutParams as Params).update(1f, 0)
                                val temp = rightFrame
                                rightFrame = leftFrame
                                leftFrame = temp!!
                                inAnimation = false
                                requestLayout()
                                presentScreenInternalRemoveOld(
                                    false,
                                    false,
                                    false,
                                    false,
                                    oldScreen
                                )
                                if (frameAnimationFinishRunnable != null)
                                    frameAnimationFinishRunnable!!.run()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                        if (inAnimation) {
                            (leftFrame.layoutParams as Params).update(
                                0.35f - 0.15f * interpolatedTime,
                                -(measuredWidth * 0.20f * interpolatedTime).toInt()
                            )
                            (rightFrame!!.layoutParams as Params).weight =
                                0.65f + 0.35f * interpolatedTime
                        }
                    }
                }
            )
        }


        fun replaceScreen(view: View, actionBar: ActionBar?) {
            if (frame == null) {
                frame = Container(context)
                frame!!.layoutParams = Params(0f, measuredWidth)
            }
            if (inReplaceAnimation) {
                frameAnimationFinishRunnable = Runnable {
                    startReplaceAnimation(view, actionBar)
                    frameAnimationFinishRunnable = null
                }
            } else
                startReplaceAnimation(view, actionBar)
        }

        private fun startReplaceAnimation(view: View, actionBar: ActionBar?) {
            inReplaceAnimation = true
            inAnimation = true
            animationType = FROM_RIGHT_FLOATING
            (frame!!.layoutParams as Params).update(0.65f, measuredWidth)
            frame!!.addView(view)
            addView(frame)
            if (actionBar != null)
                frame!!.addView(actionBar)
            startAnimation(
                object : Animation() {
                    init {
                        duration = 300
                        setAnimationListener(object : AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                val temp = rightFrame
                                rightFrame = frame!!
                                frame = temp
                                inAnimation = false
                                removeViewInLayout(frame!!)
                                presentScreenInternalRemoveOld(false, true, false, false, oldScreen)
                                newScreen?.resume()
                                if (frameAnimationFinishRunnable == null)
                                    inReplaceAnimation = false
                                else
                                    frameAnimationFinishRunnable!!.run()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        })
                    }

                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        if (inAnimation) {
                            (frame!!.layoutParams as Params).leftOffset =
                                measuredWidth - (measuredWidth * 0.65f * interpolatedTime).toInt()
                            requestLayout()
                        }
                    }
                }
            )
        }


    }

    private class Params(var weight: Float, var leftOffset: Int) :
        FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT) {
        fun update(weight: Float, leftOffset: Int) {
            this.weight = weight
            this.leftOffset = leftOffset
        }
    }

    private var waitingForKeyboardCloseRunnable: Runnable? = null
    private val delayedOpenAnimationRunnable: Runnable? = null

    private var containerView = GroupContainer(context)
    private var containerViewBack = GroupContainer(context)
    private var backgroundView: View? = null
    private var currentAnimationSet: AnimatorSet? = null
    private var currentGroupId = 0

    private var oldScreen: Fragment? = null
    private var newScreen: Fragment? = null
    private var newScreen2: Fragment? = null

    val parentActivity: AppCompatActivity = context as AppCompatActivity

    private val screensStack = arrayListOf<Fragment>()

    init {
        addView(containerViewBack)
        addView(containerView)
    }

    private fun presentScreenInternalRemoveOld(
        groupRemoved: Boolean,
        removeLast: Boolean,
        resumeLast: Boolean,
        getFirstStackLast: Boolean,
        fragment: Fragment?
    ) {
        if (fragment == null) {
            return
        }
//        fragment.onBecomeFullyHidden()
        fragment.pause()
        if (removeLast) {
            if (groupRemoved)
                currentGroupId--
            fragment.onScreenDestroy()
            fragment.parentLayout = null
            screensStack.remove(fragment)
            if (screensStack.size != 0) {
                val oldScreen = screensStack[screensStack.size - 1]
                if (resumeLast)
                    oldScreen.resume()
                if (getFirstStackLast)
                    oldScreen.onGetFirstInStack()
            }
        } else {
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
            }
            if (fragment.actionBar != null && fragment.requiredActionBar.shouldAddToContainer) {
                val parent = fragment.requiredActionBar.parent as? ViewGroup
                parent?.removeViewInLayout(fragment.actionBar)
            }
        }
        containerViewBack.visibility = GONE
    }

    fun presentScreen(
        screen: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false,
    ): Boolean {
        return presentScreen(
            screen, true, removeLast, forceWithoutAnimation
        )
    }

    private fun presentScreen(
        screen: Fragment,
        newGroup: Boolean,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false
    ): Boolean {
        if (inPreviousAnimation || inNextAnimation || inReplaceAnimation)
            return false
        if (!screen.onScreenCreate()) {
            return false
        }
        currentAnimationSet?.removeAllListeners()
        currentAnimationSet?.end()

        if (parentActivity.currentFocus != null && screen.hideKeyboardOnShow())
            parentActivity.currentFocus.hideKeyboard()

        if (newGroup)
            currentGroupId++
        screen.groupId = currentGroupId
        screen.parentLayout = this

        var screenView = screen.savedView
        if (screenView != null) {
            val parent = screenView.parent as? ViewGroup
            if (parent != null) {
                screen.onRemoveFromParent()
                parent.removeView(screenView)
            }
        } else
            screenView = screen.createView(context)

        if (screen.actionBar != null && screen.requiredActionBar.shouldAddToContainer) {
            val parent = screen.requiredActionBar.parent as? ViewGroup
            parent?.removeView(screen.actionBar)
            containerViewBack.addGroup(screenView, screen.requiredActionBar)
        } else
            containerViewBack.addGroup(screenView, null)

        screensStack.add(screen)

        val temp = containerView
        containerView = containerViewBack
        containerViewBack = temp
        containerView.visibility = View.VISIBLE

        bringChildToFront(containerView)

//        if (forceWithoutAnimation) {
//            presentScreenInternalRemoveOld(true, removeLast, currentScreen)
//        }

//        if (!forceWithoutAnimation) {

        screen.onViewCreated()
        currentAnimationSet = AnimatorSet()
        currentAnimationSet!!.duration = 200
        val alphaAnimation = ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.8f, 1.0f)
        val translationXAnimation = ObjectAnimator.ofFloat(
            containerView,
            View.TRANSLATION_X,
            containerView.measuredWidth * 0.3f,
            0f
        )
//        currentAnimationSet!!.addListener(object :Animator.AnimatorListener{
//            override fun onAnimationStart(animation: Animator?) {
//            }
//
//            override fun onAnimationEnd(animation: Animator?) {
//                screen.resume()
//                if (screensStack.size > 1) {
//                    val oldScreen = screensStack[screensStack.size - 2]
//                    presentScreenInternalRemoveOld(newGroup, removeLast, false, false, oldScreen)
//                    if (screensStack.size > 2 && !removeLast || screensStack.size > 1 && removeLast) {
//                        if (oldScreen.groupId == screensStack[screensStack.size - 3].groupId)
//                            presentScreenInternalRemoveOld(
//                                true,
//                                false,
//                                false,
//                                false,
//                                screensStack[screensStack.size - 3]
//                            )
//                    }
//                }
//            }
//
//            override fun onAnimationCancel(animation: Animator?) {
//                screen.resume()
//                if (screensStack.size > 1) {
//                    val oldScreen = screensStack[screensStack.size - 2]
//                    presentScreenInternalRemoveOld(newGroup, removeLast, false, false, oldScreen)
//                    if (screensStack.size > 2 && !removeLast || screensStack.size > 1 && removeLast) {
//                        if (oldScreen.groupId == screensStack[screensStack.size - 3].groupId)
//                            presentScreenInternalRemoveOld(
//                                true,
//                                false,
//                                false,
//                                false,
//                                screensStack[screensStack.size - 3]
//                            )
//                    }
//                }
//            }
//
//            override fun onAnimationRepeat(animation: Animator?) {
//            }
//        })
        currentAnimationSet!!.playTogether(alphaAnimation, translationXAnimation)
        currentAnimationSet!!.start()
//        } else {
//            if (backgroundView != null) {
//                backgroundView!!.alpha = 1.0f
//                backgroundView!!.visibility = View.VISIBLE
//            }
//            if (currentScreen != null) {
//                currentScreen.onTransitionAnimationStart(false, false)
//                currentScreen.onTransitionAnimationEnd(false, false)
//            }
//            screen.onTransitionAnimationStart(true, false)
//            screen.onTransitionAnimationEnd(true, false)
//            screen.onBecomeFullyVisible()
//        }

        screen.resume()
        if (screensStack.size > 1) {
            val oldScreen = screensStack[screensStack.size - 2]
            presentScreenInternalRemoveOld(newGroup, removeLast, false, false, oldScreen)
            if (screensStack.size > 2 && !removeLast || screensStack.size > 1 && removeLast) {
                if (oldScreen.groupId == screensStack[screensStack.size - 3].groupId)
                    presentScreenInternalRemoveOld(
                        true,
                        false,
                        false,
                        false,
                        screensStack[screensStack.size - 3]
                    )
            }
        }
        return true
    }

    fun nextScreen(screen: Fragment, removeLast: Boolean): Boolean {
        if (!Utilities.isLandscapeTablet)
            return presentScreen(screen, false, false, false)
        if (inPreviousAnimation || inNextAnimation || inReplaceAnimation)
            return false
        if (!screen.onScreenCreate()) {
            return false
        }
        screen.parentLayout = this
        screen.groupId = currentGroupId
        var screenView = screen.savedView
        if (screenView != null) {
            val parent = screenView.parent as? ViewGroup
            if (parent != null) {
                screen.onRemoveFromParent()
                parent.removeView(screenView)
            }
        } else
            screenView = screen.createView(context)

        oldScreen = if (screensStack.size > 1) {
            if (removeLast)
                screensStack[screensStack.size - 1]
            else if (screensStack[screensStack.size - 2].groupId == currentGroupId)
                screensStack[screensStack.size - 2]
            else
                null
        } else
            null
        newScreen = screen

        screensStack.add(screen)

        if (screen.actionBar != null && screen.requiredActionBar.shouldAddToContainer) {
            val parent = screen.requiredActionBar.parent as? ViewGroup
            parent?.removeView(screen.actionBar)
            if (removeLast)
                containerView.replaceScreen(screenView, screen.actionBar)
            else
                containerView.nextScreen(screenView, screen.actionBar, false)
        } else if (removeLast)
            containerView.replaceScreen(screenView, null)
        else
            containerView.nextScreen(screenView, null, false)
        return true
    }

    fun presentAsSheet(screen: Fragment) {
        screen.parentLayout = this
        screen.groupId = -2
        screensStack.add(screen)
        BottomSheet(screen).show(parentActivity.supportFragmentManager, "Sheet")
    }

    fun addScreenToStack(screen: Fragment): Boolean {
        return addScreenToStack(screen, -1)
    }

    fun addScreenToStack(screen: Fragment, position: Int): Boolean {
        if (!screen.onScreenCreate()) {
            return false
        }
        currentGroupId++
        screen.groupId = currentGroupId
        screen.parentLayout = this
        if (position == -1) {
            if (screensStack.isNotEmpty()) {
                val previousFragment: Fragment = screensStack[screensStack.size - 1]
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
            screensStack.add(screen)
        } else {
            screensStack.add(position, screen)
        }
        return true
    }

    fun showLastFragment() {
        if (screensStack.isEmpty()) {
            return
        }
        for (a in 0 until screensStack.size - 1) {
            val previousFragment: Fragment = screensStack.get(a)
            if (previousFragment.actionBar != null && previousFragment.requiredActionBar.shouldAddToContainer) {
                val parent = previousFragment.requiredActionBar.parent as ViewGroup
                parent.removeView(previousFragment.actionBar)
            }
            if (previousFragment.savedView != null) {
                val parent = previousFragment.savedView!!.parent as? ViewGroup
                if (parent != null) {
                    previousFragment.onPause()
                    previousFragment.onRemoveFromParent()
                    parent.removeView(previousFragment.savedView)
                }
            }
        }
        val previousFragment: Fragment = screensStack[screensStack.size - 1]
        previousFragment.parentLayout = this
        var fragmentView: View? = previousFragment.savedView
        if (fragmentView == null) {
            fragmentView = previousFragment.createView(parentActivity)
        } else {
            val parent = fragmentView.parent as? ViewGroup
            if (parent != null) {
                previousFragment.onRemoveFromParent()
                parent.removeView(fragmentView)
            }
        }
        containerView.addView(fragmentView)
        if (previousFragment.actionBar != null && previousFragment.requiredActionBar.shouldAddToContainer) {
            val parent = previousFragment.requiredActionBar.parent as? ViewGroup
            parent?.removeView(previousFragment.actionBar)
            containerView.addView(previousFragment.actionBar)
        }
        previousFragment.onResume()
    }

    fun closeLastScreen(animated: Boolean = true) {
        if (inPreviousAnimation || inNextAnimation || inReplaceAnimation)
            return
        currentAnimationSet?.cancel()

        val currentScreen = screensStack[screensStack.size - 1]

        if (currentScreen.groupId == -2) {
            (parentActivity.supportFragmentManager.findFragmentByTag("Sheet") as? BottomSheetDialogFragment)?.dismissAllowingStateLoss()
        } else if (screensStack.size != 1) {
            val groupRemoved: Boolean
            if (screensStack[screensStack.size - 2].groupId == currentGroupId) {
                groupRemoved = false
                if (Utilities.isLandscapeTablet) {
                    oldScreen = currentScreen
                    if (screensStack.size > 2 && screensStack[screensStack.size - 3].groupId == currentGroupId) {
                        val preScreen = screensStack[screensStack.size - 3]
                        var preView = preScreen.savedView
                        newScreen = preScreen
                        if (preView != null) {
                            val parent = preView.parent as? ViewGroup
                            parent?.removeView(preView)
                        } else
                            preView = preScreen.createView(context)
                        if (preScreen.actionBar != null && preScreen.requiredActionBar.shouldAddToContainer) {
                            val parent = preScreen.requiredActionBar.parent as? ViewGroup
                            parent?.removeView(preScreen.actionBar)
                        }
                        newScreen = preScreen
                        containerView.previousScreen(preView, preScreen.actionBar)
                    } else {
                        newScreen = null
                        containerView.previousScreen(null, null)
                    }
                    return
                }
            } else
                groupRemoved = true

            val screen = screensStack[screensStack.size - 2]

            var rightView: View? = null
            var rightActionBar: ActionBar? = null
            if (Utilities.isLandscapeTablet && screensStack.size > 2 && screensStack[screensStack.size - 3].groupId == screen.groupId) {
                val rightScreen = screensStack[screensStack.size - 3]
                newScreen2 = rightScreen
                rightView = rightScreen.savedView
                if (rightView == null)
                    rightView = rightScreen.createView(context)
                else {
                    (rightView.parent as? ViewGroup)?.removeView(rightView)
                }
                rightActionBar = rightScreen.actionBar
                if (rightActionBar != null && rightActionBar.shouldAddToContainer) {
                    val parent = rightActionBar.parent as? ViewGroup
                    parent?.removeView(rightActionBar)
                }
            }

            var view = screen.savedView
            if (view == null)
                view = screen.createView(context)
            else {
                (view.parent as? ViewGroup)?.removeView(view)
            }
            var actionBar: ActionBar? = screen.actionBar
            if (actionBar != null && actionBar.shouldAddToContainer) {
                val parent = actionBar.parent as? ViewGroup
                parent?.removeView(actionBar)
            } else
                actionBar = null


            if (rightView == null)
                containerViewBack.addGroup(view, actionBar)
            else {
                containerViewBack.addGroup(rightView, rightActionBar)
                containerViewBack.nextScreen(view, actionBar, true)
            }

            val temp = containerViewBack
            containerViewBack = containerView
            containerView = temp
            containerView.visibility = VISIBLE

            bringChildToFront(containerView)
            presentScreenInternalRemoveOld(groupRemoved, true, true, true, currentScreen)
        } else
            presentScreenInternalRemoveOld(true, true, false, false, currentScreen)

    }

    fun closeAllUntil(screen: Fragment, containThis: Boolean = true) {
        var index = screensStack.indexOf(screen)
        if (index == -1)
            return
        if (!containThis)
            index++
        while (screensStack.size != index) {
            val currentScreen = screensStack[index]
            if (currentScreen.groupId == -2)
                (parentActivity.supportFragmentManager.findFragmentByTag("Sheet") as? BottomSheetDialogFragment)?.dismissAllowingStateLoss()
            else {
                currentScreen.pause()
                currentScreen.onScreenDestroy()
                currentScreen.parentLayout = null
                screensStack.remove(currentScreen)
                if (screensStack.size != 0)
                    screensStack[screensStack.size - 1].onGetFirstInStack()
            }
        }

        val currentScreen = screensStack[screensStack.size - 1]

        var rightView: View? = null
        var rightActionBar: ActionBar? = null
        if (Utilities.isLandscapeTablet && screensStack.size > 1 && screensStack[screensStack.size - 2].groupId == currentScreen.groupId) {
            val rightScreen = screensStack[screensStack.size - 2]
            newScreen2 = rightScreen
            rightView = rightScreen.savedView
            if (rightView == null)
                rightView = rightScreen.createView(context)
            else {
                (rightView.parent as? ViewGroup)?.removeView(rightView)
            }
            rightActionBar = rightScreen.actionBar
            if (rightActionBar != null && rightActionBar.shouldAddToContainer) {
                val parent = rightActionBar.parent as? ViewGroup
                parent?.removeView(rightActionBar)
            }
        }

        var view = currentScreen.savedView
        if (view == null)
            view = currentScreen.createView(context)
        else {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        var actionBar: ActionBar? = currentScreen.actionBar
        if (actionBar != null && actionBar.shouldAddToContainer) {
            val parent = actionBar.parent as? ViewGroup
            parent?.removeView(actionBar)
        } else
            actionBar = null

        if (rightView == null)
            containerViewBack.addGroup(view, actionBar)
        else {
            containerViewBack.addGroup(rightView, rightActionBar)
            containerViewBack.nextScreen(view, actionBar, true)
        }


        val temp = containerViewBack
        containerViewBack = containerView
        containerView = temp
        containerView.visibility = VISIBLE

        bringChildToFront(containerView)
    }

    private fun removeScreenFromStackInternal(fragment: Fragment, updateGroupId: Boolean) {
        if (fragment.groupId != -2)
            fragment.pause()
        fragment.onScreenDestroy()
        fragment.parentLayout = null
        screensStack.remove(fragment)
        if (fragment.groupId != -2 && updateGroupId)
            currentGroupId = if (screensStack.size >= 1) {
                screensStack[screensStack.size - 1].onGetFirstInStack()
                screensStack[screensStack.size - 1].groupId
            } else
                0
    }

    fun popScreensFromStack(count: Int, removeLatest: Boolean) {
        var index = screensStack.size - 2
        val lastIndex = index - count
        while (index > lastIndex) {
            removeScreenFromStack(index, false)
            index--
        }
        if (removeLatest)
            screensStack[screensStack.size].finishFragment(true)
    }

    fun removeScreenFromStack(index: Int, updateGroupId: Boolean) {
        if (index >= screensStack.size) {
            return
        }
        removeScreenFromStackInternal(screensStack[index], updateGroupId)
    }

    fun removeScreenFromStack(fragment: Fragment) {
        removeScreenFromStackInternal(fragment, true)
    }

    fun removeAllFragments() {
        while (screensStack.size != 0) {
            removeScreenFromStack(screensStack.size - 1, true)
        }
    }

//    override fun onConfigurationChanged(newConfig: Configuration?) {
//        super.onConfigurationChanged(newConfig)
//        if (screensStack.isNotEmpty()) {
//            screensStack.forEach {
//                it.onConfigurationChanged(newConfig)
//            }
//        }
//    }

    fun send(toRight: Boolean, vararg data: Any?) {
        screensStack[screensStack.size - if (toRight) 1 else 2].onReceive(*data)
    }

    fun send(current: Fragment, step: Int, vararg data: Any?) {
        screensStack[screensStack.indexOf(current) + step].onReceive(*data)
    }

    fun setBackground(view: View?) {
        backgroundView = view
    }

    fun onResume() {
        if (screensStack.isNotEmpty())
            screensStack[screensStack.size - 1].resume()
    }

    fun onPause() {
        if (screensStack.isNotEmpty())
            screensStack[screensStack.size - 1].pause()
    }

    fun getLastFragment(): Fragment? {
        return if (screensStack.isEmpty()) {
            null
        } else screensStack[screensStack.size - 1]
    }

    fun onOrientationChanged(){
        if (screensStack.isNotEmpty()) {
            screensStack.forEach {
                it.onOrientationChanged()
            }
            if (screensStack.size > 1) {
                val preScreen = screensStack[screensStack.size - 2]
                if (preScreen.groupId == screensStack[screensStack.size - 1].groupId) {
                    if (Utilities.isLandscapeTablet) {
                        newScreen = preScreen
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
                        oldScreen = preScreen
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
        screensStack.forEach {
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
        screensStack.forEach {
            it.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    fun onBackPressed(): Boolean {
        if (screensStack.isEmpty())
            return false
        val lastFragment: Fragment = screensStack[screensStack.size - 1]
        if (!lastFragment.onBackPressed()) {
            if (screensStack.isNotEmpty()) {
                closeLastScreen(true)
                return screensStack.isNotEmpty()
            }
        } else
            return true
        return false
    }


}
