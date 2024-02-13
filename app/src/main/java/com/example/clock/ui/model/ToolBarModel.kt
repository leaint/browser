package com.example.clock.ui.model

import android.annotation.SuppressLint
import android.content.Context
import android.util.ArraySet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.children
import com.example.clock.databinding.ActivityChromeBinding
import com.example.clock.internal.J
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.tab.manager.HolderController
import com.example.clock.utils.SafeURI
import java.net.URI
import kotlin.math.absoluteValue

fun interface ICanHide {
    fun canHide(): Boolean
}

interface ToolbarEvent {
    fun onGoBack()
    fun onGoForward()
    fun goBackLong()
    fun onSwitchTab(i: Int)
    fun onReload()
    fun onHide()
    fun onShow()
    fun onUpdateUI()
}

class ToolBarModel(setting: GlobalWebViewSetting, context: Context) {

    var width = context.resources.displayMetrics.widthPixels

    private val canDoGestureList = ArraySet<() -> Boolean>()
    private val canDoGesture
        get() = canDoGestureList.all { it() }
    val gestureListener = object : GestureDetector.SimpleOnGestureListener() {

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null && canDoGesture) {
                if (e1.x < 200f && e2.x - e1.x > 300f) {
                    toolbarEventListener?.onSwitchTab(1)
                } else if (width - e1.x < 200f && e1.x - e2.x > 300f) {
                    toolbarEventListener?.onSwitchTab(-1)
                }
            }
            return false

        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {

            if (e1 != null && isShown && canDoGesture) {
                val offset = e2.x - e1.x
                if (offset.absoluteValue > 0.1 * width || translationX != 0f) {
                    translationX = offset
                    toolbarEventListener?.onUpdateUI()
                }
            }
            return false
        }
    }

    private var isShown = true

    var translationX = 0f
    var toolbarEventListener: ToolbarEvent? = null

    private val canHideArr = ArraySet<ICanHide>()

    fun addCanHider(iCanHide: ICanHide) {
        canHideArr.add(iCanHide)
    }

    fun removeCanHider(iCanHide: ICanHide) {
        canHideArr.remove(iCanHide)
    }

    fun addCanDoGesture(i: () -> Boolean) {
        canDoGestureList.add(i)
    }

    fun removeCanDoGesture(i: () -> Boolean) {
        canDoGestureList.remove(i)
    }

    fun goBack() {
        toolbarEventListener?.onGoBack()
    }

    fun goForward() {
        toolbarEventListener?.onGoForward()
    }

    fun goBackLong() {
        toolbarEventListener?.goBackLong()
    }

    fun hide() {
        if (!isShown) return
        if (canHide()) {

            isShown = false
            toolbarEventListener?.onHide()
        }
    }

    fun show() {
        if (isShown) return
        isShown = true
        toolbarEventListener?.onShow()
    }

    fun reloadTabInProxy() {
        toolbarEventListener?.onReload()
    }

    private fun canHide(): Boolean {

        return canHideArr.all {
            it.canHide()
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
fun initToolBarModel(
    context: Context,
    toolBarModel: ToolBarModel,
    urlEditModel: URLEditBarModel,
    uiModelListener: UIModelListener,
    holderController:HolderController,
    binding:ActivityChromeBinding,
) {

    toolBarModel.addCanHider {
        !urlEditModel.isEditing
    }

    toolBarModel.toolbarEventListener = object : ToolbarEvent {
        override fun onGoBack() {
            uiModelListener.goBackOrClose()
        }

        override fun onGoForward() {
            uiModelListener.goForward()
        }

        override fun goBackLong() {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.scrollY = 0
        }

        override fun onSwitchTab(i: Int) {
           uiModelListener.switchToTab(holderController.currentIdx + i)
        }

        override fun onReload() {
            val curGroup = holderController.currentGroup

            val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()

            webViewHolder.webView?.get()?.let {
                val lurl = it.url ?: return@let
                val url = SafeURI.parse(lurl)

                val host = url?.authority ?: return@let
                val newUrl = URI(
                    "http", J.concat(host, ".localhost:1232"), url.path, url.query, url.fragment
                )

               uiModelListener.newGroupTab(newUrl.toString())

            }
        }

        override fun onHide() {
            binding.fullscreenContentControls.visibility = View.INVISIBLE
        }

        override fun onShow() {
            binding.fullscreenContentControls.visibility = View.VISIBLE
        }

        override fun onUpdateUI() {
            binding.toolbarBox.translationX = toolBarModel.translationX
        }

    }

    binding.toolbarBox.animate().apply {
        duration = 160
        setListener(null)
    }

    val gestureDetector = GestureDetector(context, toolBarModel.gestureListener)

    binding.fullscreenContentControls.gestureDetector = gestureDetector

    binding.fullscreenContentControls.setOnTouchListener { v, event ->
        run {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.fullscreenContentControls.children.firstOrNull()?.animate()
                        ?.translationX(0f)
                }
            }
            gestureDetector.onTouchEvent(event)
        }
    }

    binding.backButton.setOnClickListener {
        toolBarModel.goBack()
    }
    binding.backButton.setOnLongClickListener {
        toolBarModel.goBackLong()
        true
    }
    binding.goButton.setOnClickListener {
        toolBarModel.goForward()
    }

//        binding.homeBtn.setOnClickListener {
//            holderController.loadUrl(setting.home_page)
//        }
    binding.loadRefreshBtn.setOnClickListener {
        holderController.reloadTab()
    }

    binding.loadRefreshBtn.setOnLongClickListener {
        toolBarModel.reloadTabInProxy()
        true
    }

}
