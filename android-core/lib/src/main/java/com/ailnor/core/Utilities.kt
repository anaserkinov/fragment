/**
 * Created by Anaskhan on 23/02/23.
 **/

package com.ailnor.core

object Utilities {

    fun clamp(value: Float, maxValue: Float, minValue: Float): Float {
        if (java.lang.Float.isNaN(value)) {
            return minValue
        }
        return if (java.lang.Float.isInfinite(value)) {
            maxValue
        } else Math.max(Math.min(value, maxValue), minValue)
    }

}