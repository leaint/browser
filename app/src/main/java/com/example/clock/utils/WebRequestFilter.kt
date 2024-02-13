package com.example.clock.utils

import android.util.ArrayMap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.clock.internal.J
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.settings.ignore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

class RequestFilter(
    @JvmField val rules: Array<String>,
    @JvmField val filterFunc: (WebResourceRequest, globalWebViewSetting: GlobalWebViewSetting) -> WebResourceResponse?
)

object WebRequestFilter {

    val EmptyInputStream = object : InputStream() {
        override fun read(): Int = -1
    }

    fun replaceRuleFilter(
        request: WebResourceRequest,
        globalWebViewSetting: GlobalWebViewSetting
    ): String? {
        val host = hostMatch(request.url.toString()) ?: return null
        val tar = run {
            for ((ori, target) in globalWebViewSetting.replace_rule) {
                if (ori == host) {
                    return@run target
                } else {
                    val hl = host.length
                    var patternLen = ori.length
                    var offset = 0
                    if (ori[0] == '*') {
                        offset = 1
                        --patternLen
                    }
                    if (hl < patternLen) {
                        continue
                    }

                    if (ori.regionMatches(
                            offset,
                            host,
                            hl - patternLen,
                            patternLen,
                            false
                        )
                    ) {
                        return@run target
                    }
                }
            }
            null
        }

        if (tar != null) {
            val url = request.url
            var newUrl = "http://$tar"
            if (url.path != null) newUrl += url.encodedPath
            if (url.query != null) newUrl += "?" + url.encodedQuery
            if (url.fragment != null) newUrl += "#" + url.encodedFragment

            return newUrl
        }
        return null
    }

    var webRequestFilter = arrayOf(
        RequestFilter(arrayOf("moyu.im"), ::moyuFilter),
        RequestFilter(
            arrayOf(".reddit.com", ".redditstatic.com", ".redditmedia.com"),
            ::redditFilter
        ),
        RequestFilter(
            arrayOf(GlobalWebViewSetting.EXTERNAL_CACHE_HOST),
            ::externalCacheFilter
        )
    )

    val backupRequestFilter = RequestFilter(emptyArray(), ::customDns)

    private val cacheStream = ArrayMap<String, ByteArray>(16)

