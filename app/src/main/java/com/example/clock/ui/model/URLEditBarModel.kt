package com.example.clock.ui.model

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.children
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.clock.R
import com.example.clock.databinding.ActivityChromeBinding
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.tab.manager.HolderController
import com.example.clock.utils.MyToast
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface URLEditEvent {
    fun onStartEdit()
    fun onExitEdit()
    fun onCancelEdit()
    fun onAction(url: String)
    fun onExpand()
    fun onFold()
    fun onShowTool()
}

class URLEditBarModel {

    var isEditing = false
    private var isExpand = false
    var urlEditEventListener: URLEditEvent? = null
    fun cancelEdit() {
        isEditing = false
        doFold()
        urlEditEventListener?.onCancelEdit()
        urlEditEventListener?.onExitEdit()
    }

    fun startEdit() {
        isEditing = true
        urlEditEventListener?.onStartEdit()
    }

    fun doAction(url: String) {
        cancelEdit()
        urlEditEventListener?.onAction(url)
//        urlEditEventListener?.onExitEdit()
    }

    fun toggleExpand() {
        if (isExpand) {
            doFold()
        } else {
            doExapnd()
        }
    }

    fun doExapnd() {
        isExpand = true
        urlEditEventListener?.onExpand()
    }

    fun doFold() {
        isExpand = false
        urlEditEventListener?.onFold()
    }

    fun showTool() {
        urlEditEventListener?.onShowTool()
    }

}


fun initURLEditModel(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    binding: ActivityChromeBinding,
    urlEditModel: URLEditBarModel,
    exclusiveModel: ExclusiveModel,
    uiModelListener: UIModelListener,
    toolBarModel: ToolBarModel,
    holderController: HolderController,
    setting: GlobalWebViewSetting,
    urlSuggestionModel: URLSuggestionModel
) {

    val layoutInflater = LayoutInflater.from(context)
    val suggestAdapter = object : ArrayAdapter<SuggestItem>(
        context, R.layout.simple_history_item, ArrayList<SuggestItem>()
    ) {


        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(
                R.layout.simple_history_item, parent, false
            )


            getItem(position)?.let {
                view.findViewById<TextView>(R.id.text1)?.text = it.title
                view.findViewById<TextView>(R.id.text2)?.text = it.url
            }

            return view
        }
    }

    binding.suggestionList.apply {

        adapter = suggestAdapter

        setOnItemClickListener { parent, view, position, id ->
            run {
                suggestAdapter.getItem(position)?.url?.let { urlEditModel.doAction(it) }
            }
        }
    }


    binding.urlEditToolbox.apply {
//        animate().apply {
//            startDelay = 100
//            duration = 160
//            setListener(null)
//        }
        setOnClickListener {
            urlEditModel.cancelEdit()
        }
    }

//    binding.expandBtn.setOnClickListener {
//        urlEditModel.toggleExpand()
//    }

    binding.expandBtn.movementMethod = LinkMovementMethod.getInstance()
    val spannableString = SpannableStringBuilder("http:// https:// <-  ->  ⋀ ").apply {
        setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                binding.urlEditText.editableText?.let {
                    it.insert(0, "http://")
                }
            }

        }, 0, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                binding.urlEditText.editableText?.let {
                    it.insert(0, "https://")

                }
            }
        }, 8, 16, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                binding.urlEditText.setSelection(0)
            }
        }, 17, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                binding.urlEditText.setSelection(binding.urlEditText.length())
            }
        }, 21, 23, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                urlEditModel.toggleExpand()
            }
        }, 24, 27, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    binding.expandBtn.setText(spannableString, TextView.BufferType.SPANNABLE)
    val canDoGestureFun = { !urlEditModel.isEditing }

    urlEditModel.urlEditEventListener = object : URLEditEvent {
        override fun onStartEdit() {
            binding.urlEditText.isEnabled = true

            binding.urlEditText.requestFocus()
            uiModelListener.showIME(binding.urlEditText)

            exclusiveModel.doAndAddCallback(
                urlEditModel.hashCode(),
                urlEditModel.hashCode() to urlEditModel::cancelEdit
            )

            binding.urlEditToolbox.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).duration = 150
            }

