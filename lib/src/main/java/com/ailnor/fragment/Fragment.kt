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

abstract class Fragment(arguments: Bundle? = null) : LifecycleOwner {

    interface LifecycleCallback {
        fun onChange(state: Lifecycle.Event)
    }

    private abstract class OnPreAttachedListener {
        abstract fun onPreAttached()
    }

    interface AttachedSheet {
        val windowView: View?
        val isShown: Boolean
        fun dismiss()
        fun dismiss(tabs: Boolean) {
            dismiss()
        }

        fun release()

        val isFullyVisible: Boolean

        fun attachedToParent(): Boolean

        fun onAttachedBackPressed(): Boolean
        fun showDialog(dialog: Dialog?): Boolean

        fun setKeyboardHeightFromParent(keyboardHeight: Int)

        val isAttachedLightStatusBar: Boolean
        fun getNavigationBarColor(color: Int): Int

        fun setOnDismissListener(onDismiss: Runnable?)

        fun setLastVisible(lastVisible: Boolean) {}
    }

    interface AttachedSheetWindow

    class BottomSheetParams {
        var transitionFromLeft: Boolean = false
        var allowNestedScroll: Boolean = false
        var onDismiss: Runnable? = null
        var onOpenAnimationFinished: Runnable? = null
        var onPreFinished: Runnable? = null
        var occupyNavigationBar: Boolean = false
    }

    var viewLifecycleOwner: LifecycleOwner
        private set
    private var viewLifecycleRegistry: LifecycleRegistry
    private var lifecycleRegistry: LifecycleRegistry

    private var lifecycleCallbacks = mutableSetOf<LifecycleCallback>()

    // make protected after update bottom sheet
    var arguments: Bundle? = null
        private set
    private var visibleDialog: Dialog? = null
    private var showDialogRequested = false

    @set:JvmName("setParentDialogLocal")
    protected var parentDialog: Dialog? = null

    var dialog: Dialog? = null

    private var tempDismiss = false

