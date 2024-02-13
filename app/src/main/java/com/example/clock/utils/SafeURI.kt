package com.example.clock.utils

import java.io.FileInputStream
import java.io.InputStream
import java.net.URI

/**
 * Nullable URI field
 */
class SafeURI private constructor(str: String) {
    private val uri = URI(str)

    val authority: String? get() = uri.authority
    val path: String? get() = uri.path
    val query: String? get() = uri.query
    val fragment: String? get() = uri.fragment

    companion object {
        fun parse(str: String): SafeURI? {
            return try {
                SafeURI(str)
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }
        }
    }

}

/*
fun JSONObject.getNullableBoolean(key: String?): Boolean? = getBoolean(key)

fun JSONObject.getNullableObject(key: String?): JSONObject? = getJSONObject(key)

fun JSONObject.getNullableArray(key: String?): JSONArray? = getJSONArray(key)

fun parseJSONObjectNullable(k:String):JSONObject? = JSON.parseObject(k)
fun parseJSONObjectNullable(k:InputStream):JSONObject? = JSON.parseObject(k)
fun parseJSONObjectNullable(k: FileInputStream):JSONObject? = JSON.parseObject(k)

fun parseJSONObjectNullable(k:ByteArray):JSONObject? = JSON.parseObject(k)

fun JSON.parseObjectNullable(k:String):JSONObject? = JSON.parseObject(k)

 */