package moe.huiguang.pixiv.data.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.net.Session
import moe.huiguang.pixiv.data.net.SessionHolder
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Display languages the app ships with; `SYSTEM` follows the device locale. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    SIMPLIFIED_CHINESE("zh-CN"),
    TRADITIONAL_CHINESE_HK("zh-HK"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            if (tag.isNullOrEmpty()) SYSTEM
            else entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: SYSTEM
    }
}

/** Light / dark override; `SYSTEM` keeps following the OS. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromKey(value: String?): ThemeMode =
            entries.firstOrNull { it.key == value } ?: SYSTEM
    }
}

/** Reader canvas palette; `SYSTEM` tracks the app light/dark theme. */
enum class PaperTheme(val key: String) {
    SYSTEM("system"),
    IVORY("ivory"),
    SAGE("sage"),
    DARK("dark"),
    ;

    companion object {
        fun fromKey(value: String?): PaperTheme =
            entries.firstOrNull { it.key == value } ?: SYSTEM
    }
}

/** Reader typography, persisted so the next novel opens with the same look. */
data class ReaderStyle(
    val fontSize: Int = 17,
    /** 300 / 400 / 500 / 700. */
    val weight: Int = 400,
    val indent: Boolean = true,
    val paperTheme: PaperTheme = PaperTheme.SYSTEM,
    val lineSpacing: Float = 1.9f,
    val paged: Boolean = false,
)

/** Everything the UI needs to observe in one snapshot. */
data class SettingsState(
    val session: Session? = null,
    /** `true` = R-18 works start hidden and need a tap; `false` = show directly. */
    val revealR18: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contourGlow: Boolean = true,
    val leftHanded: Boolean = false,
    val welcomeAccepted: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val favoriteTags: List<String> = emptyList(),
    val reader: ReaderStyle = ReaderStyle(),
)

/**
 * Persisted session, browsing preferences and lookup history.
 *
 * Only the **session cookie** is stored — never the account password, which is
 * typed straight into Pixiv's own page inside the login WebView.
 */
