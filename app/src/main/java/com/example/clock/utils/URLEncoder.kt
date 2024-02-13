package com.example.clock.utils

import java.io.CharArrayWriter
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException
import java.util.BitSet
import java.util.Objects

/*
* Copyright (c) 1995, 2017, Oracle and/or its affiliates. All rights reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
*
* This code is free software; you can redistribute it and/or modify it
* under the terms of the GNU General Public License version 2 only, as
* published by the Free Software Foundation.  Oracle designates this
* particular file as subject to the "Classpath" exception as provided
* by Oracle in the LICENSE file that accompanied this code.
*
* This code is distributed in the hope that it will be useful, but WITHOUT
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
* FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
* version 2 for more details (a copy is included in the LICENSE file that
* accompanied this code).
*
* You should have received a copy of the GNU General Public License version
* 2 along with this work; if not, write to the Free Software Foundation,
* Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
*
* Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
* or visit www.oracle.com if you need additional information or have any
* questions.
*/

/**
 * Utility class for HTML form encoding. This class contains static methods
 * for converting a String to the <CODE>application/x-www-form-urlencoded</CODE> MIME
 * format. For more information about HTML form encoding, consult the HTML
 * <A HREF="http://www.w3.org/TR/html4/">specification</A>.
 *
 *
 *
 * When encoding a String, the following rules apply:
 *
 *
 *  * The alphanumeric characters &quot;`a`&quot; through
 * &quot;`z`&quot;, &quot;`A`&quot; through
 * &quot;`Z`&quot; and &quot;`0`&quot;
 * through &quot;`9`&quot; remain the same.
 *  * The special characters &quot;`.`&quot;,
 * &quot;`-`&quot;, &quot;`*`&quot;, and
 * &quot;`_`&quot; remain the same.
 *  * The space character &quot; &nbsp; &quot; is
 * converted into a plus sign &quot;`+`&quot;.
 *  * All other characters are unsafe and are first converted into
 * one or more bytes using some encoding scheme. Then each byte is
 * represented by the 3-character string
 * &quot;*`%xy`*&quot;, where *xy* is the
 * two-digit hexadecimal representation of the byte.
 * The recommended encoding scheme to use is UTF-8. However,
 * for compatibility reasons, if an encoding is not specified,
 * then the default encoding of the platform is used.
 *
 *
 *
 *
 * For example using UTF-8 as the encoding scheme the string &quot;The
 * string &#252;@foo-bar&quot; would get converted to
 * &quot;The+string+%C3%BC%40foo-bar&quot; because in UTF-8 the character
 * &#252; is encoded as two bytes C3 (hex) and BC (hex), and the
 * character @ is encoded as one byte 40 (hex).
 *
 * @author  Herb Jellinek
 * @since   1.0
 */
object URLEncoder {

    private var dontNeedEncoding: BitSet = BitSet(256)
    private const val caseDiff = 'a'.code - 'A'.code
    private var dfltEncName: String = Charset.defaultCharset().name()

    init {

        /* The list of characters that are not encoded has been
         * determined as follows:
         *
         * RFC 2396 states:
         * -----
         * Data characters that are allowed in a URI but do not have a
         * reserved purpose are called unreserved.  These include upper
         * and lower case letters, decimal digits, and a limited set of
         * punctuation marks and symbols.
         *
         * unreserved  = alphanum | mark
         *
         * mark        = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
         *
         * Unreserved characters can be escaped without changing the
         * semantics of the URI, but this should not be done unless the
         * URI is being used in a context that does not allow the
         * unescaped character to appear.
         * -----
         *
         * It appears that both Netscape and Internet Explorer escape
         * all special characters from this list with the exception
         * of "-", "_", ".", "*". While it is not clear why they are
         * escaping the other characters, perhaps it is safest to
         * assume that there might be contexts in which the others
         * are unsafe if not escaped. Therefore, we will use the same
         * list. It is also noteworthy that this is consistent with
         * O'Reilly's "HTML: The Definitive Guide" (page 164).
         *
         * As a last note, Internet Explorer does not encode the "@"
         * character which is clearly not unreserved according to the
         * RFC. We are being consistent with the RFC in this matter,
         * as is Netscape.
         *
         */
        dontNeedEncoding = BitSet(256)
        var i = 'a'.code
        while (i <= 'z'.code) {
            dontNeedEncoding.set(i)
            i++
        }
        i = 'A'.code
        while (i <= 'Z'.code) {
            dontNeedEncoding.set(i)
            i++
        }
        i = '0'.code
        while (i <= '9'.code) {
            dontNeedEncoding.set(i)
            i++
        }
        dontNeedEncoding.set(' '.code) /* encoding a space to a + is done
                                    * in the encode() method */
        dontNeedEncoding.set('-'.code)
        dontNeedEncoding.set('_'.code)
        dontNeedEncoding.set('.'.code)
        dontNeedEncoding.set('*'.code)

    }