    var isFinished = false
        private set(value) {
            field = value
            if (
                field &&
                savedView != null &&
                viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
            )
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    var isFinishing = false
    private var isFullyVisible = false
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

    var sheetsStack: ArrayList<AttachedSheet>? = null

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
                    if (field != null && savedView?.context != field!!.context) savedView =
                        null
                }
                if (actionBar != null) {
                    val differentParent = field != null && field!!.context !== requiredActionBar.context
                    if (requiredActionBar.shouldAddToContainer || differentParent) {
                        val parent = requiredActionBar.parent as? ViewGroup
                        if (parent != null) {
                            try {
                                parent.removeViewInLayout(actionBar)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    if (differentParent) {
                        actionBar = null
                    }
                }
                if (hasToolbar && field != null && actionBar == null) {
                    actionBar = createActionBar(field!!.context)
                    setUpActionBar(requiredActionBar)
                    requiredActionBar.actionListener = object : ActionBar.ActionListener {
                        override fun onAction(action: Int) {
                            parentLayout ?: return
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
            if (value == null) {
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            } else {
                if (viewLifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
                    viewLifecycleOwner = object : LifecycleOwner {
                        override val lifecycle: Lifecycle
                            get() = viewLifecycleRegistry
                    }
                    viewLifecycleRegistry = LifecycleRegistry(viewLifecycleOwner)
                }
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            }
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

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

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

    protected open fun parseArguments(arguments: Bundle) {

    }

    open fun onAttackToContext(context: Context) {}

    open fun createActionBar(context: Context): ActionBar {
        return ActionBar(context).also { it.fitsSystemWindows = true }
    }

    open fun setUpActionBar(actionBar: ActionBar) {}
    open fun onCreateOptionsMenu() {}
    open fun onCreateOptionsMenu(builder: ActionBar.Builder) {}
    @CallSuper
    open fun onOptionsItemSelected(menuId: Int): Boolean { return false }

    fun createView(context: Context): View {
        savedView = onCreateView(context)
        onViewCreated()
        return savedView!!
    }

    // for views with default false isClickable, make sure you set it true yourself
    protected abstract fun onCreateView(context: Context): View

    open fun onViewCreated() {}

    open fun onStart() {}

    open fun onPreResume() {}

    open fun onResume() {
        if (showDialogRequested) {
            showDialogRequested = false
            try {
                visibleDialog?.show()
            } catch (_: Exception) {
            }
        }
    }

    open fun onGetFirstInStack() {
        if (showDialogRequested) {
            showDialogRequested = false
            try {
                visibleDialog?.show()
            } catch (_: Exception) { }
        }
    }

    open fun onPrePause() {
        if (viewLifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    open fun onPause() {
        try {
            if (visibleDialog?.isShowing == true && dismissDialogOnPause(visibleDialog!!)) {
                tempDismiss = true
                showDialogRequested = true
                visibleDialog!!.dismiss()
            }
        } catch (_: Exception) {
        }
    }

    open fun clearViews() {
        (savedView?.parent as? ViewGroup)?.let {
            try {
                onRemoveFromParent()
                it.removeViewInLayout(savedView)
            } catch (_: Exception) { }
        }

        (actionBar?.parent as? ViewGroup)?.let {
            try {
                it.removeViewInLayout(actionBar)
            } catch (_: Exception) {
            }
        }

        savedView = null
        actionBar = null
        parentLayout = null
    }

    open fun onRemoveFromParent() {
        if (sheetsStack == null || sheetsStack!!.isEmpty()) return
        updateSheetsVisibility()
    }

    open fun onFragmentDestroy() {
        if (sheetsStack != null) {
            for (i in sheetsStack!!.indices.reversed()) {
                val sheet = sheetsStack!![i]
                sheet.setLastVisible(false)
                sheet.dismiss(true)
                sheetsStack!!.removeAt(i)
            }
        }

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED))
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        savedView = null
        isFinished = true
        lifecycleCallbacks.forEach {
            it.onChange(Lifecycle.Event.ON_DESTROY)
        }
    }

    open fun reCreate() {
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleCallbacks.forEach {
            it.onChange(Lifecycle.Event.ON_CREATE)
        }
        isFinished = false
        isFinishing = false
        isStarted = false
        isPaused = true
    }


    fun resume() {
        if (savedView == null) return
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

    fun addLifecycleCallback(callback: LifecycleCallback) = lifecycleCallbacks.add(callback)
    fun removeLifecycleCallback(callback: LifecycleCallback) = lifecycleCallbacks.remove(callback)

    fun fragmentsCount() = parentLayout!!.fragmentsCount
    fun fragmentsCountInAnimation() = parentLayout!!.fragmentCountInAnimation

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

    fun getLastSheet(): AttachedSheet? {
        if (sheetsStack == null || sheetsStack!!.isEmpty()) return null
        for (i in sheetsStack!!.indices.reversed()) {
            if (sheetsStack!![i].isShown) {
                return sheetsStack!![i]
            }
        }
        return null
    }

    fun hasSheet(): Boolean {
        return sheetsStack != null && !sheetsStack!!.isEmpty()
    }

    fun hasShownSheet(): Boolean {
        if (!hasSheet()) return false
        for (i in sheetsStack!!.indices.reversed()) {
            if (sheetsStack!![i].isShown) {
                return true
            }
        }
        return false
    }

    fun hasSheets(fragment: Fragment?): Boolean {
        if (fragment == null) return false
        if (fragment.hasShownSheet()) return true
        if (fragment.parentLayout !is FragmentContainer) return false
        return false
//        val sheetFragment: Fragment? = (fragment.parentLayout as FragmentContainer).getSheetFragment(false)
//        return sheetFragment != null && sheetFragment.hasShownSheet()
    }

    fun clearSheets() {
        if (sheetsStack == null || sheetsStack!!.isEmpty()) return
        for (i in sheetsStack!!.indices.reversed()) {
            sheetsStack!![i].dismiss(true)
        }
        sheetsStack!!.clear()
    }

    fun closeSheet(): Boolean {
        if (sheetsStack != null) {
            for (i in sheetsStack!!.indices.reversed()) {
                if (sheetsStack!![i].isShown) {
                    return sheetsStack!![i].onAttachedBackPressed()
                }
            }
        }
        return false
    }

    private fun updateSheetsVisibility() {
        if (sheetsStack == null) return
        for (i in sheetsStack!!.indices) {
            val sheet = sheetsStack!![i]
            sheet.setLastVisible(i == sheetsStack!!.size - 1 && isFullyVisible)
        }
    }

    fun attachSheets(parentLayout: FragmentContainer) {
        if (sheetsStack != null) {
            for (i in sheetsStack!!.indices) {
                val sheet: AttachedSheet? = sheetsStack!![i]
                if (sheet != null && sheet.attachedToParent()) {
                    AndroidUtilities.removeFromParent(sheet.windowView)
                    parentLayout.addView(sheet.windowView)
                }
            }
        }
    }

    fun detachSheets() {
        if (sheetsStack != null) {
            for (i in sheetsStack!!.indices) {
                val sheet: AttachedSheet? = sheetsStack!![i]
                if (sheet != null && sheet.attachedToParent()) {
                    AndroidUtilities.removeFromParent(sheet.windowView)
                }
            }
        }
    }

    fun setKeyboardHeightFromParent(keyboardHeight: Int) {
        if (sheetsStack != null) {
            for (i in sheetsStack!!.indices) {
                val storyViewer: AttachedSheet? = sheetsStack!![i]
                storyViewer?.setKeyboardHeightFromParent(keyboardHeight)
            }
        }
    }

    fun removeSheet(sheet: AttachedSheet?) {
        if (sheetsStack == null) return
        sheetsStack!!.remove(sheet!!)
        updateSheetsVisibility()
    }

    fun addSheet(sheet: AttachedSheet?) {
        if (sheetsStack == null) {
            sheetsStack = java.util.ArrayList<AttachedSheet>()
        }
        sheetsStack!!.add(sheet!!)
        updateSheetsVisibility()
    }

    fun onBottomSheetCreated() {
    }

    open fun onConfigurationChanged(newConfig: Configuration?) {}
    open fun onOrientationChanged() {}

    open fun getLayoutContainer(): FrameLayout? {
        val parent = fragmentView.parent
        if (parent is FrameLayout) {
            return parent
        }
        return null
    }

    fun presentFragment(
        fragment: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1,
        synchronized: Boolean = true
    ) {
        parentLayout?.presentFragmentGroup(
            fragment,
            fragmentId,
            removeLast,
            forceWithoutAnimation,
            uniqueWith,
            synchronized
        )
    }

    fun nextFragment(
        fragment: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false
    ) {
        parentLayout?.nextFragment(fragment, fragmentId, removeLast, forceWithoutAnimation)
    }

    fun nextFragmentInnerGroup(
        fragment: Fragment,
        forceWithoutAnimation: Boolean = false
    ) {
        parentLayout?.nextFragmentInnerGroup(fragment, fragmentId, forceWithoutAnimation)
    }

    fun presentFragmentAsSheet(
        fragment: Fragment,
        parentFragmentId: Int = fragmentId,
        fullScreen: Boolean = false,
        height: Int = MATCH_PARENT
    ) {
        parentLayout?.presentFragmentAsSheet(fragment, parentFragmentId, fullScreen, height)
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
        if (parentFragmentId != -1)
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
        if (visibleDialog == null)
            return
        try {
            visibleDialog!!.dismiss()
            visibleDialog = null
        } catch (_: Exception) { }
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
//        } catch (e: Exception) {
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

        if (sheetsStack != null) {
            for (i in sheetsStack!!.indices.reversed()) {
                if (sheetsStack!![i].isShown) {
                    if (sheetsStack!![i].showDialog(dialog)) {
                        return dialog
                    }
                }
            }
        }

        try {
            visibleDialog?.dismiss()
            visibleDialog = null
        } catch (_: Exception) {
        }
        try {
            visibleDialog = dialog
            visibleDialog!!.setCanceledOnTouchOutside(true)
            visibleDialog!!.setOnShowListener {
                tempDismiss = false
                showDialogRequested = false
                onShowListener?.onShow(it as Dialog)
            }
            visibleDialog!!.setOnDismissListener { dialog1: DialogInterface ->
                onDismissListener?.onDismiss(dialog1)
                onDialogDismiss(dialog1 as Dialog)
                if (dialog1 === visibleDialog && !tempDismiss) {
                    showDialogRequested = false
                    visibleDialog = null
                }
            }
            visibleDialog!!.show()
            return visibleDialog
        } catch (_: Exception) {

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

    open fun onBecomeFullyVisible() {
        isFullyVisible = true
        updateSheetsVisibility()
    }

    fun onBecomeFullyHidden() {
        isFullyVisible = false
        updateSheetsVisibility()
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

}
