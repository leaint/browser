package com.example.clock.utils

import android.net.http.SslError
import android.util.SparseArray

inline fun SslError.getPrimaryErrorString() = this.getErrorString(this.primaryError)

inline fun SslError.getErrorString(error: Int): String = SslErrorString.get(error, "Unknown error")

val SslErrorString = SparseArray<String>(6).apply {
    set(SslError.SSL_NOTYETVALID, "The certificate is not yet valid")
    set(SslError.SSL_EXPIRED, "The certificate has expired")
    set(SslError.SSL_IDMISMATCH, "Hostname mismatch")
    set(SslError.SSL_UNTRUSTED, "The certificate authority is not trusted")
    set(SslError.SSL_DATE_INVALID, "The date of the certificate is invalid")
    set(SslError.SSL_INVALID, "A generic error occurred")
}
