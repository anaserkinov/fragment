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
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.ailnor.core.Theme
import com.ailnor.core.Utilities

abstract class Fragment(arguments: Bundle? = null) : LifecycleOwner {

    interface LifecycleCallback {
        fun onChange(state: Lifecycle.Event)
    }

    var viewLifecycleOwner: LifecycleOwner
        private set
    private lateinit var viewLifecycleRegistry: LifecycleRegistry
    private var lifecycleRegistry: LifecycleRegistry

    var lifecycleCallback: LifecycleCallback? = null

    // make protected after update bottom sheet
    var arguments: Bundle? = null
        private set

    private var isFinished = false
        set(value) {
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
    var popup = false
    var dialog = false
    var parentFragmentId = -1

    var backgroundColor = Theme.white

    val myIndex: Int
        get() = parentLayout!!.indexOf(this)

    fun fragmentsCount() = parentLayout!!.fragmentsCount
    fun fragmentsCountInAnimation() = parentLayout!!.fragmentCountInAnimation

    val context: Context
        get() = parentLayout!!.context

    var parentLayout: FragmentContainer? = null
        set(value) {
            if (value != field) {
                field = value
                if (field != null)
                    onAttackToContext(context)
                if (savedView != null) {
                    val parent = savedView!!.parent as? ViewGroup
                    if (parent != null) {
                        try {
                            onRemoveFromParent()
                            parent.removeViewInLayout(savedView)
                        } catch (_: Exception) {

                        }
                    }
                    if (parentLayout != null && savedView?.context != parentLayout!!.context)
                        savedView = null
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
    public var isPaused = true
        set(value) {
            field = value
            if (isPaused) {
                if (savedView != null) {
                    viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
            } else if (savedView != null) {
                if (viewLifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
                    viewLifecycleOwner = LifecycleOwner {
                        return@LifecycleOwner viewLifecycleRegistry
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

    var visibleDialog: DialogFragment? = null
    protected var inTransitionAnimation = false
    protected var fragmentBeginToShow = false

    init {
        this.arguments = arguments
        viewLifecycleOwner = LifecycleOwner {
            return@LifecycleOwner viewLifecycleRegistry
        }
        viewLifecycleRegistry = LifecycleRegistry(viewLifecycleOwner)

        lifecycleRegistry = LifecycleRegistry(this)

        fragmentId = Utilities.generateFragmentId()
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    fun createView(context: Context): View {
        savedView = onCreateView(context)
        onViewCreated()
        return savedView!!
    }

    protected abstract fun onCreateView(context: Context): View

    open fun onViewCreated() {

    }

    protected open fun parseArguments(arguments: Bundle){

    }

    open fun finishFragment(animated: Boolean) {
        if (isFinishing || isFinished || parentLayout == null)
            return
        isFinishing = true
        parentLayout!!.closeLastFragment(this, animated)
    }

    open fun finishFragmentById(animated: Boolean) {
        if (isFinishing || isFinished || parentLayout == null)
            return
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
        isFinished = true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
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
        return ActionBar(context)
    }

    open fun setUpActionBar(actionBar: ActionBar) {

    }

    open fun onConfigurationChanged(newConfig: Configuration?) {}
    open fun onOrientationChanged() {}

    open fun onStart() {}

    open fun onResume() {}
    open fun onPause() {

    }

    open fun onPreResume() {}
    open fun onPrePause() {}

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
        try {
            if (visibleDialog != null && !visibleDialog!!.isPaused && dismissDialogOnPause(visibleDialog!!)) {
                visibleDialog!!.finishFragment(false)
                visibleDialog = null
            }
        } catch (_: Exception) {

        }
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

    open fun onRemoveFromParent() {}

    fun presentFragment(
        fragment: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false,
        uniqueWith: Int = -1
    ): Boolean?{
        return parentLayout?.presentFragmentGroup(fragment, fragmentId, removeLast, forceWithoutAnimation, uniqueWith)
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

    fun presentFragmentAsSheet(fragment: Fragment, fullScreen: Boolean = false) {
        parentLayout?.presentFragmentAsSheet(fragment, fragmentId, fullScreen)
    }

    fun presentFragmentAsPopup(
        fragment: Fragment,
        uniqueWith: Int = -1
    ): Boolean?{
        return parentLayout?.presentFragmentAsPopUp(fragment, uniqueWith)
    }


    open fun onReceive(vararg data: Any?) {

    }

    protected fun sendParent(vararg data: Any?){
        parentLayout?.send(parentFragmentId, *data)
    }

    protected fun send(toRight: Boolean, vararg data: Any?) {
        parentLayout?.send(toRight, *data)
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
            visibleDialog!!.finishFragment(false)
            visibleDialog = null
        } catch (_: java.lang.Exception) {

        }
    }

    open fun dismissDialogOnPause(dialog: DialogFragment): Boolean {
        return true
    }

    open fun canBeginSlide(): Boolean {
        return true
    }

    open fun onBeginSlide() {
        try {
            if (visibleDialog != null && !visibleDialog!!.isPaused) {
                visibleDialog!!.finishFragment(false)
                visibleDialog = null
            }
        } catch (_: Exception) {

        }
    }

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

    open fun showDialog(
        dialog: DialogFragment?,
        allowInTransition: Boolean = false
    ): DialogFragment? {
        if (dialog == null || parentLayout == null  || parentLayout!!.startedTracking || !allowInTransition && parentLayout!!.inAnimation) {
            return null
        }
        try {
            if (visibleDialog != null) {
                visibleDialog!!.finishFragment(false)
                visibleDialog = null
            }
        } catch (_: Exception) {

        }
        try {
            visibleDialog = dialog
            presentFragmentAsPopup(visibleDialog!!)
            return visibleDialog
        } catch (_: Exception) {
        }
        return null
    }


    open fun onCreateOptionsMenu() {}

    open fun onCreateOptionsMenu(builder: ActionBar.Builder) {

    }

    @CallSuper
    protected open fun onOptionsItemSelected(menuId: Int): Boolean {
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
        intent: Intent?,
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


    fun requestPermissions(
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

    open fun onGetFirstInStack() {}


}
