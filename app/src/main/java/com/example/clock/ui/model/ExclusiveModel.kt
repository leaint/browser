package com.example.clock.ui.model

import kotlin.jvm.internal.Ref

class ExclusiveModel {
    private val callbackRef: Ref.ObjectRef<Pair<Int, (() -> Unit)>?> = Ref.ObjectRef()

    fun doAndAddCallback(key: Int, element: Pair<Int, (() -> Unit)>) {
        callbackRef.element?.let {
            if (it.first != key) it.second()
        }
        callbackRef.element = element
    }

    fun cancelCallback(key:Int) {
        callbackRef.element?.let {
            if (it.first == key) callbackRef.element = null
        }
    }
}