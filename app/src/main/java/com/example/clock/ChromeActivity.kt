package com.example.clock

//import androidx.preference.PreferenceManager
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.http.HttpResponseCache
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.preference.PreferenceManager
import android.util.Log
import android.view.ActionMode
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Menu
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.example.clock.databinding.ActivityChromeBinding
import com.example.clock.databinding.AdPickBinding
import com.example.clock.databinding.SearchBoxBinding
import com.example.clock.internal.J
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.settings.ignore
import com.example.clock.tab.manager.ChangedListener
import com.example.clock.tab.manager.GroupHolderListener
import com.example.clock.tab.manager.GroupWebViewHolder
import com.example.clock.tab.manager.HolderController
import com.example.clock.tab.manager.ShadowWebViewHolder
import com.example.clock.tab.manager.WebViewHolder
import com.example.clock.ui.main.LoadDataUrl
import com.example.clock.ui.main.MyWebView
import com.example.clock.ui.main.TAB_TITLES
import com.example.clock.ui.main.WebViewTransport
import com.example.clock.ui.main.WebviewFragment
import com.example.clock.ui.main.WebviewFragment.Companion.initWebView
import com.example.clock.ui.model.ADPickModel
import com.example.clock.ui.model.BookMarkModel
import com.example.clock.ui.model.MainMenuEventListener
import com.example.clock.ui.model.MainMenuModel
import com.example.clock.ui.model.MenuEnum
import com.example.clock.ui.model.SearchModel
import com.example.clock.ui.model.TabListModel
import com.example.clock.ui.model.ToolBarModel
import com.example.clock.ui.model.UIModel
import com.example.clock.ui.model.UIModelListener
import com.example.clock.ui.model.URLEditBarModel
import com.example.clock.ui.model.URLSuggestionModel
import com.example.clock.ui.model.initAdPickModel
import com.example.clock.ui.model.initSearchModel
import com.example.clock.ui.model.initTabListModel
import com.example.clock.ui.model.initToolBarModel
import com.example.clock.ui.model.initURLEditModel
import com.example.clock.utils.HistoryManager
import com.example.clock.utils.MyJSInterface
import com.example.clock.utils.MyToast
import com.example.clock.utils.MyWebChromeClient
import com.example.clock.utils.MyWebViewClient
import com.example.clock.utils.SafeURI
import com.example.clock.utils.translateEscapes
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.net.CookieHandler
import java.net.CookieManager
import java.util.LinkedList
import kotlin.jvm.internal.Ref.ObjectRef

class ChromeActivity : FragmentActivity() {