    /**
     * Translates a string into `x-www-form-urlencoded`
     * format. This method uses the platform's default encoding
     * as the encoding scheme to obtain the bytes for unsafe characters.
     *
     * @param   s   `String` to be translated.
     * @return  the translated `String`.
     */
    @Deprecated(
        """The resulting string may vary depending on the platform's
                  default encoding. Instead, use the encode(String,String)
                  method to specify the encoding.
      """
    )
    fun encode(s: String): String? {
        var str: String? = null
        try {
            str = encode(s, dfltEncName)
        } catch (e: UnsupportedEncodingException) {
            // The system should always have the platform default
        }
        return str
    }

    /**
     * Translates a string into `application/x-www-form-urlencoded`
     * format using a specific encoding scheme.
     *
     *
     * This method behaves the same as [encode(String s, Charset charset)][String]
     * except that it will [look up the charset][java.nio.charset.Charset.forName]
     * using the given encoding name.
     *
     * @param   s   `String` to be translated.
     * @param   enc   The name of a supported
     * [character
 * encoding](../lang/package-summary.html#charenc).
     * @return  the translated `String`.
     * @throws  UnsupportedEncodingException
     * If the named encoding is not supported
     * @see URLDecoder.decode
     * @since 1.4
     */
    @Throws(UnsupportedEncodingException::class)
    fun encode(s: String, enc: String): String {

        return try {
            val charset = Charset.forName(enc)
            encode(s, charset)
        } catch (e: IllegalCharsetNameException) {
            throw UnsupportedEncodingException(enc)
        } catch (e: UnsupportedCharsetException) {
            throw UnsupportedEncodingException(enc)
        }
    }

    /**
     * Translates a string into `application/x-www-form-urlencoded`
     * format using a specific [Charset][java.nio.charset.Charset].
     * This method uses the supplied charset to obtain the bytes for unsafe
     * characters.
     *
     *
     * ***Note:** The [
 * World Wide Web Consortium Recommendation](http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars) states that
     * UTF-8 should be used. Not doing so may introduce incompatibilities.*
     *
     * @param   s   `String` to be translated.
     * @param charset the given charset
     * @return  the translated `String`.
     * @throws NullPointerException if `s` or `charset` is `null`.
     * @see URLDecoder.decode
     * @since 10
     */
    fun encode(s: String, charset: Charset): String {
        Objects.requireNonNull(charset, "charset")
        var needToChange = false
        val out = StringBuilder(s.length)
        val charArrayWriter = CharArrayWriter()
        var i = 0
        while (i < s.length) {
            var c = s[i].code
            //System.out.println("Examining character: " + c);
            if (dontNeedEncoding.get(c)) {
                if (c == ' '.code) {
//                    c = '+'.code
                    out.append("%20")
                    needToChange = true
                } else {
                    out.append(c.toChar())
                }
                //System.out.println("Storing: " + c);
                i++
            } else {
                // convert to external encoding before hex conversion
                do {
                    charArrayWriter.write(c)
                    /*
                     * If this character represents the start of a Unicode
                     * surrogate pair, then pass in two characters. It's not
                     * clear what should be done if a byte reserved in the
                     * surrogate pairs range occurs outside of a legal
                     * surrogate pair. For now, just treat it as if it were
                     * any other character.
                     */if (c in 0xD800..0xDBFF) {
                        /*
                          System.out.println(Integer.toHexString(c)
                          + " is high surrogate");
                        */
                        if (i + 1 < s.length) {
                            val d = s[i + 1].code
                            /*
                              System.out.println("\tExamining "
                              + Integer.toHexString(d));
                            */if (d in 0xDC00..0xDFFF) {
                                /*
                                  System.out.println("\t"
                                  + Integer.toHexString(d)
                                  + " is low surrogate");
                                */
                                charArrayWriter.write(d)
                                i++
                            }
                        }
                    }
                    i++
                } while (i < s.length && !dontNeedEncoding.get(s[i].code.also {
                        c = it
                    }))
                charArrayWriter.flush()
                val str = String(charArrayWriter.toCharArray())
                val ba = str.toByteArray(charset)
                for (j in ba.indices) {
                    out.append('%')
                    var ch = Character.forDigit(ba[j].toInt() shr 4 and 0xF, 16)
                    // converting to use uppercase letter as part of
                    // the hex value if ch is a letter.
                    if (Character.isLetter(ch)) {
                        ch = Char(ch - caseDiff.toChar())
                    }
                    out.append(ch)
                    ch = Character.forDigit(ba[j].toInt() and 0xF, 16)
                    if (Character.isLetter(ch)) {
                        ch = Char(ch - caseDiff.toChar())
                    }
                    out.append(ch)
                }
                charArrayWriter.reset()
                needToChange = true
            }
        }
        return if (needToChange) out.toString() else s
    }
}