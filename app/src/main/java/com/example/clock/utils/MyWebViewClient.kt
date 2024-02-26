package com.example.clock.utils

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.clock.internal.J
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.tab.manager.WebViewHolder
import com.example.clock.ui.main.WebViewTag
import com.example.clock.ui.model.UIModelListener
import java.io.InputStream


class MyWebViewClient(
//        val holderController: HolderController,
//    val tag: String?,
    val uiModelListener: UIModelListener,
    val globalWebViewSetting: GlobalWebViewSetting,
//    val cacheHistoryItem: CacheHistoryItem
) : WebViewClient() {


    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)

    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {

        AlertDialog.Builder(view!!.context).apply {
            setTitle("证书错误：" + error!!.getPrimaryErrorString())
            setMessage("URL:\n" + error.url + "\nCertificate:\n" + error.certificate)

            setPositiveButton("停止访问") { _, _ ->
                run {
                    handler?.cancel()
                }
            }
            setNegativeButton("忽略") { _, _ ->
                run {
                    handler?.proceed()
                }
            }

            setOnDismissListener {
                handler?.cancel()
            }
            create()
        }.show()
    }

    override fun doUpdateVisitedHistory(
        view: WebView,
        url: String,
        isReload: Boolean
    ) {
        if (!isReload) {

            val tagObj = view.tag as? WebViewTag ?: return
            if (!tagObj.enable) return
            val tag = tagObj.tag

            if (url != "about:blank") {
                val cachedHistoryItem = uiModelListener.findHolder(tag)?.cachedHistoryItem
                if (cachedHistoryItem != null) {

                    if (cachedHistoryItem.value.time != 0L) {
                        cachedHistoryItem.reset()
                    }

                    cachedHistoryItem.value.apply {
                        time = System.currentTimeMillis()
                        this.url = url
                        title = view.title ?: ""
                    }
                }

                uiModelListener.recycleHolderClearHistory(tag)
            }
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {

        super.onPageStarted(view, url, favicon)

        val tag = (view?.tag as? WebViewTag)?.let {
            if (it.enable) {
                it.tag
            } else {
                null
            }
        } ?: -1

        if (url == null || tag == -1 || !uiModelListener.onPageStarted(tag, url, favicon)) return

        if (url == "about:blank") return

        val js = if (globalWebViewSetting.can_copy) {
            globalWebViewSetting.can_copy_js
        } else {
            globalWebViewSetting.cannot_copy_js
        }

        view?.evaluateJavascript(js, null)

        val uri = SafeURI.parse(url)
        val host = uri?.authority
        if (host != null) {

            var firstAdRule = globalWebViewSetting.ad_rule[uri.authority]

            val domainRoot = domainRootMatch(host)

            if (domainRoot.length != host.length) {
                val secondAdRule = globalWebViewSetting.ad_rule[domainRoot]
                if (firstAdRule.isNullOrEmpty()) {
                    firstAdRule = secondAdRule
                } else if (!secondAdRule.isNullOrEmpty()) {
                    firstAdRule = "$firstAdRule,$secondAdRule"
                }
            }

            if (firstAdRule != null) {
                view?.evaluateJavascript(
                    StringBuilder(1100).append(
                        "const doOnce=(_f98,..._args98)=>{let _first98=!0;" +
                                "return()=>{if(_first98){try{_f98(..._args98)}catch(e){console.error(e)}finally{_first98=!1}}}};" +
                                "const regLoaded=(_func91,..._args91)=>{window.addEventListener(\"load\",(event)=>{" +
                                "_func91(..._args91);console.log('loaded')})};" +
                                "const regEvent=(_func91,..._args91)=>{window.requestAnimationFrame(" +
                                "()=>{_func91(..._args91);console.log('requestAnimationFrame')});" +
                                "window.requestIdleCallback(()=>{_func91(..._args91);" +
                                "console.log('requestIdleCallback')});const doop=()=>{if(document.readyState==='interactive'){" +
                                "_func91(..._args91);console.log('interactive')}" +
                                "else if(document.readyState==='complete'){}};" +
                                "document.addEventListener(\"readystatechange\",doop)};" +
                                "let doFunc=doOnce(()=>{const s='"
                    )
                        .append(firstAdRule)
                        .append(
                            "{display: none !important;}';const st=document.createElement('style');" +
                                    "st.innerText=s;document.head.appendChild(st)});regEvent(doFunc);"
                        )
                        .toString(), null
                )
            }

            for (it in globalWebViewSetting.userscriptMap) {
                if (J.startsWith(url, it.key) || J.endsWith(host, it.key)) {
                    view?.evaluateJavascript(it.value.content, null)
                }
            }
//            globalWebViewSetting.userscriptMap.asSequence()
//                .filter { (k, _) -> url.startsWith(k) || host.endsWith(k) }
//                .forEach { (_, u) -> view?.evaluateJavascript(u.content, null) }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) {
            val tag = (view?.tag as? WebViewTag)?.tag ?: return

            uiModelListener.onPageFinished(tag, url)
        }

//                    hideHandler.removeCallbacks(longLoadingHandler)

    }

    override fun onLoadResource(view: WebView?, url: String?) {

        if (url != null) {
            val tag = (view?.tag as? WebViewTag)?.tag ?: return
            uiModelListener.findHolder(tag)?.loadedResources?.add(url)
        }
    }

    // TODO: loadurl do not call this function
    // TODO: 不应忽略request中的http header和method
    override fun shouldOverrideUrlLoading(
        view: WebView, request: WebResourceRequest
    ): Boolean {

        val tagObj = (view.tag as? WebViewTag)
        val tag = tagObj?.let {
            if (it.enable) {
                it.tag
            } else {
                -1
            }
        } ?: -1

        val urlStr = request.url.toString()

        val targetSiteSetting =
            globalWebViewSetting.siteSettings[request.url.authority]

        val curUA = view.settings.userAgentString
        val siteUA = targetSiteSetting?.user_agent ?: globalWebViewSetting.user_agent

        when (request.url.scheme) {
            "https", "http", "file", "view-source", "about", "chrome", "data", "blob", "javascript" -> {
                // 通过 WebView.WebViewTransport 第一次打开的链接
                if (tagObj?.firstJump == true) {
                    tagObj.firstJump = false
                    if (curUA != siteUA) {
                        view.settings.userAgentString = siteUA
                        // TODO 可能会忽略掉一些webviewtransport对webview的特殊设置
                        view.post {
                            view.loadUrl(urlStr)
                        }
                        return true
                    }
                    return false
                }
            }

            else -> {

                val urlStr = request.url.toString()
                uiModelListener.makeToast(J.concat("Go to:\n", urlStr), MyToast.LENGTH_SHORT)
                    .setAction("YES") {
                        uiModelListener.goToSpecialURL(urlStr)
                    }.show()
                return true
            }
        }

        if (request.isRedirect) {
            return false
        }

        if (request.isForMainFrame) {

            if (globalWebViewSetting.enable_replace) {
                val newUrl = WebRequestFilter.replaceRuleFilter(request, globalWebViewSetting)
                if (tag != -1 && newUrl != null) {
                    uiModelListener.runOnUiThread {
                        uiModelListener.redirectTo(newUrl, tag)
                    }
                    return true
                }
            }


            val host = view.url?.let { hostMatch(it) }
            val domainRoot = host?.let { domainRootMatch(it) }

            val requestHost = request.url.authority

            if (!request.hasGesture() && (domainRoot == null || requestHost != null && !J.endsWith(
                    requestHost,
                    domainRoot
                ))
            ) {
                uiModelListener.makeToast(J.concat("Go to:\n", urlStr), MyToast.LENGTH_SHORT)
                    .setAction("YES") {
                        uiModelListener.newGroupTab(urlStr)
                    }.show()

                return true
            }

            if (urlStr.lowercase() == "chrome://homepage") {
                if (tag != -1) {
                    uiModelListener.clearForwardTab(tag)
                }
                uiModelListener.runOnUiThread {
                    view.loadUrl(globalWebViewSetting.inner_home_page)
                }
                return true
            }

            // 有用户手势，但跳转到非同源外部网站也要提示
            if (domainRoot == null || requestHost == null || !J.endsWith(
                    requestHost,
                    domainRoot
                ) && globalWebViewSetting.siteSettings[host]?.allow_go_outside == false
            ) {
                uiModelListener.makeToast(
                    J.concat("Go to:\n", urlStr),
                    MyToast.LENGTH_SHORT
                )
                    .setAction("YES") {
                        uiModelListener.newTab(urlStr, tag)
                    }.show()
                return true
            }

            if (curUA != siteUA) {

                // TODO: 不明所以

//                view.settings.userAgentString = siteUA
//                              uiUpdateListener.runOnUiThread {
                //                            webView.stopLoading()
//                                    view.loadUrl(request.url.toString())
//                                }
                if (tag != -1) {
                    uiModelListener.runOnUiThread {
                        uiModelListener.newTab(urlStr, tag)
                    }
                }
                return true
            }

            if (view.url == globalWebViewSetting.inner_home_page || J.startsWith(
                    urlStr,
                    globalWebViewSetting.search_url
                )
            ) {
                if (tag != -1) {
                    uiModelListener.clearForwardTab(tag)
                }
                return false
            }

            return if (host != null) {
                val siteCacheNavigation =
                    globalWebViewSetting.siteSettings[host]?.cache_navigation
                if (siteCacheNavigation == null && globalWebViewSetting.cache_navigation || siteCacheNavigation == true) {
                    if (tag != -1) {
                        uiModelListener.clearForwardTab(tag)
                    }
                    false
                } else {
                    if (urlStr == view.url) {
                        false
                    } else {
                        if (tag != -1) {
                            uiModelListener.runOnUiThread {
                                uiModelListener.newTab(urlStr, tag)
                            }
                        }
                        true

                    }
                }
            } else {
                false
            }
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    // 注意多线程访问问题
    override fun shouldInterceptRequest(
        view: WebView?, request: WebResourceRequest?
    ): WebResourceResponse? {

        request?.url ?: return null

        val urlStr = request.url.toString()
        val host = hostMatch(urlStr)

//        val host = request?.url?.authority

        val tag = (view?.tag as? WebViewTag)?.let {
            if (it.enable) {
                it.tag
            } else {
                null
            }
        } ?: -1

        if (request.isForMainFrame) {
            if (tag != -1) {
                uiModelListener.runOnUiThread {
                    uiModelListener.findHolder(tag)?.change(WebViewHolder.UPDATE_URL) {
                        it.startLoadingUri = urlStr
                    }
//                    uiUpdateListener.updateHolderUrl(tag, request.url.toString())
                }
            }
//                            hideHandler.removeCallbacks(longLoadingHandler)
//                            hideHandler.postDelayed(longLoadingHandler, 2000L)

        }

        if (host != null) {

            for (it in globalWebViewSetting.block_rule) {
                if (J.endsWith(host, it)) {
                    uiModelListener.runOnUiThread {
                        uiModelListener.findHolder(tag)?.loadedResources?.add(urlStr)
                    }
                    return BlockWebResourceResponse
                }
            }
//            if (globalWebViewSetting.block_rule.asSequence()
//                    .filter { host?.endsWith(it) == true }.any()
//            ) {
//                if (request != null) {
//                    uiUpdateListener.findHolder(tag)?.loadedResources?.add(request.url.toString())
//                }
//                return BlockWebResourceResponse
//            }
//            if (uiUpdateListener.findHolder(tag)?.pc_mode == false) {
//                if (request.requestHeaders["Cache-Control"] == null) {
//                    request.requestHeaders["Cache-Control"] = "max-stale=120"
//                }
//            }
            var res: WebResourceResponse? = null
            for (it in WebRequestFilter.webRequestFilter) {

                for (l in it.rules) {
                    if (J.endsWith(host, l)) {
                        res = it.filterFunc(request, globalWebViewSetting)
                        break
                    }
                }
                if (res != null) {
                    break
                }
            }

            if (res == null) {
                res = WebRequestFilter.backupRequestFilter.filterFunc(
                    request,
                    globalWebViewSetting
                )
            }
//            val res = WebRequestFilter.webRequestfilter.asSequence()
//                .filter { it.rules.any { host.endsWith(it) } }
//                .firstNotNullOfOrNull { it.filterFunc(request, globalWebViewSetting) }
//                ?: WebRequestFilter.backupRequestFilter.filterFunc(
//                    request,
//                    globalWebViewSetting
//                )

            if (res != null) {
                uiModelListener.runOnUiThread {
                    uiModelListener.findHolder(tag)?.loadedResources?.add(urlStr)
                }
                return res
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    companion object {
        val BlockWebResourceResponse = WebResourceResponse("text/html",
            "utf-8",
            502,
            "Bad Gateway",
            emptyMap(),
            object : InputStream() {
                override fun read(): Int = -1
            })
        val NotFoundWebResourceResponse = WebResourceResponse("text/html",
            "utf-8",
            404,
            "Not Found",
            emptyMap(),
            object : InputStream() {
                override fun read(): Int = -1
            })
        /*
const doOnce = (_f98,..._args98)=>{
    let _first98 = true;
    return ()=>{
        if (_first98) {
            try {
                _f98(..._args98);
            } catch (e) {
                console.error(e);
            } finally {
                _first98 = false;
            }
        }
    }
    ;
}
;
const regLoaded = (_func91,..._args91)=>{
    window.addEventListener("load", (event)=>{
        _func91(..._args91);
        console.log('loaded');
    }
    );
}
;
const regEvent = (_func91,..._args91)=>{
    window.requestAnimationFrame(()=>{
        _func91(..._args91);
        console.log('requestAnimationFrame');
    }
    );
    window.requestIdleCallback(()=>{
        _func91(..._args91);
        console.log('requestIdleCallback');
    }
    );
    const doop = ()=>{
        if (document.readyState === 'interactive') {
            _func91(..._args91);
            console.log('interactive');
        } else if (document.readyState === 'complete') {}
    }
    ;
    document.addEventListener("readystatechange", doop);
}
;
let doFunc = doOnce(()=>{
    const s = '  { display: none !important; }';
    const st = document.createElement('style');
    st.innerText = s;
    document.head.appendChild(st);
}
);
regEvent(doFunc);

        */
    }
}