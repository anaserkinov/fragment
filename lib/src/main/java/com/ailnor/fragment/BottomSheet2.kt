/* 
 * Copyright Erkinjanov Anaskhan, 15/02/2022.
 */

package com.ailnor.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import com.ailnor.core.AndroidUtilities
import com.ailnor.core.MATCH_PARENT
import com.ailnor.core.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomSheet2(
    private val screen: Fragment? = null,
    val fullScreen: Boolean = false,
    val height: Int = MATCH_PARENT
) : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        screen?.dialog = dialog
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
//            dialog.window?.setDecorFitsSystemWindows(true)
//        }
//        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
//        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        dialog.setOnShowListener {
            (it as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.let { view ->
                    if (AndroidUtilities.isLandscape || fullScreen) {
                        val bottomSheetBehavior = BottomSheetBehavior.from(view)
                        bottomSheetBehavior.peekHeight = dp(1000)
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                    val layoutParams = view.layoutParams
                    layoutParams.height = height
                    view.layoutParams = layoutParams
                }
        }
        return dialog
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen?.onFragmentCreate()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (screen == null) {
            dismissAllowingStateLoss()
            return null
        }
        return screen.createView(requireContext()).also {
//            it.setOnApplyWindowInsetsListener(object: OnApplyWindowInsetsListener{
//                override fun onApplyWindowInsets(v: View?, insets: WindowInsets): WindowInsets {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
//                        it.setPadding(
//                            it.paddingLeft,
//                            it.paddingTop,
//                            it.paddingRight,
//                            it.paddingBottom + insets.getInsets(WindowInsets.Type.ime()).bottom
//                        )
//                    }
//                    return insets
//                }
//            })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (screen == null)
            dismissAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        if (screen?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.CREATED) == true) {
            screen.onPreResume()
            screen.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (screen?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
            screen.onPrePause()
            screen.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screen?.removeSelfFromStack()
    }

}
