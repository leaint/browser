package com.example.clock.ui.model

import android.graphics.Bitmap
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import androidx.annotation.Keep
import com.example.clock.settings.ChangedType
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.settings.obj
import com.example.clock.tab.manager.WebViewHolder
import com.example.clock.utils.IJSONOpt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32

class BookMark(
    @Keep
    @JvmField
    var icon_url: String,
    @Keep
    @JvmField
    var url: String,
    @Keep
    @JvmField
    var title: String,
    @Keep
    @JvmField
    var default_icon: String,
    @Keep
    @JvmField
    var pinned: Boolean,
    @Keep
    @JvmField
    var rect: Boolean,
) : IJSONOpt<BookMark>() {
    private val fields = BookMark::class.java.declaredFields
    private val fieldNameArr = Array<String>(fields.size) {
        fields[it].name
    }

    override fun parseJSON(r: JsonReader): BookMark {

        r.obj { k ->
            run {
                val i = fieldNameArr.indexOf(k)

                if (i < 0) {
                    skipValue()
                    return@run
                }

                when (k) {
                    BookMark::default_icon.name, BookMark::url.name, BookMark::title.name, BookMark::icon_url.name -> {
                        fields[i].set(this@BookMark, nextString())
                    }

                    BookMark::pinned.name, BookMark::rect.name -> {
                        fields[i].setBoolean(this@BookMark, nextBoolean())
                    }

                    else -> {
                        skipValue()
                    }
                }
            }
        }

        return this
    }

    override fun writeJSON(w: JsonWriter) {
        w.name(BookMark::icon_url.name).value(icon_url)
        w.name(BookMark::url.name).value(url)
        w.name(BookMark::title.name).value(title)
        w.name(BookMark::default_icon.name).value(default_icon)
        w.name(BookMark::pinned.name).value(pinned)
        w.name(BookMark::rect.name).value(rect)
    }

    constructor() : this("", "", "", "", false, false)
}

class BookMarkModel(private val setting: GlobalWebViewSetting) {

    var arr = setting.bookMarkArr

    init {
        setting.addChangedListener { t ->
            run {
                if (t == ChangedType.BOOKMARK) {
                    arr = setting.bookMarkArr
                }
            }
        }
    }

    fun indexOf(url: String): Int {
        var bookIdx = -1
        for (i in 0..<arr.size) {
            if (url == arr[i].url) {
                bookIdx = i
                break
            }
        }
        return bookIdx
    }

    suspend fun toggleIt(h: WebViewHolder): Boolean {

        val idx = indexOf(h.startLoadingUri)
        if (idx >= 0) {
            removeAt(idx)
        } else {
            bookIt(h)
        }
        return idx < 0
    }

    fun removeAt(idx: Int) {
        arr.removeAt(idx)
        setting.bookMarksVersion++
    }

    suspend fun bookIt(h: WebViewHolder) {

        withContext(Dispatchers.IO) {
            val bitmap = h.iconBitmap
            var defaultIcon = ""
            if (bitmap != null) {
                ByteArrayOutputStream().use {
                    if (bitmap.compress(
                            Bitmap.CompressFormat.WEBP_LOSSLESS,
                            100,
                            it
                        )

                    ) {
                        val c = CRC32()
                        val buf = it.toByteArray()
                        c.update(buf)
                        val filename = "${c.value}.webp"
                        File(setting.externalIconCacheDir, filename).apply {

                            val res: Boolean
                            if (!exists()) {
                                res = createNewFile()
                            } else {
                                res = true
                            }

                            if (res) {
                                outputStream()
                                    .use {
                                        it.write(buf)
                                    }
                            }
                        }

                        defaultIcon = "${setting.icon_cache_url}$filename"
                    }
                }
            }

            val obj = BookMark(
                h.iconUrl,
                h.startLoadingUri,
                h.title,
                defaultIcon,
                false,
                rect = false,
            )

            arr.add(obj)
            setting.bookMarksVersion++
        }

    }
}