    private val delayLoadingHandler = object : Handler(Looper.myLooper()!!) {

        override fun handleMessage(msg: Message) {

            when (msg.what) {
                1 -> {
                    when (val obj = msg.obj) {
                        is WebViewTransport -> {
                            val data = obj.data
                            if (data is LoadDataUrl) {
                                holderController.findHolder(obj.uuid)?.webView?.get()
                                    ?.loadDataWithBaseURL(
                                        data.baseUrl,
                                        data.data,
                                        data.mimeType,
                                        data.encoding,
                                        data.historyUrl
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    private val mainScope = MainScope()

    private lateinit var uiModel: UIModelListener

    private lateinit var setting: GlobalWebViewSetting

    private lateinit var historyManager: HistoryManager

    private lateinit var myJSInterface: MyJSInterface

    private val searchModel = SearchModel()

    private val tabListModel = TabListModel()

    private val mainMenuModel = MainMenuModel()

    private val urlEditModel = URLEditBarModel()

    private val adPickModel = ADPickModel()

    private lateinit var toolBarModel: ToolBarModel

    private lateinit var bookMarkModel: BookMarkModel

    private lateinit var urlSuggestionModel: URLSuggestionModel

    private lateinit var binding: ActivityChromeBinding

//    private lateinit var toastView: View

    private lateinit var settingLauncher: ActivityResultLauncher<Intent>

    private val cookieManager = CookieManager()

    private var adPickInflated = false
        set(value) {
            if (value) {
                field = value
            }
        }

    private var searchBoxInflated = false
        set(value) {
            if (value) {
                field = value
            }
        }

    private val pendingDelete = ArrayList<File>()

    private val holderController = HolderController()

    private val cachedWebViewFragment = LinkedList<ShadowWebViewHolder>()

    private val startHistory =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it.data?.extras?.let {
                it.getString("url")?.let {
                    uiModel.newGroupTab(it)
                }
                if (it.getBoolean("changed")) {
                    mainScope.launch {
                        setting.reloadBookMark(this@ChromeActivity)
                    }
                }
            }
        }

    private val clipboardJSInterface = object {

        @JavascriptInterface
        fun writeText(s: String?) {
            if (!s.isNullOrEmpty()) runOnUiThread {
                uiModel.makeToast("$s", MyToast.LENGTH_LONG).setAction("Copy") {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("WebView", s))
                }.show()

            }
        }

        @JavascriptInterface
        fun back() {
            runOnUiThread {
                uiModel.goBackOrClose()
            }
        }
    }

    private val navigationChangedCallback = ArrayList<() -> Unit>()

    private val exclusiveModelCallback: ObjectRef<Pair<Int, (() -> Unit)>?> = ObjectRef()

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let {
            when (it.action) {
                Intent.ACTION_VIEW -> it.dataString?.let { it1 -> uiModel.newGroupTab(it1) }
                else -> {}
            }
        }
    }

    private lateinit var loadSettingJob: Job

    private lateinit var readModeGestureDetector: GestureDetector

    init {
//        enableStrictMode()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableStrictMode()
        binding = ActivityChromeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            // 必须是第一个有关 WebView 的语句
            WebView.setDataDirectorySuffix(packageName)
        } catch (e: IllegalStateException) {
            e.message?.let { Log.e("WebView", it) }
        }

        WebView.setWebContentsDebuggingEnabled(true)

        setting = GlobalWebViewSetting(this@ChromeActivity, this@ChromeActivity)

        bookMarkModel = BookMarkModel(setting)
        toolBarModel = ToolBarModel(setting, this@ChromeActivity)

        historyManager = HistoryManager(this@ChromeActivity, this@ChromeActivity)

        urlSuggestionModel = URLSuggestionModel(this, historyManager, setting)
        ignore {
            CookieHandler.setDefault(cookieManager)
        }

        mainScope.launch(Dispatchers.IO) {

            loadSettingJob = mainScope.launch {
                setting.loadSetting(this@ChromeActivity)

                holderController.currentGroup?.getCurrent()?.loadingUrl = setting.INIT_URI

                setting.reloadBookMark(this@ChromeActivity)
            }

            try {
                val httpCacheDir = File(
                    this@ChromeActivity.cacheDir, "HttpResponseCache"
                )
                val httpCacheSize = 10 * 1024 * 1024L
                HttpResponseCache.install(
                    httpCacheDir, httpCacheSize
                )
            } catch (e: Exception) {
                Log.i("Cache", "HTTP response cache installation failed:$e")
            }
            PreferenceManager.setDefaultValues(this@ChromeActivity, R.xml.site_preferences, true)

        }

        myJSInterface = object : MyJSInterface() {
            @JavascriptInterface
            override fun getSearchUrl(): String = setting.search_url

            @JavascriptInterface
            override fun getBookMarks(): String = setting.bookMarkStr

            @JavascriptInterface
            override fun setBookMarks(s: String) {
                setting.bookMarkStr = s
            }
        }
        run {
            val g = GroupWebViewHolder()
            val h = WebViewHolder("about:blank", newTask = true)
            g.addTab(h)
            holderController.add(g)
        }


//        window.insetsController?.hide(WindowInsets.Type.systemBars())

//        window.decorView.windowInsetsController?.hide(WindowInsets.Type.systemBars())
//        window.attributes.fitInsetsTypes = WindowInsets.Type.navigationBars()
//        actionBar?.setDisplayHomeAsUpEnabled(false)
        actionBar?.hide()
//        binding.root.windowInsetsController?.show(WindowInsets.Type.ime())
        binding.root.setOnApplyWindowInsetsListener { v, insets ->
            run {
//                window.insetsController?.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())

                if ((v.layoutParams as MarginLayoutParams).bottomMargin != insets.getInsets(
                        WindowInsets.Type.ime()
                    ).bottom
                ) {

                    v.updateLayoutParams<MarginLayoutParams> {
                        bottomMargin = insets.getInsets(WindowInsets.Type.ime()).bottom
                    }
                    WindowInsets.CONSUMED
                } else {
                    insets
                }
            }
        }

        binding.loadingBar.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!v.rootWindowInsets.isVisible(WindowInsets.Type.statusBars())) {
                    v.rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.let {
                        v.setPadding(it.center.x / 3 * 2, 0, it.center.x, 0)
                    }
                }
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })

//        toastView =  binding.toastBox
//         = findViewById(R.id.toast_box)

        initFragmentListener()

        initMenuModel()

        initTabModel()

        uiModel = UIModel(
            this,
            holderController,
            setting,
            tabListModel,
            toolBarModel,
            binding,
            searchModel,
        )

        uiModel.requestFullscreen(setting.isFullScreen)

        initTabListModel(
            this,
            this,
            binding,
            tabListModel,
            uiModel,
            setting,
            holderController,
            exclusiveModelCallback
        )

        initURLEditModel(
            this,
            this,
            binding,
            urlEditModel,
            exclusiveModelCallback,
            uiModel,
            toolBarModel,
            holderController,
            setting,
            urlSuggestionModel
        )

        initToolBarModel(this, toolBarModel, urlEditModel, uiModel, holderController, binding)

        var lastBack = System.currentTimeMillis() - 2000
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {

            override fun handleOnBackPressed() {
                if (uiModel.goBackOrClose()) {
                    if (!isEnabled) isEnabled = true
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBack < 1000) {
                        isEnabled = false
                        finish()
                    } else {
                        Toast.makeText(
                            this@ChromeActivity, "Press again to exit.", Toast.LENGTH_SHORT
                        ).show()
                        lastBack = now
                    }
                }

            }
        })
//
        settingLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { setting.settingCallback(it, this) }

