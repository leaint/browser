package com.example.clock.ui.model

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView.FindListener
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.updateBounds
import androidx.core.view.get
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.clock.R
import com.example.clock.databinding.ActivityChromeBinding
import com.example.clock.internal.J
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.settings.ignore
import com.example.clock.tab.manager.ChangedListener
import com.example.clock.tab.manager.GroupHolderListener
import com.example.clock.tab.manager.GroupWebViewHolder
import com.example.clock.tab.manager.HolderController
import com.example.clock.tab.manager.ShadowWebViewHolder
import com.example.clock.tab.manager.UpdateType
import com.example.clock.tab.manager.WebViewHolder
import com.example.clock.ui.main.WebViewTag
import com.example.clock.ui.main.WebviewFragment
import com.example.clock.utils.MyToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.LinkedList

interface UIModelListener : FindListener {

    fun recycleHolderClearHistory(tag: Int)

    fun onPageStarted(tag: Int, url: String, favicon: Bitmap?): Boolean
    fun onPageFinished(tag: Int, url: String)
    fun runOnUiThread(action: Runnable)
    fun redirectTo(newUrl: String, tag: Int)
    fun newTab(initUrl: String, tag: Int)
    fun addTab(idx: Int = -1)
    fun closeTab(position: Int)
    fun newGroupTab(h: WebViewHolder, background: Boolean = false)
    fun newGroupTab(initUrl: String, initMsg: Message? = null, background: Boolean = false)
    fun goToSpecialURL(url: String)
    fun clearForwardTab(tag: Int)
    fun restoreTabGroup()
    fun setFullScreenViewStatus(view: View?, hidden: Boolean)
    fun updateUI(isNative: Boolean = false)
    fun updateUI(
        h: WebViewHolder, g: GroupWebViewHolder,
        @UpdateType
        changedType: Int, isNative: Boolean = false
    )

    fun findHolder(tag: Int): WebViewHolder?
    fun makeToast(msg: String, length: Int): MyToast
    fun goBackOrClose(): Boolean
    fun goForward()
    fun switchToTab(position: Int)
    fun hideIME(windowToken: IBinder)
    fun showIME(v: View)
    fun onToolbarFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean

    fun showFileChooser(intent: Intent, callback: (result: ActivityResult) -> Unit): Boolean
    fun requestFullscreen(fullscreen: Boolean)
    fun isFullScreen(): Boolean
}

