package com.example.clock.ui.model

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.example.clock.R
import com.example.clock.databinding.ActivityChromeBinding
import com.example.clock.internal.J
import com.example.clock.tab.manager.HolderController
import com.example.clock.tab.manager.WebViewHolder

interface MainMenuEventListener : AdapterView.OnItemClickListener {

    fun onMenuShow()

    fun onMenuHide()

    fun onMenuUpdate()

}

class MainMenuModel {

    var mainMenuEventListener: MainMenuEventListener? = null

    var isShow = false

    fun toogleMemu() {
        if(isShow) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    fun showMenu() {
        isShow = true
        mainMenuEventListener?.onMenuUpdate()
        mainMenuEventListener?.onMenuShow()
    }

    fun hideMenu() {
        isShow = false
        mainMenuEventListener?.onMenuHide()
    }

    fun updateMenu() {
        mainMenuEventListener?.onMenuUpdate()
    }

}
