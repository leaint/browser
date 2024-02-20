package com.example.clock.ui.model

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.example.clock.R
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.settings.ignore
import com.example.clock.tab.manager.HolderController
import com.example.clock.tab.manager.WebViewHolder
import com.example.clock.ui.main.MyWebView
import com.example.clock.ui.main.WebViewTransport
import com.example.clock.ui.main.WebviewFragment
import com.example.clock.utils.HistoryManager
import com.example.clock.utils.MyJSInterface
import com.example.clock.utils.MyToast
import com.example.clock.utils.MyWebChromeClient
import com.example.clock.utils.MyWebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

fun initFragment(
    it: WebView,
    h: WebViewHolder,
    setting: GlobalWebViewSetting,
    uiModel: UIModelListener,
    context: Context,
) {
    val myJSInterface = object : MyJSInterface() {
        @JavascriptInterface
        override fun getSearchUrl(): String = setting.search_url

        @JavascriptInterface
        override fun getBookMarks(): String = setting.bookMarkStr

        @JavascriptInterface
        override fun setBookMarks(s: String) {
            setting.bookMarkStr = s
        }
    }

    if (URLUtil.isAssetUrl(h.loadingUrl)) it.addJavascriptInterface(
        myJSInterface, "Android"
    )
    val clipboardJSInterface = object {

        @JavascriptInterface
        fun writeText(s: String?) {
            if (!s.isNullOrEmpty()) uiModel.runOnUiThread {
                uiModel.makeToast("$s", MyToast.LENGTH_LONG).setAction("Copy") {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("WebView", s))
                }.show()

            }
        }

        @JavascriptInterface
        fun back() {
            uiModel.runOnUiThread {
                uiModel.goBackOrClose()
            }
        }
    }
    it.addJavascriptInterface(clipboardJSInterface, "MyHistory")

    val msg = h.initMsg
    if (msg != null) {
        // 即便设置了userAgentString，请求时user agent仍为默认值
//            it.settings.userAgentString = setting.user_agent
        when (val obj = msg.obj) {
            is WebView.WebViewTransport -> {
                obj.webView = it
            }

            is WebViewTransport -> {
                obj.uuid = h.uuid
            }
        }
//            val tr = msg.obj as? WebView.WebViewTransport
//            tr?.webView = it
        msg.sendToTarget()
    } else {
        ignore {
            val u = Uri.parse(h.loadingUrl)
            val ua = setting.siteSettings[u.authority]?.user_agent

            if (ua != null) {
                if (it.settings.userAgentString != ua) {
                    it.settings.userAgentString = ua
                }
            } else {
                it.settings.userAgentString = setting.user_agent
            }
            if (h.pc_mode) {
//                    it.settings.useWideViewPort = false
//                    it.settings.loadWithOverviewMode = false
                it.settings.userAgentString = setting.pc_user_agent
            }
        }
        val savedState = h.savedState
        if (savedState != null) {
            it.restoreState(savedState)
            h.savedState = null
        } else {
            it.loadUrl(h.loadingUrl)
        }


    }
}

fun initFragmentListener(
    holderController: HolderController,
    supportFragmentManager: FragmentManager,
    context: Context,
    setting: GlobalWebViewSetting,
    uiModel: UIModelListener,
    loadSettingJob: Job,
    mainScope: CoroutineScope,
    historyManager: HistoryManager,
) {
    supportFragmentManager.commit {
        val curGroup = holderController.currentGroup
        val webViewHolder = curGroup?.getCurrent() ?: throw Exception("empty group")

        add<WebviewFragment>(R.id.webview_box, webViewHolder.uuidString)
    }

    val width = context.resources.displayMetrics.widthPixels
    val gap = (width * 0.2).toInt()
    val readModeGestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            val height = context.resources.displayMetrics.heightPixels
            val density = context.resources.displayMetrics.density
            val marginLeft = (width * 0.4).toInt()
            val marginRight = (width * 0.6).toInt()

            override fun onSingleTapUp(e: MotionEvent): Boolean {

                if (setting.read_mode) {
                    when (e.x.toInt()) {
                        in 0..marginLeft -> {
                            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                                val to = it.scrollY - height
                                it.scrollY = to.coerceAtLeast(0)
                            }
                        }

                        in marginRight..width -> {
                            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                                val to = it.scrollY + height
                                it.scrollY =
                                    to.coerceAtMost((it.contentHeight * density - height).toInt())
                            }
                        }

                        else -> {}
                    }
                    return true
                }

                return super.onSingleTapUp(e)
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {

                uiModel.onToolbarFling(e1, e2, velocityX, velocityY)
                return super.onFling(e1, e2, velocityX, velocityY)
            }
        })
    val marL = (width - gap) / 2
    val marR = marL + gap

    fun readModeOnTouch(v: View, event: MotionEvent): Boolean {

        readModeGestureDetector.onTouchEvent(event)

        return if (setting.read_mode) {
            if (event.x.toInt() in marL..marR) {
                false
            } else {
                setting.read_mode
            }
        } else {
            false
        }
    }

    var webViewClient: MyWebViewClient? = null
    var webChromeClient: MyWebChromeClient? = null

    supportFragmentManager.registerFragmentLifecycleCallbacks(object :
        FragmentManager.FragmentLifecycleCallbacks() {

        override fun onFragmentViewCreated(
            fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?
        ) {

            v.findViewById<MyWebView>(R.id.webview)?.let {
                val h: WebViewHolder? = holderController.findHolder(f.tag)

                if (h != null) {
                    val msg = h.initMsg
                    WebviewFragment.initWebView(
                        context,
                        uiModel,
                        setting,
                        it,
                        h.uuid,
                        msg != null
                    )

                    it.setOnTouchListener(::readModeOnTouch)

                    h.webView = WeakReference(it)

                    if (loadSettingJob.isActive) {
                        mainScope.launch {
                            loadSettingJob.join()
                            webViewClient = MyWebViewClient(uiModel, setting)
                            webChromeClient =
                                MyWebChromeClient(uiModel, setting, historyManager)
                            it.webViewClient = webViewClient!!
                            it.webChromeClient = webChromeClient

                            it.settings.userAgentString = setting.user_agent

                            initFragment(it, h, setting, uiModel, context)
                        }
                    } else {
                        it.webViewClient = webViewClient!!
                        it.webChromeClient = webChromeClient!!
                        initFragment(it, h, setting, uiModel, context)
                    }
                }
            }
        }

    }, false)

}