//            binding.urlEditToolbox.visibility = View.VISIBLE

            binding.toolbarBox.children.forEach {
                if (it != binding.loadingBox) {
                    it.visibility = View.GONE
                } else {
//                        binding.urlEdittext.inputType = binding.urlEdittext.inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
            }

            toolBarModel.addCanDoGesture(canDoGestureFun)
        }

        override fun onExitEdit() {
            binding.urlEditText.clearFocus()
            uiModelListener.hideIME(binding.urlEditText.windowToken)

            if (binding.urlEditText.isFocused) {
                binding.urlEditText.clearFocus()
            }
            binding.urlEditToolbox.apply {
                animate().alpha(0f).withEndAction {
                    visibility = View.GONE
                }
            }
            exclusiveModel.cancelCallback(urlEditModel.hashCode())

            binding.urlEditToolbox.visibility = View.GONE

            binding.toolbarBox.children.forEach {
                if (it != binding.loadingBox) {
                    it.visibility = View.VISIBLE
                } else {
//                        binding.urlEdittext.inputType = binding.urlEdittext.inputType xor InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
            }
            binding.urlEditText.isEnabled = false
            toolBarModel.removeCanDoGesture(canDoGestureFun)
            suggestAdapter.clear()
        }

        override fun onCancelEdit() {

            holderController.currentGroup?.getCurrent()?.let {
                if (!binding.urlEditText.text.contentEquals(it.startLoadingUri)) {
                    binding.urlEditText.setText(

                        it.startLoadingUri
                    )
                } else if (binding.urlEditText.selectionStart != 0) {
                    binding.urlEditText.setSelection(0)
                }
            }
        }

        override fun onAction(url: String) {
            var url = url
            if (!url.startsWith("https://", true) && !url.startsWith(
                    "http://"
                ) && !url.startsWith("chrome://", true) && !url.startsWith(
                    "view-source:"
                )
            ) {
                url = StringBuilder(100).append(setting.search_url).append(url).toString()
            }
            val webViewHolder =
                holderController.currentGroup?.getCurrent() ?: throw KotlinNullPointerException()
            webViewHolder.webView?.get()?.loadUrl(url)
        }

        override fun onExpand() {
//                binding.urlEdittext.layoutParams.height =
//                    TypedValue.applyDimension(
//                        TypedValue.COMPLEX_UNIT_SP,
//                        100F,
//                        resources.displayMetrics
//                    )
//                        .toInt()
            binding.urlEditText.apply {

                gravity = Gravity.TOP
                inputType = inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 10
                imeOptions = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()
//                    maxLines = 1
//                    breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
//                    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_FULL
                minHeight = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 200F, resources.displayMetrics
                ).toInt()
            }
//            binding.expandBtn.text = "—"
            binding.suggestionList.visibility = View.GONE
        }

        override fun onFold() {
//                binding.urlEdittext.layoutParams.height = LayoutParams.WRAP_CONTENT
//                binding.urlEdittext.inputType = binding.urlEdittext.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE.inv()
            binding.urlEditText.apply {
                gravity = Gravity.CENTER_VERTICAL
                inputType = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE.inv()
//                    minLines = 1
//                    maxLines = 1

                minHeight = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 30F, resources.displayMetrics
                ).toInt()
            }
//            binding.expandBtn.text = "⋀"
            binding.suggestionList.visibility = View.VISIBLE
        }

        override fun onShowTool() {
            val url = binding.urlEditText.text.toString()
            uiModelListener.makeToast(url, MyToast.LENGTH_SHORT).setAction("COPY") {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                clipboard.setPrimaryClip(ClipData.newPlainText("WebView", url))

            }.show()
        }
    }
    binding.urlEditText.setOnClickListener {
//            it.isEnabled = false

        if (!urlEditModel.isEditing) {
            urlEditModel.startEdit()
        }
    }
//        binding.urlEdittext.onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
//            if (hasFocus) {
//                urlEditModel.startEdit()
//            } else {
////                urlEditModel.cancelEdit()
//            }
//        }

    val searchChannel =
        Channel<String>(Channel.RENDEZVOUS, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    lifecycleOwner.lifecycleScope.launch {
        while (isActive) {
            val it1 = searchChannel.receive()

            val sugg = urlSuggestionModel.getSuggest(it1)

            suggestAdapter.clear()
            if (sugg.isNotEmpty()) {
                suggestAdapter.addAll(sugg)
//                        binding.suggestionList.scrollListBy(1) //.smoothScrollToPosition(sugg.size-1)
            }

            delay(500)
        }
    }

    binding.urlEditText.addTextChangedListener {

        if (it != null && urlEditModel.isEditing) {
            searchChannel.trySend(it.toString())
        }
    }
    binding.urlEditText.setOnEditorActionListener { v, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {

            urlEditModel.doAction(v.text.toString())

            true
        } else {
            false
        }
    }

    binding.urlEditText.setOnLongClickListener {

        if (!urlEditModel.isEditing) {
            urlEditModel.showTool()
            return@setOnLongClickListener true
        }

        false
    }

    val actionModeCallback = object : ActionMode.Callback {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        var lastT: CharSequence = ""
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            lastT = ""
            val p = clipboard.primaryClip
            if (p != null && p.itemCount > 0) {

                lastT = p.getItemAt(0).text
                val title = if (lastT.startsWith("http://") || lastT.startsWith("https://")) {
                    "粘贴并打开"
                } else {
                    "粘贴并搜索"
                }
                menu?.add(0, -8956894, Menu.FIRST, title)
                    ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            if (item?.groupId == 0 && item.itemId == -8956894) {

                if (lastT.isNotEmpty()) {
                    urlEditModel.doAction(lastT.toString())
//                        Toast.makeText(this@ChromeActivity, p.getItemAt(0).text, Toast.LENGTH_SHORT)
//                            .show()
                }
                mode?.finish()
                return true
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {}

    }
    binding.urlEditText.customSelectionActionModeCallback = actionModeCallback
    binding.urlEditText.customInsertionActionModeCallback = actionModeCallback
}
