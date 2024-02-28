package com.example.clock.ui.model

import androidx.annotation.IntDef
import com.example.clock.internal.J
import com.example.clock.settings.ChangedType
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.utils.HistoryManager
import com.example.clock.utils.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@IntDef(
    SuggestItem.LAST_SEARCH_SUGGEST,
    SuggestItem.CLIP_TEXT_SUGGEST,
    SuggestItem.SEARCH_RESULT_SUGGEST,
    SuggestItem.SEARCH_SUGGEST,
    SuggestItem.HISTORY_SUGGEST
)
@Retention(AnnotationRetention.SOURCE)
annotation class SuggestItemType

class SuggestItem(@SuggestItemType val c: Int, val url: String, val title: String) {

    companion object {
        const val LAST_SEARCH_SUGGEST = -5
        const val CLIP_TEXT_SUGGEST = -4
        const val SEARCH_RESULT_SUGGEST = -3
        const val SEARCH_SUGGEST = -1
        const val HISTORY_SUGGEST = 1
    }
}

class URLSuggestionModel(
    private val historyManager: HistoryManager,
    private val setting: GlobalWebViewSetting
) {

    private var bookMarks = setting.bookMarkArr

    private val itemPattern: Pattern = Pattern.compile("<item><title>(.*?)</link>")

    private val buf = ByteArray(1024)
    private val buf2 = ByteArray(8 * 1024)

    init {
        setting.addChangedListener {
            if (it == ChangedType.BOOKMARK) {
                bookMarks = setting.bookMarkArr
            }
        }
    }

    var lastSearchKeyword = ""

    suspend fun getSuggest(s: String): ArrayList<SuggestItem> {
        var arr = ArrayList<SuggestItem>()
        if (s.isEmpty()) return arr
        withContext(Dispatchers.IO) {

            val searchResultJob = async {
                var count = 3
                val sugs = ArrayList<SuggestItem>(count)
                val searchUrl = "https://cn.bing.com/search?format=rss&rdr=1&q=$s"
                try {

                    val urlConn = URL(searchUrl).openConnection() as HttpURLConnection
                    urlConn.readTimeout = 2000
                    urlConn.connectTimeout = 3000
                    urlConn.setRequestProperty("Accept-Language", "en,zh-CN;q=0.9,zh;q=0.8")
                    urlConn.setRequestProperty("User-Agent", setting.pc_user_agent)

                    if (urlConn.responseCode != 200) {
                        urlConn.inputStream.close()
                        return@async sugs
                    }
                    urlConn.inputStream.use {

                        val len = it.readAtMost(buf2)
                        if (len == -1) return@use

                        val txt = String(buf2, 0, len)

                        val matcher = itemPattern.matcher(txt)
                        while (matcher.find() && count-- > 0) {
                            if (matcher.groupCount() >= 1) {
                                val s = matcher.group(1)
                                val ss = s.split("</title><link>")
                                if (ss.size >= 2) {
                                    sugs.add(
                                        0,
                                        SuggestItem(SuggestItem.SEARCH_RESULT_SUGGEST, ss[1], ss[0])
                                    )
                                }
                            }
                        }

                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }

                return@async sugs

            }

            val searchSuggestionJob = async {
                var count = 5
                val sugs = ArrayList<String>(count)
                try {
                    val url = "https://api.bing.com/osjson.aspx?market=zh-CN&query=$s"
                    val urlConn = URL(url).openConnection() as HttpURLConnection
                    urlConn.readTimeout = 2000
                    urlConn.connectTimeout = 3000
                    urlConn.setRequestProperty("Accept-Language", "en,zh-CN;q=0.9,zh;q=0.8")
                    urlConn.setRequestProperty("User-Agent", setting.pc_user_agent)

                    if (urlConn.responseCode != 200) {
                        urlConn.inputStream.close()
                        return@async sugs
                    }
                    urlConn.inputStream.use {

                        val len = it.readAtMost(buf)

                        val txt = String(buf, 0, len)
                        if (txt.length < 3) return@use

                        val b = txt.indexOf("[\"", 2)
                        if (b < 0) return@use

                        val e = txt.indexOf("\"]", b + 1)
                        if (e < 0) return@use

                        val ss = txt.subSequence(b + 2, e)

                        var idx = 0
                        var j = ss.indexOf("\",\"", idx)

                        while (j > -1 && count-- > 0) {
                            sugs.add(0, ss.substring(idx, j))
                            idx = j + 3
                            j = ss.indexOf("\",\"", idx)
                        }
                        if (count > 0) {
                            sugs.add(0, ss.substring(idx, ss.length))
                        }

//                            ss.asSequence().take(5).forEach { sugs.add(it) }

//
//                            val obj = txt.parseArray()
//                            if (obj.size >= 2) {
//                                val sug = obj.getJSONArray(1)
//                                for (i in 0..sug.size.coerceAtMost(4)) {
//                                    sugs.add(sug.getString(i))
//                                }
//                            }

                    }

                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                return@async sugs
            }

            arr = historyManager.getTopSite(s)
            for (item in bookMarks) {
                val url = item.url
                val title = item.title
                if (url.indexOf(s) != -1 || title.indexOf(s) != -1) {
                    arr.add(
                        SuggestItem(
                            SuggestItem.HISTORY_SUGGEST, url, title
                        )
                    )
                }
            }
            searchSuggestionJob.await().forEach {
                arr.add(
                    SuggestItem(
                        SuggestItem.SEARCH_SUGGEST,
                        J.concat(setting.search_url, it),
                        it
                    )
                )
            }
            arr.addAll(searchResultJob.await())
        }

        return arr
    }

}