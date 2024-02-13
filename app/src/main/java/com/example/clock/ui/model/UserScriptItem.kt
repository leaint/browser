package com.example.clock.ui.model

import android.util.JsonReader
import android.util.JsonWriter
import com.example.clock.settings.obj
import com.example.clock.utils.IJSONOpt
import java.lang.reflect.Field

class UserScriptItem(
    var key: String,
    var file: String,
) : IJSONOpt<UserScriptItem>() {

    override fun parseJSON(r: JsonReader): UserScriptItem? {

        r.obj { k ->
            run {

                when (k) {
                    UserScriptItem::key.name -> key = nextString()
                    UserScriptItem::file.name -> file = nextString()
                    else -> skipValue()
                }
            }
        }
        return this

    }

    override fun writeJSON(w: JsonWriter) {
    }

}