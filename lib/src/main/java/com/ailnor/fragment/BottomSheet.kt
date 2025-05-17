/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package com.ailnor.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.View.MeasureSpec
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.Interpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.view.NestedScrollingParent
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import com.ailnor.core.AndroidUtilities
import com.ailnor.core.AndroidUtilities.computePerceivedBrightness
import com.ailnor.core.AndroidUtilities.displaySize
import com.ailnor.core.AndroidUtilities.getPixelsInCM
import com.ailnor.core.AndroidUtilities.getViewInset
import com.ailnor.core.AndroidUtilities.isTablet
import com.ailnor.core.AndroidUtilities.removeFromParent
import com.ailnor.core.AndroidUtilities.runOnUIThread
import com.ailnor.core.AndroidUtilities.setLightNavigationBar
import com.ailnor.core.CubicBezierInterpolator
import com.ailnor.core.LocaleController.isRTL
import com.ailnor.core.Theme
import com.ailnor.core.Theme.AdaptiveRipple.filledRect
import com.ailnor.core.createSelectorDrawable
import com.ailnor.core.dp
import com.ailnor.fragment.bulletin.Bulletin
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.size
import androidx.core.view.isGone
import com.ailnor.core.MATCH_PARENT
import com.ailnor.core.Utilities
import com.ailnor.core.WRAP_CONTENT
import com.ailnor.core.frameLayoutParams

