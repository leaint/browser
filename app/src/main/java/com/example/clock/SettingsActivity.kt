@file:Suppress("DEPRECATION")

package com.example.clock

import android.app.Fragment
import android.app.FragmentManager
import android.app.FragmentTransaction
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceDataStore
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.ArraySet
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.FragmentResultOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.ui.main.EditFileFragment
import com.example.clock.ui.main.UserScriptFragment
import java.util.Collections

class LifecycleAwareResultListener(
    private val mLifecycle: Lifecycle,
    private val mListener: FragmentResultListener,
    private val mObserver: LifecycleEventObserver
) :
    FragmentResultListener {
    fun isAtLeast(state: Lifecycle.State): Boolean {
        return mLifecycle.currentState.isAtLeast(state)
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        mListener.onFragmentResult(requestKey, result)
    }

    fun removeObserver() {
        mLifecycle.removeObserver(mObserver)
    }
}

class MyFragmentResult(private val lifecycleOwner: LifecycleOwner) : FragmentResultOwner {
    private val mResults = Collections.synchronizedMap(HashMap<String, Bundle>())
    private val mResultListeners =
        Collections.synchronizedMap(HashMap<String, LifecycleAwareResultListener>())

    override fun setFragmentResult(requestKey: String, result: Bundle) {
        // Check if there is a listener waiting for a result with this key
        val resultListener: LifecycleAwareResultListener? = mResultListeners[requestKey]
        // if there is and it is started, fire the callback
        if (resultListener != null && resultListener.isAtLeast(Lifecycle.State.STARTED)) {
            resultListener.onFragmentResult(requestKey, result)
        } else {
            // else, save the result for later
            mResults[requestKey] = result
        }

    }

    override fun clearFragmentResult(requestKey: String) {
        mResults.remove(requestKey)
    }

    override fun setFragmentResultListener(
        requestKey: String,
        lifecycleOwner: LifecycleOwner,
        listener: FragmentResultListener
    ) {
        val lifecycle = lifecycleOwner.lifecycle
        if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        val observer: LifecycleEventObserver = object : LifecycleEventObserver {
            override fun onStateChanged(
                source: LifecycleOwner,
                event: Lifecycle.Event
            ) {
                if (event == Lifecycle.Event.ON_START) {
                    // once we are started, check for any stored results
                    val storedResult: Bundle? = mResults[requestKey]
                    if (storedResult != null) {
                        // if there is a result, fire the callback
                        listener.onFragmentResult(requestKey, storedResult)
                        // and clear the result
                        clearFragmentResult(requestKey)
                    }
                }
                if (event == Lifecycle.Event.ON_DESTROY) {
                    lifecycle.removeObserver(this)
                    mResultListeners.remove(requestKey)
                }
            }
        }
        mResultListeners.put(
            requestKey,
            LifecycleAwareResultListener(lifecycle, listener, observer)
        )?.removeObserver()

        // Only add the observer after we've added the listener to the map
        // to ensure that re-entrant removals actually have a registered listener to remove
        lifecycle.addObserver(observer)
    }

    override fun clearFragmentResultListener(requestKey: String) {
        mResultListeners.remove(requestKey)?.removeObserver()
    }

    fun setFragmentResultListener(
        requestKey: String,
        listener: FragmentResultListener
    ) {
        setFragmentResultListener(requestKey, lifecycleOwner, listener)
    }

}

class SettingsActivity : ComponentActivity(), PreferenceFragment.OnPreferenceStartFragmentCallback {

    val fragmentResult = MyFragmentResult(this)

