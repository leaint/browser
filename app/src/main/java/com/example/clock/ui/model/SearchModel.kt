package com.example.clock.ui.model

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import com.example.clock.databinding.SearchBoxBinding
import com.example.clock.tab.manager.HolderController
import java.lang.ref.WeakReference

interface SearchEventListener {
    fun onShowSearchBar()

    fun onSearch(keyword: String)

    fun onSearchNext(forward: Boolean)

    fun onCloseSearchBar()

    fun onUpdateUI()

}

class DelayHandler(looper: Looper, target: SearchModel) : Handler(looper) {

    private val target = WeakReference(target)

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            SearchModel.DELAY_SEARCH -> {
                target.get()?.doDelaySearch()
            }
        }
    }
}

class SearchModel {

    companion object {
        const val DELAY_SEARCH = 101
    }

    private val delayHandler = DelayHandler(Looper.myLooper()!!, this)

    private var delayMills = 1200L

    private var lastSearchText = ""
    var currentText: String = ""

    private var idx = 0
    private var numberOfMatches = 0

    val emptyResult
        get() = numberOfMatches == 0

    var searchListener: SearchEventListener? = null

    var isShown = false
        set(value) {
            field = value
            if (field) {
                searchListener?.onUpdateUI()
                searchListener?.onShowSearchBar()
            } else {
                searchListener?.onCloseSearchBar()
                reset()
            }
        }

    private fun reset() {
        lastSearchText = ""
        currentText = ""
        idx = 0
        numberOfMatches = 0
        searchListener?.onUpdateUI()
        delayHandler.removeCallbacksAndMessages(null)
    }

    fun getCountText(): String {

        return if (numberOfMatches > 0) {
            "$idx/$numberOfMatches"
        } else {
            if (currentText.isEmpty()) {
                ""
            } else {
                "0/0"
            }
        }
    }

    internal fun doDelaySearch() {
        if (currentText.isNotEmpty() && currentText != lastSearchText) {
            doSearch(currentText)
            lastSearchText = currentText
        }
    }

    fun doSearch(keyword: String, delayMills: Long = 0L) {
        currentText = keyword

        // TODO: 是否要缓存关键词以便显示搜索栏时查询？
        if (!isShown) return

        if (delayMills == 0L) {
            searchListener?.onSearch(keyword)
        } else {
            delayHandler.sendEmptyMessageDelayed(DELAY_SEARCH, delayMills)
        }
    }

    fun pendSearch(keyword: String) {
        doSearch(keyword, delayMills)
    }


    fun findNext(forward: Boolean) {
        searchListener?.onSearchNext(forward)
    }

    fun updateResult(
        activeMatchOrdinal: Int,
        numberOfMatches: Int,
        isDoneCounting: Boolean
    ) {
        idx = if (numberOfMatches == 0) {
            0
        } else {
            activeMatchOrdinal + 1
        }
        this.numberOfMatches = numberOfMatches
        searchListener?.onUpdateUI()
    }
}

fun initSearchModel(
    binding: SearchBoxBinding, searchModel: SearchModel,
    navigationChangedCallback: ArrayList<() -> Unit>,
    holderController: HolderController,
    uiModelListener: UIModelListener
) {

    binding.searchCount.setTextColor(
        ColorStateList(arrayOf(
            IntArray(1) { android.R.attr.state_enabled }, IntArray(0)
        ), IntArray(2).apply {
            set(0, Color.BLACK)
            set(1, Color.RED)
        })
    )


    searchModel.searchListener = object : SearchEventListener {
        override fun onShowSearchBar() {
            with(binding.searchBox) {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f)
            }
            navigationChangedCallback.add {
                searchModel.isShown = false
            }

            binding.searchText.requestFocus()
            uiModelListener.showIME(binding.searchText)
        }

        override fun onSearch(keyword: String) {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.findAllAsync(keyword)
        }

        override fun onSearchNext(forward: Boolean) {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.findNext(forward)
        }

        override fun onCloseSearchBar() {
//                if (binding.searchBox.visibility != View.GONE)
//                    binding.closeSearch.performClick()

            binding.searchText.clearFocus()
            with(binding.searchBox) {

                animate().alpha(0f)
                    .withEndAction {
                        visibility = View.GONE
                        alpha = 1f
                    }
            }
            uiModelListener.hideIME(binding.searchText.windowToken)

            holderController.currentGroup?.getCurrent()?.webView?.get()?.clearMatches()
        }

        override fun onUpdateUI() {

            binding.searchCount.text = searchModel.getCountText()

            if (binding.searchCount.isEnabled == searchModel.emptyResult) {
                binding.searchCount.isEnabled = !searchModel.emptyResult
            }

            if (!binding.searchText.text.contentEquals(searchModel.currentText)) {
                binding.searchText.setText(searchModel.currentText)
            }
        }

    }

    binding.closeSearch.setOnClickListener {
        searchModel.isShown = false
    }

    binding.searchText.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?, start: Int, count: Int, after: Int
        ) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (s != null) searchModel.pendSearch(s.toString())
        }
    })

    binding.searchText.setOnEditorActionListener { v, actionId, event ->
        run {
            val text = v.text.toString()
            if (actionId == EditorInfo.IME_ACTION_DONE && text.isNotEmpty()) {
                searchModel.doSearch(text)
                return@run true
            }

            false
        }
    }

    binding.nextSearch.setOnClickListener {
        searchModel.findNext(true)
    }
    binding.prevSearch.setOnClickListener {
        searchModel.findNext(false)

    }

}
