package com.example.clock.ui.model

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.DataSetObserver
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.clock.R
import com.example.clock.databinding.ActivityChromeBinding
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.tab.manager.GroupWebViewHolder
import com.example.clock.tab.manager.HolderController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

interface TabListEventListener {

    fun onCloseTabList()
    fun onShowTabList()
    fun onUpdateUI()
}

class TabListModel {

    var tabListEventListener: TabListEventListener? = null

    var isShown = false

    fun toggleTabListStatus() {
        if (isShown) {
            closeTabList()
        } else {
            showTabList()
        }
    }

    fun showTabList() {
        if (isShown) return
        isShown = true
        tabListEventListener?.onShowTabList()
    }

    fun closeTabList() {
        if (!isShown) return
        isShown = false
        tabListEventListener?.onCloseTabList()
    }

    fun updateUI() {
        tabListEventListener?.onUpdateUI()
    }
}


@SuppressLint("ClickableViewAccessibility")
fun initTabListModel(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    binding: ActivityChromeBinding,
    tabListModel: TabListModel,
    uiModelListener: UIModelListener,
    setting: GlobalWebViewSetting,
    holderController: HolderController,
    exclusiveModel: ExclusiveModel
) {

    binding.tablistBtn.apply {

        setOnClickListener {
            tabListModel.toggleTabListStatus()
        }

        animate().apply {
            duration = 250
            this.setInterpolator {
                -8 * it * (it - 1)
            }

            setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    translationY = 0f
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }

        setOnLongClickListener {
            it.animate().translationY(-0.2f * it.height)
            uiModelListener.newGroupTab(setting.home_page)
            true
        }
    }

    binding.tablistBox.setOnClickListener {
        tabListModel.closeTabList()
    }


    binding.addTab.setOnClickListener {
        uiModelListener.addTab()
    }

    var tabAdapter: ArrayAdapter<GroupWebViewHolder>

    binding.tabList.apply {

        this.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                Toast.makeText(context, position.toString(), Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.tablistBox.visibility = View.GONE

        var isTranslation = false

        var isItemGesture = false
        var curView: View? = null
        var curPosition = 0
        var animateJob: Job? = null
        val tabGesture = GestureDetector(context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(
                    e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
                ): Boolean {

                    if (e1 != null) {
                        val deltaX = e2.x - e1.x
                        curView?.translationX = deltaX //.coerceAtLeast(0f)
                        return true
                    }
                    isItemGesture = true
                    return true

                }

                override fun onFling(
                    e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
                ): Boolean {

                    isItemGesture = true

                    val localCurPosition = curPosition
                    if (e1 != null && (e2.x - e1.x).absoluteValue > 200f && velocityX.absoluteValue > 100f && curPosition >= 0) {
                        animateJob?.cancel()

                        curView?.let {
                            val width = resources.displayMetrics.widthPixels.toFloat()
                            val d = if (it.translationX < 0) {
                                (-width + it.translationX) * 0.1f
                            } else {
                                (width - it.translationX) * 0.1f
                            }

                            lifecycleOwner.lifecycleScope.launch {
                                for (i in 0..10) {
                                    it.translationX += d
                                    delay(16)
                                }
                                it.isPressed = false
//                                it.translationX = 0f

                                uiModelListener.closeTab(localCurPosition)
                            }
                        }
                        curView = null
//                    curView?.translationX = 0f
                    }
                    return true
//                return super.onFling(e1, e2, velocityX, velocityY)
                }
            })


        val viewTouchListener = View.OnTouchListener { v, ev ->
            run {

                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        curView = v
                        val position = v.tag as? Int ?: pointToPosition(ev.x.toInt(), ev.y.toInt())
                        curPosition = position

                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        curPosition = -1
                    }
                }

                false
            }

        }

        val closeDrawable = resources.getDrawable(R.drawable.outline_close_24, null).apply {
            setBounds(0, 0, 48, 48)
        }
        tabAdapter = object : ArrayAdapter<GroupWebViewHolder>(
            context,
            R.layout.simple_tablist_item,
            R.id.tab_title,
            holderController.getMutableData(),
        ) {
            override fun getItemId(position: Int): Long {
                return holderController[position].getCurrent()!!.uuid.toLong()
            }

            override fun hasStableIds(): Boolean {
                return true
            }


            @SuppressLint("ClickableViewAccessibility")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                view.translationX = 0f
//                    parent.isPressed = false
//                    view.isPressed = false
//                    view.clearFocus()
                view.tag = position
                if (convertView == null) {
                    view.setOnTouchListener(viewTouchListener)
                }
                (view as? TextView)?.let {

                    it.setCompoundDrawables(
                        holderController[position].getCurrent()?.iconBitmapDrawable,
                        null,
                        closeDrawable,
                        null
                    )
                }

//                Log.d("TextView", "getView: $position ${view.hashCode()} ${convertView != null}")
                return view
            }
        }.apply {

            registerDataSetObserver(object : DataSetObserver() {
                override fun onChanged() {

                    holderController.showLength = holderController.size
                    uiModelListener.updateUI()
                    isTranslation = false
                }
            })
        }