open class BottomSheet @JvmOverloads constructor(
    context: Context,
    needFocus: Boolean,
    protected var resourcesProvider: Theme.ResourcesProvider? = null
) : Dialog(context, R.style.TransparentDialog), Fragment.AttachedSheet {

    var sheetContainer: ViewGroup? = null
        protected set

    @JvmField
    var container: ContainerView?
    var isKeyboardVisible: Boolean = false
        protected set
    private var lastKeyboardHeight = 0
    protected var keyboardHeight: Int = 0
    private var lastInsets: WindowInsets? = null
    var drawNavigationBar: Boolean = false
    var drawDoubleNavigationBar: Boolean = false
    var scrollNavBar: Boolean = false
    var occupyNavigationBar: Boolean = false
    protected var waitingKeyboard: Boolean = false
    var topBulletinContainer: FrameLayout? = null

    protected var useSmoothKeyboard: Boolean = false

    protected var startAnimationRunnable: Runnable? = null
    private var layoutCount = 0

    var isDismissed: Boolean = false
        private set
    var tag: Int = 0
        private set

    private var allowDrawContent = true

    protected var useHardwareLayer: Boolean = true

    private var onClickListener: DialogInterface.OnClickListener? = null

    private var items: Array<CharSequence?>? = null
    private var itemIcons: IntArray? = null
    private var customView: View? = null
    private var title: CharSequence? = null
    private var bigTitle = false
    private var multipleLinesTitle = false
    private var bottomInset = 0
    private var leftInset = 0
    private var rightInset = 0
    protected var fullWidth: Boolean = false
    protected var isFullscreen: Boolean = false
    private var fullHeight = false
    private var cellType = 0
    private var selectedPos: Int? = null
    var backDrawable: ColorDrawable = object : ColorDrawable(-0x1000000) {
        override fun setAlpha(alpha: Int) {
            super.setAlpha(alpha)
            container!!.invalidate()
        }
    }
        protected set

    private var useLightStatusBar: Boolean = true
    protected var useLightNavBar: Boolean = false

    override val isAttachedLightStatusBar: Boolean
        get() = useLightStatusBar

    protected var behindKeyboardColor: Int = 0

    private var canDismissWithSwipe = true
    private var canDismissWithTouchOutside = true

    private var allowCustomAnimation = true
    private var showWithoutAnimation = false
    var sheetShowing: Boolean = false

    private var statusBarHeight = AndroidUtilities.statusBarHeight

    protected var calcMandatoryInsets: Boolean = false
        set(value) {
            field = value
            drawNavigationBar = value
        }

    private val touchSlop: Int
    private var useFastDismiss = false
    protected var openInterpolator: Interpolator = CubicBezierInterpolator.EASE_OUT_QUINT

    var titleView: TextView? = null
        private set

    private var focusable: Boolean

    protected var dimBehind: Boolean = true
    protected var dimBehindAlpha: Int = 51

    protected var allowNestedScroll: Boolean = true
        set(value) {
            field = value
            if (!allowNestedScroll) {
                sheetContainer!!.translationY = 0f
            }
        }

    @JvmField
    protected var shadowDrawable: Drawable
    var backgroundPaddingTop: Int
        protected set
    var backgroundPaddingLeft: Int
        protected set

    private var applyTopPadding = true
    private var applyBottomPadding = true

    val itemViews: ArrayList<BottomSheetCell> = ArrayList<BottomSheetCell>()

    private val dismissRunnable = Runnable { this.dismiss() }

    protected var delegate: BottomSheetDelegateInterface? = null

    protected var currentSheetAnimation: AnimatorSet? = null
    var sheetAnimationType: Int = 0
        protected set
    protected var navigationBarAnimation: ValueAnimator? = null
    protected var navigationBarAlpha: Float = 0f

    protected var nestedScrollChild: View? = null
    private var disableScroll = false
    private var currentPanTranslationY = 0f

    protected var navBarColor: Int = 0

    private var onHideListener: DialogInterface.OnDismissListener? = null
    protected var isPortrait: Boolean = false
    protected var playingImagesLayerNum: Int = 0
    protected var openedLayerNum: Int = 0
    private var skipDismissAnimation = false

    fun setDisableScroll(b: Boolean) {
        disableScroll = b
    }

    private var keyboardContentAnimator: ValueAnimator? = null
    var smoothKeyboardAnimationEnabled: Boolean = false
    var smoothKeyboardByBottom: Boolean = false
    private var openNoDelay = false

    private var hideSystemVerticalInsetsProgress = 0f
    var useBackgroundTopPadding: Boolean = true
    protected var customViewGravity: Int = Gravity.LEFT or Gravity.TOP
    private var transitionFromRight = false

    fun transitionFromRight(transitionFromRight: Boolean) {
        this.transitionFromRight = transitionFromRight
    }

    fun onOpenAnimationEnd() {
    }

    open inner class ContainerView(context: Context) : FrameLayout(context), NestedScrollingParent {
        private var velocityTracker: VelocityTracker? = null
        private var startedTrackingX = 0
        private var startedTrackingY = 0
        private var startedTrackingPointerId = -1
        private var maybeStartTracking = false
        private var startedTracking = false
        private var currentAnimation: AnimatorSet? = null
        private val nestedScrollingParentHelper: NestedScrollingParentHelper
        private val rect = Rect()
        private val backgroundPaint = Paint()
        private var keyboardChanged = false

        override fun onStartNestedScroll(
            child: View,
            target: View,
            nestedScrollAxes: Int
        ): Boolean {
            return !(nestedScrollChild != null && child !== nestedScrollChild) && !this@BottomSheet.isDismissed && allowNestedScroll && nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL && !canDismissWithSwipe()
        }

        override fun onNestedScrollAccepted(child: View, target: View, nestedScrollAxes: Int) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes)
            if (this@BottomSheet.isDismissed || !allowNestedScroll) {
                return
            }
            cancelCurrentAnimation()
        }

        override fun onStopNestedScroll(target: View) {
            nestedScrollingParentHelper.onStopNestedScroll(target)
            if (this@BottomSheet.isDismissed || !allowNestedScroll) {
                return
            }
            val currentTranslation = sheetContainer!!.getTranslationY()
            checkDismiss(0f, 0f)
        }

        override fun onNestedScroll(
            target: View,
            dxConsumed: Int,
            dyConsumed: Int,
            dxUnconsumed: Int,
            dyUnconsumed: Int
        ) {
            if (this@BottomSheet.isDismissed || !allowNestedScroll) {
                return
            }
            cancelCurrentAnimation()
            if (dyUnconsumed != 0) {
                var currentTranslation = sheetContainer!!.translationY
                currentTranslation -= dyUnconsumed.toFloat()
                if (currentTranslation < 0) {
                    currentTranslation = 0f
                }
                sheetContainer!!.translationY = currentTranslation
                container!!.invalidate()
            }
        }

        override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
            if (this@BottomSheet.isDismissed || !allowNestedScroll) {
                return
            }
            cancelCurrentAnimation()
            var currentTranslation = sheetContainer!!.translationY
            if (currentTranslation > 0 && dy > 0) {
                currentTranslation -= dy.toFloat()
                consumed[1] = dy
                if (currentTranslation < 0) {
                    currentTranslation = 0f
                }
                sheetContainer!!.translationY = currentTranslation
                container!!.invalidate()
            }
        }

        override fun onNestedFling(
            target: View,
            velocityX: Float,
            velocityY: Float,
            consumed: Boolean
        ): Boolean {
            return false
        }

        override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
            return false
        }

        override fun getNestedScrollAxes(): Int {
            return nestedScrollingParentHelper.nestedScrollAxes
        }

        private fun checkDismiss(velX: Float, velY: Float) {
            val translationY = sheetContainer!!.translationY
            val backAnimation = translationY < getPixelsInCM(
                0.8f,
                false
            ) && (velY < 3500 || abs(velY.toDouble()) < abs(velX.toDouble())) || velY < 0 && abs(
                velY.toDouble()
            ) >= 3500
            if (!backAnimation) {
                val allowOld = allowCustomAnimation
                allowCustomAnimation = false
                useFastDismiss = true
                dismiss()
                allowCustomAnimation = allowOld
            } else {
                currentAnimation = AnimatorSet()
                val invalidateContainer = ValueAnimator.ofFloat(0f, 1f)
                invalidateContainer.addUpdateListener(AnimatorUpdateListener { a: ValueAnimator? ->
                    if (container != null) {
                        container!!.invalidate()
                    }
                })
                currentAnimation!!.playTogether(
                    ObjectAnimator.ofFloat(this@BottomSheet.sheetContainer, "translationY", 0f),
                    invalidateContainer
                )
                currentAnimation!!.setDuration(
                    (250 * (max(
                        0.0,
                        translationY.toDouble()
                    ) / getPixelsInCM(0.8f, false))).toInt().toLong()
                )
                currentAnimation!!.interpolator = CubicBezierInterpolator.DEFAULT
                currentAnimation!!.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (currentAnimation != null && currentAnimation == animation) {
                            currentAnimation = null
                        }
                    }
                })
                currentAnimation!!.start()
            }
        }

        private fun cancelCurrentAnimation() {
            if (currentAnimation != null) {
                currentAnimation!!.cancel()
                currentAnimation = null
            }
        }

        private var y = 0f
        private var swipeBackX = 0f
        private var allowedSwipeToBack = false

        init {
            nestedScrollingParentHelper = NestedScrollingParentHelper(this)
            setWillNotDraw(false)
        }

        fun processTouchEvent(ev: MotionEvent?, intercept: Boolean): Boolean {
            if (this@BottomSheet.isDismissed) {
                return false
            }
            if (onContainerTouchEvent(ev)) {
                return true
            }
            if (canSwipeToBack(ev) || allowedSwipeToBack) {
                if (ev != null && (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) && (!startedTracking && !maybeStartTracking && ev.getPointerCount() == 1)) {
                    allowedSwipeToBack = true
                    startedTrackingX = ev.getX().toInt()
                    startedTrackingY = ev.getY().toInt()
                    startedTrackingPointerId = ev.getPointerId(0)
                    maybeStartTracking = true
                    cancelCurrentAnimation()
                } else if (ev != null && ev.action == MotionEvent.ACTION_MOVE && ev.getPointerId(
                        0
                    ) == startedTrackingPointerId
                ) {
                    val dx = ev.getX() - startedTrackingX
                    val dy = ev.getY() - startedTrackingY
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain()
                    }
                    velocityTracker!!.addMovement(ev)
                    if (!disableScroll && maybeStartTracking && !startedTracking && (dx > 0 && dx / 3.0f > abs(
                            dy.toDouble()
                        ) && abs(dx.toDouble()) >= touchSlop)
                    ) {
                        startedTrackingX = ev.getX().toInt()
                        maybeStartTracking = false
                        startedTracking = true
                    } else if (startedTracking) {
                        swipeBackX += dx
                        sheetContainer!!.setTranslationX(max(swipeBackX.toDouble(), 0.0).toFloat())
                        startedTrackingX = ev.getX().toInt()
                        container!!.invalidate()
                    }
                } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_POINTER_UP)) {
//                    containerView.setTranslationX(0);
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain()
                    }
                    val velX = velocityTracker!!.xVelocity
                    val velY = velocityTracker!!.yVelocity
                    val backAnimation =
                        swipeBackX < sheetContainer!!.measuredWidth / 3.0f && (velX < 3500 || velX < velY)
                    if (backAnimation) {
                        swipeBackX = max(swipeBackX.toDouble(), 0.0).toFloat()
                        val animator = ValueAnimator.ofFloat(swipeBackX, 0f)
                        animator.addUpdateListener(AnimatorUpdateListener { animation: ValueAnimator? ->
                            swipeBackX = animation!!.getAnimatedValue() as Float
                            sheetContainer!!.translationX = swipeBackX
                            container!!.invalidate()
                        })
                        animator.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                swipeBackX = 0f
                                sheetContainer!!.translationX = 0f
                                container!!.invalidate()
                            }
                        })
                        animator.interpolator = CubicBezierInterpolator.DEFAULT
                        animator.setDuration(220)
                        animator.start()
                    } else {
                        val animator =
                            ValueAnimator.ofFloat(swipeBackX, getMeasuredWidth().toFloat())
                        animator.addUpdateListener(AnimatorUpdateListener { animation: ValueAnimator? ->
                            swipeBackX = animation!!.getAnimatedValue() as Float
                            sheetContainer!!.setTranslationX(swipeBackX)
                            container!!.invalidate()
                        })
                        animator.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                skipDismissAnimation = true
                                sheetContainer!!.translationX = measuredWidth.toFloat()
                                dismiss()
                                container!!.invalidate()
                            }
                        })
                        animator.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
                        animator.setDuration(320)
                        animator.start()

                        val dimAnimator = ValueAnimator.ofFloat(1f, 0f)
                        dimAnimator.addUpdateListener(AnimatorUpdateListener { animation: ValueAnimator? ->
                            val t = animation!!.getAnimatedValue() as Float
                            backDrawable.setAlpha(if (dimBehind) (dimBehindAlpha * t).toInt() else 0)
                        })
                        dimAnimator.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
                        dimAnimator.setDuration(320)
                        dimAnimator.start()
                    }
                    maybeStartTracking = false
                    startedTracking = false
                    startedTrackingPointerId = -1
                    allowedSwipeToBack = false
                }
            } else {
                if (canDismissWithTouchOutside() && ev != null && (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) && (!startedTracking && !maybeStartTracking && ev.pointerCount == 1)) {
                    startedTrackingX = ev.x.toInt()
                    startedTrackingY = ev.y.toInt()
                    if (startedTrackingY < sheetContainer!!.getTop() || startedTrackingX < sheetContainer!!.left || startedTrackingX > sheetContainer!!.right) {
                        onDismissWithTouchOutside()
                        return true
                    }
                    onScrollUpBegin(y)
                    startedTrackingPointerId = ev.getPointerId(0)
                    maybeStartTracking = true
                    cancelCurrentAnimation()
                    if (velocityTracker != null) {
                        velocityTracker!!.clear()
                    }
                } else if (canDismissWithSwipe() && ev != null && ev.action == MotionEvent.ACTION_MOVE && ev.getPointerId(
                        0
                    ) == startedTrackingPointerId
                ) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain()
                    }
                    val dx = abs((ev.getX() - startedTrackingX).toInt().toDouble()).toFloat()
                    val dy = (ev.getY().toInt() - startedTrackingY).toFloat()
                    val canScrollUp = onScrollUp(y + dy)
                    velocityTracker!!.addMovement(ev)
                    if (!disableScroll && maybeStartTracking && !startedTracking && (dy > 0 && dy / 3.0f > abs(
                            dx.toDouble()
                        ) && abs(dy.toDouble()) >= touchSlop)
                    ) {
                        startedTrackingY = ev.getY().toInt()
                        maybeStartTracking = false
                        startedTracking = true
                        requestDisallowInterceptTouchEvent(true)
                    } else if (startedTracking) {
                        y += dy
                        if (!canScrollUp) y = max(y.toDouble(), 0.0).toFloat()
                        sheetContainer!!.setTranslationY(max(y.toDouble(), 0.0).toFloat())
                        startedTrackingY = ev.getY().toInt()
                        container!!.invalidate()
                    }
                } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_POINTER_UP)) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain()
                    }
                    velocityTracker!!.computeCurrentVelocity(1000)
                    onScrollUpEnd(y)
                    if (startedTracking || y > 0) {
                        checkDismiss(
                            velocityTracker!!.getXVelocity(),
                            velocityTracker!!.getYVelocity()
                        )
                    } else {
                        maybeStartTracking = false
                    }
                    startedTracking = false
                    if (velocityTracker != null) {
                        velocityTracker!!.recycle()
                        velocityTracker = null
                    }
                    startedTrackingPointerId = -1
                }
            }
            return (!intercept && maybeStartTracking) || startedTracking || !(canDismissWithSwipe() || canSwipeToBack(
                ev
            ))
        }

        override fun onTouchEvent(ev: MotionEvent?): Boolean {
            return processTouchEvent(ev, false)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var width = MeasureSpec.getSize(widthMeasureSpec)
            var height = MeasureSpec.getSize(heightMeasureSpec)
            var containerHeight = height
            val rootView = getRootView()
            getWindowVisibleDisplayFrame(rect)
            val oldKeyboardHeight = keyboardHeight
            if (rect.bottom != 0 && rect.top != 0) {
                val usableViewHeight =
                    (rootView.getHeight() - (if (rect.top != 0) AndroidUtilities.statusBarHeight * (1f - hideSystemVerticalInsetsProgress) else 0f) - getViewInset(
                        rootView
                    ) * (1f - hideSystemVerticalInsetsProgress)).toInt()
                keyboardHeight =
                    max(0.0, (usableViewHeight - (rect.bottom - rect.top)).toDouble()).toInt()
                if (keyboardHeight < dp(20)) {
                    keyboardHeight = 0
                } else {
                    lastKeyboardHeight = keyboardHeight
                }
                bottomInset -= keyboardHeight
            } else {
                keyboardHeight = 0
            }
            if (oldKeyboardHeight != keyboardHeight) {
                keyboardChanged = true
            }
            this@BottomSheet.isKeyboardVisible = keyboardHeight > dp(20)
            if (lastInsets != null) {
                bottomInset = lastInsets!!.systemWindowInsetBottom
                leftInset = lastInsets!!.systemWindowInsetLeft
                rightInset = lastInsets!!.systemWindowInsetRight
                if (Build.VERSION.SDK_INT >= 29) {
                    bottomInset += this@BottomSheet.additionalMandatoryOffsets
                }
                if (this@BottomSheet.isKeyboardVisible && rect.bottom != 0 && rect.top != 0) {
                    bottomInset -= keyboardHeight
                }
                if (!drawNavigationBar && !occupyNavigationBar) {
                    containerHeight -= getBottomInset()
                }
            }
            setMeasuredDimension(width, containerHeight)
            if (lastInsets != null && !occupyNavigationBar) {
                var inset =
                    (lastInsets!!.systemWindowInsetBottom * (1f - hideSystemVerticalInsetsProgress)).toInt()
                if (Build.VERSION.SDK_INT >= 29) {
                    inset += this@BottomSheet.additionalMandatoryOffsets
                }
                height -= inset
            }
            if (lastInsets != null) {
                width -= getRightInset() + getLeftInset()
            }
            isPortrait = width < height

            if (this@BottomSheet.sheetContainer != null) {
                if (!fullWidth) {
                    val widthSpec: Int
                    if (isTablet()) {
                        widthSpec = MeasureSpec.makeMeasureSpec(
                            (min(
                                displaySize.x.toDouble(),
                                displaySize.y.toDouble()
                            ) * 0.8f).toInt() + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY
                        )
                    } else {
                        widthSpec = MeasureSpec.makeMeasureSpec(
                            (getBottomSheetWidth(
                                isPortrait,
                                width,
                                height
                            )) + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY
                        )
                    }
                    sheetContainer!!.measure(
                        widthSpec,
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
                    )
                } else {
                    sheetContainer!!.measure(
                        MeasureSpec.makeMeasureSpec(
                            width + backgroundPaddingLeft * 2,
                            MeasureSpec.EXACTLY
                        ), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
                    )
                }
            }
            val childCount = size
            for (i in 0..<childCount) {
                val child = getChildAt(i)
                if (child.isGone || child === this@BottomSheet.sheetContainer) {
                    continue
                }
                if (!onCustomMeasure(child, width, height)) {
                    measureChildWithMargins(
                        child,
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        0,
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
                        0
                    )
                }
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            var left = left
            var right = right
            layoutCount--
            if (this@BottomSheet.sheetContainer != null) {
                var t = (bottom - top) - sheetContainer!!.measuredHeight
                if (lastInsets != null) {
                    left += getLeftInset()
                    right -= getRightInset()
                    if (useSmoothKeyboard) {
                        t = 0
                    } else if (!occupyNavigationBar) {
                        t =
                            (t - (lastInsets!!.systemWindowInsetBottom * (1f - hideSystemVerticalInsetsProgress) - (if (drawNavigationBar) 0 else getBottomInset()))).toInt()
                        if (Build.VERSION.SDK_INT >= 29) {
                            t -= this@BottomSheet.additionalMandatoryOffsets
                        }
                    }
                }
                var l = ((right - left) - sheetContainer!!.measuredWidth) / 2
                if (lastInsets != null) {
                    l += getLeftInset()
                }
                if (smoothKeyboardAnimationEnabled && startAnimationRunnable == null && keyboardChanged && !this@BottomSheet.isDismissed && (if (smoothKeyboardByBottom) sheetContainer!!.getBottom() != t + sheetContainer!!.getMeasuredHeight() else sheetContainer!!.getTop() != t) || smoothContainerViewLayoutUntil > 0 && System.currentTimeMillis() < smoothContainerViewLayoutUntil) {
                    sheetContainer!!.translationY = (if (smoothKeyboardByBottom) sheetContainer!!.getBottom() - (t + sheetContainer!!.getMeasuredHeight()) else sheetContainer!!.getTop() - t).toFloat()
                    onSmoothContainerViewLayout(sheetContainer!!.getTranslationY())
                    if (keyboardContentAnimator != null) {
                        keyboardContentAnimator!!.cancel()
                    }
                    keyboardContentAnimator =
                        ValueAnimator.ofFloat(sheetContainer!!.getTranslationY(), 0f)
                    keyboardContentAnimator!!.addUpdateListener(AnimatorUpdateListener { valueAnimator: ValueAnimator? ->
                        sheetContainer!!.translationY = (valueAnimator!!.getAnimatedValue() as kotlin.Float?)!!
                        onSmoothContainerViewLayout(sheetContainer!!.getTranslationY())
                        invalidate()
                    })
                    keyboardContentAnimator!!.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            sheetContainer!!.translationY = 0f
                            onSmoothContainerViewLayout(sheetContainer!!.getTranslationY())
                            invalidate()
                        }
                    })
                    keyboardContentAnimator!!.setDuration(AdjustPanLayoutHelper.keyboardDuration)
                        .setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator)
                    keyboardContentAnimator!!.start()
                    smoothContainerViewLayoutUntil = -1
                }
                sheetContainer!!.layout(
                    l,
                    t,
                    l + sheetContainer!!.getMeasuredWidth(),
                    t + sheetContainer!!.getMeasuredHeight()
                )
            }

            val count = getChildCount()
            for (i in 0..<count) {
                val child = getChildAt(i)
                if (child.getVisibility() == GONE || child === this@BottomSheet.sheetContainer) {
                    continue
                }
                if (!onCustomLayout(
                        child,
                        left,
                        top,
                        right,
                        bottom - (if (drawNavigationBar) getBottomInset() else 0)
                    )
                ) {
                    val lp = child.getLayoutParams() as LayoutParams

                    val width = child.getMeasuredWidth()
                    val height = child.getMeasuredHeight()

                    var childLeft: Int
                    val childTop: Int

                    var gravity = lp.gravity
                    if (gravity == -1) {
                        gravity = Gravity.TOP or Gravity.LEFT
                    }

                    val absoluteGravity = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
                    val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK

                    when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                        Gravity.CENTER_HORIZONTAL -> childLeft =
                            (right - left - width) / 2 + lp.leftMargin - lp.rightMargin

                        Gravity.RIGHT -> childLeft = right - width - lp.rightMargin
                        Gravity.LEFT -> childLeft = lp.leftMargin
                        else -> childLeft = lp.leftMargin
                    }

                    when (verticalGravity) {
                        Gravity.CENTER_VERTICAL -> childTop =
                            (bottom - top - height) / 2 + lp.topMargin - lp.bottomMargin

                        Gravity.BOTTOM -> childTop = (bottom - top) - height - lp.bottomMargin
                        else -> childTop = lp.topMargin
                    }
                    if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                        childLeft += getLeftInset()
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height)
                }
            }
            if (layoutCount == 0 && startAnimationRunnable != null && !waitingKeyboard) {
                AndroidUtilities.cancelRunOnUIThread(startAnimationRunnable!!)
                startAnimationRunnable!!.run()
                startAnimationRunnable = null
            }
            if (waitingKeyboard && this@BottomSheet.isKeyboardVisible) {
                if (startAnimationRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(startAnimationRunnable!!)
                    startAnimationRunnable!!.run()
                }
                waitingKeyboard = false
            }
            keyboardChanged = false
        }

        override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
            if (canDismissWithSwipe() || canSwipeToBack(event)) {
                return processTouchEvent(event, true)
            }
            return super.onInterceptTouchEvent(event)
        }

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            if (maybeStartTracking && !startedTracking) {
                onTouchEvent(null)
            }
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }

        override fun hasOverlappingRendering(): Boolean {
            return false
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                backgroundPaint.setColor(navBarColor)
            } else {
                backgroundPaint.setColor(-0x1000000)
            }
            if (drawDoubleNavigationBar && !shouldOverlayCameraViewOverNavBar())
                drawNavigationBar(canvas, 1f)
            if (backgroundPaint.getAlpha() < 255 && drawNavigationBar) {
                var translation = 0f
                if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && this@BottomSheet.additionalMandatoryOffsets > 0) {
                    val dist = sheetContainer!!.measuredHeight - sheetContainer!!.translationY
                    translation = max(0.0, (getBottomInset() - dist).toDouble()).toFloat()
                }
                val navBarHeight = if (drawNavigationBar) getBottomInset() else 0
                canvas.save()
                canvas.clipRect(
                    (sheetContainer!!.left + backgroundPaddingLeft).toFloat(),
                    measuredHeight - navBarHeight + translation - currentPanTranslationY,
                    (sheetContainer!!.right - backgroundPaddingLeft).toFloat(),
                    measuredHeight + translation,
                    Region.Op.DIFFERENCE
                )
                super.dispatchDraw(canvas)
                canvas.restore()
            } else {
                super.dispatchDraw(canvas)
            }
            if (!shouldOverlayCameraViewOverNavBar()) {
                drawNavigationBar(
                    canvas,
                    (if (drawDoubleNavigationBar) 0.7f * navigationBarAlpha else 1f)
                )
            }
            if (drawNavigationBar && rightInset != 0 && rightInset > leftInset && fullWidth && displaySize.x > displaySize.y) {
                canvas.drawRect(
                    (sheetContainer!!.right - backgroundPaddingLeft).toFloat(),
                    sheetContainer!!.translationY,
                    (sheetContainer!!.right + rightInset).toFloat(),
                    measuredHeight.toFloat(),
                    backgroundPaint
                )
            }

            if (drawNavigationBar && leftInset != 0 && leftInset > rightInset && fullWidth && displaySize.x > displaySize.y) {
                canvas.drawRect(
                    0f,
                    sheetContainer!!.translationY,
                    (sheetContainer!!.left + backgroundPaddingLeft).toFloat(),
                    measuredHeight.toFloat(),
                    backgroundPaint
                )
            }

            if (sheetContainer!!.translationY < 0) {
                backgroundPaint.setColor(behindKeyboardColor)
                canvas.drawRect(
                    (sheetContainer!!.left + backgroundPaddingLeft).toFloat(),
                    sheetContainer!!.y + sheetContainer!!.measuredHeight,
                    (sheetContainer!!.right - backgroundPaddingLeft).toFloat(),
                    measuredHeight.toFloat(),
                    backgroundPaint
                )
            }
        }

        override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
