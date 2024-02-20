package com.example.clock.ui.main

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.fragment.app.Fragment
import com.example.clock.R
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.ui.model.UIModelListener
import com.example.clock.utils.Downloader
import com.example.clock.utils.SafeURI
import java.net.URI
import java.net.URLDecoder

class WebViewTag(
    @JvmField val tag: Int,
    @JvmField var enable: Boolean = true,
    /**
     * 是通过webviewtransport打开的链接
     */
    @JvmField var firstJump: Boolean = false
)

class LoadDataUrl(
    val baseUrl: String?, val data: String,
    val mimeType: String?, val encoding: String?, val historyUrl: String?
)

class WebViewTransport(
    @JvmField val data: Any,
    @JvmField var uuid: Int = -1
)

class MyWebView : WebView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    var invalidateCallback: (() -> Unit)? = null

    override fun invalidate() {
        invalidateCallback?.let { it() }
//        Log.d("invalidate", "invalidate: webview")
        super.invalidate()
    }

}

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [WebviewFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class WebviewFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private var webView: MyWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = view.findViewById(R.id.webview)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        webView?.destroy()
        webView = null
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            webView?.evaluateJavascript(
                "\"use strict\";for(let e of document.querySelectorAll('video,audio')){if(e.paused===false)e.pause();}",
                null
            )
            webView?.onPause()

        } else {
            webView?.onResume()
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment WebviewFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            WebviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }

        fun url2proxy(url: String): URI? {
            val url = SafeURI.parse(url) ?: return null

            return URI(
                "http", url.authority + ".localhost:1232", url.path, url.query, url.fragment
            )
        }

        @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
        fun initWebView(
            context: Context,
            uiModelListener: UIModelListener,
            globalWebViewSetting: GlobalWebViewSetting,
            localWebView: WebView,
            tag: Int,
            jumpTo: Boolean = false
        ) {
//        val hideHandler = Handler(Looper.myLooper()!!)

            var lastHitHref: Message? = null
            val hitTestHandler = object : Handler(Looper.myLooper()!!) {
                override fun handleMessage(msg: Message) {
                    lastHitHref = msg
                    localWebView.showContextMenu()
                }
            }

            localWebView.apply {
                setTag(WebViewTag(tag, firstJump = jumpTo))
                with(settings) {

                    javaScriptEnabled = true
                    domStorageEnabled = true
//            safeBrowsingEnabled = false

                    setEnableSmoothTransition(true)
//                    userAgentString = globalWebViewSetting.user_agent
//            cacheMode = WebSettings.LOAD_CACHE_ONLY
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportZoom(true)
//            zoomBy(0)
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
//                    allowFileAccess = true
//            disabledActionModeMenuItems = WebSettings.MENU_ITEM_PROCESS_TEXT
//            builtInZoomControls = false
                }

//                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                setFindListener(uiModelListener)

                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                    run {
                        val a = contentDisposition.split(';')
                        var filename = ""
                        if (a.size > 1) {
                            a.firstOrNull { it.contains("filename=") }?.let {
                                filename = it.trim().replace("filename=", "")
                            }
                        }
                        val decodedUrl = try {
                            URLDecoder.decode(url, "UTF8")
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            url
                        }
                        val msg = if (decodedUrl.length > 300) {

                            decodedUrl.substring(0, 250) + "..." + decodedUrl.substring(
                                decodedUrl.length - 50,
                                decodedUrl.length
                            )
                        } else {
                            decodedUrl
                        }
                        uiModelListener.runOnUiThread {

                            with(AlertDialog.Builder(context)) {
                                setTitle("Download a file?")
                                setMessage(msg)
                                setPositiveButton(android.R.string.ok) { dialog, id ->
                                    Downloader.downloadFile(
                                        context,
                                        url,
                                        filename,
                                        mimetype,
                                        mapOf(Pair("User-Agent", userAgent))
                                    )
                                }
                                setNegativeButton(android.R.string.cancel, null)
                                setNeutralButton(R.string.copy_link, null)

                                create()

                            }.let {
                                it.window?.decorView?.windowInsetsController?.hide(WindowInsets.Type.navigationBars())
                                it.setCanceledOnTouchOutside(false)
                                it.show()
                                it.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { _ ->
                                    run {
                                        val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "WebView",
                                                url
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                setOnLongClickListener {
                    val msg = Message.obtain(hitTestHandler)
                    lastHitHref = null
                    requestFocusNodeHref(msg)
                    false
                }

                setOnCreateContextMenuListener { menu, v, menuInfo ->

                    run {

                        if (lastHitHref == null) {
                            return@run
                        }
                        val res = hitTestResult

                        val title = lastHitHref?.data?.getString("title") ?: res.extra

                        val imageSrc = lastHitHref?.data?.getString("src")

                        val url = when (res.type) {
                            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                res.extra
                            }

                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                lastHitHref?.data?.getString("url") ?: res.extra
                            }

                            WebView.HitTestResult.IMAGE_TYPE -> {
                                lastHitHref?.data?.getString("src") ?: res.extra
                            }

                            else -> null
                        }

//                        title?.let { menu.setHeaderTitle(it.trim()) }
                        url?.let {
                            menu.setHeaderTitle(it.trim())
                            menu.add(R.string.open_in_new_tab)
                                .setOnMenuItemClickListener { item ->
                                    run {
                                        uiModelListener.newGroupTab(it)
                                        true
                                    }
                                }
                            menu.add(R.string.open_in_background_tab)
                                .setOnMenuItemClickListener { item ->
                                    run {
                                        uiModelListener.newGroupTab(it, background = true)
                                        true
                                    }
                                }
                            menu.add("Open in proxy")
                                .setOnMenuItemClickListener { item ->
                                    run {
                                        url2proxy(it)?.toString()
                                            ?.let { it1 -> uiModelListener.newGroupTab(it1) }
                                        true
                                    }
                                }
                            menu.add(R.string.copy_link)
                                .setOnMenuItemClickListener { item ->
                                    run {
                                        val clipboardManager =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboardManager.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "url", it
                                            )
                                        )
                                        android.R.layout.simple_list_item_activated_1
                                        Toast.makeText(context, "Copied:\n$it", Toast.LENGTH_SHORT)
                                            .show()
                                        true
                                    }
                                }
                            if (title != null && title != it) {
                                val trimmedTitle = title.trim()
                                menu.add(resources.getString(R.string.copy_title, trimmedTitle))
                                    .setOnMenuItemClickListener { item ->
                                        run {
                                            val clipboardManager =
                                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboardManager.setPrimaryClip(
                                                ClipData.newPlainText(
                                                    "text", trimmedTitle
                                                )
                                            )
                                            android.R.layout.simple_list_item_activated_1
                                            Toast.makeText(
                                                context,
                                                "Copied:\n$trimmedTitle",
                                                Toast.LENGTH_SHORT
                                            )
                                                .show()
                                            true
                                        }
                                    }
                            }
                        }
                        if (imageSrc != null) {

                            menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.save_image)
                                .setOnMenuItemClickListener { item ->
                                    run {
                                        val u = Uri.parse(this.url ?: url)
                                        val referer = "${u.scheme}://${u.host}"
                                        Downloader.downloadFile(
                                            context,
                                            imageSrc,
                                            "",
                                            null,
                                            mapOf(
                                                "Referer" to referer,
                                                "User-Agent" to globalWebViewSetting.user_agent,
                                            )
                                        )
                                        true
                                    }

                                }
                        }
                    }
                }
            }
        }

    }
}