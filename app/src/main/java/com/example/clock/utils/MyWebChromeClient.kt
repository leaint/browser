package com.example.clock.utils

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.graphics.drawable.updateBounds
import com.example.clock.ui.main.WebViewTag
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.ui.model.UIModelListener
import com.example.clock.tab.manager.WebViewHolder

class MyWebChromeClient(
//    val tag: String?,
    private val uiModelListener: UIModelListener,
    private val globalWebViewSetting: GlobalWebViewSetting,
    private val historyManager: HistoryManager,
//    val cacheHistoryItem: CacheHistoryItem
) : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return super.onPermissionRequest(request)
        uiModelListener.runOnUiThread {
            uiModelListener.makeToast("${request.resources}", MyToast.LENGTH_LONG)
                .setAction("Allow") {
                    request.grant(request.resources)
                }.show()
        }
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        uiModelListener.setFullScreenViewStatus(view, false)
    }

    override fun onHideCustomView() {
        uiModelListener.setFullScreenViewStatus(null, true)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        return true
    }

    override fun onCreateWindow(
        view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
    ): Boolean {

        return if (isUserGesture) {

            uiModelListener.newGroupTab(globalWebViewSetting.JUMP_URI, resultMsg)
            true
        } else {
            false
        }
    }

    override fun onCloseWindow(window: WebView?) {

    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)

        val title = title ?: view.url ?: ""
        val tag = view.tag as? WebViewTag ?: return
        if (!tag.enable) return

        uiModelListener.findHolder(tag.tag)?.change(WebViewHolder.UPDATE_TITLE) {
            it.title = title
        }


//                    tabAdapter.notifyDataSetChanged()
        val cachedHistoryItem = uiModelListener.findHolder(tag.tag)?.cachedHistoryItem
        if (cachedHistoryItem != null) {

            if (cachedHistoryItem.value.time != 0L && cachedHistoryItem.value.url == view.url) {
                cachedHistoryItem.value.title = title
                historyManager.addHistoryItem(cachedHistoryItem.value)
            }
        }
    }

    //进度条到100 后，点击新链接，进度条会重0开始，到100后，不触发pagefinished事件
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        val tag = view?.tag as? WebViewTag ?: return

        val h = uiModelListener.findHolder(tag.tag) ?: return
        if (!tag.enable) return

        h.change(WebViewHolder.UPDATE_PROGRESS) {
            it.progress = newProgress
        }

        if (newProgress < 100 && !h.isLoading) {
            h.change { it.isLoading = true }
        } else if (newProgress == 100 && h.isLoading) {
            h.change { it.isLoading = false }
        }

    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        val tag = view?.tag as? WebViewTag ?: return
        if (!tag.enable) return

        uiModelListener.findHolder(tag.tag)?.change(WebViewHolder.UPDATE_ICON) {
            it.iconBitmapDrawable = if (icon != null) {
                BitmapDrawable(view.resources, icon).apply {
                    updateBounds()
                }
            } else {
                WebViewHolder.emptyDrawable
            }
        }
    }

    override fun onReceivedTouchIconUrl(
        view: WebView?, url: String?, precomposed: Boolean
    ) {
        super.onReceivedTouchIconUrl(view, url, precomposed)

        val tag = view?.tag as? WebViewTag ?: return
        if (!tag.enable) return

        uiModelListener.findHolder(tag.tag)?.let {
            it.iconUrl = url ?: ""
        }
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        fileChooserParams ?: return false

        val intent = fileChooserParams.createIntent() ?: return false
        return uiModelListener.showFileChooser(intent) {
            val data = it.data
            if (data == null) {
                filePathCallback?.onReceiveValue(null)
                return@showFileChooser
            }
            var res = FileChooserParams.parseResult(it.resultCode, data)
            if (res == null) {
                val clipData = data.clipData
                if (clipData != null) {
                    val arr = arrayOfNulls<Uri>(clipData.itemCount)
                    for (i in 0..<clipData.itemCount) {
                        arr[i] = clipData.getItemAt(i).uri
                    }
                    if (arr.isNotEmpty()) {
                        res = arr
                    }
                }

            }
            filePathCallback?.onReceiveValue(res)
        }
    }

}

