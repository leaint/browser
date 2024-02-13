package com.example.clock.utils

import android.util.JsonReader
import android.util.JsonWriter
import androidx.annotation.Keep
import java.lang.reflect.Field

abstract class IJSONOpt<T> {

    abstract fun parseJSON(r: JsonReader): T?
    abstract fun writeJSON(w: JsonWriter)
}