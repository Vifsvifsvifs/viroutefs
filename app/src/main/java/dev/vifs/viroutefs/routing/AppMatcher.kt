package dev.vifs.viroutefs.routing

fun appMatcherExamples(): List<AppMatcher> = listOf(
    AppMatcher(AppMatcherPlatform.Android, "org.telegram.messenger", "Telegram Android"),
    AppMatcher(AppMatcherPlatform.Android, "ru.sberbankmobile", "СберБанк Онлайн"),
    AppMatcher(AppMatcherPlatform.Linux, "telegram-desktop", "Telegram Desktop"),
    AppMatcher(AppMatcherPlatform.Linux, "firefox", "Firefox"),
    AppMatcher(AppMatcherPlatform.Windows, "Telegram.exe", "Telegram Windows"),
    AppMatcher(AppMatcherPlatform.Windows, "chrome.exe", "Chrome Windows"),
    AppMatcher(AppMatcherPlatform.Any, "telegram", "Telegram"),
)