        binding.adPickStub.setOnInflateListener { stub, inflated ->
            run {
                stub.setOnInflateListener(null)

                initAdPickModel(
                    this,
                    setting,
                    AdPickBinding.bind(inflated),
                    adPickModel,
                    holderController,
                    uiModel
                )
            }
        }
        binding.searchStub.setOnInflateListener { stub, inflated ->
            run {
                stub.setOnInflateListener(null)
                initSearchModel(
                    SearchBoxBinding.bind(inflated),
                    searchModel,
                    navigationChangedCallback,
                    holderController,
                    uiModel
                )
            }
        }
    }

    private fun initFragmentListener() {
        supportFragmentManager.commit {
            val curGroup = holderController.currentGroup
            val webViewHolder = curGroup?.getCurrent() ?: throw Exception("empty group")

            add<WebviewFragment>(R.id.webview_box, webViewHolder.uuidString)
        }

        val width = resources.displayMetrics.widthPixels
        val gap = (width * 0.2).toInt()
        readModeGestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {

            val height = resources.displayMetrics.heightPixels
            val density = resources.displayMetrics.density
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
            FragmentLifecycleCallbacks() {

            override fun onFragmentViewCreated(
                fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?
            ) {

                v.findViewById<MyWebView>(R.id.webview)?.let {
                    val h: WebViewHolder? = holderController.findHolder(f.tag)

                    if (h != null) {
                        val msg = h.initMsg
                        initWebView(
                            this@ChromeActivity,
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

                                initFragment(it, h)
                            }
                        } else {
                            it.webViewClient = webViewClient!!
                            it.webChromeClient = webChromeClient!!
                            initFragment(it, h)
                        }
                    }
                }
            }

        }, false)

    }

    private fun initMenuModel() {

        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                mainMenuModel.hideMenu()
                isEnabled = false
            }
        }
        onBackPressedDispatcher.addCallback(callback)

        val menuTitleMap =
            arrayOfNulls<(h: WebViewHolder, view: TextView) -> Unit>(MenuEnum.entries.size).apply {
                set(MenuEnum.BOOKMARK.ordinal) { h, view ->
                    view.text = if (bookMarkModel.indexOf(h.startLoadingUri) >= 0) {
                        "Booked ✓"
                    } else {
                        "Book it"
                    }
                }
                set(MenuEnum.PC_MODE.ordinal) { h, view ->
                    view.setTextColor(
                        if (h.pc_mode) {
                            view.text = J.concat(MenuEnum.PC_MODE.title, " ✓")
                            Color.GREEN
                        } else {
                            view.text = MenuEnum.PC_MODE.title
                            Color.BLACK
                        }
                    )

                }

                set(MenuEnum.RESTORE_TAB.ordinal) { h, view ->
                    view.isEnabled = holderController.restoreGroupHolder.hs.size > 0
                }
            }


        mainMenuModel.mainMenuEventListener = object : MainMenuEventListener {
            override fun onMenuShow() {
                printDebugInfo()

                exclusiveModelCallback.element?.let {
                    if (it.first != mainMenuModel.hashCode()) {
                        it.second()
                    }
                }

//                if (tabListModel.isShown) {
//                    tabListModel.closeTabList()
//                    return
//                }

                callback.isEnabled = true

//                binding.menuBox.setBackgroundColor(Color.TRANSPARENT)
                binding.menuBox.alpha = 0f

                binding.menuBox.animate().alpha(1f).apply {
                    duration = 150
                }
                binding.menuBox.visibility = View.VISIBLE

                exclusiveModelCallback.element =
                    mainMenuModel.hashCode() to mainMenuModel::hideMenu
//                binding.menuBox.animate().alpha(1)
            }

            override fun onMenuHide() {
                binding.menuBox.animate().alpha(0f).withEndAction {
                    binding.menuBox.visibility = View.INVISIBLE
                    binding.menuBox.alpha = 1f
                }
                exclusiveModelCallback.element?.let {
                    if (it.first == mainMenuModel.hashCode()) {
                        exclusiveModelCallback.element = null
                    }
                }
            }

            override fun onMenuUpdate() {
                val h =
                    holderController.currentGroup?.getCurrent()
                        ?: throw KotlinNullPointerException()
                val menuGrid = binding.menuGrid
                menuTitleMap.forEachIndexed { index, function ->
                    if (function != null) {
                        menuGrid.getChildAt(index)?.findViewById<TextView>(R.id.text1)?.let {
                            function(h, it)
                        }
                    }

                }
//                menuTitleMap.withIndex().asSequence()
//                    .filter { it.value != null }
//                    .forEach { oit ->
//                        menuGrid.getChildAt(oit.index)
//                            ?.findViewById<TextView>(R.id.text1)?.let {
//                                oit.value!!(h, it)
//                            }
//                    }
            }

            override fun onItemClick(
                parent: AdapterView<*>, view: View, position: Int, id: Long
            ) {
                menuItemClick(parent, view, position, id)
            }

        }

        binding.menuBox.apply {
            setOnClickListener {
                mainMenuModel.hideMenu()
            }
            visibility = View.INVISIBLE
        }