    companion object {
        const val CHANGED = "CHANGED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar?.setDisplayHomeAsUpEnabled(true)

        setContentView(R.layout.settings_activity)
        val resultIntent = Intent()

        if (savedInstanceState == null) {
            setResult(RESULT_CANCELED)
            val fragment = when (intent.action) {
                SiteSettingsFragment.KEY -> {

                    intent.extras?.getString("HOST")?.let {
                        resultIntent.putExtra("HOST", it)
                    }
                    SiteSettingsFragment().apply {
                        preferenceDataStore = MemoryPreferenceDataStore(intent.extras)
                    }
                }

                else -> SettingsFragment()
            }

            resultIntent.action = intent.action

            fragmentManager.registerFragmentLifecycleCallbacks(object :
                FragmentManager.FragmentLifecycleCallbacks() {

                @Deprecated("Deprecated in Java")
                override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                    super.onFragmentStarted(fm, f)

                    fragmentResult.setFragmentResultListener(CHANGED) { s, b ->
                        run {

                            b.getStringArrayList("keys")?.let {
                                it.distinct().toTypedArray()
                                setResult(
                                    RESULT_OK,
                                    resultIntent
                                        .putExtra(
                                            "keys",
                                            it.distinct().toTypedArray()
                                        )
                                        .putExtra("data", b.getBundle("data"))
                                )
                            }
                        }
                    }

                }
            }, false)

            fragmentManager
                .beginTransaction()
                .replace(R.id.settings, fragment)
                .commit()
        }
    }

    override fun onNavigateUp(): Boolean {

        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
            return true
        } else {
            finish()
        }
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onPreferenceStartFragment(
        caller: PreferenceFragment?,
        pref: Preference?
    ): Boolean {

        pref ?: return false
        val tag = pref.key
        val fragment = when (tag) {
            "ad_rule" -> {
                EditFileFragment.newInstance(GlobalWebViewSetting.AD_RULE_FILE)
            }

            "replace_rule" -> {
                EditFileFragment.newInstance(GlobalWebViewSetting.REPLACE_RULE_FILE)
            }

            "user_script" -> {
                UserScriptFragment.newInstance()
            }

            "custom_dns_list" -> {
                EditFileFragment.newInstance(GlobalWebViewSetting.CUSTOM_DNS_LIST_FILE)
            }

            else -> return false
        }

        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(R.id.settings, fragment, tag)
            .addToBackStack(null)
            .commit()

        return true
    }
}

class SettingsFragment : PreferenceFragment() {
    companion object {
        const val KEY = "GLOBAL_SETTING"
        private const val CHANGED = "CHANGED"
    }

    private val changedBundle = Bundle()
    private val changedKey = ArrayList<String>()

    private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener

    @Deprecated("Deprecated in Java")
    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Deprecated("Deprecated in Java")
    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
        activity?.title = "Setting"
    }

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.root_preferences)

        listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key != null) {
                    changedKey.add(key)
                    changedBundle.putStringArrayList("keys", changedKey)
                    (activity as? SettingsActivity)?.fragmentResult?.setFragmentResult(
                        CHANGED,
                        changedBundle
                    )
                }
            }
        context?.let {
            val ua =
                PreferenceManager.getDefaultSharedPreferences(it).getString("user_agent", null)

            (findPreference("cached_tab_count") as? EditTextPreference)?.editText?.let {

                it.addTextChangedListener(object : TextWatcher {
                    var olds = 8
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                        olds = s?.toString()?.toIntOrNull()?.coerceIn(0, 16) ?: 8
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        /* no-op */
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val count = s?.toString()?.toIntOrNull()

                        if (count == null) {
                            it.setText("8")
                            return
                        }
                        if (count < 0 || count > 16) {
                            it.setText(olds.toString())
                        }
                    }
                })
            }

            findPreference("user_agent")?.setDefaultValue(ua)
        }
    }

}

class SiteSettingsFragment :
    PreferenceFragment() {

    companion object {
        const val KEY = "SITE_SETTING"
    }

    private val changedBundle = Bundle()
    private val changedKey = ArrayList<String>()

    var preferenceDataStore: MemoryPreferenceDataStore? = null

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.preferenceDataStore = preferenceDataStore
        addPreferencesFromResource(R.xml.site_preferences)
        findPreference("enabled")?.title =
            preferenceDataStore?.getString("HOST", "")

        preferenceDataStore?.registerOnChangeListener { _, key ->
            run {
                if (isAdded)
                    if (key != null) {
                        changedKey.add(key)
                        changedBundle.putBundle("data", preferenceDataStore?.editor)
                        changedBundle.putStringArrayList("keys", changedKey)
                        (activity as? SettingsActivity)?.fragmentResult?.setFragmentResult(
                            SettingsActivity.CHANGED,
                            changedBundle
                        )
                    }
            }
        }
    }
}

fun interface OnChangeListener {
    fun onChange(preferenceStore: PreferenceDataStore, key: String?)
}

class MemoryPreferenceDataStore(bundle: Bundle? = null) : PreferenceDataStore {

