/* 
 * Copyright Erkinjanov Anaskhan, 14/02/2022.
 */

package com.ailnor.core

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.SparseArray
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.util.forEach
import androidx.core.view.children
import com.ailnor.fragment.R
import java.lang.reflect.Field
import java.security.SecureRandom
import java.util.Calendar
import java.util.Hashtable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.ceil

object AndroidUtilities {

    private val typefaceCache = Hashtable<String, Typeface>()
    private var mediumTypeface: Typeface? = null
    private var adjustOwnerId = 0
    private var lastFragmentId = 1
    var density = 1f
    var displaySize = Point()
    var displayMetrics = DisplayMetrics()
    var statusBarHeight = 0
    var screenRefreshRate = 60f
    var wasPortrait = true
    val isPortrait: Boolean
        get() {
            wasPortrait = Application.context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            return wasPortrait
        }
    private var isTablet: Boolean? = null
        private set
    var isInMultiWindow = false
    private var mAttachInfoField: Field? = null
    private var mStableInsetsField: Field? = null
    var touchSlop: Float = 0f

    // temp
    var smoothKeyboard = true

    var accelerateInterpolator = AccelerateInterpolator()
    var decelerateInterpolator = DecelerateInterpolator()

    var mainInterfacePaused = true
    var mainInterfaceStopped = true

    val rectTmp = RectF()
    val rectTmp2: Rect = Rect()

    private var broadcasting = 0
    private val addAfterBroadcast = SparseArray<ArrayList<ActionListener>>()
    private val removeAfterBroadcast = SparseArray<ArrayList<ActionListener>>()
    private val listeners = SparseArray<ArrayList<ActionListener>>()

    var random = SecureRandom()

    val isLandscape: Boolean
        get() = !isPortrait

    var animationEnabled = true

    private var lastWinterCheckTime = -1L
    var isWinter = false
        get() {
            if ((System.currentTimeMillis() - lastWinterCheckTime) > 60 * 1000){
                val month = Calendar.getInstance().get(Calendar.MONTH)
                field = month == 0 || month == 1 || month == 11
                lastWinterCheckTime = System.currentTimeMillis()
            }
            return field
        }

    fun isTablet(): Boolean{
        if (isTablet == null)
            isTablet = Application.context.resources.getBoolean(R.bool.isTablet)
        return isTablet!!
    }

    fun resetTabletFlag(){
        isTablet = null
    }

    fun isRTL() = false

    fun isRTL(text: CharSequence?) = false


