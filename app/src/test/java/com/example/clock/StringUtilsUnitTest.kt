package com.example.clock

import com.example.clock.utils.domainRootMatch
import com.example.clock.utils.hostMatch
import com.example.clock.utils.translateEscapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StringUtilsUnitTest {

    @Test
    fun translateEscapes_unicode_single() {

        val s = translateEscapes("\\u0041")

        assertNotNull(s)

        val t = String(s!!.chars, 0, s.length)
        assertEquals(t, "\u0041")
    }

    @Test
    fun translateEscapes_unicode() {

        val s = translateEscapes("456asdf\\u0041456asdf")

        assertNotNull(s)

        val t = String(s!!.chars, 0, s.length)
        assertEquals(t, "456asdf\u0041456asdf")
    }

    @Test
    fun translateEscapes_unicode2() {

        val s = translateEscapes("456asdf\\r\\n\\u0041\\u0043456asdf")

        assertNotNull(s)

        val t = String(s!!.chars, 0, s.length)
        assertEquals(t, "456asdf\r\n\u0041\u0043456asdf")
    }

    @Test
    fun hostMatch_test1() {

        val dataset = listOf(
            "https://example.com/fkosf/9652" to "example.com",
            "" to null,
            "https//example.com/fkosf/9652" to null,
            "ftp://example.com/fkosf/9652" to "example.com",
            "https://example.com/" to "example.com",
            "https://example.com" to "example.com",
            "https://www.example.com/fkosf/9652" to "www.example.com",

            )
        dataset.forEach {
            val targetHost = hostMatch(it.first)
            assertEquals(targetHost, it.second)
        }
    }

    @Test
    fun domainRootMatch_test() {

        val dataset = listOf(
            "" to "",
            "abc" to "abc",
            "abc.com" to "abc.com",
            "www.abc.com" to "abc.com",
            "www.e.abc.com" to "abc.com",

            )
        dataset.forEach {
            val root = domainRootMatch(it.first)
            assertEquals(root, it.second)
        }
    }
}