    val editor = bundle?.deepCopy() ?: Bundle()

    private val listeners = ArraySet<OnChangeListener>()

    @Deprecated("Deprecated in Java")
    override fun putString(key: String?, value: String?) {
        editor.putString(key, value)
        listeners.forEach {
            it.onChange(this, key)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun putStringSet(key: String?, values: MutableSet<String>?) {

        editor.putStringArray(key, values?.toTypedArray())
        listeners.forEach {
            it.onChange(this, key)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun putInt(key: String?, value: Int) {
        editor.putInt(key, value)
        listeners.forEach {
            it.onChange(this, key)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun putLong(key: String?, value: Long) {
        editor.putLong(key, value)
        listeners.forEach {
            it.onChange(this, key)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun putFloat(key: String?, value: Float) {
        editor.putFloat(key, value)
        listeners.forEach {
            it.onChange(this, key)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun putBoolean(key: String?, value: Boolean) {
        editor.putBoolean(key, value)
        listeners.forEach {
            it.onChange(this, key)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getString(key: String?, defValue: String?): String? {
        return editor.getString(key, defValue)
    }

    @Deprecated("Deprecated in Java")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?
    ): MutableSet<String>? {
        return editor.getStringArray(key)?.toMutableSet() ?: defValues
    }

    @Deprecated("Deprecated in Java")
    override fun getInt(key: String?, defValue: Int): Int {
        return editor.getInt(key, defValue)
    }

    @Deprecated("Deprecated in Java")
    override fun getLong(key: String?, defValue: Long): Long {
        return editor.getLong(key, defValue)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("preferences.getFloat(key, defValue)"))
    override fun getFloat(key: String?, defValue: Float): Float {
        return editor.getFloat(key, defValue)
    }

    @Deprecated("Deprecated in Java")
    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return editor.getBoolean(key, defValue)
    }

    fun registerOnChangeListener(listener: OnChangeListener?): Boolean {
        return listeners.add(listener)
    }

    fun unregisterOnChangeListener(listener: OnChangeListener?): Boolean {
        return listeners.remove(listener)
    }

}

class TempPreferenceStore(context: Context) : PreferenceDataStore {

    val preferences: SharedPreferences = context.getSharedPreferences(
        SiteSettingsFragment.KEY,
        PreferenceActivity.MODE_PRIVATE
    )
    private val editor: SharedPreferences.Editor by lazy {

        preferences.edit()
    }

    init {
        preferences.edit().clear().apply()
    }

    @Deprecated("Deprecated in Java")
    override fun putString(key: String?, value: String?) {
        editor.putString(key, value)
    }

    @Deprecated("Deprecated in Java")
    override fun putStringSet(key: String?, values: MutableSet<String>?) {
        editor.putStringSet(key, values)
    }

    @Deprecated("Deprecated in Java")
    override fun putInt(key: String?, value: Int) {
        editor.putInt(key, value)
    }

    @Deprecated("Deprecated in Java")
    override fun putLong(key: String?, value: Long) {
        editor.putLong(key, value)
    }

    @Deprecated("Deprecated in Java")
    override fun putFloat(key: String?, value: Float) {
        editor.putFloat(key, value)
    }

    @Deprecated("Deprecated in Java")
    override fun putBoolean(key: String?, value: Boolean) {
        editor.putBoolean(key, value)
    }

    @Deprecated("Deprecated in Java")
    override fun getString(key: String?, defValue: String?): String? {
        return preferences.getString(key, defValue)
    }

    @Deprecated("Deprecated in Java")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?
    ): MutableSet<String>? {
        return preferences.getStringSet(key, defValues)
    }

    @Deprecated("Deprecated in Java")
    override fun getInt(key: String?, defValue: Int): Int {
        return preferences.getInt(key, defValue)
    }

    @Deprecated("Deprecated in Java")
    override fun getLong(key: String?, defValue: Long): Long {
        return preferences.getLong(key, defValue)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("preferences.getFloat(key, defValue)"))
    override fun getFloat(key: String?, defValue: Float): Float {
        return preferences.getFloat(key, defValue)
    }

    @Deprecated("Deprecated in Java")
    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return preferences.getBoolean(key, defValue)
    }

    fun apply() {
        editor.apply()
    }


}
