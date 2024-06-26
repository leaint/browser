package com.example.clock.ui.model

import android.os.Parcel
import android.os.Parcelable
import android.util.JsonReader
import android.util.JsonWriter
import com.example.clock.settings.obj
import com.example.clock.utils.IJSONOpt

class SiteSetting(
    @JvmField
    var user_agent: String,
    @JvmField
    var cache_navigation: Boolean,
    @JvmField
    var allow_go_outside: Boolean,
    @JvmField
    var allow_auto_redirect: Boolean,
    @JvmField
    var no_js: Boolean,
) : IJSONOpt<SiteSetting>(), Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
    )

    constructor() : this("", true, true, false, false)

    override fun parseJSON(r: JsonReader): SiteSetting {


        r.obj { k ->
            run {
                when (k) {
                    SiteSetting::user_agent.name -> user_agent = nextString()
                    SiteSetting::allow_go_outside.name -> allow_go_outside = nextBoolean()
                    SiteSetting::cache_navigation.name -> cache_navigation = nextBoolean()
                    SiteSetting::allow_auto_redirect.name -> allow_auto_redirect = nextBoolean()
                    SiteSetting::no_js.name -> no_js = nextBoolean()
                    else -> skipValue()
                }
            }
        }
        return this
    }

    override fun writeJSON(w: JsonWriter) {
        w.name(SiteSetting::user_agent.name).value(user_agent)
        w.name(SiteSetting::cache_navigation.name).value(cache_navigation)
        w.name(SiteSetting::allow_go_outside.name).value(allow_go_outside)
        w.name(SiteSetting::allow_auto_redirect.name).value(allow_auto_redirect)
        w.name(SiteSetting::no_js.name).value(no_js)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(user_agent)
        parcel.writeByte(if (cache_navigation) 1 else 0)
        parcel.writeByte(if (allow_go_outside) 1 else 0)
        parcel.writeByte(if (allow_auto_redirect) 1 else 0)
        parcel.writeByte(if (no_js) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SiteSetting> {
        override fun createFromParcel(parcel: Parcel): SiteSetting {
            return SiteSetting(parcel)
        }

        override fun newArray(size: Int): Array<SiteSetting?> {
            return arrayOfNulls(size)
        }
    }
}