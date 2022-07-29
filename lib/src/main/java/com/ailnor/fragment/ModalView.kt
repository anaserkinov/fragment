/* 
 * Copyright Erkinjanov Anaskhan, 29/07/22.
 */

package com.ailnor.fragment

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout

class ModalView(context: Context): ViewGroup(context){

    val contentView = FrameLayout(context)
    val extraView = FrameLayout(context)

    init {

        addView(contentView)
        addView(extraView)

    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

    }

}