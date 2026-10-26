package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Hashtags the user saved under a name, to add to a caption in one tap. */
@Serializable
data class SavedTags(val id: Long, val name: String, val tags: List<String>)

/** A piece of caption the user reuses: a sign-off, a shop line, a series intro. */
@Serializable
data class SavedCaption(val id: Long, val name: String, val text: String)

/**
 * The user's saved hashtag sets and captions, newest first. Kept on the phone only.
 * Saving under a name already used replaces that entry, so re-saving updates it instead of piling up copies.
 */
@Serializable
data class CaptionLibrary(
    val tags: List<SavedTags> = emptyList(),
    val captions: List<SavedCaption> = emptyList(),
) {
    /** Saves the hashtags found in [from] (at most Instagram's limit), or returns this unchanged when there are none. */
    fun saveTags(name: String, from: List<String>, id: Long): CaptionLibrary {
        val list = from.map { "#" + it.trim().trimStart('#').lowercase() }.filter { it.length > 1 }.distinct().take(InstagramSpec.MAX_HASHTAGS)
        if (list.isEmpty()) return this
        val title = name.trim().ifEmpty { defaultName(list.joinToString(" ")) }
        val kept = tags.filterNot { it.name.equals(title, ignoreCase = true) }
        return copy(tags = (listOf(SavedTags(id, title, list)) + kept).take(MAX_ITEMS))
    }

    /** Saves [text] as a reusable caption, or returns this unchanged when it is blank. */
    fun saveCaption(name: String, text: String, id: Long): CaptionLibrary {
        val body = text.trim()
        if (body.isEmpty()) return this
        val title = name.trim().ifEmpty { defaultName(body) }
        val kept = captions.filterNot { it.name.equals(title, ignoreCase = true) }
        return copy(captions = (listOf(SavedCaption(id, title, body)) + kept).take(MAX_ITEMS))
    }

    fun removeTags(id: Long) = copy(tags = tags.filterNot { it.id == id })

    fun removeCaption(id: Long) = copy(captions = captions.filterNot { it.id == id })

    companion object {
        /** Entries kept of each kind; the oldest go first. */
        const val MAX_ITEMS = 20

        /** Longest name made up from the content when the user gives none. */
        const val NAME_CHARS = 24

        private val json = Json { ignoreUnknownKeys = true }

        fun toJson(library: CaptionLibrary): String = json.encodeToString(serializer(), library)

        /** An empty library when [text] is missing or unreadable, so a bad save never blocks the composer. */
        fun fromJson(text: String?): CaptionLibrary =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: CaptionLibrary()

        /** The first line of [text], shortened to [NAME_CHARS]. */
        fun defaultName(text: String): String {
            val line = text.trim().substringBefore('\n').trim()
            return if (line.length <= NAME_CHARS) line else line.take(NAME_CHARS - 1).trimEnd() + "…"
        }
    }
}
