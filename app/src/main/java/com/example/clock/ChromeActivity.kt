package com.example.clock

import android.app.AlertDialog
import android.app.SearchManager
import android.content.Intent
import android.graphics.Color
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
import android.view.KeyEvent
import android.view.Menu
import android.view.RoundedCorner
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowInsets
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.clock.databinding.ActivityChromeBinding
import com.example.clock.databinding.AdPickBinding
import com.example.clock.databinding.SearchBoxBinding
import com.example.clock.internal.J
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.settings.ignore
import com.example.clock.tab.manager.GroupWebViewHolder
import com.example.clock.tab.manager.HolderController
import com.example.clock.tab.manager.WebViewHolder
import com.example.clock.ui.main.LoadDataUrl
import com.example.clock.ui.main.TAB_TITLES
import com.example.clock.ui.main.WebViewTransport
import com.example.clock.ui.model.ADPickViewModel
import com.example.clock.ui.model.BookMarkModel
import com.example.clock.ui.model.ExclusiveModel
import com.example.clock.ui.model.MainMenuEventListener
import com.example.clock.ui.model.MainMenuModel
import com.example.clock.ui.model.MenuEnum
import com.example.clock.ui.model.NavigationChangedModel
import com.example.clock.ui.model.SearchModel
import com.example.clock.ui.model.TabListModel
import com.example.clock.ui.model.ToolBarModel
import com.example.clock.ui.model.UIModel
import com.example.clock.ui.model.UIModelListener
import com.example.clock.ui.model.URLEditBarModel
import com.example.clock.ui.model.URLSuggestionModel
import com.example.clock.ui.model.initAdPickModel
import com.example.clock.ui.model.initFragmentListener
import com.example.clock.ui.model.initSearchModel
import com.example.clock.ui.model.initTabListModel
import com.example.clock.ui.model.initTabModel
import com.example.clock.ui.model.initToolBarModel
import com.example.clock.ui.model.initURLEditModel
import com.example.clock.utils.HistoryManager
import com.example.clock.utils.MyToast
import com.example.clock.utils.SafeURI
import com.example.clock.utils.translateEscapes
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager

class ChromeActivity : FragmentActivity() {

