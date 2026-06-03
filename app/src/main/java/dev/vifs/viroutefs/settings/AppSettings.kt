package dev.vifs.viroutefs.settings

import android.content.Context

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val language: AppLanguage = AppLanguage.Russian,
)

enum class AppThemeMode(val storageKey: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    AmoledBlack("amoled_black");

    companion object {
        fun fromStorageKey(value: String?): AppThemeMode = entries.firstOrNull { it.storageKey == value } ?: System
    }
}

enum class AppLanguage(val storageKey: String, val nativeName: String) {
    Russian("ru", "Русский"),
    English("en", "English"),
    ChineseSimplified("zh_hans", "中文简体");

    companion object {
        fun fromStorageKey(value: String?): AppLanguage = entries.firstOrNull { it.storageKey == value } ?: Russian
    }
}

class AppSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        themeMode = AppThemeMode.fromStorageKey(prefs.getString(KEY_THEME, null)),
        language = AppLanguage.fromStorageKey(prefs.getString(KEY_LANGUAGE, null)),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME, settings.themeMode.storageKey)
            .putString(KEY_LANGUAGE, settings.language.storageKey)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "viroutefs_app_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "language"
    }
}