    fun checkDisplaySize(context: Context, newConfiguration: Configuration?) {
        // just calling get function of isPortrait to save current orientation into wasPortrait
        isPortrait
        fillStatusBarHeight(context)
        try {
//            val oldDensity: Float = density
            density = context.resources.displayMetrics.density
//            val newDensity: Float = density
//            if (firstConfigurationWas && abs(oldDensity - newDensity) > 0.001) {
//                Theme.reloadAllResources(context)
//            }
//            firstConfigurationWas = true
            var configuration = newConfiguration
            if (configuration == null) {
                configuration = context.resources.configuration
            }
//            usingHardwareInput = configuration!!.keyboard != Configuration.KEYBOARD_NOKEYS && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = manager.defaultDisplay
            if (display != null) {
                display.getMetrics(displayMetrics)
                display.getSize(displaySize)
                screenRefreshRate = display.refreshRate
            }
            if (configuration != null && configuration.screenWidthDp != Configuration.SCREEN_WIDTH_DP_UNDEFINED) {
                val newSize =
                    ceil((configuration.screenWidthDp * density).toDouble())
                        .toInt()
                if (abs(displaySize.x - newSize) > 3)
                    displaySize.x = newSize
            }
            if (configuration != null && configuration.screenHeightDp != Configuration.SCREEN_HEIGHT_DP_UNDEFINED) {
                val newSize =
                    ceil((configuration.screenHeightDp * density).toDouble())
                        .toInt()
                if (abs(displaySize.y - newSize) > 3)
                    displaySize.y = newSize
            }

            val vc = ViewConfiguration.get(context)
            touchSlop = vc.scaledTouchSlop.toFloat()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val duration = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        animationEnabled = duration != 0f && transition != 0f
    }

    fun getPixelsInCM(cm: Float, isX: Boolean): Float {
        return cm / 2.54f * if (isX) displayMetrics.xdpi else displayMetrics.ydpi
    }

    fun fillStatusBarHeight(context: Context?) {
        if (context == null || statusBarHeight > 0) {
            return
        }
        statusBarHeight = getStatusBarHeight(context)
    }

    fun setLightStatusBar(window: Window, enable: Boolean) {
        setLightStatusBar(window, enable, false)
    }

    fun setLightStatusBar(window: Window, enable: Boolean, forceTransparentStatusbar: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            if (enable){
                window.decorView.windowInsetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                window.decorView.windowInsetsController?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            if (enable) {
                if (flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR == 0) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    decorView.systemUiVisibility = flags
                }
//                val statusBarColor: Int
//                if (!SharedConfig.noStatusBar && !forceTransparentStatusbar) {
//                    statusBarColor = AndroidUtilities.LIGHT_STATUS_BAR_OVERLAY
//                } else {
//                    statusBarColor = Color.TRANSPARENT
//                }
//                if (window.statusBarColor != statusBarColor) {
//                    window.statusBarColor = statusBarColor
//                }
            } else {
                if (flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0) {
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    decorView.systemUiVisibility = flags
                }
//                val statusBarColor: Int
//                if (!SharedConfig.noStatusBar && !forceTransparentStatusbar) {
//                    statusBarColor = AndroidUtilities.DARK_STATUS_BAR_OVERLAY
//                } else {
//                    statusBarColor = Color.TRANSPARENT
//                }
//                if (window.statusBarColor != statusBarColor) {
//                    window.statusBarColor = statusBarColor
//                }
            }
        }
    }

