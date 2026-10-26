package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderTest {
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test
    fun `early morning offers today's three slots`() {
        assertEquals(listOf(at(24, 9), at(24, 13), at(24, 19)), ReminderTimes.quickPicks(at(24, 7, 30)))
    }

    @Test
    fun `slots closer than the lead time are skipped`() {
        // 8:50 is ten minutes before nine, too soon to be worth a reminder.
        assertEquals(listOf(at(24, 13), at(24, 19), at(25, 9)), ReminderTimes.quickPicks(at(24, 8, 50)))
        assertEquals(at(24, 9), ReminderTimes.quickPicks(at(24, 8, 45)).first())
    }

    @Test
    fun `late evening rolls over to tomorrow`() {
        assertEquals(listOf(at(25, 9), at(25, 13), at(25, 19)), ReminderTimes.quickPicks(at(24, 21)))
    }

    @Test
    fun `the end of a month rolls into the next`() {
        val picks = ReminderTimes.quickPicks(LocalDateTime.of(2026, 10, 31, 20, 0))
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), picks.first())
    }

    @Test
    fun `times too soon are not valid`() {
        val now = at(24, 10)
        assertFalse(ReminderTimes.isValid(at(24, 10, 5), now))
        assertTrue(ReminderTimes.isValid(at(24, 10, 15), now))
        assertFalse(ReminderTimes.isValid(at(23, 19), now))
    }

    @Test
    fun `quick picks say which day they fall on`() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals(ReminderTimes.Day.Today, ReminderTimes.day(at(24, 19), today))
        assertEquals(ReminderTimes.Day.Tomorrow, ReminderTimes.day(at(25, 9), today))
        assertEquals(ReminderTimes.Day.Later, ReminderTimes.day(at(27, 9), today))
    }

    @Test
    fun `reminders survive saving, and broken files are ignored`() {
        val reminder = PostReminder("r1", 1_761_300_000_000, listOf("content://a", "content://b"), "image/jpeg", "Hi\n⠀\nthere", true)
        assertEquals(reminder, PostReminder.fromJson(PostReminder.toJson(reminder)))
        assertNull(PostReminder.fromJson("{oops"))
    }
}
