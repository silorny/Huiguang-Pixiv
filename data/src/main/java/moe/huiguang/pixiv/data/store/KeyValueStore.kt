package moe.huiguang.pixiv.data.store

import android.content.SharedPreferences

/**
 * The only storage primitive the data layer knows about.
 *
 * Keeping `SharedPreferences` behind this interface lets every store be
 * exercised on a plain JVM (see [InMemoryKeyValueStore]) instead of needing an
 * Android instrumentation test.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

/** Android implementation, backed by a private `SharedPreferences` file. */
class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** Test / preview implementation. Not thread-safe on purpose: tests are single-threaded. */
class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {

    private val values = LinkedHashMap(initial)

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value.toString()
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    fun snapshot(): Map<String, String> = values.toMap()
}
