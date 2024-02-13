package com.example.clock.tab.manager

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Message
import android.util.Log
import androidx.annotation.IntDef
import com.example.clock.ui.main.MyWebView
import com.example.clock.utils.HistoryItemRef
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.UUID


interface TabDataChanged {
    fun onTabDataChanged(h: WebViewHolder, changedType: Int)
}

interface GroupHolderListener {
    fun onTabGroupSelected(oldG: Int, newG: Int)
    fun onTabGroupRemoved(position: Int)
    fun onTabGroupAdded()
    fun onReloadTabInProxy()
    fun onLoadUrl(url: String)
}

interface ChangedListener {
    fun onTabRemoved()
    fun onTabAdded()
    fun onTabReplaced()
    fun onTabSelected(oldV: Int, newV: Int)
    fun onTabDataChanged(h: WebViewHolder, g: GroupWebViewHolder, changedType: Int)
}

class GroupWebViewHolder : TabDataChanged {
    @JvmField
    val groupArr = LinkedList<WebViewHolder>()

    val size: Int
        get() = groupArr.size

    var curIdx = size - 1
        set(value) {
            val v = field
            field = value
            changedListener?.onTabSelected(v, value)
        }

    var changedListener: ChangedListener? = null

    fun removeElse(): Set<WebViewHolder> {
        changedListener?.onTabRemoved()
        val ret = if (size > 0) {
            groupArr.slice(curIdx + 1 until groupArr.size).toSet()
        } else {
            emptySet()
        }
        groupArr.removeAll(ret.toSet())
        return ret
    }

    fun addTab(holder: WebViewHolder): Set<WebViewHolder> {
        holder.tabDataChangedListener = this
        changedListener?.onTabAdded()
        // TODO: 必须 隐藏
        if (groupArr.size == 0 || curIdx == groupArr.size - 1) { //last
            groupArr.add(holder)
            curIdx = groupArr.size - 1
            return emptySet()
        } else {
            val ret = removeElse()
            groupArr.add(holder)
            curIdx = groupArr.size - 1
            return ret
        }
    }

    fun canGoBack() = curIdx > 0
    fun canGoForward() = curIdx < size - 1

    fun goBack(): WebViewHolder? {
        if (groupArr.size == 0) {
            return null
        }
        if (canGoBack()) {
            curIdx--
            return groupArr[curIdx + 1]
        }
        return null

    }

    fun getCurrent(): WebViewHolder? {
        return if (groupArr.size > 0) {
            groupArr[curIdx]
        } else {
            null
        }
    }

    fun goForward(): WebViewHolder? {
        return if (canGoForward()) {
            groupArr[++curIdx]
        } else {
            null
        }
    }

    // TODO: clear check
    fun clear(): Set<WebViewHolder> {
        changedListener?.onTabRemoved()
        return groupArr.toSet()
    }

    override fun onTabDataChanged(h: WebViewHolder, changedType: Int) {
        changedListener?.onTabDataChanged(h, this, changedType)
    }

    override fun toString(): String {
        return groupArr[curIdx].title
    }
}

class ShadowWebViewHolder(
    val webView: WeakReference<MyWebView>,
    val uuid: Int,
    val uuidString: String,
)

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@IntDef(
    WebViewHolder.UPDATE_ALL,
    WebViewHolder.UPDATE_ICON,
    WebViewHolder.UPDATE_LOADING_STATUS,
    WebViewHolder.UPDATE_PROGRESS,
    WebViewHolder.UPDATE_TITLE,
    WebViewHolder.UPDATE_URL
)
@Retention(AnnotationRetention.SOURCE)
annotation class UpdateType

class WebViewHolder(
    var loadingUrl: String,
    @JvmField
    var isLoading: Boolean = false,
    @JvmField
    var startLoadingUri: String = "",
    var initMsg: Message? = null,
    @JvmField
    var newTask: Boolean = false,
    @JvmField
    val uuid: Int = UUID.randomUUID().hashCode(),
) {
    @JvmField
    var webView: WeakReference<MyWebView>? = null

    @JvmField
    val uuidString = uuid.toString()

    @JvmField
    var title = uuidString

    @JvmField
    var progress = 0
    var iconUrl = ""

    var iconBitmapDrawable: Drawable? = emptyDrawable
        set(value) {
            value?.setBounds(0, -2, 48, 48)
            field = value
        }
    val iconBitmap: Bitmap?
        get() = (iconBitmapDrawable as? BitmapDrawable)?.bitmap

    var recycleInit: Boolean = false
    var tabDataChangedListener: TabDataChanged? = null
    var cachedHistoryItem = HistoryItemRef()

    var disposable = false
    var pc_mode = false
    var dummy = false
    var savedState: Bundle? = null
    var loadedResources = ArrayList<String>()

    fun notifyDataChanged(changedType: Int) =
        tabDataChangedListener?.onTabDataChanged(this, changedType)

    inline fun change(changedType: Int = UPDATE_ALL, f: (h: WebViewHolder) -> Unit) {
        f(this)
        notifyDataChanged(changedType)
    }

    override fun toString(): String = title

    companion object {
        const val UPDATE_PROGRESS = 1
        const val UPDATE_TITLE = 1 shl 1
        const val UPDATE_URL = 1 shl 2
        const val UPDATE_LOADING_STATUS = 1 shl 3
        const val UPDATE_ICON = 1 shl 4

        const val UPDATE_ALL = 100
        val emptyDrawable = ColorDrawable(Color.TRANSPARENT).apply {
            setBounds(0, 0, 48, 48)
        }
    }

}