class UIModel(
    private val fragmentActivity: FragmentActivity,
    private val holderController: HolderController,
    private val setting: GlobalWebViewSetting,
    private var tabListModel: TabListModel,
    private val toolBarModel: ToolBarModel,
    private val binding: ActivityChromeBinding,
    private val searchModel: SearchModel,
) :
    UIModelListener {

    private val supportFragmentManager: FragmentManager = fragmentActivity.supportFragmentManager
    private val imm by lazy(LazyThreadSafetyMode.NONE) {
        fragmentActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    private val cachedWebViewFragment = LinkedList<ShadowWebViewHolder>()

    private val closeDrawable =
        fragmentActivity.resources.getDrawable(R.drawable.outline_close_24, null).apply {
            setBounds(0, 0, 48, 48)
        }

    private var _isFullScreen = true

    override fun updateUI(
        h: WebViewHolder, g: GroupWebViewHolder, @UpdateType changedType: Int, isNative: Boolean
    ) {

        var processed = true
        when (changedType) {
            WebViewHolder.UPDATE_PROGRESS -> {
                binding.loadingBar.progress = h.progress
            }

            WebViewHolder.UPDATE_TITLE -> {
                if (holderController.currentIdx < binding.tabList.childCount) (binding.tabList[holderController.currentIdx] as? TextView)?.text =
                    if (isNative) {
                        h.webView?.get()?.let {
                            it.title ?: it.url
                        } ?: ""
                    } else {
                        holderController.currentGroup.toString()
                    }
            }

            WebViewHolder.UPDATE_URL -> {
                h.startLoadingUri.let {
                    if (it.isNotEmpty() && !isNative) {
                        binding.urlEditText.setText(it)
                    }
                }
            }

            WebViewHolder.UPDATE_LOADING_STATUS -> {
                if (h.isLoading && binding.loadingBar.visibility != View.VISIBLE) {
                    binding.loadingBar.visibility = View.VISIBLE
                    binding.loadRefreshBtn.text = "✕"//"Ｘ"
                } else if (!h.isLoading && binding.loadingBar.visibility != View.GONE) {
                    binding.loadingBar.visibility = View.GONE
                    binding.loadRefreshBtn.text = "〇"
                }
            }

            WebViewHolder.UPDATE_ICON -> {

                if (holderController.currentIdx < binding.tabList.childCount) {

                    (binding.tabList[holderController.currentIdx] as? TextView)?.setCompoundDrawables(
                        h.iconBitmapDrawable,
                        null,
                        closeDrawable,
                        null
                    )
                }
            }

            else -> processed = false
        }

        if (processed) return

        if (holderController.currentIdx < binding.tabList.childCount) {
            (binding.tabList[holderController.currentIdx] as? TextView)?.let {
                it.text = if (isNative) {
                    h.webView?.get()?.let {
                        it.title ?: it.url
                    } ?: ""
                } else {
                    holderController.currentGroup.toString()
                }

                it.setCompoundDrawables(
                    h.iconBitmapDrawable, null, closeDrawable, null
                )
            }
        }
        binding.loadingBar.progress = h.progress
        h.startLoadingUri.let {
            if (it.isNotEmpty() && !isNative) {
                binding.urlEditText.setText(it)
            } else {
//                                    holderArr[currentIdx]
                h.webView?.get()?.let {
                    binding.urlEditText.setText(it.url)
                }
            }
        }

        binding.tablistBtn.text = "[${holderController.size}]"
        if (h.isLoading && binding.loadingBar.visibility != View.VISIBLE) {
            binding.loadingBar.visibility = View.VISIBLE
            binding.loadRefreshBtn.text = "✕"
        } else if (!h.isLoading && binding.loadingBar.visibility != View.GONE) {
            binding.loadingBar.visibility = View.GONE
            binding.loadRefreshBtn.text = "〇"//"Ｏ"
        }
        binding.tabList.setItemChecked(holderController.currentIdx, true)

    }


    override fun updateUI(isNative: Boolean) {
        holderController.currentGroup?.let { g ->
            g.getCurrent()?.let {
                updateUI(
                    it, g, WebViewHolder.UPDATE_ALL, isNative
                )
            }
        }
    }

    override fun hideIME(windowToken: IBinder) {
        if (fragmentActivity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true) {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    override fun showIME(v: View) {
        imm.showSoftInput(v, 0)
    }

    override fun goForward() {

        val curGroup = holderController.currentGroup

        val curHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()

        val w = curHolder.webView?.get()
        if (w != null && w.canGoForward()) {
            w.goForward()
            updateUI(true)
            return
        }

        if (curGroup.canGoForward()) {

            val w = curHolder.webView?.get()
            if (w != null && w.canGoForward()) {
                w.goForward()
                updateUI(true)
                return
            }

            val nextHolder = curGroup.goForward() ?: throw KotlinNullPointerException()

            val transaction = supportFragmentManager.beginTransaction()

            supportFragmentManager.findFragmentByTag(curHolder.uuidString)?.let {
                transaction.hide(it)
            }
            supportFragmentManager.findFragmentByTag(nextHolder.uuidString)?.let {
                transaction.show(it)
            }
            transaction.commit()
        }
//            holderArr[currentIdx].webView?.get()?.goForward()
    }

    override fun goBackOrClose(): Boolean {
        val curG = holderController.currentGroup
        val h = curG?.getCurrent() ?: throw KotlinNullPointerException()

        val webView = h.webView?.get() ?: return false

        if (webView.canGoBack()) {
            webView.goBack()
            updateUI(true)
            return true
        }

        if (holderController.size == 1 && curG.curIdx == 0) {
            return false
        }
        if (curG.canGoBack()) {
//            val lastCur = currentIdx
//            val h = holderArr[lastCur]

//            if (currentIdx >= holderArr.size - 1) { //lastone
//                currentIdx--
//            }
            val backItem = curG.goBack()

            if (backItem != null) {
                val transaction = supportFragmentManager.beginTransaction()

                supportFragmentManager.findFragmentByTag(backItem.uuidString)?.let {
                    transaction.hide(it)
                }
                val h = curG.getCurrent() ?: throw KotlinNullPointerException()

//            removeTab(lastCur)
                if (h.dummy) {
                    h.dummy = false
                    transaction.add<WebviewFragment>(R.id.webview_box, h.uuidString)
                } else {
                    supportFragmentManager.findFragmentByTag(h.uuidString)
                        ?.let { transaction.show(it) }
                }
                transaction.commit()

//                tabAdapter.notifyDataSetChanged()
            } else {
                val transaction = supportFragmentManager.beginTransaction()
                removeTabGroup(holderController.currentIdx, transaction)
                transaction.commit()
            }
        } else {
            val transaction = supportFragmentManager.beginTransaction()

            val position = holderController.currentIdx
            if (holderController.currentIdx >= holderController.size - 1) { //lastone
                holderController.currentIdx--
            }

            removeTabGroup(position, transaction)

            if (holderController.currentIdx < 0 || holderController.isEmpty()) { // the first new one

                val h = WebViewHolder(setting.INIT_URI, newTask = true)
                val g = GroupWebViewHolder()
                g.addTab(h)
                holderController.add(g)
                transaction.add<WebviewFragment>(R.id.webview_box, h.uuidString)

                holderController.currentIdx = 0

            }

            val curGroup = holderController.currentGroup
            val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()

            supportFragmentManager.findFragmentByTag(webViewHolder.uuidString)
                ?.let { transaction.show(it) }
//            tabAdapter.notifyDataSetChanged()

            transaction.commit()
        }
        fragmentActivity.lifecycleScope.launch {
            delay(250)
            tabListModel.updateUI()
        }
//        tabListModel.updateUI()

        return true
    }

    private fun popFromCache(): ShadowWebViewHolder? {
        if (cachedWebViewFragment.isEmpty()) return null

        return cachedWebViewFragment.pop()
    }

    private fun removeToCache(h: WebViewHolder): Boolean {

        if (cachedWebViewFragment.size >= setting.cached_tab_count || h.initMsg != null || h.disposable) {
            return false
        }
        val w = h.webView?.get() ?: return false

        h.iconBitmapDrawable = null
        h.recycleInit = true
        h.loadingUrl = ""
        h.startLoadingUri = ""
        h.webView?.clear()

        val newH = ShadowWebViewHolder(WeakReference(w), h.uuid, h.uuidString)

        w.stopLoading()
        (w.tag as? WebViewTag)?.enable = false
        w.loadUrl("about:blank")
        w.clearView()
        cachedWebViewFragment.add(newH)
        return true
    }

    private fun removeTabGroup(idx: Int, trans: FragmentTransaction) {
        val curGroup = holderController[idx]

        val hs = holderController.restoreGroupHolder.hs
        val hb = holderController.restoreGroupHolder.hb
        hb.clear()
        hs.clear()
        holderController.restoreGroupHolder.curIdx = curGroup.curIdx
        curGroup.clear().forEach {
            val hh = it
            hs.add(it.startLoadingUri)
            hb.add(Bundle().apply {
                it.webView?.get()?.saveState(this)
            })
            supportFragmentManager.findFragmentByTag(it.uuidString)?.let {
                if (removeToCache(hh)) {
                    trans.hide(it)
                } else {
                    trans.remove(it)
                }
            }
        }

        holderController.removeAt(idx)
//        tabListModel.updateUI()
    }

    override fun closeTab(position: Int) {
        if (position !in holderController.indices) {
            Log.e("closeTab", "position:$position")
            return
        }
        // Close tab
        val transaction = supportFragmentManager.beginTransaction()
        // clear
        var isSingle = false
        if (holderController.size <= 1) {
            if (holderController.size == 1) {
                isSingle = true
                val hs = holderController.restoreGroupHolder.hs
                val hb = holderController.restoreGroupHolder.hb
                hb.clear()
                hs.clear()
                holderController.restoreGroupHolder.curIdx = holderController.currentGroup!!.curIdx
                holderController.first().clear().forEach {
                    hs.add(it.startLoadingUri)
                    hb.add(Bundle().apply {
                        it.webView?.get()?.saveState(this)
                    })
                    val hh = it
                    supportFragmentManager.findFragmentByTag(it.uuidString)?.let {
                        if (removeToCache(hh)) {
                            transaction.hide(it)
                        } else {
                            transaction.remove(it)
                        }
                    }
                }
            }

            holderController.clear()

            val h = WebViewHolder(setting.INIT_URI, newTask = true)
            val g = GroupWebViewHolder()
            g.addTab(h)
            holderController.add(g)

            holderController.currentIdx = 0
            transaction.add<WebviewFragment>(R.id.webview_box, h.uuidString)

//            tabAdapter.notifyDataSetChanged()
            tabListModel.closeTabList()
            // TODO: 应该在commit之后调用

        } else {

            if (position == holderController.currentIdx) { // current
                if (holderController.currentIdx >= holderController.size - 1) { //lastone
                    holderController.currentIdx--
                }

//                                supportFragmentManager.findFragmentByTag(holderArr[position].uuidString)
//                                    ?.let { transaction.remove(it) }
                removeTabGroup(position, transaction)

//                tabAdapter.notifyDataSetChanged()
            } else if (position < holderController.currentIdx) {
                holderController.currentIdx--
//
//                                supportFragmentManager.findFragmentByTag(holderArr[position].uuidString)
//                                    ?.let { transaction.remove(it) }
                removeTabGroup(position, transaction)

//                tabAdapter.notifyDataSetChanged()
            } else {

//                                supportFragmentManager.findFragmentByTag(holderArr[position].uuidString)
//                                    ?.let { transaction.remove(it) }
                removeTabGroup(position, transaction)

//                tabAdapter.notifyDataSetChanged()
            }

            if (holderController.currentIdx < 0 || holderController.isEmpty()) { // the first new one

                val h = WebViewHolder(setting.INIT_URI, newTask = true)
                val g = GroupWebViewHolder()
                g.addTab(h)
                holderController.add(g)
                transaction.add<WebviewFragment>(
                    R.id.webview_box, h.uuidString
                )

                holderController.currentIdx = 0
//                tabAdapter.notifyDataSetChanged()

            }

        }

        val curGroup = holderController.currentGroup
        val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()

        supportFragmentManager.findFragmentByTag(webViewHolder.uuidString)
            ?.let { transaction.show(it) }
        transaction.commit()

        if (isSingle) {

            fragmentActivity.lifecycleScope.launch {
                delay(250)
                tabListModel.updateUI()
            }
        } else {

            tabListModel.updateUI()
        }

//        binding.tabList.postDelayed({
//
//        }, 250)
    }

    override fun addTab(idx: Int) {

        if (idx == -1) {
            val h = WebViewHolder(setting.INIT_URI, newTask = true)
            val groupHolder = GroupWebViewHolder()

            groupHolder.addTab(h)
            groupHolder.changedListener = holderController

            val curH =
                holderController.currentGroup?.getCurrent() ?: throw KotlinNullPointerException()

            holderController.add(groupHolder, 0)

            supportFragmentManager.commit {

                supportFragmentManager.findFragmentByTag(curH.uuidString)?.let {
                    hide(it)
                }

                add<WebviewFragment>(R.id.webview_box, h.uuidString)

            }

            tabListModel.closeTabList()

            fragmentActivity.lifecycleScope.launch {
                delay(250)
                holderController.currentIdx = 0
                tabListModel.updateUI()
            }
//            binding.tabList.postDelayed({
//                tabListModel.updateUI()
//            }, 250)

        }

    }

    private var lastMill = 0L
    private var lastTag = 0

    //    val miniNewTabTime = 100L
    override fun newTab(initUrl: String, tag: Int) {
        val oldLastTag = lastTag
        val oldLastMill = lastMill
        lastMill = System.currentTimeMillis()
        lastTag = tag
        if (tag == -1) return

        holderController.findGroup(tag)?.let {
            supportFragmentManager.commit {

                it.getCurrent()?.let {
                    if (lastMill - oldLastMill < 100 && oldLastTag == tag && it.loadingUrl == initUrl) {
                        return
                    }
                    supportFragmentManager.findFragmentByTag(it.uuidString)?.let {
                        hide(it)
                    }
                }

                it.removeElse().forEach {
                    val hh = it
                    supportFragmentManager.findFragmentByTag(it.uuidString)?.let {
                        if (removeToCache(hh)) {
                            hide(it)
                        } else {
                            remove(it)
                        }
                    }
                }

                val cacheH = popFromCache()

                val h = if (cacheH != null) {
                    WebViewHolder(initUrl, uuid = cacheH.uuid).apply {
                        recycleInit = true
                        webView = cacheH.webView
                        webView?.get()?.let {

                            it.invalidateCallback = null
                            (it.tag as? WebViewTag)?.enable = true

                            ignore {
                                val u = Uri.parse(loadingUrl)
                                val ua = setting.siteSettings[u.authority]?.user_agent
                                if (ua != null) {
                                    if (it.settings.userAgentString != ua) {
                                        it.settings.userAgentString = ua
                                    }
                                } else {
                                    it.settings.userAgentString = setting.user_agent
                                }
                            }

                            it.loadUrl(loadingUrl)

                        }
                    }
                } else {
                    WebViewHolder(initUrl)
                }

                it.addTab(h).forEach {
                    supportFragmentManager.findFragmentByTag(it.uuidString)?.let {
                        remove(it)
                    }
                }

                if (cacheH != null) {
                    supportFragmentManager.findFragmentByTag(h.uuidString)?.let {
                        show(it)
                    }
                } else {
                    add<WebviewFragment>(R.id.webview_box, h.uuidString)
                }
            }

        }
    }

    override fun clearForwardTab(tag: Int) {

//
//        if ( window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true) {
//            imm.hideSoftInputFromWindow(binding.urlEdittext.windowToken, 0)
//        }

        //清除可 go forward 的webview
        holderController.findGroup(tag)?.let {
            val trans = supportFragmentManager.beginTransaction()
            it.removeElse().forEach {
                val hh = it
                supportFragmentManager.findFragmentByTag(it.uuidString)?.let {
                    if (!removeToCache(hh)) {
                        trans.remove(it)
                    }
                }
            }
            trans.commit()
        }
    }

    override fun restoreTabGroup() {

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


    override fun setFullScreenViewStatus(view: View?, hidden: Boolean) {
        if (hidden) {
            binding.fullscreenBox.removeAllViews()
            binding.fullscreenBox.visibility = View.GONE
            if (!setting.isFullScreen) {
                requestFullscreen(false)
            }
        } else {
            if (binding.fullscreenBox.childCount > 0) {
                binding.fullscreenBox.removeAllViews()
            }
            if (!setting.isFullScreen) {
                requestFullscreen(true)
            }
            binding.fullscreenBox.addView(view)
            binding.fullscreenBox.visibility = View.VISIBLE
        }
    }

    override fun findHolder(tag: Int): WebViewHolder? {
        return holderController.findHolder(tag)
    }

    override fun newGroupTab(h: WebViewHolder, background: Boolean) {
        val groupHolder = GroupWebViewHolder()

        groupHolder.addTab(h)
        groupHolder.changedListener = holderController

        val curH = holderController.currentGroup?.getCurrent() ?: throw KotlinNullPointerException()
        holderController.add(groupHolder, holderController.currentIdx)

        if (background) {
            supportFragmentManager.commit {
                val fragment = WebviewFragment()
                add(R.id.webview_box, fragment, h.uuidString)
                hide(fragment)
            }
            holderController.currentIdx++

        } else {

            supportFragmentManager.commit {
                supportFragmentManager.findFragmentByTag(curH.uuidString)?.let {
                    hide(it)
                }

                add<WebviewFragment>(R.id.webview_box, h.uuidString)

                supportFragmentManager.findFragmentByTag(h.uuidString)?.let {
                    hide(it)
                }

            }
        }
        tabListModel.updateUI()
    }

    override fun newGroupTab(initUrl: String, initMsg: Message?, background: Boolean) {

        val h = WebViewHolder(initUrl, initMsg = initMsg)

        newGroupTab(h, background)
    }

    override fun goToSpecialURL(url: String) {
        try {
            if (url.startsWith("intent://")) {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                fragmentActivity.startActivity(intent)
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                fragmentActivity.startActivity(intent)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(fragmentActivity, "Can't handle this url:\n$url", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun replaceTab(initUrl: String, tag: Int) {

        val gh = holderController.findGH(tag) ?: return
        val curG = gh.g
        val curH = gh.h

        val newH = WebViewHolder(initUrl, curH.newTask)
        val groupId = curG.groupArr.indexOf(curH)
        curG.groupArr[groupId] = newH

        tabListModel.updateUI()

        supportFragmentManager.commit {
            supportFragmentManager.findFragmentByTag(curH.uuidString)?.let {
                remove(it)
            }
            add<WebviewFragment>(R.id.webview_box, newH.uuidString)
        }
    }

    override fun switchToTab(position: Int) {

        val position = position.coerceIn(0, holderController.size - 1)

        if (position == holderController.currentIdx) return

        val toGroup = holderController[position]

        val toShowWebViewHolder = toGroup.getCurrent() ?: throw NullPointerException()

        val trans = supportFragmentManager.beginTransaction()
        run {

            val curGroup = holderController.currentGroup

            val webViewHolder = curGroup?.getCurrent() ?: throw KotlinNullPointerException()
            supportFragmentManager.findFragmentByTag(webViewHolder.uuidString)?.let {
                trans.hide(it)
            }
        }
        holderController.currentIdx = position

        supportFragmentManager.findFragmentByTag(toShowWebViewHolder.uuidString)?.let {
            trans.show(it)
        }

        trans.commit()
    }

    override fun redirectTo(newUrl: String, tag: Int) {
        // 当设置了webviewtransport 后，loadurl由initmsg调用，不要手动loadurl，否则会有错误：
        // android  Fatal signal 5 (SIGTRAP), code 1 (TRAP_BRKPT), fault addr 0x74d140d010 in tid
        Toast.makeText(
            fragmentActivity, "auto redirect to:\n$newUrl", Toast.LENGTH_SHORT
        ).show()
        replaceTab(newUrl, tag)
    }

    override fun recycleHolderClearHistory(tag: Int) {
        holderController.findHolder(tag)?.let {
            if (it.recycleInit) {
                it.recycleInit = false
                it.webView?.get()?.let {
                    it.post {
                        it.clearHistory()
                    }
                }
            }
        }
    }

    override fun onToolbarFling(
        e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
    ): Boolean {
        if (e1 != null) {
            if (e1.x < 100f && e2.x - e1.x > 500f) {
                toolBarModel.goBack()
            }
        }

        if (velocityY < -10) {
            toolBarModel.hide()
        }
        if (velocityY > 2000) {
            toolBarModel.show()
        }
        return false
    }

    private var fileChooserCallback: ((result: ActivityResult) -> Unit)? = null
    private val showFileChooser =
        fragmentActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            run {
                val tmp = fileChooserCallback
                fileChooserCallback = null
                tmp?.let { it(res) }

            }
        }


    override fun showFileChooser(
        intent: Intent, callback: (result: ActivityResult) -> Unit
    ): Boolean {
        if (fileChooserCallback != null) {
            return false
        }
        fileChooserCallback = callback
        showFileChooser.launch(intent)
        return true
    }

    override fun onFindResultReceived(
        activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean
    ) {
        searchModel.updateResult(activeMatchOrdinal, numberOfMatches, isDoneCounting)
    }

    override fun makeToast(msg: String, length: Int): MyToast {
        return MyToast.make(binding.toastBox, msg, length)
    }

    override fun onPageStarted(tag: Int, url: String, favicon: Bitmap?): Boolean {
        val holder = holderController.currentGroup?.getCurrent()

        val h = if (holder?.uuid == tag) {
            holder
        } else {
            holderController.findHolder(tag) ?: return false
        }
        h.change {
            it.isLoading = true

            it.startLoadingUri = url
            it.iconBitmapDrawable = if (favicon != null) {
                BitmapDrawable(fragmentActivity.resources, favicon).apply {
                    updateBounds()
                }
            } else {
                WebViewHolder.emptyDrawable
            }
        }
        return true
    }

    override fun onPageFinished(tag: Int, url: String) {

        cachedWebViewFragment.find { it.uuid == tag }?.let {
            val h = it
            if (url == "about:blank") {
                it.webView.get()?.let {
                    it.post {
                        it.clearHistory()

                        var idx = 0
                        it.invalidateCallback = {
                            if (idx++ > 100) {
                                it.invalidateCallback = null

                                Log.w(
                                    "WebView", J.concat(
                                        h.uuidString,
                                        " detect a bad webview which is dodraw continuously, remove it."
                                    )
                                )
                                supportFragmentManager.commit {
                                    cachedWebViewFragment.remove(h)
                                    supportFragmentManager.findFragmentByTag(h.uuidString)?.let {
                                        remove(it)
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }

        val webViewHolder = holderController.findHolder(tag) ?: return

        webViewHolder.change(WebViewHolder.UPDATE_LOADING_STATUS) {
            it.isLoading = false
        }
    }

    override fun runOnUiThread(action: Runnable) {
        fragmentActivity.runOnUiThread(action)
    }

    override fun requestFullscreen(fullscreen: Boolean) {

        val window = fragmentActivity.window
        if (fullscreen) {
            window.setDecorFitsSystemWindows(false)
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.decorView.windowInsetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.decorView.windowInsetsController?.hide(WindowInsets.Type.systemBars())
        } else {
            window.setDecorFitsSystemWindows(true)
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.decorView.windowInsetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_DEFAULT
            window.decorView.windowInsetsController?.show(WindowInsets.Type.systemBars())
        }
        window.decorView.requestLayout()
        _isFullScreen = fullscreen
    }

    override fun isFullScreen() = _isFullScreen

}


fun initTabModel(
    holderController: HolderController,
    navigationChangedModel: NavigationChangedModel,
    uiModel: UIModelListener,
    binding: ActivityChromeBinding,
) {
    holderController.changedListener = object : ChangedListener {
        override fun onTabRemoved() {
            navigationChangedModel.clearNavigationChangedCallback()
        }

        override fun onTabAdded() {
            navigationChangedModel.clearNavigationChangedCallback()
        }

        override fun onTabReplaced() {}

        override fun onTabSelected(oldV: Int, newV: Int) {
            navigationChangedModel.clearNavigationChangedCallback()
            uiModel.updateUI()
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

            navigationChangedModel.clearNavigationChangedCallback()
            uiModel.updateUI()
        }

        override fun onTabGroupRemoved(position: Int) {
            navigationChangedModel.clearNavigationChangedCallback()
        }

        override fun onTabGroupAdded() {
            if (holderController.size > 1) binding.tablistBtn.animate()
                .translationY(-binding.tablistBtn.height * 0.2f)

            navigationChangedModel.clearNavigationChangedCallback()
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