//            if (child instanceof CameraView) {
//                if (shouldOverlayCameraViewOverNavBar()) {
//                    drawNavigationBar(canvas, 1f);
//                }
//                return super.drawChild(canvas, child, drawingTime);
//            }
            return super.drawChild(canvas, child, drawingTime)
        }

        override fun onDraw(canvas: Canvas) {
            var restore = false
            if (backgroundPaint.getAlpha() < 255 && drawNavigationBar) {
                var translation = 0f
                if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && this@BottomSheet.additionalMandatoryOffsets > 0) {
                    val dist =
                        sheetContainer!!.measuredHeight - sheetContainer!!.translationY
                    translation = max(0.0, (getBottomInset() - dist).toDouble()).toFloat()
                }
                val navBarHeight = if (drawNavigationBar) getBottomInset() else 0
                canvas.save()
                canvas.clipRect(
                    (sheetContainer!!.getLeft() + backgroundPaddingLeft).toFloat(),
                    getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY,
                    (sheetContainer!!.getRight() - backgroundPaddingLeft).toFloat(),
                    getMeasuredHeight() + translation,
                    Region.Op.DIFFERENCE
                )
                restore = true
            }
            super.onDraw(canvas)
            if (drawNavigationBar && lastInsets != null && keyboardHeight != 0) {
                backgroundPaint.setColor(behindKeyboardColor)
                canvas.drawRect(
                    (sheetContainer!!.getLeft() + backgroundPaddingLeft).toFloat(),
                    (getMeasuredHeight() - keyboardHeight - (if (drawNavigationBar) getBottomInset() else 0)).toFloat(),
                    (sheetContainer!!.getRight() - backgroundPaddingLeft).toFloat(),
                    (getMeasuredHeight() - (if (drawNavigationBar) getBottomInset() else 0)).toFloat(),
                    backgroundPaint
                )
            }
            onContainerDraw(canvas)
            if (restore) {
                canvas.restore()
            }
        }

        fun drawNavigationBar(canvas: Canvas, alpha: Float) {
            var alpha = alpha
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                backgroundPaint.setColor(navBarColor)
            } else {
                backgroundPaint.setColor(-0x1000000)
            }
            if (transitionFromRight && sheetContainer!!.visibility != VISIBLE) {
                return
            }
            if ((drawNavigationBar && bottomInset != 0) || currentPanTranslationY != 0f) {
                var translation = 0f
                val navBarHeight = if (drawNavigationBar) getBottomInset() else 0
                if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && this@BottomSheet.additionalMandatoryOffsets > 0) {
                    if (drawDoubleNavigationBar) {
                        translation = max(
                            0.0,
                            min(
                                (navBarHeight - currentPanTranslationY).toDouble(),
                                sheetContainer!!.translationY.toDouble()
                            )
                        ).toFloat()
                    } else {
                        val dist =
                            sheetContainer!!.measuredHeight - sheetContainer!!.translationY
                        translation = max(0.0, (getBottomInset() - dist).toDouble()).toFloat()
                    }
                }
                var wasAlpha = backgroundPaint.getAlpha()
                if (transitionFromRight) {
                    alpha *= sheetContainer!!.getAlpha()
                }
                val left = if (transitionFromRight) sheetContainer!!.getX()
                    .toInt() else sheetContainer!!.getLeft()
                if (alpha < 1f) {
                    backgroundPaint.setAlpha((wasAlpha * alpha).toInt())
                }
                canvas.drawRect(
                    (left + backgroundPaddingLeft).toFloat(),
                    getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY,
                    (sheetContainer!!.getRight() - backgroundPaddingLeft).toFloat(),
                    getMeasuredHeight() + translation,
                    backgroundPaint
                )
                backgroundPaint.setAlpha(wasAlpha)

                if (overlayDrawNavBarColor != 0) {
                    backgroundPaint.setColor(overlayDrawNavBarColor)
                    wasAlpha = backgroundPaint.getAlpha()
                    if (alpha < 1f) {
                        backgroundPaint.setAlpha((wasAlpha * alpha).toInt())
                        translation = 0f
                    }
                    canvas.drawRect(
                        (left + backgroundPaddingLeft).toFloat(),
                        getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY,
                        (sheetContainer!!.getRight() - backgroundPaddingLeft).toFloat(),
                        getMeasuredHeight() + translation,
                        backgroundPaint
                    )
                    backgroundPaint.setAlpha(wasAlpha)
                }
            }
        }
    }

    protected fun getBottomSheetWidth(isPortrait: Boolean, width: Int, height: Int): Int {
        return if (isPortrait) width else max(
            (width * 0.8f).toDouble(),
            min(dp(480).toDouble(), width.toDouble())
        ).toInt()
    }

    protected fun shouldOverlayCameraViewOverNavBar(): Boolean {
        return false
    }

    fun setHideSystemVerticalInsets(hideSystemVerticalInsets: Boolean) {
        val animator = ValueAnimator.ofFloat(
            hideSystemVerticalInsetsProgress,
            if (hideSystemVerticalInsets) 1f else 0f
        ).setDuration(180)
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT)
        animator.addUpdateListener(AnimatorUpdateListener { animation: ValueAnimator? ->
            hideSystemVerticalInsetsProgress = animation!!.getAnimatedValue() as Float
            container!!.requestLayout()
            sheetContainer!!.requestLayout()
        })
        animator.start()
    }

    @get:RequiresApi(api = Build.VERSION_CODES.Q)
    private val additionalMandatoryOffsets: Int
        get() {
            if (!calcMandatoryInsets || lastInsets == null) {
                return 0
            }
            val insets = lastInsets!!.getSystemGestureInsets()
            return if (!this.isKeyboardVisible && drawNavigationBar && insets != null && (insets.left != 0 || insets.right != 0)) insets.bottom else 0
        }

    interface BottomSheetDelegateInterface {
        fun onOpenAnimationStart()
        fun onOpenAnimationEnd()
        fun canDismiss(): Boolean
    }


    class BottomSheetDelegate : BottomSheetDelegateInterface {
        override fun onOpenAnimationStart() {
        }

        override fun onOpenAnimationEnd() {
        }

        override fun canDismiss(): Boolean {
            return true
        }
    }

    class BottomSheetCell @JvmOverloads constructor(
        context: Context,
        var currentType: Int,
        private val resourcesProvider: Theme.ResourcesProvider? = null
    ) : FrameLayout(context) {
        var textView: TextView
            private set
        val imageView: ImageView
        private val imageView2: ImageView

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var widthMeasureSpec = widthMeasureSpec
            val height = if (currentType == 2) 80 else 48
            if (currentType == 0) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    MeasureSpec.getSize(widthMeasureSpec),
                    MeasureSpec.EXACTLY
                )
            }
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(dp(height), MeasureSpec.EXACTLY)
            )
        }

        fun setTextColor(color: Int) {
            textView.setTextColor(color)
        }

        fun setIconColor(color: Int) {
            imageView.setColorFilter(PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY))
        }

        fun setGravity(gravity: Int) {
            textView.setGravity(gravity)
        }

        fun setTextAndIcon(text: CharSequence?, icon: Int) {
            setTextAndIcon(text, icon, null, false)
        }

        fun setTextAndIcon(text: CharSequence?, icon: Drawable?) {
            setTextAndIcon(text, 0, icon, false)
        }

        fun setTextAndIcon(text: CharSequence?, icon: Int, drawable: Drawable?, bigTitle: Boolean) {
            textView.setText(text)
            if (icon != 0 || drawable != null) {
                if (drawable != null) {
                    imageView.setImageDrawable(drawable)
                } else {
                    imageView.setImageResource(icon)
                }
                imageView.setVisibility(VISIBLE)
                if (bigTitle) {
                    textView.setPadding(dp(if (isRTL) 21 else 72), 0, dp(if (isRTL) 72 else 21), 0)
                    imageView.setPadding(if (isRTL) 0 else dp(5), 0, if (isRTL) dp(5) else 5, 0)
                } else {
                    textView.setPadding(dp(if (isRTL) 16 else 72), 0, dp(if (isRTL) 72 else 16), 0)
                    imageView.setPadding(0, 0, 0, 0)
                }
            } else {
                imageView.setVisibility(INVISIBLE)
                textView.setPadding(
                    dp(if (bigTitle) 21 else 16),
                    0,
                    dp(if (bigTitle) 21 else 16),
                    0
                )
            }
        }

        var checked: Boolean = false
            set(checked) {
//            imageView2.setImageResource((this.checked = checked) ? R.drawable.checkbig : 0);
            }

        var isSheetSelected: Boolean = false

        init {
            if (currentType != Builder.Companion.CELL_TYPE_CALL) {
                setBackgroundDrawable(createSelectorDrawable(Theme.platinum))
            }

            //setPadding(dp(16), 0, dp(16), 0);
            imageView = ImageView(context)
            imageView.setScaleType(ImageView.ScaleType.CENTER)
            imageView.setColorFilter(
                PorterDuffColorFilter(
                    Color.RED,
                    PorterDuff.Mode.MULTIPLY
                )
            )
            addView(
                imageView,
                frameLayoutParams(
                    dp(56),
                    dp(48),
                    Gravity.CENTER_VERTICAL or (if (isRTL) Gravity.RIGHT else Gravity.LEFT)
                )
            )

            imageView2 = ImageView(context)
            imageView2.setScaleType(ImageView.ScaleType.CENTER)
            imageView2.setColorFilter(
                PorterDuffColorFilter(
                    Color.BLUE,
                    PorterDuff.Mode.SRC_IN
                )
            )
            addView(
                imageView2,
                frameLayoutParams(
                    dp(56),
                    dp(48),
                    Gravity.CENTER_VERTICAL or (if (isRTL) Gravity.LEFT else Gravity.RIGHT)
                )
            )

            textView = TextView(context)
            textView.setLines(1)
            textView.setSingleLine(true)
            textView.setGravity(Gravity.CENTER_HORIZONTAL)
            textView.setEllipsize(TextUtils.TruncateAt.END)
            if (currentType == 0 || currentType == Builder.Companion.CELL_TYPE_CALL) {
                textView.setTextColor(Color.YELLOW)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                addView(
                    textView,
                    frameLayoutParams(
                        WRAP_CONTENT,
                        WRAP_CONTENT,
                        (if (isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
                    )
                )
            } else if (currentType == 1) {
                textView.setGravity(Gravity.CENTER)
                textView.setTextColor(Color.GREEN)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                textView.setTypeface(AndroidUtilities.bold())
                addView(
                    textView,
                    frameLayoutParams(MATCH_PARENT, MATCH_PARENT)
                )
            } else if (currentType == 2) {
                textView.setGravity(Gravity.CENTER)
                textView.setTextColor(Color.CYAN)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                textView.setTypeface(AndroidUtilities.bold())
                textView.setBackground(
                    filledRect(
                        Color.RED,
                        dp(6f)
                    )
                )
                addView(
                    textView,
                    frameLayoutParams(
                        MATCH_PARENT,
                        MATCH_PARENT,
                        0,
                        dp(16),
                        dp(16),
                        dp(16),
                        dp(16)
                    )
                )
            }
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            if (isSheetSelected) {
                info.setSelected(true)
            }
        }
    }

    protected fun onInsetsChanged() {
    }

    protected fun mainContainerDispatchDraw(canvas: Canvas?) {
    }

    @JvmOverloads
    fun fixNavigationBar(bgColor: Int = Color.BLUE) {
        drawNavigationBar = !occupyNavigationBar
        drawDoubleNavigationBar = true
        scrollNavBar = true
        setOverlayNavBarColor(bgColor.also { navBarColor = it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreateInternal()
    }

    private fun onCreateInternal() {
        var window: Window? = null
        if (attachedFragment != null) {
            attachedFragment!!.addSheet(this)
            if (attachedFragment!!.getLayoutContainer() == null) return

            val imm =
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (imm.hideSoftInputFromWindow(
                    attachedFragment!!.getLayoutContainer()!!.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
            ) {
                runOnUIThread(Runnable {
                    removeFromParent(container)
                    attachedFragment!!.getLayoutContainer()!!.addView(container)
                }, 80)
            } else {
                removeFromParent(container)
                attachedFragment!!.getLayoutContainer()!!.addView(container)
            }
        } else {
            window = getWindow()
            window!!.setWindowAnimations(R.style.DialogNoAnimation)
            setContentView(
                container!!,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        if (this.useLightStatusBar && Build.VERSION.SDK_INT >= 23) {
            val color = Theme.getColor(Theme.key_actionBarDefault)
            if (color == -0x1) {
                var flags = container!!.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                container!!.setSystemUiVisibility(flags)
            }
        }
        if (useLightNavBar && Build.VERSION.SDK_INT >= 26) {
            AndroidUtilities.setLightNavigationBar(getWindow()!!, false)
        }

        if (this.sheetContainer == null) {
            this.sheetContainer = object : FrameLayout(context) {
                override fun hasOverlappingRendering(): Boolean {
                    return false
                }

                override fun setTranslationY(translationY: Float) {
                    super.setTranslationY(translationY)
                    if (topBulletinContainer != null) {
                        topBulletinContainer!!.translationY = -(container!!.getHeight() - sheetContainer!!.getY()) + backgroundPaddingTop
                    }
                    onContainerTranslationYChanged(translationY)
                }
            }
            sheetContainer!!.setBackgroundDrawable(shadowDrawable)
            sheetContainer!!.setPadding(
                backgroundPaddingLeft,
                (if (applyTopPadding) dp(8) else 0) + backgroundPaddingTop - 1,
                backgroundPaddingLeft,
                (if (applyBottomPadding) dp(8) else 0)
            )
        }
        sheetContainer!!.visibility = View.INVISIBLE
        container!!.addView(
            this.sheetContainer,
            0,
            frameLayoutParams(
                MATCH_PARENT,
                WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        if (topBulletinContainer == null) {
            topBulletinContainer = FrameLayout(context)
            container!!.addView(
                topBulletinContainer,
                container!!.indexOfChild(this.sheetContainer) + 1,
                frameLayoutParams(
                    MATCH_PARENT,
                    WRAP_CONTENT,
                    Gravity.BOTTOM
                )
            )
        }

        var topOffset = 0
        if (title != null) {
            titleView = object : TextView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                    if (multipleLinesTitle) {
                        var topOffset = measuredHeight
                        if (customView != null) {
                            (customView!!.layoutParams as MarginLayoutParams).topMargin = topOffset
                        } else if (this@BottomSheet.sheetContainer != null) {
                            for (i in 1..<sheetContainer!!.size) {
                                val child = sheetContainer!!.getChildAt(i)
                                if (child is BottomSheetCell) {
                                    (child.layoutParams as MarginLayoutParams).topMargin = topOffset
                                    topOffset += dp(48)
                                }
                            }
                        }
                    }
                }
            }
            val height = 48
            titleView!!.setText(title)
            if (bigTitle) {
                titleView!!.setTextColor(Color.GREEN)
                titleView!!.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
                titleView!!.setTypeface(AndroidUtilities.bold())
                titleView!!.setPadding(dp(21), dp(if (multipleLinesTitle) 14 else 6), dp(21), dp(8))
            } else {
                titleView!!.setTextColor(Color.CYAN)
                titleView!!.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                titleView!!.setPadding(dp(16), dp(if (multipleLinesTitle) 8 else 0), dp(16), dp(8))
            }
            if (multipleLinesTitle) {
                titleView!!.setSingleLine(false)
                titleView!!.setMaxLines(5)
                titleView!!.setEllipsize(TextUtils.TruncateAt.END)
            } else {
                titleView!!.setLines(1)
                titleView!!.setSingleLine(true)
                titleView!!.setEllipsize(TextUtils.TruncateAt.MIDDLE)
            }
            titleView!!.setGravity(Gravity.CENTER_VERTICAL)
            sheetContainer!!.addView(
                titleView,
                frameLayoutParams(
                    MATCH_PARENT,
                    if (multipleLinesTitle) ViewGroup.LayoutParams.WRAP_CONTENT else height
                )
            )
            titleView!!.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? -> true })
            topOffset += height
        }
        if (customView != null) {
            if (customView!!.parent != null) {
                val viewGroup = customView!!.parent as ViewGroup
                viewGroup.removeView(customView)
            }
            if (!useBackgroundTopPadding) {
                sheetContainer!!.clipToPadding = false
                sheetContainer!!.setClipChildren(false)
                container!!.clipToPadding = false
                container!!.setClipChildren(false)
                sheetContainer!!.addView(
                    customView,
                    frameLayoutParams(
                        MATCH_PARENT,
                        WRAP_CONTENT,
                        customViewGravity,
                        0,
                        topOffset,
                        0,
                        0
                    )
                )
                (customView!!.layoutParams as MarginLayoutParams).topMargin = -backgroundPaddingTop + dp(topOffset)
            } else {
                sheetContainer!!.addView(
                    customView,
                    frameLayoutParams(
                        MATCH_PARENT,
                        WRAP_CONTENT,
                        customViewGravity,
                        0,
                        topOffset,
                        0,
                        0
                    )
                )
            }
        } else {
            if (items != null) {
                val rowLayout: FrameLayout? = null
                val lastRowLayoutNum = 0
                for (a in items!!.indices) {
                    if (items!![a] == null) {
                        continue
                    }
                    val cell = BottomSheetCell(getContext(), cellType, resourcesProvider)
                    cell.setTextAndIcon(
                        items!![a],
                        if (itemIcons != null) itemIcons!![a] else 0,
                        null,
                        bigTitle
                    )
                    sheetContainer!!.addView(
                        cell,
                        frameLayoutParams(
                            MATCH_PARENT,
                            48,
                            Gravity.LEFT or Gravity.TOP,
                            0,
                            topOffset,
                            0,
                            0
                        )
                    )
                    topOffset += 48
                    cell.setTag(a)
                    cell.setOnClickListener(View.OnClickListener { v: View? ->
                        dismissWithButtonClick(
                            (v!!.tag as Int?)!!
                        )
                    })
                    itemViews.add(cell)
                }
            }
        }

        if (attachedFragment != null) {
        } else if (window != null) {
            val params = window.attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP or Gravity.LEFT
            params.dimAmount = 0f
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            if (focusable) {
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            }
            if (isFullscreen) {
                params.flags =
                    params.flags or (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                container!!.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN)
            }
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            if (Build.VERSION.SDK_INT >= 28) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.setAttributes(params)
        }
    }

    override fun onStart() {
        super.onStart()
    }

    fun setUseLightStatusBar(value: Boolean) {
        this.useLightStatusBar = value
        if (Build.VERSION.SDK_INT >= 23) {
            val color = Theme.getColor(Theme.key_actionBarDefault)
            var flags = container!!.systemUiVisibility
            if (this.useLightStatusBar && color == -0x1) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            container!!.systemUiVisibility = flags
        }
        if (attachedFragment != null) {
//            LaunchActivity.instance.checkSystemBarColors(true, true, true, false)
        }
    }

    fun isFocusable(): Boolean {
        return focusable
    }

    fun setFocusable(value: Boolean) {
        if (focusable == value) {
            return
        }
        focusable = value
        val window = getWindow()
        val params = window!!.getAttributes()
        if (focusable) {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
        } else {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        }
        window.setAttributes(params)
    }

    fun setShowWithoutAnimation(value: Boolean) {
        showWithoutAnimation = value
    }

    fun setBackgroundColor(color: Int) {
        shadowDrawable.setColorFilter(color, PorterDuff.Mode.MULTIPLY)
    }

    override fun show() {
        if (!AndroidUtilities.isSafeToShow(context)) return
        if (attachedFragment != null) {
            onCreateInternal()
        } else {
            super.show()
        }
        setShowing(true)
        if (focusable) {
            getWindow()!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        this.isDismissed = false
        cancelSheetAnimation()
        sheetContainer!!.measure(
            MeasureSpec.makeMeasureSpec(
                displaySize.x + backgroundPaddingLeft * 2,
                MeasureSpec.AT_MOST
            ),
            MeasureSpec.makeMeasureSpec(displaySize.y, MeasureSpec.AT_MOST)
        )
        if (showWithoutAnimation) {
            backDrawable.setAlpha(if (dimBehind) dimBehindAlpha else 0)
            sheetContainer!!.translationY = 0f
            return
        }
        backDrawable.setAlpha(0)
        layoutCount = 2
        sheetContainer!!.translationY = (AndroidUtilities.statusBarHeight * (1f - hideSystemVerticalInsetsProgress)) + sheetContainer!!.measuredHeight + (if (scrollNavBar) getBottomInset() else 0)
        var delay = (if (openNoDelay) 0 else 150).toLong()
        if (waitingKeyboard) {
            delay = 500
        }
        runOnUIThread(object : Runnable {
            override fun run() {
                if (startAnimationRunnable !== this || this@BottomSheet.isDismissed) {
                    return
                }
                startAnimationRunnable = null
                startOpenAnimation()
            }
        }.also { startAnimationRunnable = it }, delay)
    }

    fun setAllowDrawContent(value: Boolean) {
        if (allowDrawContent != value) {
            allowDrawContent = value
            container!!.setBackgroundDrawable(if (allowDrawContent) backDrawable else null)
            container!!.invalidate()
        }
    }

    protected open fun canDismissWithSwipe(): Boolean {
        return canDismissWithSwipe
    }

    fun setCanDismissWithSwipe(value: Boolean) {
        canDismissWithSwipe = value
    }

    protected fun onContainerTouchEvent(event: MotionEvent?): Boolean {
        return false
    }

    protected fun onScrollUp(translationY: Float): Boolean {
        return false
    }

    protected fun onScrollUpEnd(translationY: Float) {
    }

    protected fun onScrollUpBegin(translationY: Float) {}

    fun setCustomView(view: View?) {
        customView = view
    }

    override fun setTitle(value: CharSequence?) {
        setTitle(value, false)
    }

    fun setTitle(value: CharSequence?, big: Boolean) {
        title = value
        bigTitle = big
    }

    fun setApplyTopPadding(value: Boolean) {
        applyTopPadding = value
    }

    fun setApplyBottomPadding(value: Boolean) {
        applyBottomPadding = value
    }

    protected fun onCustomMeasure(view: View?, width: Int, height: Int): Boolean {
        return false
    }

    protected fun onCustomLayout(
        view: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Boolean {
        return false
    }

    protected fun onDismissWithTouchOutside() {
        dismiss()
    }

    protected fun canDismissWithTouchOutside(): Boolean {
        return canDismissWithTouchOutside
    }

    fun setCanDismissWithTouchOutside(value: Boolean) {
        canDismissWithTouchOutside = value
    }

    protected fun onContainerTranslationYChanged(translationY: Float) {
    }

    protected fun cancelSheetAnimation() {
        if (currentSheetAnimation != null) {
            currentSheetAnimation!!.cancel()
            currentSheetAnimation = null
        }
        this.sheetAnimationType = 0
    }

    fun setOnHideListener(listener: DialogInterface.OnDismissListener?) {
        onHideListener = listener
    }

    protected val targetOpenTranslationY: Int
        get() = 0

    private fun startOpenAnimation() {
        if (this.isDismissed) {
            return
        }
        sheetContainer!!.setVisibility(View.VISIBLE)

        if (!onCustomOpenAnimation()) {
            if (Build.VERSION.SDK_INT >= 20 && useHardwareLayer) {
                container!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            if (transitionFromRight) {
                sheetContainer!!.setTranslationX(dp(48).toFloat())
                sheetContainer!!.setAlpha(0f)
                sheetContainer!!.setTranslationY(0f)
            } else {
                sheetContainer!!.setTranslationY((this.containerViewHeight + keyboardHeight + dp(10) + (if (scrollNavBar) getBottomInset() else 0)).toFloat())
            }
            this.sheetAnimationType = 1
            if (navigationBarAnimation != null) {
                navigationBarAnimation!!.cancel()
            }
            navigationBarAnimation = ValueAnimator.ofFloat(navigationBarAlpha, 1f)
            navigationBarAnimation!!.addUpdateListener(AnimatorUpdateListener { a: ValueAnimator? ->
                navigationBarAlpha = a!!.getAnimatedValue() as Float
                if (container != null) {
                    container!!.invalidate()
                }
            })
            currentSheetAnimation = AnimatorSet()
            currentSheetAnimation!!.playTogether(
                ObjectAnimator.ofFloat<View?>(this.sheetContainer, View.TRANSLATION_X, 0f),
                ObjectAnimator.ofFloat<View?>(this.sheetContainer, View.ALPHA, 1f),
                ObjectAnimator.ofFloat<View?>(this.sheetContainer, View.TRANSLATION_Y, 0f),
                ObjectAnimator.ofInt<ColorDrawable?>(
                    backDrawable,
                    AnimationProperties.COLOR_DRAWABLE_ALPHA,
                    if (dimBehind) dimBehindAlpha else 0
                ),
                navigationBarAnimation
            )
            if (transitionFromRight) {
                currentSheetAnimation!!.setDuration(250)
                currentSheetAnimation!!.setInterpolator(CubicBezierInterpolator.DEFAULT)
            } else {
                currentSheetAnimation!!.setDuration(400)
                currentSheetAnimation!!.setInterpolator(openInterpolator)
            }
            currentSheetAnimation!!.setStartDelay((if (waitingKeyboard) 0 else 20).toLong())
            currentSheetAnimation!!.setInterpolator(openInterpolator)
            currentSheetAnimation!!.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentSheetAnimation != null && currentSheetAnimation == animation) {
                        currentSheetAnimation = null
                        this@BottomSheet.sheetAnimationType = 0
                        onOpenAnimationEnd()
                        if (delegate != null) {
                            delegate!!.onOpenAnimationEnd()
                        }
                        if (useHardwareLayer) {
                            container!!.setLayerType(View.LAYER_TYPE_NONE, null)
                        }

                        if (isFullscreen) {
                            val params = getWindow()!!.getAttributes()
                            params.flags =
                                params.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                            getWindow()!!.setAttributes(params)
                        }
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (currentSheetAnimation != null && currentSheetAnimation == animation) {
                        currentSheetAnimation = null
                        this@BottomSheet.sheetAnimationType = 0
                    }
                }
            })
            currentSheetAnimation!!.start()
        }
    }

    fun setItemText(item: Int, text: CharSequence?) {
        if (item < 0 || item >= itemViews.size) {
            return
        }
        val cell = itemViews.get(item)
        cell.textView.setText(text)
    }

    fun setItemColor(item: Int, color: Int, icon: Int) {
        if (item < 0 || item >= itemViews.size) {
            return
        }
        val cell = itemViews.get(item)
        cell.textView.setTextColor(color)
        cell.imageView.setColorFilter(PorterDuffColorFilter(icon, PorterDuff.Mode.MULTIPLY))
    }

    fun setItems(
        i: Array<CharSequence?>?,
        icons: IntArray?,
        listener: DialogInterface.OnClickListener?
    ) {
        items = i
        itemIcons = icons
        onClickListener = listener
    }

    fun setTitleColor(color: Int) {
        if (titleView == null) {
            return
        }
        titleView!!.setTextColor(color)
    }

    fun dismissWithButtonClick(item: Int) {
        if (this.isDismissed) {
            return
        }
        this.isDismissed = true
        cancelSheetAnimation()
        this.sheetAnimationType = 2
        currentSheetAnimation = AnimatorSet()
        currentSheetAnimation!!.playTogether(
            ObjectAnimator.ofFloat<View?>(
                this.sheetContainer,
                View.TRANSLATION_Y,
                (this.containerViewHeight + keyboardHeight + dp(10) + (if (scrollNavBar) getBottomInset() else 0)).toFloat()
            ),
            ObjectAnimator.ofInt<ColorDrawable?>(
                backDrawable,
                AnimationProperties.COLOR_DRAWABLE_ALPHA,
                0
            )
        )
        currentSheetAnimation!!.setDuration((if (cellType == Builder.Companion.CELL_TYPE_CALL) 330 else 180).toLong())
        currentSheetAnimation!!.setInterpolator(CubicBezierInterpolator.EASE_OUT)
        currentSheetAnimation!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (currentSheetAnimation != null && currentSheetAnimation == animation) {
                    currentSheetAnimation = null
                    this@BottomSheet.sheetAnimationType = 0
                    if (onClickListener != null) {
                        onClickListener!!.onClick(this@BottomSheet, item)
                    }
                    runOnUIThread(Runnable {
                        if (onHideListener != null) {
                            onHideListener!!.onDismiss(this@BottomSheet)
                        }
                        try {
                            dismissInternal()
                        } catch (e: Exception) {
                        }
                    })
                }
            }

            override fun onAnimationCancel(animation: Animator) {
                if (currentSheetAnimation != null && currentSheetAnimation == animation) {
                    currentSheetAnimation = null
                    this@BottomSheet.sheetAnimationType = 0
                }
            }
        })
        currentSheetAnimation!!.start()

        if (cellType == Builder.Companion.CELL_TYPE_CALL && selectedPos != null) {
            val color1 = this.itemViews[selectedPos!!].textView.currentTextColor
            val color2 = this.itemViews[item].textView.currentTextColor
            val animator = ValueAnimator.ofArgb(color1, color2)
            animator.addUpdateListener(AnimatorUpdateListener { a: ValueAnimator? ->
                val color = a!!.getAnimatedValue() as Int
                setItemColor(selectedPos!!, color, color)
            })
            animator.setDuration(130)
            animator.interpolator = CubicBezierInterpolator.DEFAULT
            animator.start()
            val animator2 = ValueAnimator.ofArgb(color2, color1)
            animator2.addUpdateListener(AnimatorUpdateListener { a: ValueAnimator? ->
                val color = a!!.getAnimatedValue() as Int
                setItemColor(item, color, color)
            })
            animator2.setDuration(130)
            animator2.interpolator = CubicBezierInterpolator.DEFAULT
            animator2.start()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (this.isDismissed) {
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    fun onDismissAnimationStart() {}

    val containerViewHeight: Int
        get() {
            if (this.sheetContainer == null) {
                return 0
            }
            return sheetContainer!!.measuredHeight
        }

    protected fun canSwipeToBack(event: MotionEvent?): Boolean {
        return false
    }

    private var forceKeyboardOnDismiss = false
    fun forceKeyboardOnDismiss() {
        forceKeyboardOnDismiss = true
    }

    override val windowView: View?
        get() = container

    override val isShown: Boolean
        get() = !this.isDismissed

    override fun dismiss() {
        if (delegate != null && !delegate!!.canDismiss()) {
            return
        }
        if (this.isDismissed) {
            return
        }
        this.isDismissed = true
        if (onHideListener != null) {
            onHideListener!!.onDismiss(this)
        }
        cancelSheetAnimation()
        var duration: Long = 0
        onDismissAnimationStart()
        if (skipDismissAnimation) {
            runOnUIThread(Runnable {
                try {
                    dismissInternal()
                } catch (e: Exception) {
                }
            })
        } else {
            if (!allowCustomAnimation || !onCustomCloseAnimation()) {
                this.sheetAnimationType = 2
                if (navigationBarAnimation != null) {
                    navigationBarAnimation!!.cancel()
                }
                navigationBarAnimation = ValueAnimator.ofFloat(navigationBarAlpha, 0f)
                navigationBarAnimation!!.addUpdateListener(AnimatorUpdateListener { a: ValueAnimator? ->
                    navigationBarAlpha = a!!.getAnimatedValue() as Float
                    if (container != null) {
                        container!!.invalidate()
                    }
                })
                currentSheetAnimation = AnimatorSet()
                val animators = ArrayList<Animator?>()
                if (this.sheetContainer != null) {
                    if (transitionFromRight) {
                        animators.add(
                            ObjectAnimator.ofFloat<View?>(
                                this.sheetContainer,
                                View.TRANSLATION_X,
                                dp(48).toFloat()
                            )
                        )
                        animators.add(
                            ObjectAnimator.ofFloat<View?>(
                                this.sheetContainer,
                                View.ALPHA,
                                0f
                            )
                        )
                    } else {
                        animators.add(
                            ObjectAnimator.ofFloat<View?>(
                                this.sheetContainer,
                                View.TRANSLATION_Y,
                                (this.containerViewHeight + (if (forceKeyboardOnDismiss) lastKeyboardHeight else keyboardHeight) + dp(
                                    10
                                ) + (if (scrollNavBar) getBottomInset() else 0)).toFloat()
                            )
                        )
                    }
                }
                animators.add(
                    ObjectAnimator.ofInt<ColorDrawable?>(
                        backDrawable,
                        AnimationProperties.COLOR_DRAWABLE_ALPHA,
                        0
                    )
                )
                animators.add(navigationBarAnimation)
                currentSheetAnimation!!.playTogether(animators)

                if (transitionFromRight) {
                    currentSheetAnimation!!.setDuration(200)
                    currentSheetAnimation!!.interpolator = CubicBezierInterpolator.DEFAULT
                } else {
                    currentSheetAnimation!!.setDuration(250L.also { duration = it })
                    currentSheetAnimation!!.interpolator = CubicBezierInterpolator.EASE_OUT
                }
                currentSheetAnimation!!.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (currentSheetAnimation != null && currentSheetAnimation == animation) {
                            currentSheetAnimation = null
                            this@BottomSheet.sheetAnimationType = 0
                            runOnUIThread(Runnable {
                                try {
                                    dismissInternal()
                                } catch (e: Exception) {
                                }
                            })
                        }
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        if (currentSheetAnimation != null && currentSheetAnimation == animation) {
                            currentSheetAnimation = null
                            this@BottomSheet.sheetAnimationType = 0
                        }
                    }
                })
                currentSheetAnimation!!.start()
            }
        }

        val bulletin = Bulletin.getVisibleBulletin()
        if (bulletin != null && bulletin.isShowing && bulletin.hideAfterBottomSheet) {
            if (duration > 0) {
                bulletin.hide((duration * 0.6f).toLong())
            } else {
                bulletin.hide()
            }
        }
        setShowing(false)
    }

    public override fun dismiss(tabs: Boolean) {
        this.dismiss()
    }

    public override fun release() {
        dismissInternal()
    }

    override val isFullyVisible: Boolean
        get() = false

    public override fun attachedToParent(): Boolean {
        return container != null && container!!.isAttachedToWindow
    }

    public override fun showDialog(dialog: Dialog?): Boolean {
        return false
    }

    public override fun setKeyboardHeightFromParent(keyboardHeight: Int) {
    }

    public override fun getNavigationBarColor(color: Int): Int {
        val t: Float
        if (!attachedToParent() || this.sheetContainer == null) {
            t = 0f
        } else if (transitionFromRight) {
            t = sheetContainer!!.alpha
        } else {
            val fullHeight =
                (this.containerViewHeight + keyboardHeight + dp(10) + (if (scrollNavBar) getBottomInset() else 0)).toFloat()
            t = Utilities.clamp01(1f - sheetContainer!!.translationY / fullHeight)
        }
        return ColorUtils.blendARGB(color, navBarColor, t)
    }

    public override fun setOnDismissListener(onDismiss: Runnable?) {
        if (onDismiss != null) {
            setOnHideListener(DialogInterface.OnDismissListener { d: DialogInterface? -> onDismiss.run() })
        }
    }

    override fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        super.setOnDismissListener(listener)
    }

    fun dismissInternal() {
        if (attachedFragment != null) {
            attachedFragment!!.removeSheet(this)
            removeFromParent(container)
        } else {
            try {
                super<android.app.Dialog>.dismiss()
            } catch (e: Exception) {
            }
        }
    }

    protected fun onCustomCloseAnimation(): Boolean {
        return false
    }

    protected fun onCustomOpenAnimation(): Boolean {
        return false
    }

    class Builder {
        private val bottomSheet: BottomSheet

        constructor(context: Context, bgColor: Int) : this(context, false, null, bgColor)

        constructor(context: Context, needFocus: Boolean, bgColor: Int) : this(
            context,
            needFocus,
            null,
            bgColor
        )

        @JvmOverloads
        constructor(
            context: Context,
            needFocus: Boolean = false,
            resourcesProvider: Theme.ResourcesProvider? = null
        ) {
            bottomSheet = BottomSheet(context, needFocus, resourcesProvider)
            bottomSheet.fixNavigationBar()
        }

        constructor(
            context: Context,
            needFocus: Boolean,
            resourcesProvider: Theme.ResourcesProvider?,
            bgColor: Int
        ) {
            bottomSheet = BottomSheet(context, needFocus, resourcesProvider)
            bottomSheet.setBackgroundColor(bgColor)
            bottomSheet.fixNavigationBar(bgColor)
        }

        fun setItems(
            items: Array<CharSequence?>?,
            onClickListener: DialogInterface.OnClickListener?
        ): Builder {
            bottomSheet.items = items
            bottomSheet.onClickListener = onClickListener
            return this
        }

        fun setItems(
            items: Array<CharSequence?>?,
            icons: IntArray?,
            onClickListener: DialogInterface.OnClickListener?
        ): Builder {
            bottomSheet.items = items
            bottomSheet.itemIcons = icons
            bottomSheet.onClickListener = onClickListener
            return this
        }

        fun setCustomView(view: View?): Builder {
            bottomSheet.customView = view
            return this
        }

        fun setCustomView(view: View?, gravity: Int): Builder {
            bottomSheet.customView = view
            bottomSheet.customViewGravity = gravity
            return this
        }

        fun getCustomView(): View? {
            return bottomSheet.customView
        }

        fun setTitle(title: CharSequence?): Builder {
            return setTitle(title, false)
        }

        fun setTitle(title: CharSequence?, big: Boolean): Builder {
            bottomSheet.title = title
            bottomSheet.bigTitle = big
            return this
        }

        fun selectedPos(pos: Int?): Builder {
            bottomSheet.selectedPos = pos
            return this
        }

        fun setCellType(cellType: Int): Builder {
            bottomSheet.cellType = cellType
            return this
        }

        fun setTitleMultipleLines(allowMultipleLines: Boolean): Builder {
            bottomSheet.multipleLinesTitle = allowMultipleLines
            return this
        }

        fun create(): BottomSheet {
            return bottomSheet
        }

        fun setDimBehind(value: Boolean): BottomSheet {
            bottomSheet.dimBehind = value
            return bottomSheet
        }

        fun show(): BottomSheet {
            bottomSheet.show()
            return bottomSheet
        }

        fun setTag(tag: Int): Builder {
            bottomSheet.tag = tag
            return this
        }

        fun setUseHardwareLayer(value: Boolean): Builder {
            bottomSheet.useHardwareLayer = value
            return this
        }

        fun setDelegate(delegate: BottomSheetDelegate?): Builder {
            bottomSheet.delegate = delegate
            return this
        }

        fun setApplyTopPadding(value: Boolean): Builder {
            bottomSheet.applyTopPadding = value
            return this
        }

        fun setApplyBottomPadding(value: Boolean): Builder {
            bottomSheet.applyBottomPadding = value
            return this
        }

        fun getDismissRunnable(): Runnable {
            return bottomSheet.dismissRunnable
        }

        fun setUseFullWidth(value: Boolean): BottomSheet {
            bottomSheet.fullWidth = value
            return bottomSheet
        }

        fun setUseFullscreen(value: Boolean): BottomSheet {
            bottomSheet.isFullscreen = value
            return bottomSheet
        }

        fun setOnPreDismissListener(onDismissListener: DialogInterface.OnDismissListener?): Builder {
            bottomSheet.setOnHideListener(onDismissListener)
            return this
        }

        companion object {
            var CELL_TYPE_CALL: Int = 4
        }
    }

    fun getLeftInset(): Int {
        if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
            val inset: Float
            if (AVOID_SYSTEM_CUTOUT_FULLSCREEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lastInsets!!.getDisplayCutout() != null) {
                inset = lastInsets!!.getDisplayCutout()!!
                    .getSafeInsetLeft() + (lastInsets!!.systemWindowInsetLeft - lastInsets!!.getDisplayCutout()!!
                    .getSafeInsetLeft()) * (1f - hideSystemVerticalInsetsProgress)
            } else {
                inset =
                    lastInsets!!.systemWindowInsetLeft * (1f - hideSystemVerticalInsetsProgress)
            }
            return inset.toInt()
        }
        return 0
    }

    fun getRightInset(): Int {
        if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
            val inset: Float
            if (AVOID_SYSTEM_CUTOUT_FULLSCREEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lastInsets!!.getDisplayCutout() != null) {
                inset = lastInsets!!.getDisplayCutout()!!
                    .getSafeInsetRight() + (lastInsets!!.getSystemWindowInsetRight() - lastInsets!!.getDisplayCutout()!!
                    .getSafeInsetRight()) * (1f - hideSystemVerticalInsetsProgress)
            } else {
                inset =
                    lastInsets!!.getSystemWindowInsetRight() * (1f - hideSystemVerticalInsetsProgress)
            }
            return inset.toInt()
        }
        return 0
    }

    fun getStatusBarHeight(): Int {
        return (statusBarHeight * (1f - hideSystemVerticalInsetsProgress)).toInt()
    }

    fun getBottomInset(): Int {
        return (bottomInset * (1f - hideSystemVerticalInsetsProgress)).toInt()
    }

    fun onConfigurationChanged(newConfig: Configuration?) {
    }

    fun onContainerDraw(canvas: Canvas?) {
    }

    fun setCurrentPanTranslationY(currentPanTranslationY: Float) {
        this.currentPanTranslationY = currentPanTranslationY
        container!!.invalidate()
    }

    private var overlayDrawNavBarColor = 0

    fun setOverlayNavBarColor(color: Int) {
        overlayDrawNavBarColor = color
        if (container != null) {
            container!!.invalidate()
        }

        if (attachedFragment != null) {
//            LaunchActivity.instance.checkSystemBarColors(true, true, true, false)
            setLightNavigationBar(
                windowView!!, computePerceivedBrightness(
                    getNavigationBarColor(Color.CYAN)
                ) >= .721f
            )
            //            AndroidUtilities.setLightStatusBar(dialog != null ? dialog.windowView : windowView, attachedToActionBar && AndroidUtilities.computePerceivedBrightness(actionBar.getBackgroundColor()) > .721f);
            return
        }
        //        if (Color.alpha(color) > 120) {
//            AndroidUtilities.setLightStatusBar(getWindow(), false);
//            AndroidUtilities.setLightNavigationBar(getWindow(), false);
//        } else {
//            AndroidUtilities.setLightStatusBar(getWindow(), !useLightStatusBar);
//            AndroidUtilities.setLightNavigationBar(getWindow(), !useLightNavBar);
//        }
        AndroidUtilities.setNavigationBarColor(getWindow()!!, overlayDrawNavBarColor)
        AndroidUtilities.setLightNavigationBar(
            getWindow()!!,
            computePerceivedBrightness(overlayDrawNavBarColor) > .721
        )
    }

    fun setOpenNoDelay(openNoDelay: Boolean) {
        this.openNoDelay = openNoDelay
    }

    private fun setShowing(showing: Boolean) {
        if (this.sheetShowing == showing) {
            return
        }
        this.sheetShowing = showing
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return super.dispatchKeyEvent(event)
    }

    fun setImageReceiverNumLevel(playingImages: Int, onShowing: Int) {
        this.playingImagesLayerNum = playingImages
        this.openedLayerNum = onShowing
    }

    private var smoothContainerViewLayoutUntil: Long = -1
    fun smoothContainerViewLayout() {
        smoothContainerViewLayoutUntil = System.currentTimeMillis() + 80
    }

    protected fun onSmoothContainerViewLayout(ty: Float) {
    }


    var attachedFragment: Fragment? = null

    init {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow()!!.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        } else if (Build.VERSION.SDK_INT >= 21) {
            getWindow()!!.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }
        val vc = ViewConfiguration.get(context)
        touchSlop = vc.getScaledTouchSlop()

        val padding = Rect()
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate()
        shadowDrawable.setColorFilter(
            PorterDuffColorFilter(
                Color.WHITE,
                PorterDuff.Mode.MULTIPLY
            )
        )
        shadowDrawable.getPadding(padding)
        backgroundPaddingLeft = padding.left
        backgroundPaddingTop = padding.top

        container = object : ContainerView(getContext()) {
            public override fun drawChild(
                canvas: Canvas,
                child: View?,
                drawingTime: Long
            ): Boolean {
                try {
                    return allowDrawContent && super.drawChild(canvas, child, drawingTime)
                } catch (e: Exception) {
                }
                return true
            }

            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                mainContainerDispatchDraw(canvas)
            }

            override fun onConfigurationChanged(newConfig: Configuration?) {
                lastInsets = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    container!!.requestApplyInsets()
                }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                Bulletin.addDelegate(this, object : Bulletin.Delegate {
                    public override fun getTopOffset(tag: Int): Int {
                        return AndroidUtilities.statusBarHeight
                    }
                })
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                Bulletin.removeDelegate(this)
            }
        }
        container!!.background = backDrawable
        focusable = needFocus
        container!!.fitsSystemWindows = true
        container!!.setOnApplyWindowInsetsListener(View.OnApplyWindowInsetsListener { v: View?, insets: WindowInsets? ->
            val newTopInset = insets!!.systemWindowInsetTop
            if ((newTopInset != 0 || AndroidUtilities.isInMultiWindow) && statusBarHeight != newTopInset) {
                statusBarHeight = newTopInset
            }
            lastInsets = insets
            v!!.requestLayout()
            onInsetsChanged()
            if (Build.VERSION.SDK_INT >= 30) {
                return@OnApplyWindowInsetsListener WindowInsets.CONSUMED
            } else {
                return@OnApplyWindowInsetsListener insets.consumeSystemWindowInsets()
            }
        })
        if (Build.VERSION.SDK_INT >= 30) {
            container!!.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            container!!.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        backDrawable.setAlpha(0)
    }

    fun makeAttached(fragment: Fragment?) {
        if (isTablet()) return
        this.attachedFragment = fragment
    }

    public override fun onAttachedBackPressed(): Boolean {
        onBackPressed()
        return true
    }

    override fun onBackPressed() {
        if (attachedFragment == null) {
            super.onBackPressed()
        } else {
            dismiss()
        }
    }

    companion object {
        private const val AVOID_SYSTEM_CUTOUT_FULLSCREEN = false
    }
}
