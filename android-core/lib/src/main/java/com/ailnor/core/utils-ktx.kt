/* 
 * Copyright Erkinjanov Anaskhan, 14/02/2022.
 */

package com.ailnor.core

import android.app.Dialog
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.IntRange

// Dialog

fun Dialog.showKeyboard() =
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

fun Dialog.hideKeyboard() =
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

fun View?.hideKeyboard() {
    if (this == null) {
        return
    }
    try {
        val imm = this.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (!imm.isActive) {
            return
        }
        imm.hideSoftInputFromWindow(this.windowToken, 0)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// View
fun View.showKeyboard() {
    requestFocus()
    try {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
            this,
            InputMethodManager.SHOW_IMPLICIT
        )
    } catch (e: Exception) {
    }
}


// Int

fun Int.coloredDrawable(color: Int): Drawable {
    val drawable = drawable()
    drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
    return drawable
}

fun Drawable.setColor(color: Int){
    setTint(color)
//    this.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
}

fun Int.drawable(): Drawable {
    return Application.context.resources.getDrawable(this).mutate()
}