class SettingsStore(private val store: KeyValueStore) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val current: SettingsState get() = _state.value

    init {
        SessionHolder.cookie = _state.value.session?.cookie
    }

    // --------------------------------------------------------------- session

    fun saveSession(cookie: String, userId: String) {
        store.putString(KEY_COOKIE, cookie)
        store.putString(KEY_USER_ID, userId)
        SessionHolder.cookie = cookie
        _state.value = _state.value.copy(session = Session(cookie, userId))
    }

    fun logout() {
        store.remove(KEY_COOKIE)
        store.remove(KEY_USER_ID)
        SessionHolder.cookie = null
        _state.value = _state.value.copy(session = null)
    }

    // ------------------------------------------------------------ preference

    fun setRevealR18(value: Boolean) {
        store.putBoolean(KEY_REVEAL_R18, value)
        _state.value = _state.value.copy(revealR18 = value)
    }

    fun setLanguage(value: AppLanguage) {
        store.putString(KEY_LANGUAGE, value.tag)
        _state.value = _state.value.copy(language = value)
    }

    fun setThemeMode(value: ThemeMode) {
        store.putString(KEY_THEME, value.key)
        _state.value = _state.value.copy(themeMode = value)
    }

    fun setContourGlow(value: Boolean) {
        store.putBoolean(KEY_CONTOUR_GLOW, value)
        _state.value = _state.value.copy(contourGlow = value)
    }

    fun setLeftHanded(value: Boolean) {
        store.putBoolean(KEY_LEFT_HANDED, value)
        _state.value = _state.value.copy(leftHanded = value)
    }

    fun setWelcomeAccepted(value: Boolean) {
        store.putBoolean(KEY_WELCOME_ACCEPTED, value)
        _state.value = _state.value.copy(welcomeAccepted = value)
    }

    fun setReaderFontSize(size: Int) {
        val clamped = size.coerceIn(MIN_FONT, MAX_FONT)
        store.putString(KEY_READER_FONT, clamped.toString())
        _state.value = _state.value.copy(reader = _state.value.reader.copy(fontSize = clamped))
    }

    fun setReaderWeight(weight: Int) {
        store.putString(KEY_READER_WEIGHT, weight.toString())
        _state.value = _state.value.copy(reader = _state.value.reader.copy(weight = weight))
    }

    fun setReaderIndent(indent: Boolean) {
        store.putBoolean(KEY_READER_INDENT, indent)
        _state.value = _state.value.copy(reader = _state.value.reader.copy(indent = indent))
    }

    fun setReaderPaperTheme(theme: PaperTheme) {
        store.putString(KEY_READER_PAPER, theme.key)
        _state.value = _state.value.copy(reader = _state.value.reader.copy(paperTheme = theme))
    }

    fun setReaderLineSpacing(multiplier: Float) {
        val clamped = multiplier.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING)
        store.putString(KEY_READER_LINE_SPACING, clamped.toString())
        _state.value = _state.value.copy(reader = _state.value.reader.copy(lineSpacing = clamped))
    }

    fun setReaderPaged(paged: Boolean) {
        store.putBoolean(KEY_READER_PAGED, paged)
        _state.value = _state.value.copy(reader = _state.value.reader.copy(paged = paged))
    }

    // --------------------------------------------------------------- history

    /** Newest first, de-duplicated by (pid, kind), capped at [MAX_HISTORY]. */
    fun addHistory(entry: HistoryEntry) {
        if (entry.pid.isBlank() || entry.title.isBlank()) return
        val next = (listOf(entry) + _state.value.history.filterNot {
            it.pid == entry.pid && it.kind == entry.kind
        }).take(MAX_HISTORY)
        store.putString(KEY_HISTORY, encodeHistory(next))
        _state.value = _state.value.copy(history = next)
    }

    fun removeHistory(pid: String, kind: String) {
        val next = _state.value.history.filterNot { it.pid == pid && it.kind == kind }
        if (next.size == _state.value.history.size) return
        if (next.isEmpty()) store.remove(KEY_HISTORY) else store.putString(KEY_HISTORY, encodeHistory(next))
        _state.value = _state.value.copy(history = next)
    }

    fun clearHistory() {
        store.remove(KEY_HISTORY)
        _state.value = _state.value.copy(history = emptyList())
    }

    /** Toggles bookmarking [tag] locally; returns `true` when [tag] is now favorited. */
    fun toggleFavoriteTag(tag: String): Boolean {
        val normalized = tag.trim()
        if (normalized.isEmpty()) return false
        val currentList = _state.value.favoriteTags
        val alreadyFavorited = normalized in currentList
        val next = if (alreadyFavorited) {
            currentList.filterNot { it == normalized }
        } else {
            listOf(normalized) + currentList
        }
        if (next.isEmpty()) store.remove(KEY_FAVORITE_TAGS)
        else store.putString(KEY_FAVORITE_TAGS, encodeStringList(next))
        _state.value = _state.value.copy(favoriteTags = next)
        return !alreadyFavorited
    }

    private fun load(): SettingsState {
        val cookie = store.getString(KEY_COOKIE)
        val userId = store.getString(KEY_USER_ID)
        val session = if (!cookie.isNullOrBlank() && !userId.isNullOrBlank()) {
            Session(cookie, userId)
        } else {
            null
        }
        return SettingsState(
            session = session,
            revealR18 = store.getBoolean(KEY_REVEAL_R18, true),
            language = AppLanguage.fromTag(store.getString(KEY_LANGUAGE)),
            themeMode = ThemeMode.fromKey(store.getString(KEY_THEME)),
            contourGlow = store.getBoolean(KEY_CONTOUR_GLOW, true),
            leftHanded = store.getBoolean(KEY_LEFT_HANDED, false),
            welcomeAccepted = store.getBoolean(KEY_WELCOME_ACCEPTED, false),
            history = loadHistory(),
            favoriteTags = loadStringList(KEY_FAVORITE_TAGS),
            reader = ReaderStyle(
                fontSize = store.getString(KEY_READER_FONT)?.toIntOrNull()?.coerceIn(MIN_FONT, MAX_FONT) ?: 17,
                weight = store.getString(KEY_READER_WEIGHT)?.toIntOrNull() ?: 400,
                indent = store.getBoolean(KEY_READER_INDENT, true),
                paperTheme = PaperTheme.fromKey(store.getString(KEY_READER_PAPER)),
                lineSpacing = store.getString(KEY_READER_LINE_SPACING)?.toFloatOrNull()
                    ?.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING) ?: 1.9f,
                paged = store.getBoolean(KEY_READER_PAGED, false),
            ),
        )
    }

    private fun loadHistory(): List<HistoryEntry> {
        val raw = store.getString(KEY_HISTORY) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val pid = o.optString("pid")
                    if (pid.isEmpty()) continue
                    add(
                        HistoryEntry(
                            pid = pid,
                            kind = o.optString("kind", HistoryEntry.KIND_ILLUST),
                            title = o.optString("title"),
                            thumbUrl = o.optString("thumb"),
                            at = o.optLong("at"),
                            userName = o.optString("user"),
                            illustType = o.optInt("type", 0),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encodeHistory(entries: List<HistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("pid", e.pid)
                    put("kind", e.kind)
                    put("title", e.title)
                    put("thumb", e.thumbUrl)
                    put("at", e.at)
                    put("user", e.userName)
                    put("type", e.illustType)
                },
            )
        }
        return array.toString()
    }

    private fun loadStringList(key: String): List<String> {
        val raw = store.getString(key) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotEmpty() && value !in this) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeStringList(items: List<String>): String =
        JSONArray().apply { items.forEach { put(it) } }.toString()

    companion object {
        private const val KEY_COOKIE = "session_cookie"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_REVEAL_R18 = "reveal_r18"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_CONTOUR_GLOW = "contour_glow"
        private const val KEY_LEFT_HANDED = "left_handed"
        private const val KEY_WELCOME_ACCEPTED = "welcome_accepted"
        private const val KEY_HISTORY = "history"
        private const val KEY_FAVORITE_TAGS = "favorite_tags"
        private const val KEY_READER_FONT = "reader_font_size"
        private const val KEY_READER_WEIGHT = "reader_font_weight"
        private const val KEY_READER_INDENT = "reader_indent"
        private const val KEY_READER_PAPER = "reader_paper_theme"
        private const val KEY_READER_LINE_SPACING = "reader_line_spacing"
        private const val KEY_READER_PAGED = "reader_paged"
        private const val MAX_HISTORY = 60
        private const val MIN_FONT = 13
        private const val MAX_FONT = 23
        private const val MIN_LINE_SPACING = 1.5f
        private const val MAX_LINE_SPACING = 2.4f

        /** Traditional Chinese users should not have to switch manually. */
        fun systemDefaultLanguage(locale: Locale = Locale.getDefault()): AppLanguage {
            val isTraditional = locale.language == "zh" &&
                (locale.country == "HK" || locale.country == "TW" || locale.country == "MO")
            return if (isTraditional) AppLanguage.TRADITIONAL_CHINESE_HK else AppLanguage.SIMPLIFIED_CHINESE
        }
    }
}