class GroupAndHolder(@JvmField val g: GroupWebViewHolder, @JvmField val h: WebViewHolder)

class RestoreGroupHolder(
    val hs: ArrayList<String> = ArrayList(),
    val hb: ArrayList<Bundle> = ArrayList(),
    var curIdx: Int = 0
)

class HolderController : Collection<GroupWebViewHolder>, ChangedListener {
    /**
     * 标签页组数组
     */
    private val groupHolderArr = ArrayList<GroupWebViewHolder>()

    val restoreGroupHolder = RestoreGroupHolder()

    var groupHolderListener: GroupHolderListener? = null

    var changedListener: ChangedListener? = null

    /**
     * 当前组号
     */
    var currentIdx = 0
        set(value) {
            val v = field
            field = value
            if (v != field) groupHolderListener?.onTabGroupSelected(v, value)
        }

    private var currentTabIdx = 0

    var showLength = 0

    override val size
        get() = groupHolderArr.size

    override fun isEmpty() = groupHolderArr.isEmpty()

    override fun iterator() = groupHolderArr.iterator()

    override fun containsAll(elements: Collection<GroupWebViewHolder>) =
        groupHolderArr.containsAll(elements)

    override fun contains(element: GroupWebViewHolder) = groupHolderArr.contains(element)

    val currentGroup: GroupWebViewHolder?
        get() = groupHolderArr.getOrNull(currentIdx)

    operator fun get(position: Int) = groupHolderArr[position]

    fun removeAt(position: Int) {
        groupHolderArr.removeAt(position)
        groupHolderListener?.onTabGroupRemoved(position)
    }

    fun add(groupWebViewHolder: GroupWebViewHolder) {
        groupWebViewHolder.changedListener = this
        groupHolderArr.add(groupWebViewHolder)
        groupHolderListener?.onTabGroupAdded()
    }

    fun add(groupWebViewHolder: GroupWebViewHolder, index: Int) {
        groupWebViewHolder.changedListener = this
        groupHolderArr.add(index, groupWebViewHolder)
        groupHolderListener?.onTabGroupAdded()
    }

    fun clear() {
        groupHolderArr.clear()
        groupHolderListener?.onTabGroupRemoved(-1)
    }

    fun getMutableData() = groupHolderArr

    fun findHolder(tag: String?): WebViewHolder? {
        return findGH(tag)?.h
    }

    fun findGroup(tag: String?): GroupWebViewHolder? {
        return findGH(tag)?.g
    }


    private var lastHolderInt: GroupAndHolder? = null
    private var lastHolderInt2: GroupAndHolder? = null

    fun findGroup(tag: Int) = findGH(tag)?.g

    fun findHolder(tag: Int): WebViewHolder? {
        return findGH(tag)?.h
    }

    fun findGH(tag: Int): GroupAndHolder? {

        if (lastHolderInt?.h?.uuid == tag) return lastHolderInt
        if (lastHolderInt2?.h?.uuid == tag) return lastHolderInt2

        for (i in groupHolderArr) {
            for (j in i.groupArr) {
                if (j.uuid == tag) {
                    lastHolderInt2 = lastHolderInt
                    lastHolderInt = GroupAndHolder(i, j)
                    Log.d(
                        "HolderCache",
                        "CACHE(INT): $tag -> ${lastHolderInt?.h?.uuidString}, ${lastHolderInt2?.h?.uuidString}, "
                    )

                    return lastHolderInt
                }
            }
        }
        return null
    }

    private fun findGH(tag: String?): GroupAndHolder? {
        if (tag == null) return null

        for (i in groupHolderArr) {
            for (j in i.groupArr) {
                if (j.uuidString == tag) {
                    Log.d("HolderCache", "FOUND: $tag")
                    return GroupAndHolder(i, j)
                }
            }
        }
        Log.d("HolderCache", "404: $tag")

        Thread.dumpStack()

        return null
    }

    fun loadUrl(url: String) {
        groupHolderListener?.onLoadUrl(url)
    }

    fun reloadTab() {
        groupHolderListener?.onReloadTabInProxy()
    }

    override fun onTabRemoved() {
        lastHolderInt2 = null
        lastHolderInt = null
        changedListener?.onTabRemoved()
    }

    override fun onTabAdded() {

        changedListener?.onTabAdded()
    }

    override fun onTabReplaced() {
        changedListener?.onTabReplaced()
    }

    override fun onTabSelected(oldV: Int, newV: Int) {
        currentTabIdx = newV
        changedListener?.onTabSelected(oldV, newV)
    }

    override fun onTabDataChanged(h: WebViewHolder, g: GroupWebViewHolder, changedType: Int) {
        changedListener?.onTabDataChanged(h, g, changedType)
    }

}
