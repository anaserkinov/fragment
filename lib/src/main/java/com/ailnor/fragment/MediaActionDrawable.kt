/* 
 * Copyright Erkinjanov Anaskhan, 01/06/22.
 */

package com.ailnor.fragment

class MediaActionDrawable {
    companion object{
        fun getCircleValue(value: Float): Float {
            var _value = value
            while (_value > 360) {
                _value -= 360f
            }
            return _value
        }
    }
}