//        tabAdapter.notifyDataSetChanged()

//            android.R.layout.simple_list_item_checked
        adapter = tabAdapter
        var lastX = -1f
        setOnTouchListener { v, event ->
            run {
                lastX = event.x

                tabGesture.onTouchEvent(event)
//                    if (curView != null && curView?.translationX != 0f) {
//                        v = true
//                    }
                val isGesture = isItemGesture
                val c = curView
                if (c != null && c.translationX != 0f) {
                    isTranslation = true
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isTranslation = false
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        curView?.let {
                            if (animateJob != null) {
                                animateJob?.cancel()
                            }
                            animateJob = lifecycleOwner.lifecycleScope.launch {
                                val d = -it.translationX * 0.1f
                                for (i in 0..10) {
                                    it.translationX = (it.translationX + d)
                                    delay(16)

                                }
                                it.translationX = 0f
//                                it.isPressed = false
                            }

                        }
//                            curView?.translationX = 0f
                        curView = null
//                            v = false
//                        curPosition = -1
                        isItemGesture = false
                        if (isTranslation) {
                            event.action =
                                event.action and MotionEvent.ACTION_MASK.inv() or MotionEvent.ACTION_CANCEL
                        }
                    }
                }
                //curView != null ||isGesture
                return@run isItemGesture // event.actionMasked != MotionEvent.ACTION_CANCEL
            }
        }

        var offsetX = 0
        var viewWidth = 0
        setOnItemClickListener { parent, view, position, id ->
            run {

                if (isTranslation) {
                    return@run
                }
//                val closeBtn = view.findViewById<View>(R.id.close_tab)
                if (offsetX == 0 || viewWidth != view.width) {
                    val view = view.findViewById<TextView>(R.id.tab_title)
                    viewWidth = view.width
                    offsetX = viewWidth - closeDrawable.bounds.width() - view.totalPaddingEnd
                }
                if (lastX > offsetX) {
                    uiModelListener.closeTab(position)
                } else {
                    uiModelListener.switchToTab(position)

                    tabListModel.closeTabList()
                }
            }
        }
        setOnCreateContextMenuListener { menu, v, menuInfo ->
            run {

                val info = menuInfo as AdapterContextMenuInfo? ?: return@run

                menu.add(R.string.copy_link).setOnMenuItemClickListener {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val s = tabAdapter.getItem(info.position)?.getCurrent()?.startLoadingUri
                        ?: return@setOnMenuItemClickListener true
                    clipboard.setPrimaryClip(ClipData.newPlainText("WebView", s))
                    true
                }

                menu.add("Close Other Tabs").apply {

                    if (tabAdapter.count <= 1) {
                        isEnabled = false
                    }

                    setOnMenuItemClickListener {

                        val len = tabAdapter.count
                        val cur = info.position
                        for (i in 0..<cur) {
                            uiModelListener.closeTab(0)
                        }
                        for (i in 1..<len - cur) {
                            uiModelListener.closeTab(tabAdapter.count - 1)
                        }
                        true
                    }
                }

                menu.add("Close All Tabs").setOnMenuItemClickListener {
                    val len = tabAdapter.count
                    for (i in 0..<len) {
                        uiModelListener.closeTab(0)
                    }
                    true
                }
            }
        }
    }

    binding.tabList.setItemChecked(0, true)

//    val ani = ObjectAnimator.ofArgb(
//        binding.tablistBox, "backgroundColor", Color.TRANSPARENT, ChromeActivity.MASK_COLOR
//    ).apply {
//        startDelay = 0
//        duration = 160
//    }

    tabListModel.tabListEventListener = object : TabListEventListener {
        override fun onCloseTabList() {
            if (binding.tablistBox.visibility == View.VISIBLE) {
                binding.tablistBox.animate().alpha(0f).withEndAction {
                    binding.tablistBox.visibility = View.GONE
                    binding.tablistBox.alpha = 1f
                }

            }

            exclusiveModel.cancelCallback(tabListModel.hashCode())
        }

        override fun onShowTabList() {

            exclusiveModel.doAndAddCallback(
                tabListModel.hashCode(),
                tabListModel.hashCode() to tabListModel::closeTabList
            )

            if (binding.tablistBox.visibility != View.VISIBLE) {

//                binding.tablistBox.setBackgroundColor(Color.TRANSPARENT)
                binding.tablistBox.alpha = 0f
                binding.tablistBox.visibility = View.VISIBLE
                binding.tablistBox.animate().alpha(1f)
            }

        }

        override fun onUpdateUI() {
            tabAdapter.notifyDataSetChanged()
        }

    }
    tabListModel.updateUI()
}
