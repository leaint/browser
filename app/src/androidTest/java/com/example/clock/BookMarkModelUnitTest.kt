package com.example.clock

import android.util.JsonReader
import android.util.JsonWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.clock.ui.model.BookMark
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.io.StringWriter

@RunWith(AndroidJUnit4::class)
class BookMarkModelUnitTest {

    @Test
    fun json_equal() {
        val bookMark = BookMark("a.jpg", "http://example.com", "example", "a.ico", false, false)

        val sw = StringWriter()

        val jw = JsonWriter(sw)

        jw.beginObject()
        bookMark.writeJSON(jw)
        jw.endObject()

        jw.close()

        val s = sw.toString()

        val sr = StringReader(s)

        val jr = JsonReader(sr)

        val bookMark2 = BookMark().parseJSON(jr)

        jr.close()

        assertEquals(bookMark.icon_url, bookMark2.icon_url)
        assertEquals(bookMark.url, bookMark2.url)
        assertEquals(bookMark.title, bookMark2.title)
        assertEquals(bookMark.default_icon, bookMark2.default_icon)
        assertEquals(bookMark.pinned, bookMark2.pinned)
        assertEquals(bookMark.rect, bookMark2.rect)
    }
}