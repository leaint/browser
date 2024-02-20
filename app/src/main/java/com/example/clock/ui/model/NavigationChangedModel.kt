package com.example.clock.ui.model

import com.example.clock.settings.ignore

class NavigationChangedModel {
    private val navigationChangedCallback = ArrayList<() -> Unit>()

    fun add(condition:() -> Unit){
        navigationChangedCallback.add(condition)
    }

    fun clearNavigationChangedCallback() {
        navigationChangedCallback.forEach {
            ignore {
                it()
            }
        }
        navigationChangedCallback.clear()
    }
}