    fun setLightNavigationBar(window: Window, enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            flags = if (enable) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            decorView.systemUiVisibility = flags
        }
    }

    fun setLightNavigationBar(view: View?, enable: Boolean) {
        if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var flags = view.systemUiVisibility
            if (((flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) > 0) != enable) {
                if (enable) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
                view.setSystemUiVisibility(flags)
            }
        }
    }

    fun computePerceivedBrightness(color: Int): Float {
        return Color.red(color) * 0.2126f + Color.green(color) * 0.7152f + Color.blue(color) * 0.0722f / 255f
    }

    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private var navigationBarColorAnimators: HashMap<Window, ValueAnimator>? = null

    interface IntColorCallback {
        fun run(color: Int)
    }

    fun setStatusBarColor(window: Window, color: Int) {
        setStatusBarColor(window, color, true)
    }

    fun setStatusBarColor(window: Window, color: Int, animated: Boolean) {
        setStatusBarColor(window, color, animated, null)
    }

    fun setStatusBarColor(
        window: Window,
        color: Int,
        animated: Boolean,
        onUpdate: IntColorCallback?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (navigationBarColorAnimators != null) {
                val animator = navigationBarColorAnimators!!.get(window)
                if (animator != null) {
                    animator.cancel()
                    navigationBarColorAnimators!!.remove(window)
                }
            }
            if (!animated) {
                onUpdate?.run(color)
                try {
                    window.statusBarColor = color
                } catch (ignore: java.lang.Exception) {
                }
            } else {
                val animator = ValueAnimator.ofArgb(window.statusBarColor, color)
                animator.addUpdateListener { a: ValueAnimator ->
                    val tcolor = a.animatedValue as Int
                    onUpdate?.run(tcolor)
                    try {
                        window.statusBarColor = tcolor
                    } catch (ignore: java.lang.Exception) {
                    }
                }
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        navigationBarColorAnimators?.remove(window)
                    }
                })
                animator.duration = 200
                animator.interpolator = CubicBezierInterpolator.DEFAULT
                animator.start()
                if (navigationBarColorAnimators == null) {
                    navigationBarColorAnimators = HashMap()
                }
                navigationBarColorAnimators!![window] = animator
            }
        }
    }

    fun setNavigationBarColor(window: Window, color: Int) {
        setNavigationBarColor(window, color, true)
    }

    fun setNavigationBarColor(window: Window, color: Int, animated: Boolean) {
        setNavigationBarColor(window, color, animated, null)
    }

    fun setNavigationBarColor(
        window: Window,
        color: Int,
        animated: Boolean,
        onUpdate: IntColorCallback?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (navigationBarColorAnimators != null) {
                val animator = navigationBarColorAnimators!!.get(window)
                if (animator != null) {
                    animator.cancel()
                    navigationBarColorAnimators!!.remove(window)
                }
            }
            if (!animated) {
                onUpdate?.run(color)
                try {
                    window.navigationBarColor = color
                } catch (ignore: java.lang.Exception) {
                }
            } else {
                val animator = ValueAnimator.ofArgb(window.navigationBarColor, color)
                animator.addUpdateListener { a: ValueAnimator ->
                    val tcolor = a.animatedValue as Int
                    onUpdate?.run(tcolor)
                    try {
                        window.navigationBarColor = tcolor
                    } catch (ignore: java.lang.Exception) {
                    }
                }
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        navigationBarColorAnimators?.remove(window)
                    }
                })
                animator.duration = 200
                animator.interpolator = CubicBezierInterpolator.DEFAULT
                animator.start()
                if (navigationBarColorAnimators == null) {
                    navigationBarColorAnimators =
                        HashMap<Window, ValueAnimator>()
                }
                navigationBarColorAnimators!![window] = animator
            }
        }
    }

    fun getViewInset(view: View?): Int {
        if (view == null || view.height == displaySize.y || view.height == displaySize.y - statusBarHeight) {
            return 0
        }
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                val insets = view.rootWindowInsets
                return insets?.stableInsetBottom ?: 0
            } else {
                if (mAttachInfoField == null) {
                    mAttachInfoField = View::class.java.getDeclaredField("mAttachInfo")
                    mAttachInfoField!!.isAccessible = true
                }
                val mAttachInfo: Any = mAttachInfoField!!.get(view)
                if (mAttachInfo != null) {
                    if (mStableInsetsField == null) {
                        mStableInsetsField = mAttachInfo.javaClass.getDeclaredField("mStableInsets")
                        mStableInsetsField!!.isAccessible = true
                    }
                    val insets = mStableInsetsField!!.get(mAttachInfo) as Rect
                    return insets.bottom
                }
            }
        } catch (e: Exception) {
        }
        return 0
    }

    fun addActionListener(key: Int, actionListener: ActionListener) {
        if (broadcasting != 0) {
            var observers = addAfterBroadcast[key]
            if (observers == null) {
                observers = ArrayList()
                addAfterBroadcast.put(key, observers)
            }
            observers.add(actionListener)
            return
        }
        var observables = listeners.get(key)
        if (observables == null) {
            observables = arrayListOf()
            listeners.put(key, observables)
        } else if (observables.contains(actionListener))
            return
        observables.add(actionListener)
    }

    fun removeActionListener(key: Int, actionListener: ActionListener) {
        if (broadcasting != 0) {
            var observers = removeAfterBroadcast[key]
            if (observers == null) {
                observers = ArrayList()
                removeAfterBroadcast.put(key, observers)
            }
            observers.add(actionListener)
            return
        }
        listeners.get(key)?.remove(actionListener)
    }

    fun onAction(key: Int) {
        onAction(key, 0)
    }

    fun onAction(key: Int, action: Int, vararg data: Any?) {
        Application.handler.post {
            broadcasting++
            listeners[key]?.forEach {
                it.onAction(action, *data)
            }
            broadcasting--
            if (broadcasting == 0) {
                if (removeAfterBroadcast.size() != 0) {
                    removeAfterBroadcast.forEach { key, value ->
                        value.forEach {
                            removeActionListener(key, it)
                        }
                    }
                    removeAfterBroadcast.clear()
                }
                if (addAfterBroadcast.size() != 0) {
                    addAfterBroadcast.forEach { key, value ->
                        value.forEach {
                            addActionListener(key, it)
                        }
                    }
                    addAfterBroadcast.clear()
                }
            }
        }
    }

    fun runOnUIThread(runnable: Runnable) {
        runOnUIThread(runnable, 0)
    }

    fun runOnUIThread(runnable: Runnable, delay: Long) {
        if (delay == 0L) {
            Application.handler.post(runnable)
        } else {
            Application.handler.postDelayed(runnable, delay)
        }
    }

    fun cancelRunOnUIThread(runnable: Runnable) {
        Application.handler.removeCallbacks(runnable)
    }

    fun generateFragmentId() = lastFragmentId ++

    fun requestAdjustResize(activity: Activity?, fragmentId: Int) {
        if (activity == null || isTablet()) {
            return
        }
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        adjustOwnerId= fragmentId
    }

    fun requestAdjustNothing(activity: Activity?, fragmentId: Int) {
        if (activity == null || isTablet()) {
            return
        }
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        adjustOwnerId = fragmentId
    }

    fun setAdjustResizeToNothing(activity: Activity?, fragmentId: Int) {
        if (activity == null || isTablet()) {
            return
        }
        if (adjustOwnerId == 0 || adjustOwnerId == fragmentId) {
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
    }

    fun removeAdjustResize(activity: Activity?, fragmentId: Int) {
        if (activity == null || isTablet()) {
            return
        }
        if (adjustOwnerId == fragmentId) {
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
    }

    fun setEnabledFull(view: View, isEnabled: Boolean){
        view.isEnabled = isEnabled
        if (view is ViewGroup)
            view.children.forEach {
                setEnabledFull(it, isEnabled)
            }
    }

    fun isKeyguardSecure(): Boolean {
        val km =
            Application.context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isKeyguardSecure
    }

    fun isActivityRunning(activity: Activity?): Boolean {
        if (activity == null) {
            return false
        }

        return !activity.isDestroyed && !activity.isFinishing
    }

    fun findActivity(context: Context?): Activity? {
        if (context is Activity) {
            return context
        }
        if (context is ContextWrapper) {
            return findActivity(context.baseContext)
        }
        return null
    }

    fun isSafeToShow(context: Context?): Boolean {
        val activity: Activity? = findActivity(context)
        if (activity == null) return true
        return isActivityRunning(activity)
    }

    fun getShadowHeight(): Int {
        if (density >= 4.0f) {
            return 3
        } else if (density >= 2.0f) {
            return 2
        } else {
            return 1
        }
    }

    fun shakeView(view: View?, x: Float, num: Int) {
        if (view == null) {
            return
        }
        if (num == 6) {
            view.translationX = 0f
            return
        }
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            ObjectAnimator.ofFloat(
                view,
                "translationX",
                dp(x)
            )
        )
        animatorSet.duration = 50
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                shakeView(view, if (num == 5) 0f else -x, num + 1)
            }
        })
        animatorSet.start()
    }

    fun updateImageViewImageAnimated(imageView: ImageView, newIcon: Int) {
        updateImageViewImageAnimated(
            imageView,
            ContextCompat.getDrawable(imageView.context, newIcon)
        )
    }

    fun updateImageViewImageAnimated(imageView: ImageView, newIcon: Drawable?) {
        if (imageView.drawable === newIcon) {
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(150)
        val changed = AtomicBoolean()
        animator.addUpdateListener { animation: ValueAnimator ->
            val `val` = animation.animatedValue as Float
            val scale = 0.5f + Math.abs(`val` - 0.5f)
            imageView.scaleX = scale
            imageView.scaleY = scale
            if (`val` >= 0.5f && !changed.get()) {
                changed.set(true)
                imageView.setImageDrawable(newIcon)
            }
        }
        animator.start()
    }

    fun updateViewVisibilityAnimated(view: View?, show: Boolean) {
        updateViewVisibilityAnimated(view, show, 1f, true)
    }

    fun updateViewVisibilityAnimated(
        view: View?,
        show: Boolean,
        scaleFactor: Float,
        animated: Boolean
    ) {
        var animated = animated
        if (view == null) {
            return
        }
        if (view.parent == null) {
            animated = false
        }
        if (!animated) {
            view.animate().setListener(null).cancel()
            view.visibility = if (show) View.VISIBLE else View.GONE
            view.tag = if (show) 1 else null
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
        } else if (show && view.tag == null) {
            view.animate().setListener(null).cancel()
            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                view.alpha = 0f
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
            }
            view.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(150).start()
            view.tag = 1
        } else if (!show && view.tag != null) {
            view.animate().setListener(null).cancel()
            view.animate().alpha(0f).scaleY(scaleFactor).scaleX(scaleFactor)
                .setListener(HideViewAfterAnimation(view)).setDuration(150).start()
            view.tag = null
        }
    }

    fun updateViewShow(view: View?, show: Boolean) {
        updateViewShow(view, show, true, true)
    }

    fun updateViewShow(view: View?, show: Boolean, scale: Boolean, animated: Boolean) {
        updateViewShow(view, show, scale, 0f, animated, null)
    }

    fun updateViewShow(
        view: View?,
        show: Boolean,
        scale: Boolean,
        animated: Boolean,
        onDone: Runnable?
    ) {
        updateViewShow(view, show, scale, 0f, animated, onDone)
    }

    fun updateViewShow(
        view: View?,
        show: Boolean,
        scale: Boolean,
        translate: Float,
        animated: Boolean,
        onDone: Runnable?
    ) {
        var animated = animated
        if (view == null) {
            return
        }
        if (view.parent == null) {
            animated = false
        }
        view.animate().setListener(null).cancel()
        if (!animated) {
            view.visibility = if (show) View.VISIBLE else View.GONE
            view.tag = if (show) 1 else null
            view.alpha = 1f
            view.scaleX = if (scale && !show) 0f else 1f
            view.scaleY = if (scale && !show) 0f else 1f
            if (translate != 0f) {
                view.translationY = (if (show) 0f else dp(-16f)) * translate
            }
            onDone?.run()
        } else if (show) {
            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                view.alpha = 0f
                view.scaleX = if (scale) 0f else 1f
                view.scaleY = if (scale) 0f else 1f
                if (translate != 0f) {
                    view.translationY = dp(-16f) * translate
                }
            }
            var animate = view.animate()
            animate = animate.alpha(1f).scaleY(1f).scaleX(1f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(340)
                .withEndAction(onDone)
            if (translate != 0f) {
                animate.translationY(0f)
            }
            animate.start()
        } else {
            var animate = view.animate()
            animate = animate.alpha(0f).scaleY(if (scale) 0f else 1f)
                .scaleX(if (scale) 0f else 1f).setListener(HideViewAfterAnimation(view))
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(340)
                .withEndAction(onDone)
            if (translate != 0f) {
                animate.translationY(dp(-16f) * translate)
            }
            animate.start()
        }
    }

    fun snapshotView(v: View, height: Int = v.height): Bitmap {
        val bm = Bitmap.createBitmap(v.width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        v.draw(canvas)
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        snapshotTextureViews(loc[0], loc[1], loc, canvas, v)
        return bm
    }

    private fun snapshotTextureViews(
        rootX: Int,
        rootY: Int,
        loc: IntArray,
        canvas: Canvas,
        v: View
    ) {
        if (v is TextureView) {
            val tv = v
            tv.getLocationInWindow(loc)
            val textureSnapshot = tv.bitmap
            if (textureSnapshot != null) {
                canvas.save()
                canvas.drawBitmap(
                    textureSnapshot,
                    (loc[0] - rootX).toFloat(),
                    (loc[1] - rootY).toFloat(),
                    null
                )
                canvas.restore()
                textureSnapshot.recycle()
            }
        }
        if (v is ViewGroup) {
            val vg = v
            for (i in 0 until vg.childCount) {
                snapshotTextureViews(rootX, rootY, loc, canvas, vg.getChildAt(i))
            }
        }
    }

    fun removeFromParent(child: View?) {
        if (child != null && child.parent != null) {
            (child.parent as ViewGroup).removeView(child)
        }
    }


    fun lerp(ab: FloatArray, f: Float): Float {
        return lerp(ab[0], ab[1], f)
    }

    fun lerp(a: Float, b: Float, f: Float): Float {
        return a + f * (b - a)
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String?): String? {
        try {
            val props = Class.forName("android.os.SystemProperties")
            return props.getMethod("get", String::class.java).invoke(null, key) as String
        } catch (ignore: java.lang.Exception) {
        }
        return null
    }

    fun getOffsetColor(color1: Int, color2: Int, offset: Float, alpha: Float): Int {
        val rF = Color.red(color2)
        val gF = Color.green(color2)
        val bF = Color.blue(color2)
        val aF = Color.alpha(color2)
        val rS = Color.red(color1)
        val gS = Color.green(color1)
        val bS = Color.blue(color1)
        val aS = Color.alpha(color1)
        return Color.argb(
            ((aS + (aF - aS) * offset) * alpha).toInt(),
            (rS + (rF - rS) * offset).toInt(),
            (gS + (gF - gS) * offset).toInt(),
            (bS + (bF - bS) * offset).toInt()
        )
    }

    fun getTypeface(assetPath: String): Typeface? {
        synchronized(typefaceCache) {
            if (!typefaceCache.containsKey(assetPath)) {
                try {
                    val t = if (Build.VERSION.SDK_INT >= 26) {
                        val builder = Typeface.Builder(Application.context.assets, assetPath)
                        if (assetPath.contains("medium"))
                            builder.setWeight(700)
                        if (assetPath.contains("italic"))
                            builder.setItalic(true)
                        builder.build()
                    } else {
                        Typeface.createFromAsset(Application.context.assets, assetPath)
                    }
                    typefaceCache[assetPath] = t
                } catch (e: java.lang.Exception) {
//                    if (BuildVars.LOGS_ENABLED) {
//                        FileLog.e("Could not get typeface '" + assetPath + "' because " + e.message)
//                    }
                    return null
                }
            }
            return typefaceCache[assetPath]
        }
    }

    fun bold(fontMedium: String = "fonts/rmedium.ttf"): Typeface? {
        if (mediumTypeface == null) {
            // so system Roboto with 500 weight doesn't support Hebrew (but 700 Roboto does)
            // there must be a way to take system font 500 and fallback it with system font 700
            // I haven't found the way, even through private API :(
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                mediumTypeface = Typeface.create(null, 500, false);
//
//                final String text = "Sample text";
//                final TextPaint normalPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
//                final TextPaint mediumPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
//                mediumPaint.setTypeface(mediumTypeface);
//                if (Math.abs(normalPaint.measureText(text) - mediumPaint.measureText(text)) < 0.1f) {
//                    mediumTypeface = Typeface.create(null, 700, false);
//                }
//            }
            if (mediumTypeface == null) {
                mediumTypeface = getTypeface(fontMedium)
            }
        }
        return mediumTypeface
    }

}