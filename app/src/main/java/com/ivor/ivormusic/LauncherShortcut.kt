package com.ivor.ivormusic

/**
 * The static launcher shortcuts declared in res/xml/shortcuts.xml (and its
 * debug copy). The action strings are part of that contract: they are fixed,
 * not derived from the application id, because the shortcut intents are
 * explicit and already name the right package.
 */
enum class LauncherShortcut(val action: String) {
    SEARCH("org.triat.mobile.action.SEARCH"),
    LIBRARY("org.triat.mobile.action.LIBRARY"),
    SHUFFLE_LIKED("org.triat.mobile.action.SHUFFLE_LIKED");

    companion object {
        fun fromAction(action: String?): LauncherShortcut? =
            entries.firstOrNull { it.action == action }
    }
}
