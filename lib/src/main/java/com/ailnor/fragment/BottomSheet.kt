/* 
 * Copyright Erkinjanov Anaskhan, 15/02/2022.
 */

package com.ailnor.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ailnor.core.Utilities
import com.ailnor.core.dp

class BottomSheet(private val screen: Fragment? = null) : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.setOnShowListener {
            (it as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.let { view ->
                    if (Utilities.isLandscapeTablet) {
                        val bottomSheetBehavior = BottomSheetBehavior.from(view)
                        bottomSheetBehavior.peekHeight = dp(1000)
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                    val layoutParams = view.layoutParams
                    layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
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
        return screen.createView(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (screen == null)
            dismissAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        screen?.onPreResume()
        screen?.resume()
    }

    override fun onPause() {
        super.onPause()
        screen?.onPrePause()
        screen?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        screen?.removeSelfFromStack()
    }

}
