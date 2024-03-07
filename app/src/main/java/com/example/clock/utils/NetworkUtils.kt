package com.example.clock.utils

import android.content.Context
import android.net.TrafficStats
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.example.clock.internal.DnsPacket
import com.example.clock.settings.ignore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLConnection
import java.nio.ByteBuffer
import java.security.Provider
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLContextSpi
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class ConnHelper {

    companion object {
        /*
                val bypassClient = OkHttpClient.Builder()
                    .sslSocketFactory(BypassSSLSocketFactory.sslSocketFactory, BypassTrustManager())
                    .hostnameVerifier { _, _ -> true }
                    .followRedirects(false)
                    .build()



                fun build2(
                    url: Uri,
                    headers: Map<String, String>,
                    dnsClient: DNSClient
                ): Result<Response> {
                    val h = headers.toMutableMap()
                    h.remove("Accept-Encoding")
                    h.remove("Transfer-Encoding")
                    h.remove("Connection")
                    val host = url.host ?: return Result.failure(Exception("Host can't be null."))

                    var ips = dnsClient.query(
                        host,
                        DNSClient.IP_TYPE.IPV6
                    )

                    if (ips.isEmpty()) {
                        ips = dnsClient.query(
                            host,
                            DNSClient.IP_TYPE.IPV4
                        )
                    }

                    if (ips.isEmpty()) return Result.failure(Throwable("empty dns ${url.host}"))

                    val ip = ips.last()
                    var urlPath = ""
                    if (url.path != null)
                        urlPath += url.encodedPath
                    if (url.query != null)
                        urlPath += "?" + url.encodedQuery
                    if (url.fragment != null)
                        urlPath += "#" + url.encodedFragment

                    var domain = ip
                    var port = 80
                    if (url.scheme == "https") {
                        port = 443
                    }
                    if (ip.contains(':'))
                        domain = "[$ip]"

                    val reddit = URL(url.scheme, domain, port, urlPath)

                    val req = Request.Builder()
                        .url(reddit)
                        .headers(h.toHeaders())
                        .header("Host", host)
                        .build()

                    val res = bypassClient.newCall(req).execute()

                    if (res.code in 300..399) {
                        try {
                            var maxRedirect = 10

                            var r = res
                            while (maxRedirect-- > 0 && r.code in 300..399) {
                                val location = r.headers("location")
                                if (location.isNotEmpty()) {
                                    val u = URL(location[0])
                                    var ips: String
                                    var ip = dnsClient.query(
                                        u.host,
                                        DNSClient.IP_TYPE.IPV6
                                    )
                                    if (ip.isEmpty()) {
                                        ip = dnsClient.query(
                                            u.host,
                                            DNSClient.IP_TYPE.IPV4
                                        )
                                        if (ip.isEmpty()) {
                                            throw UnknownError("empty dns answer ${u.host}")
                                        }
                                        ips = ip.last()
                                    } else {
                                        ips = "[${ip.last()}]"
                                    }

                                    val req = Request.Builder()
                                        .url(location[0].replace(u.host, ips))
                                        .header("Host", u.host)
                                        .build()
                                    r.close()
                                    r = bypassClient.newCall(req).execute()

                                } else {
                                    break
                                }
                            }
                            if (r.code in 300..399) {
                                r.close()
                                throw Exception("Too many redirect or bad ${r.code} redirect!")
                            }
                            return Result.success(r)
                        } catch (e: Exception) {
                            return Result.failure(e)
                        }
                    }

                    return Result.success(res)

                }
        */

        fun build(url: Uri, dnsClient: DNSClient): Result<URLConnection> {
            TrafficStats.setThreadStatsTag(776)

            val host = url.host ?: return Result.failure(Exception("Host can't be null."))

            var ips = dnsClient.getDefault(host)

            if (ips.isEmpty()) {
                ips = dnsClient.query(
                    host,
                    DNSClient.IP_TYPE.IPV6
                )
            }
            if (ips.isEmpty()) {
                ips = dnsClient.query(
                    host,
                    DNSClient.IP_TYPE.IPV4
                )
            }

            if (ips.isEmpty()) return Result.failure(Exception("empty dns ${url.host}"))

            val ip = ips.last()

            var domain = ip
            var port = 80
            if (url.scheme == "https") {
                port = 443
            }
            if (ip.contains(':'))
                domain = "[$ip]"

            val reddit = URL(url.toString().replace(host, domain))
            val urlConn = reddit.openConnection() as HttpURLConnection
            with(urlConn) {
                instanceFollowRedirects = false
//                connectTimeout = 5000
                setRequestProperty("Host", host)
//                setRequestProperty("Connection", "keep-alive")
//                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Cache-Control", "max-stale=300")
                setRequestProperty(
                    "Accept-Language",
                    "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2"
                )
                connectTimeout = 20_000
            }

            if (port == 443) {

                val con = urlConn as HttpsURLConnection
                con.useCaches = true

//                con.sslSocketFactory = MySSLSocketFactory.instance
                con.sslSocketFactory = BypassSSLSocketFactory.sslSocketFactory
                con.hostnameVerifier =
                    HostnameVerifier { _, _ -> true }

            }

            return Result.success(urlConn)
        }

        fun autoRedirect2(urlConn: HttpURLConnection): Result<InputStream> {

            try {
                val locations = urlConn.headerFields["Location"]
                if (!locations.isNullOrEmpty()) {
                    val location = locations[0]

                    urlConn.inputStream.close()

                    val stream = ByteArrayInputStream(
                        """              
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8"
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta http-equiv="Refresh" content="0; url=$location" />
  </head>
  <body>
  <p>Redirecting to <a style="overflow-wrap: break-word;" id="redirect" href="$location">${location}</a></p>
  <script>
  const a = document.querySelector('#redirect');
  if(a!==null) { a.textContent = decodeURI(a.textContent); }
  </script>
  </body>
</html>
                    """.toByteArray()
                    )

                    return Result.success(stream)
                } else {
                    throw Exception("HTTP 3XX doesn't have Location header field")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                return Result.failure(e)
            } finally {
                urlConn.inputStream.close()
            }

        }

        // 内部跳转不改变url，不可行
        fun autoRedirect1(urlConn: HttpURLConnection, dnsClient: DNSClient): Result<URLConnection> {

            if (urlConn.responseCode in 300..399) {
                try {
                    var maxRedirect = 10
                    var c: HttpURLConnection = urlConn
                    while (maxRedirect-- > 0 && c.responseCode in 300..399) {
                        val location = c.headerFields["Location"]
                        if (!location.isNullOrEmpty()) {
                            var u = URL(location[0])
                            var ips: String
                            var ip = dnsClient.query(
                                u.host,
                                DNSClient.IP_TYPE.IPV6
                            )
                            if (ip.isEmpty()) {
                                ip = dnsClient.query(
                                    u.host,
                                    DNSClient.IP_TYPE.IPV4
                                )
                                if (ip.isEmpty()) {
                                    throw UnknownError("empty dns answer ${u.host}")
                                }
                                ips = ip.last()
                            } else {
                                ips = "[${ip.last()}]"
                            }
                            val prevHost = u.host
                            u = URL(location[0].replace(u.host, ips))
                            c.inputStream.close()
                            c = u.openConnection() as HttpURLConnection
                            with(c) {
                                instanceFollowRedirects = false
                                // TODO: Add 301 Http Header
                                setRequestProperty("Host", prevHost)
//                                setRequestProperty("Accept-Encoding", "gzip")
                                setRequestProperty("Cache-Control", "max-stale=600")
                            }
                            if (u.protocol == "https") {
                                with(c as HttpsURLConnection) {

                                    sslSocketFactory = BypassSSLSocketFactory.sslSocketFactory
                                    hostnameVerifier =
                                        HostnameVerifier { _, _ -> true }
                                }
                            }
                        } else {
                            c.inputStream.close()
                            break
                        }
                    }

                    if (c.responseCode in 300..399) {
                        c.inputStream.close()
                        c.disconnect()
                        throw Exception("Too many redirect or bad ${c.responseCode} redirect!")
                    }
                    return Result.success(c)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return Result.failure(e)
                }
            }

            return Result.success(urlConn)

        }

        fun wrongResponse(urlConn: HttpURLConnection): Result<InputStream> {

            try {
                ignore {
                    if (urlConn.responseCode < 400) {
                        urlConn.inputStream.close()
                    } else {
                        urlConn.errorStream.close()
                    }
                }

                val sbuf = StringBuilder(1024)
                sbuf.append(
                    """              
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8"
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  </head>
  <body>"""
                )

                sbuf.append("<p>").append(urlConn.responseCode).append(' ')
                    .append(urlConn.responseMessage)
                    .append("</p>")
                for (header in urlConn.headerFields) {
                    sbuf.append("<p>").append(header.key).append(": ")
                        .append(header.value.joinToString(","))
                        .append("</p>")
                }

                sbuf.append("</body></html>")
                val stream = ByteArrayInputStream(
                    sbuf.toString().toByteArray()
                )

                return Result.success(stream)

            } catch (e: Exception) {
                e.printStackTrace()
                return Result.failure(e)
            }

        }
    }
}

class BypassTrustManager : X509TrustManager {
    companion object {
        val emptyIssuers = emptyArray<X509Certificate>()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        /* no-op */
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        /* no-op */
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return emptyIssuers
    }
}

class MySSLSocketFactory : SSLSocketFactory() {

    companion object {
        val instance = MySSLSocketFactory()
    }

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        val host = if (host == "cloudflare.com") {
            "104.16.132.229"
        } else {
            host
        }

        return BypassSSLSocketFactory.sslSocketFactory.createSocket(s, host, port, autoClose)
    }

    override fun createSocket(host: String?, port: Int): Socket {
        val host = if (host == "cloudflare.com") {
            "104.16.132.229"
        } else {
            host
        }
        val s = BypassSSLSocketFactory.sslSocketFactory.createSocket(host, port)

        return s
    }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int
    ): Socket {
        val host = if (host == "cloudflare.com") {
            "104.16.132.229"
        } else {
            host
        }
        return BypassSSLSocketFactory.sslSocketFactory.createSocket(
            host,
            port,
            localHost,
            localPort
        )
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {

        return BypassSSLSocketFactory.sslSocketFactory.createSocket(host, port)
    }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket {

        return BypassSSLSocketFactory.sslSocketFactory.createSocket(
            address,
            port,
            localAddress,
            localPort
        )
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return BypassSSLSocketFactory.sslSocketFactory.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return BypassSSLSocketFactory.sslSocketFactory.supportedCipherSuites
    }

}

