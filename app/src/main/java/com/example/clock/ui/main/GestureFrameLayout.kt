package com.example.clock.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.AttrRes

class GestureFrameLayout : FrameLayout {

    var onInterceptTouchEventListener: ((ev: MotionEvent?) -> Boolean)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
        0
    )

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {

        return onInterceptTouchEventListener?.invoke(ev) == true || super.onInterceptTouchEvent(ev)
    }
}