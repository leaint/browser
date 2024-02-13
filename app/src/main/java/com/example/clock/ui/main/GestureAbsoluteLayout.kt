package com.example.clock.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.AbsoluteLayout
import androidx.annotation.AttrRes
import androidx.core.view.children

class GestureAbsoluteLayout : AbsoluteLayout {

    var gestureDetector: GestureDetector? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
        0
    )

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        var gestureConsumed = false
        if (ev != null) {

            gestureDetector?.onTouchEvent(ev)

            if (children.firstOrNull()?.translationX != 0f) {
                gestureConsumed = true
            }
        }
        //&& ev?.actionMasked != MotionEvent.ACTION_CANCEL
        return if (gestureConsumed ) {
            true
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }
}