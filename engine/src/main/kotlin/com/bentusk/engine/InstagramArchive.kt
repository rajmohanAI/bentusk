package com.bentusk.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** One post from the user's own Instagram archive. [media] are paths inside the ZIP, the cover first. */
data class ArchivePost(val takenAt: Long, val media: List<String>, val caption: String) {
    val cover: String get() = media.first()
    val isVideo: Boolean get() = cover.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS

    companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "webm")
    }
}

/**
 * Reads the post list from the ZIP that Instagram's "Download your information" export gives the user, so their
 * real grid can be laid under a plan. Only the JSON format is read. The export has changed layout over the years,
 * so three indexes are understood:
 *
 * - `content/posts_N.json` (since 2020, under `your_instagram_activity/` since 2024): an array of posts
 * - `content/reels.json`: reels, which also show on the grid, under `ig_reels_media`
 * - `media.json` (before 2020): `photos` and `videos` lists
 *
 * Media paths are relative to the archive's root, which may itself sit in a folder inside the ZIP.
 */
object InstagramArchive {
    /** Posts read from an archive at most, newest first; twenty rows of the grid. */
    const val MAX_POSTS = 60

    private val postsIndex = Regex("""^(.*?)(?:your_instagram_activity/)?content/(?:posts_\d+|reels)\.json$""")
    private val legacyIndex = Regex("""^(.*/)?media\.json$""")
    private val json = Json { ignoreUnknownKeys = true }

    /** Whether the ZIP entry [name] lists posts. */
    fun isIndex(name: String): Boolean = postsIndex.matches(name) || legacyIndex.matches(name)

    /** The posts listed by index entry [name], with media paths made relative to the ZIP itself. */
    fun parse(name: String, text: String): List<ArchivePost> {
        val root = postsIndex.matchEntire(name)?.groupValues?.get(1)
            ?: legacyIndex.matchEntire(name)?.groupValues?.get(1)
            ?: return emptyList()
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        return when {
            element is JsonArray -> element.mapNotNull { post(it, root) }
            element is JsonObject && "ig_reels_media" in element -> (element["ig_reels_media"] as? JsonArray).orEmpty().mapNotNull { post(it, root) }
            element is JsonObject -> legacy(element, root)
            else -> emptyList()
        }
    }

    /** All posts from every index, newest first, each once, at most [MAX_POSTS]. */
    fun newestFirst(posts: List<ArchivePost>): List<ArchivePost> =
        posts.distinctBy { it.cover }.sortedByDescending { it.takenAt }.take(MAX_POSTS)

    /**
     * Instagram writes text as UTF-8 bytes escaped one byte per character, so "é" arrives as "Ã©". This undoes that,
     * and leaves text alone when it was not written that way.
     */
    fun fixText(text: String): String {
        if (text.none { it.code in 0x80..0xFF } || text.any { it.code > 0xFF }) return text
        val bytes = ByteArray(text.length) { text[it].code.toByte() }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            text
        }
    }

    private fun post(element: JsonElement, root: String): ArchivePost? {
        val obj = element as? JsonObject ?: return null
        val media = (obj["media"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val paths = media.mapNotNull { m -> m.string("uri")?.let { path(root, it) } }
        if (paths.isEmpty()) return null
        val takenAt = obj.long("creation_timestamp") ?: media.firstNotNullOfOrNull { it.long("creation_timestamp") } ?: 0
        val caption = obj.string("title")?.takeIf { it.isNotBlank() } ?: media.firstNotNullOfOrNull { it.string("title") } ?: ""
        return ArchivePost(takenAt, paths, fixText(caption))
    }

    private fun legacy(obj: JsonObject, root: String): List<ArchivePost> =
        listOf("photos", "videos").flatMap { key ->
            (obj[key] as? JsonArray).orEmpty().mapNotNull { item ->
                val m = item as? JsonObject ?: return@mapNotNull null
                val path = m.string("path") ?: return@mapNotNull null
                ArchivePost(legacyTime(m.string("taken_at")), listOf(path(root, path)), fixText(m.string("caption") ?: ""))
            }
        }

    /** Pre-2020 archives give times as ISO text, e.g. 2019-05-01T10:20:30+00:00. */
    private fun legacyTime(text: String?): Long {
        if (text == null) return 0
        return runCatching { OffsetDateTime.parse(text).toEpochSecond() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text).toEpochSecond(ZoneOffset.UTC) }.getOrNull()
            ?: 0
    }

    private fun path(root: String, uri: String): String = root + uri.trimStart('/')

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