    private val delayLoadingHandler = object : Handler(Looper.myLooper()!!) {

        override fun handleMessage(msg: Message) {
            val obj = msg.obj
            if (msg.what == 1 && obj is WebViewTransport) {
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

    private val mainScope = MainScope()

    private lateinit var uiModel: UIModelListener

    private lateinit var setting: GlobalWebViewSetting

    private lateinit var historyManager: HistoryManager

    private val searchModel = SearchModel()

    private val tabListModel = TabListModel()

    private val mainMenuModel = MainMenuModel()

    private val urlEditModel = URLEditBarModel()

    private lateinit var adPickViewModel: ADPickViewModel

    private lateinit var toolBarModel: ToolBarModel

    private lateinit var bookMarkModel: BookMarkModel

    private lateinit var urlSuggestionModel: URLSuggestionModel

    private lateinit var binding: ActivityChromeBinding

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

    private val startHistory =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it.data?.extras?.let {
//                it.getString("url")?.let {
//                    uiModel.newGroupTab(it)
//                }
                it.getStringArrayList("url_list")?.forEach {
                    uiModel.newGroupTab(it)
                }

                if (it.getBoolean("changed")) {
                    mainScope.launch {
                        setting.reloadBookMark(this@ChromeActivity)
                    }
                }
            }
        }

    private val navigationChangedModel = NavigationChangedModel()

    private val exclusiveModel = ExclusiveModel()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!packageName.endsWith(".release")) {
            enableStrictMode()
        }
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

        urlSuggestionModel = URLSuggestionModel(historyManager, setting)
        ignore {
            CookieHandler.setDefault(cookieManager)
        }

        loadSettingJob = mainScope.launch(Dispatchers.IO) {
            setting.loadSetting(this@ChromeActivity)

            holderController.currentGroup?.getCurrent()?.loadingUrl = setting.INIT_URI

            setting.reloadBookMark(this@ChromeActivity)
        }

        mainScope.launch(Dispatchers.IO) {


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
        window?.decorView?.background = null
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

        uiModel = UIModel(
            this,
            holderController,
            setting,
            tabListModel,
            toolBarModel,
            binding,
            searchModel,
        )

        initFragmentListener(
            holderController,
            supportFragmentManager,
            this,
            setting,
            uiModel,
            loadSettingJob,
            mainScope,
            historyManager,
        )

        initMenuModel()

        initTabModel(
            holderController,
            navigationChangedModel,
            uiModel,
            binding,
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
            exclusiveModel
        )

        initURLEditModel(
            this,
            this,
            binding,
            urlEditModel,
            exclusiveModel,
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

                adPickViewModel = ViewModelProvider(this)[ADPickViewModel::class.java]
                initAdPickModel(
                    this,
                    setting,
                    AdPickBinding.bind(inflated),
                    adPickViewModel,
                    holderController,
                    uiModel,
                    this,
                )
            }
        }
        binding.searchStub.setOnInflateListener { stub, inflated ->
            run {
                stub.setOnInflateListener(null)
                initSearchModel(
                    SearchBoxBinding.bind(inflated),
                    searchModel,
                    navigationChangedModel,
                    holderController,
                    uiModel
                )
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

    fun initMenuModel(
//        mainMenuModel: MainMenuModel,
//        fragmentActivity: FragmentActivity,
//        bookMarkModel: BookMarkModel,
//        holderController: HolderController,
//        binding: ActivityChromeBinding,
//        exclusiveModel: ExclusiveModel,
    ) {

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
//            printDebugInfo(
//                holderController,
//                fragmentActivity.supportFragmentManager,
//
//            )

                exclusiveModel.doAndAddCallback(
                    mainMenuModel.hashCode(), mainMenuModel.hashCode() to mainMenuModel::hideMenu
                )


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

//                binding.menuBox.animate().alpha(1)
            }

            override fun onMenuHide() {
                binding.menuBox.animate().alpha(0f).withEndAction {
                    binding.menuBox.visibility = View.INVISIBLE
                    binding.menuBox.alpha = 1f
                }
                exclusiveModel.cancelCallback(mainMenuModel.hashCode())

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

        binding.menuButton.setOnClickListener {
            mainMenuModel.toggleMenu()
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
                    ?.evaluateJavascript(" document.documentElement.outerHTML") {

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
                                            null,
                                            "<html><head><link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/default.min.css\">\n" +
                                                    "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js\"></script>\n" +
                                                    "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/javascript.min.js\"></script>\n</head><body><div><label >Line wrap<input id=\"line-wrap-control\" onchange=\"onWrap(this)\" type=\"checkbox\" aria-label=\"Line wrap\"></label></div><pre><code class=\"language-html\"></code></pre><xmp id=\"temp\" type=\"text/html\">" + s + "</xmp><script>let pre=document.querySelector('pre');function onWrap(e){if(e.checked){pre.style.whiteSpace = 'pre-wrap';pre.style.overflowWrap='anywhere';}else{pre.style.whiteSpace = 'pre';pre.style.overflowWrap='';}};let a = document.querySelector('code');let b = document.querySelector('#temp'); a.textContent = b.innerHTML;b.remove(); hljs.highlightAll();</script></body></html>",
                                            "text/html",
                                            null,
                                            null
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
                adPickViewModel.toggleShow()
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
                            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />" + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" + "<title></title></head><body><div id=\"app\"><style>p{line-height:150%;white-space:pre-wrap;overflow-wrap: anywhere;user-select:all;}</style>"
                        )
                        it.loadedResources.forEach {
                            buf.append("<p>").append(it).append("</p>")
                        }

                        buf.append("</div></body></html>")
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
                uiModel.restoreTabGroup()
            }

            MenuEnum.EXIT.ordinal -> finish()

            MenuEnum.QRCODE.ordinal -> {
                ignore {

                    val intentIntegrator = IntentIntegrator(this)

                    val alertDialog = intentIntegrator.initiateScan(IntentIntegrator.QR_CODE_TYPES)

                    alertDialog?.show()
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
                urlSuggestionModel.lastSearchKeyword = it
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
                        uiModel.newGroupTab(selectText)
                    } else {
                        holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                            it.evaluateJavascript("window.getSelection().toString()") {
                                val url = it.trim('"')
                                uiModel.newGroupTab(url)
                            }
                        }
                    }
                    true
                }
            }
        }

        super.onActionModeStarted(mode)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toolBarModel.show()
            mainMenuModel.showMenu()
        }
        return super.onKeyDown(keyCode, event)
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
