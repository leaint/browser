package com.example.clock.ui.main

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView

class MyTextView:TextView {
    private val TAG = "MyTextView"
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

//    override fun dispatchSetPressed(pressed: Boolean) {
//        Log.d(TAG, "dispatchSetPressed:${hashCode()} $text, $pressed")
//    }
}