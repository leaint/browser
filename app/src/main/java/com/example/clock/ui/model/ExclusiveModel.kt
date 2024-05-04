package com.example.clock.ui.model

import android.view.View
import kotlin.jvm.internal.Ref

class ExclusiveModel(private val maskModel: MaskModel) {
    private val callbackRef: Ref.ObjectRef<Pair<Int, (() -> Unit)>?> = Ref.ObjectRef()

    fun doAndAddCallback(key: Int, element: Pair<Int, (() -> Unit)>) {
        callbackRef.element?.let {
            if (it.first != key) {
                maskModel.isFreeze = true
                it.second()
            }
        }
        callbackRef.element = element
        maskModel.show()
    }

    fun cancelCallback(key: Int) {
        callbackRef.element?.let {
            if (it.first == key) {
                callbackRef.element = null
                maskModel.hide()
                maskModel.isFreeze = false
            }
        }
    }
}

class MaskModel(private val view: View) {

    var isFreeze = false

    fun show() {
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).startDelay = 50

        }
    }

    fun hide() {
        if (view.visibility == View.VISIBLE && !isFreeze) {
            view.animate().alpha(0f).withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
            }
        }
    }
}