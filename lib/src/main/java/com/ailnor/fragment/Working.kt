///**
// * Created by Anaskhan on 10/01/24.
// **/
//
//package com.ailnor.fragment
//
//import android.content.Context
//import android.view.VelocityTracker
//import android.widget.FrameLayout
//
//class Working(context: Context): FrameLayout(context) {
//
//    private var innerTranslationX = 0f
//        set(value) {
//            field = value
//            invalidate()
//        }
//
//    private var velocityTracker: VelocityTracker? = null
//    private var startedTrackingX = 0
//    private var startedTrackingY = 0
//    var startedTracking = false
//    private var maybeStartedTracking = false
//    private var beginTrackingSent = false
//    private var startedTrackingPointerId = -1
//
//    private var drawShadow = false
//
//    private val pages = Array(3){ FrameLayout(context) }
//    private val actionBars = Array(3){ FrameLayout(context) }
//
//
//
//}