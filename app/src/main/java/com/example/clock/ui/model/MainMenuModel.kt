package com.example.clock.ui.model

import android.widget.AdapterView

interface MainMenuEventListener : AdapterView.OnItemClickListener {

    fun onMenuShow()

    fun onMenuHide()

    fun onMenuUpdate()

}

class MainMenuModel {

    var mainMenuEventListener: MainMenuEventListener? = null

    var isShow = false

    fun toggleMenu() {
        if (isShow) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    fun showMenu() {
        if (isShow) return
        isShow = true
        mainMenuEventListener?.onMenuUpdate()
        mainMenuEventListener?.onMenuShow()
    }

    fun hideMenu() {
        if (!isShow) return
        isShow = false
        mainMenuEventListener?.onMenuHide()
    }

    fun updateMenu() {
        mainMenuEventListener?.onMenuUpdate()
    }

}
