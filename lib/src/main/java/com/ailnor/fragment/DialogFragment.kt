/**
 * Created by Anaskhan on 04/02/23.
 **/

package com.ailnor.fragment

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.ailnor.core.*
import com.ailnor.core.Theme.alpha

open class DialogFragment(bundle: Bundle? = null) : Fragment(bundle) {

    protected lateinit var dialogView: FrameLayout
    protected lateinit var linearLayout: LinearLayout
    private lateinit var buttonsLayout: LinearLayout

    var touchableOutside = true

    override fun onFragmentCreate(): Boolean {
        hasToolbar = false
        isDialog = true
        backgroundColor = Theme.transparent
        return super.onFragmentCreate()
    }

    override fun onCreateView(context: Context): View {
        linearLayout = object: LinearLayout(context){
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val height = MeasureSpec.getSize(heightMeasureSpec) - dp(16)
                val width = MeasureSpec.getSize(widthMeasureSpec)
                buttonsLayout.measure(
                    measureSpec_exactly(width),
                    measureSpec_unspecified
                )
                getChildAt(0).measure(
                    measureSpec_exactly(width),
                    measureSpec_at_most(
                        height - buttonsLayout.measuredHeight
                    )
                )

                setMeasuredDimension(
                    width,
                    buttonsLayout.measuredHeight + getChildAt(0).measuredHeight
                )
            }
        }
        linearLayout.setOnClickListener {  }
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.background = createRoundRectDrawable(
            dp(16f),
            Theme.white
        )
        linearLayout.clipToOutline = true
        linearLayout.elevation = dp(8f)

        dialogView = FrameLayout(context)
        dialogView.fitsSystemWindows = true
        dialogView.setBackgroundColor(Theme.black.alpha(25))
        dialogView.setOnClickListener {
            if (touchableOutside)
                finishFragment(false)
        }

        dialogView.addView(
            linearLayout,
            if (Utilities.isLandscape)
                frameLayoutParams(
                    Utilities.displaySize.y/2,
                    WRAP_CONTENT
                )
            else
                frameLayoutParams(
                    MATCH_PARENT,
                    WRAP_CONTENT,
                    marginLeft = dp(16),
                    marginRight = dp(16)
                )
        )


        return dialogView
    }

    override fun onPreResume() {
        super.onPreResume()

        val layoutParams = linearLayout.layoutParams as FrameLayout.LayoutParams
        if (Utilities.isLandscape) {
            layoutParams.width = Utilities.displaySize.y / 2
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0
        } else {
            layoutParams.width = MATCH_PARENT
            layoutParams.leftMargin = dp(16)
            layoutParams.rightMargin = dp(16)
        }

    }


    private fun createButtonsLayout() {
        if (::buttonsLayout.isInitialized)
            return
        buttonsLayout = LinearLayout(context)
        buttonsLayout.setPadding(dp(16), dp(8), dp(16), dp(8))
        buttonsLayout.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        buttonsLayout.setBackgroundColor(Theme.colorPrimary.alpha(15))
        linearLayout.addView(buttonsLayout, linearLayoutParams(MATCH_PARENT))
    }

    fun addNegativeButton(text: Int): TextView {
        createButtonsLayout()
        val textView = TextView(context)

        textView.setText(text)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        textView.setTextColor(Theme.colorPrimary)
        textView.background = createSimpleSelectorRoundRectDrawable(
            dp(4),
            Theme.transparent,
            Theme.colorPrimary.alpha(70)
        )
        textView.setPadding(dp(12))

        buttonsLayout.addView(textView, linearLayoutParams())

        return textView
    }

    fun addPositiveButton(text: Int): TextView {
        createButtonsLayout()
        val textView = TextView(context)

        textView.setText(text)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        textView.setTextColor(Theme.colorPrimary)
        textView.background = createSimpleSelectorRoundRectDrawable(
            dp(4),
            Theme.transparent,
            Theme.colorPrimary.alpha(70)
        )
        textView.setPadding(dp(12))

        buttonsLayout.addView(textView, linearLayoutParams())

        return textView
    }

    override fun onOrientationChanged() {
        super.onOrientationChanged()
        val layoutParams = linearLayout.layoutParams as FrameLayout.LayoutParams
        if (Utilities.isLandscape) {
            layoutParams.width = Utilities.displaySize.y / 2
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0
        } else {
            layoutParams.width = MATCH_PARENT
            layoutParams.leftMargin = dp(16)
            layoutParams.rightMargin = dp(16)
        }
    }

    override fun canBeginSlide(): Boolean {
        return false
    }

}