//        val menuAdapter =
//            ArrayAdapter(this, R.layout.simple_listview_item, MenuEnum.values().map { it.title })
//

        binding.menuGrid.apply {
            adapter = object : ArrayAdapter<String>(this@ChromeActivity,
                R.layout.simple_gridview_item,
                MenuEnum.values().map { it.title }) {

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView

                    view.setCompoundDrawablesWithIntrinsicBounds(
                        null,
                        resources.getDrawable(MenuEnum.entries[position].icon, null),
                        null,
                        null
                    )
                    val h = holderController.currentGroup?.getCurrent()
                        ?: throw KotlinNullPointerException()

                    menuTitleMap[position]?.let { it(h, view) }

                    return view
                }
            }

            onItemClickListener = mainMenuModel.mainMenuEventListener

        }
//
//        binding.menuList.apply {
//
//            setOnFocusChangeListener { v, hasFocus ->
//                if (!hasFocus) mainMenuModel.hideMenu()
//            }
////            adapter = menuAdapter
//
////            onItemClickListener = mainMenuModel.mainMenuEventListener
//
//        }


        binding.menuButton.setOnClickListener {
            mainMenuModel.toogleMemu()
        }

    }

    private fun initTabModel() {
        holderController.changedListener = object : ChangedListener {
            override fun onTabRemoved() {
                clearNavigationChangedCallback()
            }

            override fun onTabAdded() {
                clearNavigationChangedCallback()
            }

            override fun onTabReplaced() {}

            override fun onTabSelected(oldV: Int, newV: Int) {
                this@ChromeActivity.onTabDataChanged()
            }

            override fun onTabDataChanged(
                h: WebViewHolder, g: GroupWebViewHolder, changedType: Int
            ) {

                if (holderController.currentGroup?.getCurrent() == h && holderController.showLength == holderController.size) {
                    uiModel.updateUI(h, g, changedType)
                }
            }

        }

        holderController.groupHolderListener = object : GroupHolderListener {

            override fun onTabGroupSelected(
                oldG: Int, newG: Int
            ) {
                binding.tabList.setItemChecked(newG, true)

                onTabDataChanged()
            }

            override fun onTabGroupRemoved(position: Int) {
                clearNavigationChangedCallback()
            }

            override fun onTabGroupAdded() {
                if (holderController.size > 1) binding.tablistBtn.animate()
                    .translationY(-binding.tablistBtn.height * 0.2f)

                clearNavigationChangedCallback()
            }

            override fun onReloadTabInProxy() {

                val curGroup = holderController.currentGroup

                val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()

                if (webViewHolder.isLoading) {
                    webViewHolder.webView?.get()?.stopLoading()

                    webViewHolder.change {
                        it.progress = 100
                        it.isLoading = false
                    }
                } else {
                    webViewHolder.webView?.get()?.reload()
                }
            }

            override fun onLoadUrl(url: String) {
                val curGroup = holderController.currentGroup

                val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()
                webViewHolder.webView?.get()?.loadUrl(url)
            }
        }

    }

    private fun enableStrictMode() {

        StrictMode.setThreadPolicy(ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(
            VmPolicy.Builder().detectAll().setClassInstanceLimit(this.javaClass, 1).penaltyLog()
                .build()
        )
    }

    private fun printDebugInfo() {

        Log.d("group", J.repeat("-", 10))
        for (i in holderController.indices) {
            val k = holderController[i].groupArr
            var ii = "$i -"
            for (j in k.indices) {
                val star = if (j == holderController[i].curIdx) {
                    "**"
                } else {
                    "  "
                }
                Log.d("group", "$ii $j $star\t${k[j].title} ${k[j].uuid}")
                ii = "  |"
            }
        }

        Log.d("cachedFragment", J.repeat("=", 5))
        for (i in cachedWebViewFragment) {
            Log.d("cachedFragment", i.uuidString)
        }

        supportFragmentManager.fragments.forEach {

            val vis = if (it.isVisible) {
                "Visible"
            } else {
                "\t"
            }

            val resume = if (it.isResumed) {
                "resumed"
            } else {
                "stopped"
            }
            val cached = if (cachedWebViewFragment.none { i -> i.uuidString == it.tag }) {
                "\t"
            } else {
                "cached"
            }
            Log.d("group", "${it.tag}\t$resume\t$vis\t$cached")
        }

    }

    fun clearNavigationChangedCallback() {
        navigationChangedCallback.forEach {
            ignore {
                it()
            }
        }
        navigationChangedCallback.clear()
    }

    fun onTabDataChanged() {
        clearNavigationChangedCallback()

        uiModel.updateUI()
    }


    private fun restoreTabGroup() {

        supportFragmentManager
        if (holderController.restoreGroupHolder.hs.isEmpty() || holderController.restoreGroupHolder.hs.size != holderController.restoreGroupHolder.hb.size) return
        val g = GroupWebViewHolder()
        for (i in holderController.restoreGroupHolder.hs.indices) {
            val hsi = holderController.restoreGroupHolder.hs[i]
            val hbi = holderController.restoreGroupHolder.hb[i]
            g.groupArr.add(WebViewHolder(hsi, startLoadingUri = hsi).apply {
                dummy = true
                savedState = hbi
            })

        }
        g.curIdx = holderController.restoreGroupHolder.curIdx
        holderController.restoreGroupHolder.hs.clear()
        holderController.restoreGroupHolder.hb.clear()
        holderController.restoreGroupHolder.curIdx = 0

        g.changedListener = holderController

        val toShowHolder = g.getCurrent()!!

        val curH = holderController.currentGroup?.getCurrent() ?: throw KotlinNullPointerException()
        holderController.add(g, 0)

        supportFragmentManager.commit {
            supportFragmentManager.findFragmentByTag(curH.uuidString)?.let {
                hide(it)
            }

            add<WebviewFragment>(R.id.webview_box, toShowHolder.uuidString)

        }
        tabListModel.updateUI()
    }

    private fun initFragment(it: WebView, h: WebViewHolder) {
        if (URLUtil.isAssetUrl(h.loadingUrl)) it.addJavascriptInterface(
            myJSInterface, "Android"
        )
        it.addJavascriptInterface(clipboardJSInterface, "MyHistory")

        val msg = h.initMsg
        if (msg != null) {
            // 即便设置了userAgentString，请求时useragent仍为默认值
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

    private fun menuItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {

        when (id.toInt()) {
            MenuEnum.SETTINGS.ordinal -> {
                settingLauncher.launch(
                    Intent(
                        applicationContext, SettingsActivity::class.java
                    ).setAction(SettingsFragment.KEY)
                )
            }

            MenuEnum.SITE_SETTING.ordinal -> {
                val curGroup = holderController.currentGroup
                val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()

                webViewHolder.webView?.get()?.let {

                    val u = it.url ?: return@let
                    val newUrl = SafeURI.parse(u) ?: return@let
                    val siteSetting = setting.siteSettings[newUrl.authority]
                    val userAgentString =
                        siteSetting?.user_agent ?: setting.user_agent
                    val cacheNavigation =
                        siteSetting?.cache_navigation ?: false
                    val allowGoOutSite =
                        siteSetting?.allow_go_outside ?: true
                    settingLauncher.launch(
                        Intent(
                            applicationContext, SettingsActivity::class.java
                        ).setAction(SiteSettingsFragment.KEY).putExtra("HOST", newUrl.authority)
                            .putExtra(setting::user_agent.name, userAgentString)
                            .putExtra("enabled", siteSetting != null)
                            .putExtra(setting::cache_navigation.name, cacheNavigation)
                            .putExtra(setting::allow_go_outside.name, allowGoOutSite)
                    )
                }
            }

            MenuEnum.BOOKMARK.ordinal -> {
                val h =
                    holderController.currentGroup?.getCurrent()
                        ?: throw KotlinNullPointerException()

                mainScope.launch {
                    val ok = bookMarkModel.toggleIt(h)
                    val msg = if (ok) {
                        "Bookmark Added"
                    } else {
                        "Bookmark Removed"
                    }
                    Toast.makeText(this@ChromeActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }

            MenuEnum.VIEW_SOURCE.ordinal -> {
                val curGroup = holderController.currentGroup

                val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()

                val h = webViewHolder.startLoadingUri

                uiModel.newGroupTab("view-source:$h")
            }

            MenuEnum.CLEAR_CACHE.ordinal -> {
                val curGroup = holderController.currentGroup
                val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()
//                            holderArr[currentIdx]
                webViewHolder.webView?.get()?.let {

                    ignore {
                        val selectedItems = BooleanArray(4)
                        with(AlertDialog.Builder(this@ChromeActivity)) {
                            setTitle("Clear browsing data")
                            setMultiChoiceItems(
                                arrayOf("Cache", "History", "Cookie", "All site data"),
                                selectedItems
                            ) { dialog, which, isChecked ->
                                run {
                                    selectedItems[which] = isChecked
                                }
                            }

                            setPositiveButton(android.R.string.ok) { dialog, id ->
                                run {
                                    if (selectedItems[0]) {
                                        it.clearCache(true)
                                        pendingDelete.add(
                                            File(
                                                this@ChromeActivity.dataDir,
                                                "app_webview_${packageName}/Default/Service Worker"
                                            )
                                        )
                                        pendingDelete.add(
                                            File(
                                                this@ChromeActivity.cacheDir, "http"
                                            )
                                        )
                                    }
                                    if (selectedItems[1]) {
                                        historyManager.clearAllHistory()
                                    }
                                    if (selectedItems[2]) {
                                        android.webkit.CookieManager.getInstance()
                                            .removeAllCookies(null)
                                        cookieManager.cookieStore.removeAll()

                                    }
                                    if (selectedItems[3]) {
                                        WebStorage.getInstance().deleteAllData()
                                        pendingDelete.add(
                                            File(
                                                this@ChromeActivity.dataDir,
                                                "app_webview_${packageName}/Default"
                                            )
                                        )
                                    }
                                }
                            }
                            setNegativeButton(android.R.string.cancel, null)
                            create()
                        }.let {
                            it.window?.decorView?.windowInsetsController?.hide(WindowInsets.Type.navigationBars())
                            it.show()
                        }

//                                        File(applicationContext.cacheDir, "http").deleteOnExit()
                        ///data/data/com.example.clock/app_webview_com.example.clock/Default/Service Worker
//                                        File(applicationContext.dataDir, "app_webview_${packageName}").deleteOnExit()
                    }

                }
            }

            MenuEnum.HISTORY.ordinal -> {
                val intent = Intent(
                    this, HistoryActivity::class.java
                ).putExtra("page", TAB_TITLES[1])

                startHistory.launch(intent)
            }

            MenuEnum.FIND.ordinal -> {
                if (!searchBoxInflated) {
                    searchBoxInflated = true
                    binding.searchStub.inflate()
                }
                searchModel.isShown = true
            }

            MenuEnum.READ_MODE.ordinal -> {
                setting.read_mode = !setting.read_mode
                if (view is TextView) {
                    view.setTextColor(
                        if (setting.read_mode) {
                            Color.GREEN
                        } else {
                            Color.BLACK
                        }
                    )
                }
                Toast.makeText(this, "Read mode: ${setting.read_mode}", Toast.LENGTH_SHORT).show()
            }

            MenuEnum.VIEW_CURRENT_SOURCE.ordinal -> {
                holderController.currentGroup?.getCurrent()?.webView?.get()
                    ?.evaluateJavascript("document.documentElement.outerHTML") {

                        mainScope.launch(Dispatchers.IO) {

                            try {
                                val chars = translateEscapes(it)
                                    ?: throw Exception("Can't get source code on this site.")

                                val s = if (chars.length > 2) {
                                    val to = if (chars.chars[0] == '"') 1 else 0

                                    val length =
                                        chars.length - to - if (chars.chars[chars.length - 1] == '"') 1 else 0

                                    String(chars.chars, to, length)
                                } else {
                                    it
                                }

                                val msg = delayLoadingHandler.obtainMessage(
                                    1, WebViewTransport(
                                        LoadDataUrl(
                                            null, s, "text/plain", null, null
                                        )
                                    )
                                )

                                withContext(Dispatchers.Main) {
                                    uiModel.newGroupTab("", msg)
                                }
                            } catch (e: Throwable) {
                                e.printStackTrace()
                                uiModel.makeToast(
                                    "Failed:\n${e.message}", MyToast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
            }

            MenuEnum.AD_PICK.ordinal -> {
                if (!adPickInflated) {
                    binding.adPickStub.inflate()
                    adPickInflated = true
                }
                adPickModel.toogleShow()
            }

            MenuEnum.PC_MODE.ordinal -> {
                holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                    val u = it.originalUrl
                    if (u != null) {

                        val h = WebViewHolder(u)
                        h.disposable = true
                        h.pc_mode = true
                        uiModel.newGroupTab(h)
                    }
                }
            }

            MenuEnum.LOAD_RESOURCE.ordinal -> {
                holderController.currentGroup?.getCurrent()?.let {
                    val loadedResources = it.loadedResources
                    mainScope.launch(Dispatchers.Default) {

                        val buf = StringBuilder(loadedResources.size * 100 + 500)

                        buf.append(
                            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />" + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" + "<title></title></head><body><div id=\"app\"><pre style=\"line-height:150%;white-space:pre-wrap;overflow-wrap: anywhere;\">"
                        )
                        it.loadedResources.forEach {
                            buf.append(it).append('\n')
                        }

                        buf.append("</p></div></body></html>")
                        val msg = delayLoadingHandler.obtainMessage(
                            1, WebViewTransport(
                                LoadDataUrl(
                                    null, buf.toString(), "text/html", null, null
                                )
                            )
                        )

                        withContext(Dispatchers.Main) {
                            uiModel.newGroupTab("", msg)
                        }
                    }

                }
            }

            MenuEnum.RESTORE_TAB.ordinal -> {
                restoreTabGroup()
            }

            MenuEnum.EXIT.ordinal -> finish()

            MenuEnum.QRCODE.ordinal -> {
                ignore {

                    val intentIntegrator = IntentIntegrator(this)

                    val alertDialog = intentIntegrator.initiateScan(IntentIntegrator.QR_CODE_TYPES)
                }
            }

            MenuEnum.FULL_SCREEN.ordinal -> {

                setting.isFullScreen = !setting.isFullScreen
                uiModel.requestFullscreen(setting.isFullScreen)
            }
        }

        binding.menuBox.animate().alpha(0f).apply {
            duration = 150
        }.withEndAction {
            mainMenuModel.hideMenu()

        }

    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.let {
            it.contents?.let {
                uiModel.makeToast("Go to:\n$it", MyToast.LENGTH_LONG).setAction("YES") {
                    urlEditModel.doAction(it)
                }.show()
            }
        }

    }

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int) {

        if (intent.action == Intent.ACTION_WEB_SEARCH) {
            intent.getStringExtra(SearchManager.QUERY)?.let {
                uiModel.newGroupTab(J.concat(setting.search_url, it))
            }
        } else {
            super.startActivityForResult(intent, requestCode)
        }
    }

    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?, type: Int
    ): ActionMode? {
        val actionMode = super.onWindowStartingActionMode(callback, type)
        if (type == ActionMode.TYPE_FLOATING) {
            actionMode?.menu?.add(-8922841, 0, Menu.FIRST, "粘贴并搜索")
        }

        return actionMode
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        if (mode?.menu != null && mode.menu.size > 0) mode.menu[0].run {
            if (title == "打开") {
                var selectText = ""
                holderController.currentGroup?.getCurrent()?.webView?.get()
                    ?.evaluateJavascript("window.getSelection().toString()") {
                        selectText = it.trim('"')
                    }

                setOnMenuItemClickListener {
                    mode.finish()
                    if (selectText.isNotEmpty()) {
                        if (selectText.startsWith("http")) uiModel.newGroupTab(selectText)
                    } else {
                        holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                            it.evaluateJavascript("window.getSelection().toString()") {
                                val url = it.trim('"')
                                if (url.startsWith("http")) uiModel.newGroupTab(url)
                            }
                        }
                    }
                    true
                }
            }
        }

        super.onActionModeStarted(mode)
    }

    override fun onDestroy() {
        super.onDestroy()
        delayLoadingHandler.removeCallbacksAndMessages(null)
        ignore {
            pendingDelete.forEach {
                it.deleteRecursively()
            }
        }
    }

}
