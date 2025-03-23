package com.ailnor.core

fun interface ActionListener {
    fun onAction(action: Int, vararg data: Any?)
}