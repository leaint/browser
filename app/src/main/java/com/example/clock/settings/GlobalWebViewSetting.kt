package com.example.clock.settings

import android.content.Context
import android.util.ArrayMap
import android.util.ArraySet
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.clock.R
import com.example.clock.SettingsFragment
import com.example.clock.SiteSettingsFragment
import com.example.clock.internal.J
import com.example.clock.ui.model.UserScript
import com.example.clock.ui.main.UserScriptFragment
import com.example.clock.ui.model.BookMark
import com.example.clock.ui.model.SiteSetting
import com.example.clock.utils.DNSClient
import com.example.clock.utils.readNBytesC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

/**
 * Ignore any exception and continue execute next expression
 */
inline fun <T> ignore(body: () -> T) {
    try {
        body()
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

inline fun JsonReader.obj(block: JsonReader.(k: String) -> Unit) {
    beginObject()
    while (hasNext()) {
        block(this, nextName())
    }
    endObject()
}

inline fun JsonReader.arr(block: JsonReader.() -> Unit) {
    beginArray()
    while (hasNext()) {
        block()
    }
    endArray()
}

inline fun JsonWriter.obj(nameStr: String? = null, block: JsonWriter.() -> Unit) {
    if (nameStr != null) {
        name(nameStr)
    }
    beginObject()
    block()
    endObject()
}

inline fun JsonWriter.arr(nameStr: String? = null, block: JsonWriter.() -> Unit) {
    if (nameStr != null) {
        name(nameStr)
    }
    beginArray()
    block()
    endArray()
}

enum class ChangedType {
    BOOKMARK,
    USER_SCRIPT
}

fun interface SettingChanged {
    fun onChanged(changedType: ChangedType)
}

class GlobalWebViewSetting(private val lifecycleOwner: LifecycleOwner, context: Context) :
    DefaultLifecycleObserver {

    private val settingChangedListeners = ArraySet<SettingChanged>()

    var ad_rule = HashMap<String, String>()
    var block_rule = ArraySet<String>()
    var userscriptMap = ArrayMap<String, UserScript>()
    var siteSettings = HashMap<String, SiteSetting>()
    var siteSettingsChanged = false
    var replace_rule = HashMap<String, String>()
    var enable_replace = false
    var read_mode = false

    var user_agent = ""
    var pc_user_agent = ""
    lateinit var dnsClient: DNSClient
    val inner_home_page = "file:///android_asset/homepage/index.html"
    var home_page = "https://cn.bing.com/"
    var search_url = "https://cn.bing.com/search?q="
    var start_page = inner_home_page
    var cached_tab_count = 8
    var cache_navigation = false
    var allow_go_outside = true
    var INIT_URI = start_page
    var dns_config = "127.0.0.1,20053"
    var custom_dns_list = ArrayList<String>()
    val JUMP_URI = "http://78.jump.to"
    var ruleChanged = false
    var can_copy = true

    var isFullScreen = true

    val can_copy_js =
        "\"use strict\";history.go = new Proxy(history.go, {apply:(target,that,args)=>{" +
                "try{if(args?.length > 0 && args[0] === -1) { MyHistory.back(); }" +
                " else{throw 'currently only support [ history.go(-1); ]';} }" +
                "catch(e){target.apply(that, args);console.error(e);}}});" +
                "history.back = new Proxy(history.back, {apply:(target,that,args)=>{" +
                "try{ MyHistory.back(); }catch(e){target.apply(that, args);console.error(e);}}});" +
                "navigator.clipboard.writeText = new Proxy(navigator.clipboard.writeText, {" +
                "apply:(target,that,args)=>{ try{ MyHistory.writeText(args[0]); }" +
                "catch(e){target.apply(that, args);console.error(e);} }});"
    val cannot_copy_js =
        "\"use strict\";history.go = new Proxy(history.go, {apply:(target,that,args)=>{" +
                "try{if(args?.length > 0 && args[0] === -1) { MyHistory.back(); }" +
                " else{throw 'currently only support [ history.go(-1); ]';} }" +
                "catch(e){target.apply(that, args);console.error(e);}}});" +
                "history.back = new Proxy(history.back, {apply:(target,that,args)=>{" +
                "try{ MyHistory.back(); }catch(e){target.apply(that, args);console.error(e);}}});"

    val do_pick_js =
        "\"use strict\";let joi_lp=0;document.body.insertAdjacentHTML('beforeend',`<div id=\"pick_789\" style=\"z-index:9999;\" >" +
                "<div id=\"ad_box_top\" style=\"outline: 1px solid red;position:fixed;height:0px;z-index:9999;\"></div>" +
                "<div id=\"ad_box_left\" style=\"outline: 1px solid red;position:fixed;width:0px;z-index:9999;\"></div>" +
                "<div id=\"ad_box_right\" style=\"outline: 1px solid red;position:fixed;width:0px;z-index:9999;\"></div>" +
                "<div id=\"ad_box_bottom\" style=\"outline: 1px solid red;position:fixed;height:0px;z-index:9999;\"></div>" +
                "</div>`);let atop=document.querySelector('#ad_box_top');let left=document.querySelector('#ad_box_left');let right=document.querySelector('#ad_box_right');let bottom=document.querySelector('#ad_box_bottom');let list=[];let curIdx=0;let cur=null;function show(t,push=!0){let rect=t.getBoundingClientRect();if(push){if(list.length==0||list.length>0&&list[list.length-1]!==t){list.push(t);curIdx=list.length-1;cur=t}}\n" +
                "atop.style.left=rect.left+'px';atop.style.top=rect.top+'px';atop.style.width=rect.width+'px';left.style.left=rect.left+'px';left.style.top=rect.top+'px';left.style.height=rect.height+'px';right.style.left=rect.left+rect.width+'px';right.style.top=rect.top+'px';right.style.height=rect.height+'px';bottom.style.left=rect.left+'px';bottom.style.top=rect.top+rect.height+'px';bottom.style.width=rect.width+'px';console.log(t,rect,window.scrollX,window.scrollY)}\n" +
                "[window,...Array.from(window.frames)].forEach(w=>{try{w.addEventListener('pointermove',(e)=>{/*console.log(e);*/let t=e.target;if(e.view.frameElement!=null){t = e.view.frameElement;};list=[];curIdx=-1;show(t)},{passive:!0});}catch(e){console.error(e);}});function format(e){let s=e.tagName.toLowerCase();if(e.id.length>0){s+='#'+e.id}\n" +
                "for(let i of e.classList){s+='.'+i}\n" +
                "return s}\n" +
                "function gg(){if(list.length>0&&curIdx>0){curIdx--;cur=list[curIdx];show(cur.parentElement)}\n" +
                "if(curIdx+1<list.length){cur=list[++curIdx]}\n" +
                "show(cur)}\n" +
                "function goUp(){show(cur.parentElement);return format(cur)}\n" +
                "function goBack(){if(list.length>0&&curIdx>0){curIdx--;cur=list[curIdx];show(cur.parentElement,!1)}\n" +
                "return format(cur)}\n" +
                "function getCur(){return format(cur)}"

    var externalFilesDir: File? = null
    var externalCacheDir: File? = null
    var externalIconCacheDir: File? = null
    private val icon_cache_dir = "icon_cache"
    val cache_url = "https://${EXTERNAL_CACHE_HOST}/"
    val icon_cache_url = "$cache_url$icon_cache_dir/"

    init {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            externalFilesDir = context.getExternalFilesDir(null)
            externalCacheDir = context.externalCacheDir
            externalIconCacheDir = File(externalFilesDir, icon_cache_dir).apply {
                if (!exists()) {
                    mkdir()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(this)
    }

    var bookMarksVersion = 0
    private var bookMarkStrVersion = bookMarksVersion - 1
    private var _bookMarkStr = ""
    var bookMarkStr: String
        get() {
            if (bookMarkStrVersion != bookMarksVersion) {
                _bookMarkStr = stringifyBookMarkFile(bookMarkArr)
                bookMarkStrVersion = bookMarksVersion
            }
            return _bookMarkStr
        }
        set(value) {
            bookMarksVersion++
            bookMarkArr.clear()
            parseBookMarkFile(value.toByteArray(), bookMarkArr)
            _bookMarkStr = value
            bookMarkStrVersion = bookMarksVersion
        }

    var bookMarkArr = ArrayList<BookMark>()

    fun addChangedListener(listener: SettingChanged) {
        settingChangedListeners.add(listener)
    }

    fun removeChangedListener(listener: SettingChanged) {
        settingChangedListeners.remove(listener)
    }

    private fun notifyListener(changedType: ChangedType) {
        settingChangedListeners.forEach {
            ignore {
                it.onChanged(changedType)
            }
        }

    }

    private fun parseSiteSettingFile(buf: ByteArray, arr: MutableMap<String, SiteSetting>) {

        try {
            JsonReader(buf.inputStream().reader()).use {
                it.obj { k ->
                    run {
                        SiteSetting().parseJSON(this).let {
                            arr[k] = it
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("json", "parseSiteSettingFile: ")
            e.printStackTrace()
        }

    }

    private fun stringifyBookMarkFile(arr: List<BookMark>): String {
        val w = StringWriter()

        try {
            JsonWriter(w).use {
                it.obj {
                    arr("bookmarks") {
                        arr.forEach {
                            obj {
                                it.writeJSON(this)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("json", "stringifyBookMarkFile: ")
            e.printStackTrace()
        }

        return w.toString()
    }

    suspend fun reloadBookMark(context: Context) {
        withContext(Dispatchers.IO) {
            val f = File(
                context.getExternalFilesDir(null),
                BOOKMARK_FILE
            )

            if (!f.exists()) {
                f.createNewFile()
                bookMarksVersion++
                context.assets.open("settings/bookmarks.json").use {

                    val buf = it.readNBytesC(Int.MAX_VALUE) ?: return@use
                    _bookMarkStr = String(buf)

                    parseBookMarkFile(buf, bookMarkArr)
                }
            } else {
                val buf = Files.readAllBytes(f.toPath())
                _bookMarkStr = String(buf)

                parseBookMarkFile(buf, bookMarkArr)

            }
        }

        bookMarkStrVersion = bookMarksVersion
        withContext(Dispatchers.Main) {
            notifyListener(ChangedType.BOOKMARK)
        }

    }

    suspend fun loadSetting(context: Context) {
        withContext(Dispatchers.IO) {

            user_agent = context.resources.getStringArray(R.array.user_agent_values).getOrNull(0)
                ?: "Mozilla/5.0 (Linux; Android 13.1.1; gsdg 6 Build/gsfsg) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5.6489 Mobile Safari/537.36"

            pc_user_agent = context.resources.getString(R.string.pc_user_agent)

            val e = async {
                context.getSharedPreferences(
                    J.concat(context.packageName, "_preferences"),
                    FragmentActivity.MODE_PRIVATE
                ).let {
                    home_page = it.getString("signature", null) ?: home_page
                    search_url = it.getString(::search_url.name, null) ?: search_url
                    start_page = it.getString(::start_page.name, null) ?: start_page
                    user_agent = it.getString(::user_agent.name, null) ?: user_agent
                    can_copy = it.getBoolean(::can_copy.name, can_copy)
                    cache_navigation = it.getBoolean(::cache_navigation.name, false)
                    val cachedTabCountStr =
                        it.getString(::cached_tab_count.name, null) ?: "$cached_tab_count"

                    try {
                        cached_tab_count = cachedTabCountStr.toInt().coerceIn(0, 16)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }


                    INIT_URI = start_page
                    dns_config = it.getString(::dns_config.name, null) ?: dns_config
                    val dnss = dns_config.split(',')
                    if (dnss.size == 2) {
                        val (s, port) = dnss
                        dnsClient = DNSClient(context, lifecycleOwner, s, port.toInt())
                    }

                    val customDnsList = it.getString(::custom_dns_list.name, null) ?: ""
                    customDnsList.lineSequence().forEach {
                        custom_dns_list.add(it)
                    }
                    enable_replace = it.getBoolean(::enable_replace.name, enable_replace)
                }

            }

            val a = async {
                val f =
                    File(context.getExternalFilesDir(null), SITE_SETTINGS_FILE)
                if (!f.exists()) {
                    f.createNewFile()
                }

                f.inputStream().use { input ->
                    run {
//                        val obj = parseJSONObjectNullable(input) ?: JSONObject()
//                        obj
                        val buf = Files.readAllBytes(f.toPath())

                        parseSiteSettingFile(buf, siteSettings)
                    }
                }
            }

            val b = async {
                val initData = run {
                    val f = File(
                        context.getExternalFilesDir(null),
                        UserScriptFragment.USER_SCRIPT_FILE
                    )
                    if (!f.exists()) {
                        f.createNewFile()
                    }

                    f.inputStream().use { input ->
                        run {
                            val buf = Files.readAllBytes(f.toPath())

                            parseUserScript(buf, userscriptMap)
//                            val obj = parseJSONObjectNullable(input) ?: JSONObject().apply {
//                                putArray("user_script")
//                            }
//                            obj
                        }
                    }
                }

            }

            val c = async {
                val f = File(context.getExternalFilesDir(null), AD_RULE_FILE)
                if (!f.exists()) {
                    f.createNewFile()
                }

                f.inputStream().use { input ->
                    input.bufferedReader().use {
                        it.lines().forEach {
                            if (it.isEmpty() || it[0] == '!') return@forEach
                            if (it.startsWith("||")) {
                                block_rule.add(it.trim('|', '^', ' '))
                                return@forEach
                            }
                            val line = it.split("##", limit = 2)
                            if (line.size == 2) {
                                ad_rule[line[0]] = line[1]
                            }
                        }
                    }
                }
            }

            val d = async {
                val f = File(
                    context.getExternalFilesDir(null),
                    REPLACE_RULE_FILE
                )
                if (!f.exists()) {
                    f.createNewFile()
                }

                f.inputStream().use { input ->
                    input.bufferedReader().use {
                        it.lines().forEach {
                            val line = it.split('|')
                            if (line.size == 2) {
                                replace_rule[line[0]] = line[1]
                            }
                        }
                    }
                }

            }

            a.await()
            b.await()
            c.await()
            d.await()
            e.await()
        }
    }

    fun settingCallback(it: ActivityResult, context: Context) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            when (it.data?.action) {
                SiteSettingsFragment.KEY -> {
                    it.data!!.extras?.getStringArray("keys")?.let { keys ->
                        val host = it.data?.extras?.getString("HOST")
                        if (host != null) {
                            it.data?.extras?.getBundle("data")?.let {
                                val enabled = it.getBoolean("enabled", false)
                                var thisSiteSetting = siteSettings[host]
                                siteSettingsChanged = true
                                if (!enabled && thisSiteSetting != null) {
                                    siteSettings.remove(host)
                                } else {

                                    if (thisSiteSetting == null) {
                                        thisSiteSetting = SiteSetting()
                                        siteSettings[host] = thisSiteSetting
                                    }
                                    if (keys.contains(SiteSetting::user_agent.name)) {
                                        thisSiteSetting.user_agent =
                                            it.getString(::user_agent.name, null)
                                                ?: user_agent
                                    } else if (keys.contains(SiteSetting::cache_navigation.name)) {
                                        thisSiteSetting.cache_navigation =
                                            it.getBoolean(SiteSetting::cache_navigation.name, false)
                                    } else if (SiteSetting::allow_go_outside.name in keys) {
                                        thisSiteSetting.allow_go_outside =
                                            it.getBoolean(::allow_go_outside.name, true)
                                    } else {
                                        Unit
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsFragment.KEY -> {

                    it.data!!.extras?.getStringArray("keys")?.let { keys ->
                        context.getSharedPreferences(
                            "${context.packageName}_preferences",
                            FragmentActivity.MODE_PRIVATE
                        ).let {

                            if (keys.contains("signature")) home_page =
                                it.getString("signature", null) ?: home_page
                            if (keys.contains(::search_url.name)) search_url =
                                it.getString(::search_url.name, null) ?: search_url
                            if (keys.contains(::start_page.name)) {
                                start_page =
                                    it.getString(::start_page.name, null) ?: start_page
                                INIT_URI = start_page
                            }
                            if (keys.contains(::user_agent.name)) {
                                user_agent =
                                    it.getString(::user_agent.name, null) ?: user_agent
//
//                            val h = holderController.currentGroup.getCurrent()
//                                ?: throw KotlinNullPointerException()
//
//                            h.webView?.get()?.let {
//                                it.settings.userAgentString = user_agent
//                            }

                            }

                            if (keys.contains(::cache_navigation.name)) {
                                cache_navigation =
                                    it.getBoolean(::cache_navigation.name, false)
                            }

                            /*
                        if (keys.contains(::block_rule.name)) {
                            val blockRuleStr = it.getString(::block_rule.name, null) ?: ""
                            block_rule.clear()
                            block_rule.addAll(blockRuleStr.lines().filter {
                                it.isNotEmpty()
                            })
                        }
                         */
                            if (keys.contains(::enable_replace.name)) enable_replace =
                                it.getBoolean(::enable_replace.name, false)
                            if (keys.contains(::cached_tab_count.name)) {

                                val s = it.getString(::cached_tab_count.name, null) ?: ""
                                try {
                                    cached_tab_count = s.toInt().coerceIn(0, 16)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            if (keys.contains(::can_copy.name)) {
                                can_copy = it.getBoolean(::can_copy.name, can_copy)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            if (siteSettingsChanged) File(
                externalFilesDir,
                SITE_SETTINGS_FILE
            ).outputStream().use {
                ignore {
                    JsonWriter(it.writer()).use {
                        it.obj {
                            siteSettings.forEach { t, u ->
                                run {
                                    obj(t) {
                                        u.writeJSON(this)
                                    }
                                }
                            }
                        }
                    }
                }
                siteSettingsChanged = false
            }

            if (bookMarksVersion != 0) File(
                externalFilesDir,
                BOOKMARK_FILE
            ).outputStream().use {
                try {
                    JsonWriter(it.writer()).use {
                        it.obj {
                            arr("bookmarks") {
                                bookMarkArr.forEach {
                                    obj {
                                        it.writeJSON(this)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("json", "onPause: BOOKMARK_FILE")
                    e.printStackTrace()
                }

                bookMarksVersion = 0
            }
            if (ruleChanged) File(
                externalFilesDir,
                AD_RULE_FILE
            ).outputStream().use {
                val builder = StringBuilder(2048)
                ad_rule.forEach { (k, v) ->
                    builder.append(k).append("##").append(v).append('\n')
                }

                builder.append('\n')
                block_rule.forEach { builder.append("||").append(it).append('\n') }
                if (builder.count() > 1) {
                    it.bufferedWriter().use {
                        it.append(builder)
                    }
                }
                ruleChanged = false
            }

        }

    }

    companion object {
        const val EXTERNAL_CACHE_HOST = "externalcache"

        const val AD_RULE_FILE = "AD_RULE.txt"
        const val REPLACE_RULE_FILE = "replace_rule.txt"
        const val BOOKMARK_FILE = "BOOKMARK_FILE.json"
        const val SITE_SETTINGS_FILE = "SITE_SETTINGS.json"


        fun parseUserScriptFile(buf: ByteArray, arr: MutableMap<String, String>) {

            try {
                JsonReader(buf.inputStream().reader()).use {
                    it.obj {
                        when (it) {
                            "user_script" -> {
                                obj { k ->
                                    arr[k] = nextString()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("json", "parseUserScriptFile: ")
                e.printStackTrace()
            }


        }


        fun parseUserScript(buf: ByteArray, arr: MutableMap<String, UserScript>) {

            try {
                JsonReader(buf.inputStream().reader()).use {
                    it.obj {
                        when (it) {
                            "user_script" -> {
                                obj { k ->
                                    UserScript.parse(nextString())?.let {
                                        if (it.namespace != "off") it.match.forEach { match ->
                                            arr[match] = it
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("json", "parseUserScript: ")
                e.printStackTrace()
            }


//        for (i in 0 until it.size) {
//            it.getJSONObject(i)?.let {
//                it.keys.forEach { k ->
//                    it.getString(k)?.let { v ->
//                        UserScript.parse(v)?.let {
//                            if (it.namespace != "off") it.match.forEach { match ->
//                                scriptMap[match] = it
//                            }
//                        }
//                    }
//                }
//            }
//        }


        }

        fun stringifyUserScriptFile(arr: MutableMap<String, String>): String {
            val w = StringWriter()

            try {
                JsonWriter(w).use {
                    it.obj {
                        obj("user_script") {
                            arr.forEach {
                                name(it.key).value(it.value)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("json", "stringifyUserScriptFile: ")
                e.printStackTrace()
            }

            return w.toString()
        }

        fun parseBookMarkFile(buf: ByteArray, arr: MutableList<BookMark>) {
            try {
                JsonReader(buf.inputStream().reader()).use {

                    it.obj { k ->
                        when (k) {
                            "bookmarks" -> {
                                arr {
                                    val mm = BookMark().parseJSON(this)
                                    if (mm != null) {
                                        arr.add(mm)
                                    }
                                }
                            }

                        }
                    }
                }

            } catch (e: Throwable) {
                Log.d("json", "parseBookMarkFile: ")
                e.printStackTrace()
            }
        }

    }
}
/*

let joi_lp = 0;
document.body.insertAdjacentHTML('beforeend', `

<div id="ad_box_top" style="outline: 1px solid red;position:fixed;height:0px;"></div>
<div id="ad_box_left" style="outline: 1px solid red;position:fixed;width:0px;"></div>
<div id="ad_box_right" style="outline: 1px solid red;position:fixed;width:0px;"></div>
<div id="ad_box_bottom" style="outline: 1px solid red;position:fixed;height:0px;"></div>

`);

let atop = document.querySelector('#ad_box_top');
let left = document.querySelector('#ad_box_left');
let right = document.querySelector('#ad_box_right');
let bottom = document.querySelector('#ad_box_bottom');

let list = [];
let curIdx = 0;
let cur = null;

function show(t, push=true) {

    let rect = t.getBoundingClientRect();

    if (push) {

        if (list.length == 0 || list.length > 0 && list[list.length - 1] !== t) {

            list.push(t);

            curIdx = list.length - 1;
            cur = t;
        }
    }

    atop.style.left = rect.left + 'px';
    atop.style.top = rect.top + 'px';
    atop.style.width = rect.width + 'px';

    left.style.left = rect.left + 'px';
    left.style.top = rect.top + 'px';
    left.style.height = rect.height + 'px';

    right.style.left = rect.left + rect.width + 'px';
    right.style.top = rect.top + 'px';
    right.style.height = rect.height + 'px';

    bottom.style.left = rect.left + 'px';
    bottom.style.top = rect.top + rect.height + 'px';
    bottom.style.width = rect.width + 'px';

    console.log(t, rect, window.scrollX, window.scrollY)
}

[...Array.from(window.frames),window].forEach(w=>{

    try{
    window.addEventListener('pointermove', (e)=>{
        console.log(e);
        let t = e.target;

        list = [];
        curIdx = -1;

        show(t);
    }
    , {
        passive: true
    });
    } catch(e) { console.error(e);}
});
function format(e) {

    let s = e.tagName.toLowerCase();

    if (e.id.length > 0) {
        s += '#' + e.id;
    }

    for (let i of e.classList) {
        s += '.' + i;
    }

    return s;
}

function gg() {

    if (list.length > 0 && curIdx > 0) {
        curIdx--;
        cur = list[curIdx];
        show(cur.parentElement);

    }
    if (curIdx + 1 < list.length) {

        cur = list[++curIdx];
    }
    show(cur);
}

function goUp() {

    show(cur.parentElement);

    return format(cur);
}

function goBack() {

    if (list.length > 0 && curIdx > 0) {
        curIdx--;
        cur = list[curIdx];
        show(cur.parentElement, false);

    }
    return format(cur);
}

function getCur() {
    return format(cur);
}

 */