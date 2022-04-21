/* 
 * Copyright Erkinjanov Anaskhan, 12/02/2022.
 */

package com.ailnor.fragment

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

abstract class Fragment : LifecycleOwner {

    var viewLifecycleOwner: LifecycleOwner
        private set
    private lateinit var viewLifecycleRegistry: LifecycleRegistry
    private var lifecycleRegistry: LifecycleRegistry

    private var isFinished = false
        set(value) {
            field = value
            if (field && savedView != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(
                    Lifecycle.State.CREATED
                )
            )
                viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    private var finishing = false
    protected var hasToolbar = true
    var groupId = -1
    var id = -1

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
                        } catch (e: Exception) {

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
                            } catch (e: java.lang.Exception) {
                            }
                        }
                    }
                    if (differentParent) {
                        actionBar = null
                    }
                }
                if (hasToolbar && parentLayout != null && actionBar == null) {
                    actionBar = createActionBar(parentLayout!!.context)
                    requiredActionBar.actionListener = object: ActionBar.ActionListener{
                        override fun onAction(action: Int) {
                            onOptionsItemSelected(action)
                        }
                    }
                    onCreateOptionsMenu()
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
    val screenView: View
        get() = savedView!!
    protected var isPaused = true
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
    protected var inTransitionAnimation = false
    protected var fragmentBeginToShow = false

    init {
        viewLifecycleOwner = LifecycleOwner {
            return@LifecycleOwner viewLifecycleRegistry
        }
        viewLifecycleRegistry = LifecycleRegistry(viewLifecycleOwner)

        lifecycleRegistry = LifecycleRegistry(this)
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    fun createView(context: Context): View {
        savedView = onCreateView(context)
        return savedView!!
    }

    protected abstract fun onCreateView(context: Context): View

    open fun onViewCreated(){

    }

    open fun finishFragment(animated: Boolean) {
        if (isFinished || parentLayout == null) {
            return
        }
        finishing = true
        parentLayout!!.closeLastScreen(animated)
    }

    open fun removeSelfFromStack() {
        if (isFinished || parentLayout == null) {
            return
        }
        parentLayout!!.removeScreenFromStack(this)
    }

    protected open fun isFinishing(): Boolean {
        return finishing
    }

    open fun onAttackToContext(context: Context){}

    @CallSuper
    open fun onScreenCreate(): Boolean {
        lifecycleRegistry.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP)
                    savedView?.cancelPendingInputEvents()
            }
        })
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        return true
    }

    open fun onScreenDestroy() {
        isFinished = true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    open fun createActionBar(context: Context): ActionBar {
        return ActionBar(context)
    }

    protected open fun setActionBarNavigationType(type: Int){
        requiredActionBar.setTitle(type)
    }

    open fun onConfigurationChanged(newConfig: Configuration?) {}
    open fun onOrientationChanged() {}

    open fun onResume() {}
    open fun onPause() {}

    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
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
                } catch (e: Exception) {
                }
            }
            savedView = null
        }
        if (actionBar != null) {
            val parent = actionBar?.parent as? ViewGroup
            if (parent != null) {
                try {
                    parent.removeViewInLayout(actionBar)
                } catch (e: Exception) {
                }
            }
            actionBar = null
        }
        parentLayout = null
    }

    internal open fun onRemoveFromParent() {}

    fun presentScreen(
        screen: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false
    ) {
        parentLayout?.presentScreen(screen, removeLast, forceWithoutAnimation)
    }

    fun nextScreen(
        screen: Fragment,
        removeLast: Boolean = false,
        forceWithoutAnimation: Boolean = false
    ) {
        parentLayout?.nextScreen(screen, removeLast)
    }

    fun presentScreenAsSheet(screen: Fragment) {
        parentLayout?.presentAsSheet(screen)
    }

    open fun onReceive(vararg data: Any?) {

    }

    protected fun send(toRight: Boolean, vararg data: Any?) {
        parentLayout?.send(toRight, *data)
    }

    protected fun send(step: Int, vararg data: Any?) {
        parentLayout?.send(this, step, *data)
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