class MySSLContext(contextSpi: SSLContextSpi?, provider: Provider?, protocol: String?) : SSLContext(
    contextSpi,
    provider, protocol
) {

    val instance = getInstance("TLS")


}

object BypassSSLSocketFactory {
    var sslSocketFactory: SSLSocketFactory

    init {
        val trustManagers = arrayOf(BypassTrustManager())
        val context = SSLContext.getInstance("TLS")

        context.init(null, trustManagers, SecureRandom())

        sslSocketFactory = context.socketFactory
//        try {
//
//            val sslParams = sslSocketFactory.javaClass.getDeclaredField("sslParameters")
//            sslParams.isAccessible = true
//            val newsslParams = sslParams.get(sslSocketFactory)
//
//            val useSni = newsslParams.javaClass.getField("useSni")
//            useSni.isAccessible = true
//            val newuseSni = useSni.set(newsslParams, false)
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }

    }
}

class DNSClient(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    serverAddress: String,
    val port: Int,
    var timeout: Int = 1000
) {

    private var serverIpAddress: InetAddress
    private val localDNSMap4 = HashMap<String, List<String>>()
    private val localDNSMap6 = HashMap<String, List<String>>()

//    private val cacheDNSMap4 = LinkedHashMap<String, List<String>>()
//    private val cacheDNSMap6 = LinkedHashMap<String, List<String>>()

    init {

        /*
        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                cacheDNSMap4.keys.take(5).forEach {
                    cacheDNSMap4.remove(it)
                }

                cacheDNSMap6.keys.take(5).forEach {
                    cacheDNSMap6.remove(it)
                }

                delay(120_000)
            }
        }
*/
        try {
            val f = File(
                context.getExternalFilesDir(null),
                HOSTS_FILE
            )
            if (!f.exists()) {
                f.createNewFile()
            } else {
                f.inputStream().use { input ->
                    run {
                        input.bufferedReader().use {
                            it.lines().forEach {
                                val item = it.split(' ')
                                if (item.size < 2) return@forEach
                                val tra = item.map { it.trim() }
                                val ip = tra[0]
                                if (ip.isNotEmpty() && ip[0] == '#') return@forEach
                                val ipMap = if (ip.indexOf(':') > 0) {
                                    localDNSMap6
                                } else {
                                    localDNSMap4
                                }
                                val ips = listOf(ip)
                                for (i in 1..<tra.size) {
                                    ipMap[tra[i]] = ips
                                }
                            }
                        }

                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverIpAddress = parseInetAddress(serverAddress)
    }

    enum class IP_TYPE {
        IPV4,
        IPV6,
        IPV4_6,
    }

    companion object {
        const val HOSTS_FILE = "hosts.txt"

        private const val TRY_COUNT = 3

        fun parseInetAddress(s: String): InetAddress {
            val ip = s.split('.')
            if (ip.size != 4) throw IllegalArgumentException("ip size must = 4")

            val ipInt = ByteBuffer.allocate(4)
            ip.forEach {
                ipInt.put((it.toInt()).toByte())
            }
            return Inet4Address.getByAddress(ipInt.array())
        }
    }

    fun getDefault(domain: String): List<String> {
        val l = ArrayList<String>()

        localDNSMap4[domain]?.let { l.addAll(it) }
        localDNSMap6[domain]?.let { l.addAll(it) }
        return l
    }

    fun query(domain: String, addressType: IP_TYPE): List<String> {

        val initList = when (addressType) {
            IP_TYPE.IPV4 -> localDNSMap4[domain] //?: cacheDNSMap4[domain]
            IP_TYPE.IPV6 -> localDNSMap6[domain] //?: cacheDNSMap6[domain]
            else -> null
        }
        val b = Instant.now()

        if (!initList.isNullOrEmpty()) {
            return initList
        }

        val addressList = ArrayList<String>()

        try {
            TrafficStats.setThreadStatsTag(998)

            val buf = ByteBuffer.allocate(2048)

            val name = domain.toByteArray()

            buf.apply {
                putShort(2345) //ID
//                    put(0) //QR
//                    putInt(0) //OPCODE
                putShort(0x100)
                putShort(1) //QDCOUNT
                putShort(0) //ANCOUNT
                putShort(0) //NSCOUNT
                putShort(0) //ARCOUNT
                put(name.size.toByte())
                put(name)
                put(0)
                val ipType: Short =
                    when (addressType) {
                        IP_TYPE.IPV4 -> 1
                        IP_TYPE.IPV6 -> 28
                        else -> 28
                    }

                putShort(ipType)
                putShort(1)
            }
            val position = buf.position()

            val packet = DatagramPacket(
                buf.array().sliceArray(0..position),
                position + 1,
                serverIpAddress,
                port
            )

            val revBuff = ByteArray(2048)
            val revPacket = DatagramPacket(revBuff, revBuff.size)

            var records: Array<List<DnsPacket.DnsRecord?>?>? = null
            var trys = TRY_COUNT
            while (trys-- > 0) {

                val socket = DatagramSocket()
                socket.soTimeout = timeout
                try {

                    socket.send(packet)
                    socket.receive(revPacket)
                } catch (e: SocketTimeoutException) {
                    Log.d("dns", "query: ${e.message}")
                    continue
                } finally {
                    socket.close()
                }

                val p = DnsPacket(revBuff.sliceArray(0 until revPacket.length))

                records = p.getmRecords()
                break
            }


            if (records != null) {
                for (i in records.indices) {
                    val record = records.getOrNull(i) ?: continue
                    for (j in record.indices) {
                        val an = record[j] ?: continue

                        if (i == 1 && (an.nsType == 28 || an.nsType == 1)) {
                            if (an.rr != null) {
                                val address = InetAddress.getByAddress(an.rr).hostAddress
                                if (address != null) {
                                    addressList.add(address)
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.message?.let { Log.e("error", it) }
        }

        val l = addressList.toList()

        if (l.isNotEmpty()) {

            when (addressType) {
                IP_TYPE.IPV6 -> localDNSMap6[domain] = l
                IP_TYPE.IPV4 -> localDNSMap4[domain] = l
                else -> {}
            }
        }

        return l
    }

}