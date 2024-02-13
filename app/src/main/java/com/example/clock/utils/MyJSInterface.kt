package com.example.clock.utils

import android.webkit.JavascriptInterface

interface WebViewJSInterface {
    fun getSearchUrl(): String
    fun getBookMarks(): String
    fun setBookMarks(s: String)
}

abstract class MyJSInterface : WebViewJSInterface {

    @JavascriptInterface
    override fun getSearchUrl(): String {
        throw NotImplementedError()
    }

    @JavascriptInterface
    override fun getBookMarks(): String {
        throw NotImplementedError()
    }

    @JavascriptInterface
    override fun setBookMarks(s: String) {
        throw NotImplementedError()
    }
}