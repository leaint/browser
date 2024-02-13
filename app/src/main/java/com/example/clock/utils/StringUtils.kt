package com.example.clock.utils

import java.io.InputStream
import java.util.Arrays
import kotlin.math.min


class CharS(@JvmField val chars: CharArray, @JvmField val length: Int)

fun InputStream.readAtMost(buf: ByteArray, len: Int): Int {

    if (len < 0) return -1

    val len = min(buf.size, len)
    var nread = 0
    var n = 0

    do {
        nread += n
        n = read(
            buf, nread,
            (len - nread).coerceAtLeast(0)
        )
    } while (n > 0)

    if (read() != -1) return -1

    return nread
}

fun InputStream.readNBytesC(len: Int): ByteArray? {
    require(len >= 0) { "len < 0" }
    var bufs: MutableList<ByteArray>? = null
    var result: ByteArray? = null
    var total = 0
    var remaining = len
    var n: Int
    do {
        var buf = ByteArray(Math.min(remaining, 4096))
        var nread = 0

        // read to EOF which may read more or less than buffer size
        while (read(
                buf, nread,
                Math.min(buf.size - nread, remaining)
            ).also { n = it } > 0
        ) {
            nread += n
            remaining -= n
        }
        if (nread > 0) {
            if (Int.MAX_VALUE - 8 - total < nread) {
                throw OutOfMemoryError("Required array size too large")
            }
            if (nread < buf.size) {
                buf = buf.copyOfRange(0, nread)
            }
            total += nread
            if (result == null) {
                result = buf
            } else {
                if (bufs == null) {
                    bufs = ArrayList()
                    bufs.add(result)
                }
                bufs.add(buf)
            }
        }
        // if the last call to read returned -1 or the number of bytes
        // requested have been read then break
    } while (n >= 0 && remaining > 0)
    if (bufs == null) {
        if (result == null) {
            return ByteArray(0)
        }
        return if (result.size == total) result else Arrays.copyOf(result, total)
    }
    result = ByteArray(total)
    var offset = 0
    remaining = total
    for (b in bufs) {
        val count = Math.min(b.size, remaining)
        System.arraycopy(b, 0, result, offset, count)
        offset += count
        remaining -= count
    }
    return result
}


fun InputStream.readAtMost(buf: ByteArray) = readAtMost(buf, buf.size)

fun translateEscapes(s: String): CharS? {
    if (s.isEmpty()) {
        return null
    }
    val chars: CharArray = s.toCharArray()
    val length = chars.size
    var from = 0
    var to = 0
    while (from < length) {
        var ch = chars[from++]
        if (ch == '\\') {
            ch = if (from < length) chars[from++] else '\u0000'
            when (ch) {
                'b' -> ch = '\b'
                'f' -> ch = '\u000c'
                'n' -> ch = '\n'
                'r' -> ch = '\r'
                's' -> ch = ' '
                't' -> ch = '\t'
                '\'', '\"', '\\' -> {}
                in '0'..'7' -> {
                    val limit = Integer.min(from + if (ch <= '3') 2 else 1, length)
                    var code = ch.code - '0'.code
                    while (from < limit) {
                        ch = chars[from]
                        if (ch < '0' || '7' < ch) {
                            break
                        }
                        from++
                        code = code shl 3 or ch.code - '0'.code
                    }
                    ch = code.toChar()
                }

                '\n' -> continue
                '\r' -> {
                    if (from < length && chars[from] == '\n') {
                        from++
                    }
                    continue
                }

                'u' -> {
                    if (from + 5 >= length) {
                        val msg = String.format(
                            "need four unicode length \\%c \\\\u%04X",
                            ch, ch.code
                        )
                        throw IllegalArgumentException(msg)
                    }

                    var result = 0
                    for (i in 3 downTo 0) {
                        val ch = chars[from++]
                        when (ch.code) {
                            in '0'.code..'9'.code -> {
                                result = result or (ch.code - '0'.code) shl (i * 4)
                            }

                            in 'A'.code..'F'.code -> {
                                //  Char('A'.code - 10) = '7'
                                result = result or (ch.code - '7'.code) shl (i * 4)
                            }

                            in 'a'.code..'f'.code -> {
                                // Char('a'.code - 10) = 'W'
                                result = result or (ch.code - 'W'.code) shl (i * 4)
                            }

                            else -> {
                                val msg = String.format(
                                    "invalid hex number \\%c \\\\u%04X",
                                    ch, ch.code
                                )
                                throw IllegalArgumentException(msg)
                            }
                        }

                    }

                    ch = result.toChar()
                }

                else -> {
                    val msg = String.format(
                        "Invalid escape sequence: \\%c \\\\u%04X",
                        ch, ch.code
                    )
                    throw IllegalArgumentException(msg)
                }
            }
        }
        chars[to++] = ch
    }
    return CharS(chars, to)
}

fun hostMatch(s: String): String? {
    var idx = s.indexOf("://")
    if (idx != -1) {
        idx += 3
        if (idx < s.length - 1) {
            val jdx = s.indexOf('/', idx)
            return if (jdx == -1) {
                s.substring(idx)
            } else {
                s.substring(idx, jdx)
            }
        }
    }

    return null
}

fun domainRootMatch(host: String): String {

    val a = host.lastIndexOf('.')
    if (a <= 0) return host
    val b = host.lastIndexOf('.', a - 1)
    return host.substring(b + 1)
}