    private fun externalCacheFilter(
        request: WebResourceRequest,
        globalWebViewSetting: GlobalWebViewSetting
    ): WebResourceResponse {

        val response: WebResourceResponse

        val urlStr = request.url.toString()

        if (J.startsWith(urlStr, globalWebViewSetting.icon_cache_url)) {
            val filename = urlStr.substring(globalWebViewSetting.icon_cache_url.length - 1)
            response = try {
//                    if (!f.exists()) throw FileNotFoundException()

                val cacheF = cacheStream[filename]
                val stream: InputStream = if (cacheF == null) {
                    val f = File(globalWebViewSetting.externalIconCacheDir, filename)
                    val length = f.length()

                    if (length == 0L) return MyWebViewClient.NotFoundWebResourceResponse

                    if (length in 1..16 * 1024) {
                        val length = length.toInt()

                        val buf = ByteArray(length)
                        f.inputStream().use {
                            it.readAtMost(buf)
                        }

                        try {
                            cacheStream[filename] = buf
                        } catch (e: ConcurrentModificationException) {
                            Log.w("externalCacheFilter", "ConcurrentModificationException")
                        }
                        ByteArrayInputStream(buf)
                    } else {
                        f.inputStream()
                    }

                } else {
                    ByteArrayInputStream(cacheF)
                }

                WebResourceResponse(
                    "image/webp",
                    "utf-8",
                    stream
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                MyWebViewClient.BlockWebResourceResponse
            }
        } else {
            response = MyWebViewClient.NotFoundWebResourceResponse
        }

        return response
    }

    fun moyuFilter(
        request: WebResourceRequest,
        globalWebViewSetting: GlobalWebViewSetting
    ): WebResourceResponse? {
        val host = hostMatch(request.url.toString()) ?: return null
        val frag = host.split('.')
        if (frag.size == 3) {
            val url = request.url
            var path = ""
            if (url.path != null) path += url.encodedPath
            if (url.query != null) path += "?" + url.encodedQuery
            if (url.fragment != null) path += "#" + url.encodedFragment

            val sinaimg = URL("http", "${frag[0]}.sinaimg.cn", 80, path)

            ignore {

                val urlConn = sinaimg.openConnection() as HttpURLConnection
                urlConn.apply {
                    setRequestProperty("user-agent", globalWebViewSetting.user_agent)
                    setRequestProperty("referer", "https://weibo.com/")
//                                        urlConn.setRequestProperty("X-Android-Transports", "h2,http/1.1")
                    requestMethod = request.method
                    readTimeout = 30000
                }

                var mime = "image/jpeg"
                val mimetype = urlConn.headerFields["content-type"]
                if (mimetype != null) {
                    val responseMime = mimetype.getOrNull(0)
                    if (responseMime != null) {
                        val arr = responseMime.split(';')
                        mime = arr[0]
                    }
                }

//                                    val bufStream = BufferedInputStream(urlConn.inputStream)

                return WebResourceResponse(mime, "utf-8", urlConn.inputStream)
            }
        }
        return null
    }

    fun redditFilter(
        request: WebResourceRequest, globalWebViewSetting: GlobalWebViewSetting
    ): WebResourceResponse? {

        val url = request.url
        val host = hostMatch(request.url.toString()) ?: return null

        ignore {

            val ips = globalWebViewSetting.dnsClient.query(
                "dualstack.reddit.map.fastly.net", DNSClient.IP_TYPE.IPV6
            )

            if (ips.isNotEmpty()) {

                val ip = ips.last()
                var urlPath = ""
                if (url.path != null) urlPath += url.encodedPath
                if (url.query != null) urlPath += "?" + url.encodedQuery
                if (url.fragment != null) urlPath += "#" + url.encodedFragment

                val reddit = URL(url.scheme, "[$ip]", 443, urlPath)


                val urlConn = reddit.openConnection() as HttpsURLConnection

                urlConn.apply {
                    request.requestHeaders.forEach { (t, u) ->
                        setRequestProperty(t, u)
                    }
                    setRequestProperty("host", host)
                    setRequestProperty("X-Android-Transports", "h2,http/1.1")
                }

                urlConn.hostnameVerifier =
                    HostnameVerifier { hostname, session -> true }


                val responseHeaders = ArrayMap<String, String>()

                val bufStream = urlConn.inputStream

                var mime = "text/html"
                val mimetype = urlConn.headerFields["content-type"]
                if (mimetype != null) {
                    val responseMime = mimetype.getOrNull(0)
                    if (responseMime != null) {
                        val arr = responseMime.split(';')
                        mime = arr[0]
                    }
                }

                urlConn.headerFields.forEach { (k, l) ->
                    if (k != null) responseHeaders[k] = l.joinToString()
                }

                return WebResourceResponse(
                    mime,
                    "utf-8",
                    urlConn.responseCode,
                    urlConn.responseMessage,
                    responseHeaders,
                    bufStream
                )
            }
        }

        return null
    }

    fun customDns(
        request: WebResourceRequest, globalWebViewSetting: GlobalWebViewSetting
    ): WebResourceResponse? {
        val host = hostMatch(request.url.toString()) ?: return null
        val found = globalWebViewSetting.custom_dns_list.find { J.endsWith(host, it) }
        if (!found.isNullOrEmpty()) {

            val url = request.url
            ignore {
                var urlConn = ConnHelper.build(url, globalWebViewSetting.dnsClient)
                    .getOrThrow() as HttpURLConnection

                request.requestHeaders.forEach { (t, u) ->
                    if (t != "Accept-Encoding")
                        urlConn.setRequestProperty(t, u)
                }

//                                    val cacheRes = HttpResponseCache.getInstalled().get(URI.create(urlConn.url.toString()), request.method, urlConn.requestProperties)

//                                    urlConn.setRequestProperty("X-Android-Transports", "h2,http/1.1")
//


                val responseCode = urlConn.responseCode
                if (responseCode !in 300..399 || !request.isForMainFrame) {
                    if (responseCode in 300..399) {
                        urlConn = ConnHelper.autoRedirect1(urlConn, globalWebViewSetting.dnsClient)
                            .getOrThrow() as HttpURLConnection
                    }
//                    if (urlConn.headerFields["content-encoding"]?.getOrNull(0)
//                            ?.contains("gzip") == true
//                    ) {
//                        GZIPInputStream(urlConn.inputStream)
//                    } else {
                    val bufStream = urlConn.inputStream
                    var mime = "text/html"
                    val mimetype = urlConn.headerFields["content-type"]
                    if (mimetype != null) {
                        val responseMime = mimetype.getOrNull(0)
                        if (responseMime != null) {
                            val arr = responseMime.split(';')
                            mime = arr[0]
                        }
                    }

                    val responseHeaders = ArrayMap<String, String>()

                    urlConn.headerFields.asSequence()
                        .filter { (k, v) -> k != null && v != null && v.size > 0 }
                        .forEach { (k, v) -> responseHeaders[k] = v.joinToString(";") }

                    return WebResourceResponse(
                        mime,
                        "utf-8",
                        urlConn.responseCode,
//                    "ok",
                        urlConn.responseMessage,
                        responseHeaders,
                        bufStream
                    )

//                    }
                } else {
                    val bufStream =
                        ConnHelper.autoRedirect2(urlConn)
                            .getOrDefault(EmptyInputStream)
                    return WebResourceResponse(
                        "text/html",
                        "utf-8",
                        200,
                        "OK",
                        emptyMap(),
                        bufStream
                    )

                }


            }
        }
        return null
    }

}