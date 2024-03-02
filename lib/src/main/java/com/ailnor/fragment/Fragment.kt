/* 
 * Copyright Erkinjanov Anaskhan, 12/02/2022.
 */

package com.ailnor.fragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.arch.core.util.Function
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.ailnor.core.AndroidUtilities
import com.ailnor.core.MATCH_PARENT
import com.ailnor.core.Theme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// TODO It's preferable to invoke the onPause method of the closing fragment before the onResume method of the opening fragment

abstract class Fragment(arguments: Bundle? = null) : LifecycleOwner {

    interface LifecycleCallback {
        fun onChange(state: Lifecycle.Event)
    }

    private abstract class OnPreAttachedListener {
        abstract fun onPreAttached()
    }

    var viewLifecycleOwner: LifecycleOwner
        private set
    private lateinit var viewLifecycleRegistry: LifecycleRegistry
    private var lifecycleRegistry: LifecycleRegistry

    var lifecycleCallback: LifecycleCallback? = null

    // make protected after update bottom sheet
    var arguments: Bundle? = null
        private set
    private var visibleDialog: Dialog? = null

    @set:JvmName("setParentDialogLocal")
    protected var parentDialog: Dialog? = null

    var dialog: Dialog? = null

    private var tempDismiss = false

    var isFinished = false
        private set(value) {
            field = value
            if (field && savedView != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(
                    Lifecycle.State.CREATED
                )
            )
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    var isFinishing = false
    protected var hasToolbar = true
    var groupId = -1
    var innerGroupId = -1
    var fragmentId = -1
    var isPopup = false
    var isDialog = false
    var parentFragmentId = -1

    var backgroundColor = Theme.white

    val myIndex: Int
        get() = parentLayout!!.indexOf(this)

    private var mOnPreAttachedListeners: ArrayList<OnPreAttachedListener>? = null
    private val mNextLocalRequestCode by lazy { AtomicInteger() }


    fun fragmentsCount() = parentLayout!!.fragmentsCount
    fun fragmentsCountInAnimation() = parentLayout!!.fragmentCountInAnimation

    val context: Context
        get() = parentLayout!!.context

    var parentLayout: FragmentContainer? = null
        set(value) {
            if (value != field) {
                field = value
                if (field != null) {
                    onAttackToContext(context)
                    mOnPreAttachedListeners?.forEach { it.onPreAttached() }
                }
                if (savedView != null) {
                    val parent = savedView!!.parent as? ViewGroup
                    if (parent != null) {
                        try {
                            onRemoveFromParent()
                            parent.removeViewInLayout(savedView)
                        } catch (_: Exception) {

                        }
                    }
                    if (parentLayout != null && savedView?.context != parentLayout!!.context) savedView =
                        null
                }
                if (actionBar != null) {
                    val differentParent =
                        parentLayout != null && parentLayout!!.context !== requiredActionBar.context
                    if (requiredActionBar.shouldAddToContainer || differentParent) {
                        val parent = requiredActionBar.parent as? ViewGroup
                        if (parent != null) {
                            try {
                                parent.removeViewInLayout(actionBar)
                            } catch (_: java.lang.Exception) {
                            }
                        }
                    }
                    if (differentParent) {
                        actionBar = null
                    }
                }
                if (hasToolbar && parentLayout != null && actionBar == null) {
                    actionBar = createActionBar(parentLayout!!.context)
                    setUpActionBar(requiredActionBar)
                    requiredActionBar.actionListener = object : ActionBar.ActionListener {
                        override fun onAction(action: Int) {
                            onOptionsItemSelected(action)
                        }
                    }
                    onCreateOptionsMenu()
                    onCreateOptionsMenu(ActionBar.Builder.init(actionBar!!))
                }
            }
        }
    var savedView: View? = null
        private set(value) {
            field = value
            if (value == null)
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            else
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    val fragmentView: View
        get() = savedView!!
    protected var isStarted = false
    var isPaused = true
        set(value) {
            field = value
            if (isPaused) {
                if (savedView != null) {
                    if (viewLifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                        viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                        viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                        viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    }
                }
            } else if (savedView != null) {
                if (viewLifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
                    viewLifecycleOwner = object : LifecycleOwner {
                        override val lifecycle: Lifecycle
                            get() = viewLifecycleRegistry
                    }
                    viewLifecycleRegistry = LifecycleRegistry(viewLifecycleOwner)
                }
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }

    var actionBar: ActionBar? = null
        protected set
    val requiredActionBar: ActionBar
        get() = actionBar!!

    protected var inTransitionAnimation = false
    protected var fragmentBeginToShow = false

    init {
        this.arguments = arguments
        viewLifecycleOwner = object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = viewLifecycleRegistry
        }
        viewLifecycleRegistry = LifecycleRegistry(viewLifecycleOwner)

        lifecycleRegistry = LifecycleRegistry(this)

        fragmentId = AndroidUtilities.generateFragmentId()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun createView(context: Context): View {
        savedView = onCreateView(context)
        onViewCreated()
        return savedView!!
    }

    // for views with default false isClickable, make sure you set it true yourself
    protected abstract fun onCreateView(context: Context): View

    open fun onViewCreated() {

    }

    protected open fun parseArguments(arguments: Bundle) {

    }

    open fun finishFragment(animated: Boolean, synchronized: Boolean = true) {
        if (isFinishing || isFinished || parentLayout == null)
            return
        if (parentDialog != null) {
            parentDialog!!.dismiss()
            return
        }
        isFinishing = true
        parentLayout!!.closeLastFragment(this, animated, synchronized)
    }

    open fun finishFragmentById(animated: Boolean) {
        if (isFinishing || isFinished || parentLayout == null)
            return
        if (parentDialog != null) {
            parentDialog!!.dismiss()
            return
        }
        isFinishing = true
        parentLayout!!.closeFragment(fragmentId, animated)
    }

    fun popScreensFromStack(count: Int, removeLatest: Boolean) {
        parentLayout?.popScreensFromStack(count, removeLatest)
    }

    open fun removeSelfFromStack() {
        if (isFinished || parentLayout == null) {
            return
        }
        if (parentDialog != null) {
            parentDialog!!.dismiss()
            return
        }
        parentLayout!!.removeScreenFromStack(this)
    }

    open fun onAttackToContext(context: Context) {}

    @CallSuper
    open fun onFragmentCreate(): Boolean {
        lifecycleRegistry.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP)
                    savedView?.cancelPendingInputEvents()
            }
        })
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        if (arguments != null)
            parseArguments(arguments!!)
        return true
    }

    open fun onFragmentDestroy() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED))
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        savedView = null
        isFinished = true
        lifecycleCallback?.onChange(Lifecycle.Event.ON_DESTROY)
    }

    open fun reCreate() {
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleCallback?.onChange(Lifecycle.Event.ON_CREATE)
        isFinished = false
        isFinishing = false
        isStarted = false
        isPaused = true
    }

    open fun createActionBar(context: Context): ActionBar {
        return ActionBar(context).also { it.fitsSystemWindows = true }
    }

    open fun setUpActionBar(actionBar: ActionBar) {

    }

    open fun onConfigurationChanged(newConfig: Configuration?) {}
    open fun onOrientationChanged() {}

    open fun onStart() {}

    open fun onResume() {}
    open fun onPause() {
        try {
            if (visibleDialog != null && visibleDialog!!.isShowing && dismissDialogOnPause(
                    visibleDialog!!
                )
            ) {
                tempDismiss = true
                visibleDialog!!.dismiss()
            }
        } catch (e: java.lang.Exception) {
        }
    }

    open fun onPreResume() {}
    open fun onPrePause() {
        if (viewLifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (!isStarted) {
            isStarted = true
            onStart()
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        isPaused = false
        onResume()
    }

    fun pause() {
        if (isPaused)
            return
        onPause()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        isPaused = true
    }

    open fun clearViews() {
        if (savedView != null) {
            val parent = savedView?.parent as? ViewGroup
            if (parent != null) {
                try {
                    onRemoveFromParent()
                    parent.removeViewInLayout(savedView)
                } catch (_: Exception) {
                }
            }
            savedView = null
        }
        if (actionBar != null) {
            val parent = actionBar?.parent as? ViewGroup
            if (parent != null) {
                try {
                    parent.removeViewInLayout(actionBar)
                } catch (_: Exception) {
                }
            }
            actionBar = null
        }
        parentLayout = null
    }

    open fun getLayoutContainer(): FrameLayout? {
        val parent = fragmentView.parent
        if (parent is FrameLayout) {
            return parent
        }
        return null
    }

    open fun onRemoveFromParent() {}

    fun presentFragment(
        fragment: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1
    ) {
        parentLayout?.presentFragmentGroup(
            fragment,
            fragmentId,
            removeLast,
            forceWithoutAnimation,
            uniqueWith
        )
    }


    fun nextFragment(
        fragment: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false
    ) {
        parentLayout?.nextFragment(fragment, fragmentId, removeLast, forceWithoutAnimation)
    }

    fun nextScreenInnerGroup(
        fragment: Fragment,
        forceWithoutAnimation: Boolean = false
    ) {
        parentLayout?.nextFragmentInnerGroup(fragment, fragmentId, forceWithoutAnimation)
    }

    fun presentFragmentAsSheet(
        fragment: Fragment,
        fullScreen: Boolean = false,
        height: Int = MATCH_PARENT
    ) {
        parentLayout?.presentFragmentAsSheet(fragment, fragmentId, fullScreen, height)
    }

    fun presentFragmentAsPopup(
        fragment: Fragment,
        uniqueWith: Int = -1
    ): Boolean? {
        return parentLayout?.presentFragmentAsPopUp(fragment, uniqueWith)
    }

    open fun onReceive(vararg data: Any?) {

    }

    protected fun sendParent(vararg data: Any?) {
        parentLayout?.send(parentFragmentId, *data)
    }

    protected fun send(toRight: Boolean, vararg data: Any?) {
        parentLayout?.send(toRight, *data)
    }

    protected fun sendTo(shift: Int, vararg data: Any?) {
        parentLayout?.sendTo(shift, *data)
    }

    open fun saveSelfArgs(args: Bundle?) {}

    open fun restoreSelfArgs(args: Bundle?) {}

    open fun onBackPressed(): Boolean {
        return actionBar?.onBackPressed() == true
    }

    open fun hideKeyboardOnShow(): Boolean {
        return true
    }

    open fun getParentActivity(): FragmentActivity? {
        return if (parentLayout != null) {
            parentLayout!!.parentActivity
        } else null
    }

    open fun dismissCurrentDialog() {
        if (visibleDialog == null) {
            return
        }
        try {
            visibleDialog!!.dismiss()
            visibleDialog = null
        } catch (e: java.lang.Exception) {

        }
    }

    open fun isLightStatusBar(): Boolean {
        return false
//        if (hasForceLightStatusBar() && !Theme.getCurrentTheme().isDark()) {
//            return true
//        }
//        val color: Int
//        var key: String? = Theme.key_actionBarDefault
//        if (actionBar != null && actionBar.isActionModeShowed()) {
//            key = Theme.key_actionBarActionModeDefault
//        }
//        color = BitoTheme.getColor(key!!, null, true)
//        return ColorUtils.calculateLuminance(color) > 0.7f
    }

    open fun getVisibleDialog(): Dialog? {
        return visibleDialog
    }

    open fun setVisibleDialog(dialog: Dialog) {
        visibleDialog = dialog
    }

    open fun dismissDialogOnPause(dialog: Dialog): Boolean {
        return true
    }

    open fun canBeginSlide(): Boolean {
        return true
    }

    open fun onBeginSlide() {
//        try {
//            if (visibleDialog != null && visibleDialog!!.isShowing) {
//                visibleDialog!!.dismiss()
//                visibleDialog = null
//            }
//        } catch (e: java.lang.Exception) {
//        }
    }

    open fun showDialog(dialog: Dialog?): Dialog? {
        return showDialog(dialog, false, null)
    }

    open fun showDialog(
        dialog: Dialog?,
        onDismissListener: DialogInterface.OnDismissListener?
    ): Dialog? {
        return showDialog(dialog, false, onDismissListener)
    }

    open fun showDialog(
        dialog: Dialog?,
        allowInTransition: Boolean = false,
        onDismissListener: DialogInterface.OnDismissListener? = null,
        onShowListener: DialogInterface.OnShowListener? = null
    ): Dialog? {
        if (dialog == null || parentLayout == null || parentLayout!!.startedTracking || !allowInTransition && parentLayout!!.inAnimation) {
            return null
        }
        try {
            if (visibleDialog != null) {
                visibleDialog!!.dismiss()
                visibleDialog = null
            }
        } catch (e: java.lang.Exception) {
        }
        try {
            visibleDialog = dialog
            visibleDialog!!.setCanceledOnTouchOutside(true)
            visibleDialog!!.setOnShowListener {
                tempDismiss = false
                onShowListener?.onShow(it as Dialog)
            }
            visibleDialog!!.setOnDismissListener { dialog1: DialogInterface ->
                onDismissListener?.onDismiss(dialog1)
                onDialogDismiss(dialog1 as Dialog)
                if (dialog1 === visibleDialog && !tempDismiss) {
                    visibleDialog = null
                }
            }
            visibleDialog!!.show()
            return visibleDialog
        } catch (e: java.lang.Exception) {

        }
        return null
    }


    open fun showAsSheet(
        fragment: Fragment,
        fullScreen: Boolean = false
    ): Array<FragmentContainer?>? {
        return parentLayout?.showAsSheet(fragment, fullScreen)
    }

    fun setParentDialog(dialog: Dialog) {
        parentDialog = dialog
    }

    protected open fun onDialogDismiss(dialog: Dialog?) {}

    open fun isSwipeBackEnabled(ev: MotionEvent): Boolean {
        return true
    }

    open fun onSlideProgress(isOpen: Boolean, progress: Float) {}

    open fun onTransitionAnimationProgress(isOpen: Boolean, progress: Float) {}

    open fun onTransitionAnimationStart(isOpen: Boolean, backward: Boolean) {
        inTransitionAnimation = true
        if (isOpen) {
            fragmentBeginToShow = true
        }
    }

    open fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
        inTransitionAnimation = false
    }


    open fun onCreateOptionsMenu() {}

    open fun onCreateOptionsMenu(builder: ActionBar.Builder) {

    }

    @CallSuper
    open fun onOptionsItemSelected(menuId: Int): Boolean {
        return false
    }


    open fun onBecomeFullyVisible() {
//        if (ApplicationLoader.applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE).isEnabled) {
//            val actionBar: ActionBar = getActionBar()
//            if (actionBar != null) {
//                val title: String = actionBar.title
//                if (!TextUtils.isEmpty(title)) {
//                    setParentActivityTitle(title)
//                }
//            }
//        }
    }

    fun checkAndRequestPermission(
        permission: String,
        requestCode: Int
    ): Boolean {
        val granted = checkPermission(permission)
        if (!granted)
            requestPermissions(arrayOf(permission), requestCode)
        return granted
    }

    protected fun startActivityForResult(
        intent: Intent,
        requestCode: Int
    ) {
        parentLayout!!.startActivityForResult(intent, requestCode)
    }


    open fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    }

    fun checkPermission(permission: String) =
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED


    open fun requestPermissions(
        permissions: Array<out String>, requestCode: Int
    ) {
        parentLayout?.requestPermissions(permissions, requestCode)
    }

    open fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
    }

    @MainThread
    fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>,
    ): ActivityResultLauncher<I> {
        return prepareCallInternal(
            contract, { getParentActivity()!!.activityResultRegistry }, callback
        )
    }

    @MainThread
    fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        registry: ActivityResultRegistry,
        callback: ActivityResultCallback<O>,
    ): ActivityResultLauncher<I> {
        return prepareCallInternal(
            contract, { registry }, callback
        )
    }

    private fun <I, O> prepareCallInternal(
        contract: ActivityResultContract<I, O>,
        registryProvider: Function<Void?, ActivityResultRegistry>,
        callback: ActivityResultCallback<O>,
    ): ActivityResultLauncher<I> {
        // Throw if attempting to register after the Fragment is CREATED.
        check(lifecycle.currentState <= Lifecycle.State.CREATED) {
            ("Fragment " + this + " is attempting to " + "registerForActivityResult after being created. Fragments must call " + "registerForActivityResult() before they are created (i.e. initialization, " + "onAttach(), or onCreate()).")
        }
        val ref = AtomicReference<ActivityResultLauncher<I>?>()
        // We can't call generateActivityResultKey during initialization of the Fragment
        // since we need to wait for the mWho to be restored from saved instance state
        // so we'll wait until we have all the information needed to register  to actually
        // generate the key and register.
        registerOnPreAttachListener(object : OnPreAttachedListener() {
            override fun onPreAttached() {
                val key: String = generateActivityResultKey()
                val registry = registryProvider.apply(null)
                ref.set(registry.register(key, this@Fragment, contract, callback))
            }
        })
        return object : ActivityResultLauncher<I>() {
            override fun launch(input: I, options: ActivityOptionsCompat?) {
                val delegate = ref.get() ?: throw IllegalStateException(
                    "Operation cannot be started before fragment " + "is in created state"
                )
                delegate.launch(input, options)
            }

            override fun unregister() {
                val delegate = ref.getAndSet(null)
                delegate?.unregister()
            }

            override fun getContract(): ActivityResultContract<I, *> {
                return contract
            }
        }
    }

    private fun registerOnPreAttachListener(callback: OnPreAttachedListener) {
        //If we are already attached, we can register immediately
        if (parentLayout != null) {
            callback.onPreAttached()
        } else {
            // else we need to wait until we are attached
            if (mOnPreAttachedListeners == null) mOnPreAttachedListeners = ArrayList()
            mOnPreAttachedListeners!!.add(callback)
        }
    }

    open fun generateActivityResultKey(): String {
        return "fragment_" + fragmentId + "_rq#" + mNextLocalRequestCode.getAndIncrement()
    }

    open fun onGetFirstInStack() {
        try {
            if (visibleDialog != null) {
                visibleDialog!!.show()
            }
        } catch (e: java.lang.Exception) {
        }
    }


}
