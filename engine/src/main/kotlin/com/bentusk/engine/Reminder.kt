package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A finished post waiting for its planned time. The files are already in the gallery; at [dueAt] a notification
 * brings the user back to hand them, with the caption on the clipboard, to the Instagram app. Nothing is posted
 * on the user's behalf.
 */
@Serializable
data class PostReminder(
    val id: String,
    /** Epoch milliseconds. */
    val dueAt: Long,
    /** Content URIs of the saved files, in the order they are posted. */
    val uris: List<String>,
    val mime: String,
    val caption: String = "",
    /** A grid goes up one tile at a time, so each tile is handed over on its own. */
    val oneAtATime: Boolean = false,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun toJson(reminder: PostReminder): String = json.encodeToString(serializer(), reminder)

        fun fromJson(text: String): PostReminder? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

/** Ready-made times to offer when the user sets a reminder. */
object ReminderTimes {
    /** Morning, lunch and evening: when most people open Instagram. */
    val Slots: List<LocalTime> = listOf(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(19, 0))

    /** A reminder sooner than this is not worth setting; the user can post now instead. */
    const val MIN_LEAD_MINUTES = 15L

    /** How many quick picks to offer. */
    const val PICKS = 3

    /** The next [PICKS] slots at least [MIN_LEAD_MINUTES] after [now]. */
    fun quickPicks(now: LocalDateTime): List<LocalDateTime> {
        val earliest = now.plusMinutes(MIN_LEAD_MINUTES)
        return generateSequence(now.toLocalDate()) { it.plusDays(1) }
            .flatMap { day -> Slots.asSequence().map { day.atTime(it) } }
            .filter { !it.isBefore(earliest) }
            .take(PICKS)
            .toList()
    }

    /** Whether [time] is far enough after [now] to remind about. */
    fun isValid(time: LocalDateTime, now: LocalDateTime): Boolean = !time.isBefore(now.plusMinutes(MIN_LEAD_MINUTES))

    /** How a quick pick reads: today, tomorrow, or a later day. */
    enum class Day { Today, Tomorrow, Later }

    fun day(time: LocalDateTime, today: LocalDate): Day = when (time.toLocalDate()) {
        today -> Day.Today
        today.plusDays(1) -> Day.Tomorrow
        else -> Day.Later
    }
}
