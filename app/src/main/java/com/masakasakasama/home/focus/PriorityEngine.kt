package com.masakasakasama.home.focus

import android.content.Context
import com.masakasakasama.home.data.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlin.math.roundToInt

data class FocusItem(
    val id: String,
    val emoji: String,
    val title: String,
    val detail: String,
    val progress: String,
    val actionLabel: String,
    val score: Int,
)

data class FocusSnapshot(
    val now: FocusItem,
    val next: FocusItem,
    val summary: String,
)

/**
 * Picks the single next action Home should emphasize.
 *
 * v1 intentionally keeps the rules local and deterministic:
 * 1. A workout that is due today and not completed wins.
 * 2. IELTS rises as the week progresses when the 8 h target is behind plan.
 * 3. German fills small gaps after the higher-priority commitments are handled.
 *
 * Completing an item from the widget immediately recomputes the ranking, so the
 * next concrete action replaces it without waiting for the next periodic update.
 */
object PriorityEngine {

    private const val PREFS = "priority_progress"
    private const val IELTS_WEEKLY_TARGET_MIN = 8 * 60
    private const val IELTS_SESSION_MIN = 40

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(context: Context, today: LocalDate = LocalDate.now()): FocusSnapshot {
        val trainDays = Config.trainDays(context)
        val workoutDone = workoutDone(context, today)
        val ieltsMinutes = ieltsMinutes(context, today)
        val germanDone = germanDone(context, today)

        val candidates = mutableListOf<FocusItem>()

        if (today.dayOfWeek.value in trainDays && !workoutDone) {
            candidates += FocusItem(
                id = "workout",
                emoji = "🏋️",
                title = "今日は筋トレ",
                detail = workoutMenu(today.dayOfWeek),
                progress = "未完了 · 約50分",
                actionLabel = "筋トレ完了",
                score = 100,
            )
        } else {
            val days = daysUntilNextWorkout(today, trainDays)
            candidates += FocusItem(
                id = "workout",
                emoji = "🏋️",
                title = if (workoutDone) "筋トレ完了" else "次の筋トレ",
                detail = if (workoutDone) "今日は完了 · 次は${nextWorkoutLabel(today, trainDays)}"
                else "${nextWorkoutLabel(today, trainDays)} · ${workoutMenu(nextWorkoutDay(today, trainDays))}",
                progress = if (workoutDone) "DONE" else "予定",
                actionLabel = if (workoutDone) "完了済み" else "筋トレ完了",
                score = if (workoutDone) 12 else when (days) {
                    1 -> 58
                    2 -> 48
                    else -> 34
                },
            )
        }

        val remaining = (IELTS_WEEKLY_TARGET_MIN - ieltsMinutes).coerceAtLeast(0)
        val expected = (IELTS_WEEKLY_TARGET_MIN * today.dayOfWeek.value / 7.0).roundToInt()
        val debt = (expected - ieltsMinutes).coerceAtLeast(0)
        val session = ieltsSession(ieltsMinutes)
        val ieltsScore = when {
            remaining == 0 -> 14
            debt >= 180 -> 98
            debt >= 120 -> 92
            debt >= 60 -> 84
            today.dayOfWeek == DayOfWeek.SUNDAY -> 90
            else -> 70 + today.dayOfWeek.value
        }
        candidates += FocusItem(
            id = "ielts",
            emoji = "🇬🇧",
            title = if (remaining == 0) "IELTS 今週達成" else "IELTS · ${session.first}",
            detail = if (remaining == 0) "8時間達成 · 次週まで維持"
            else session.second,
            progress = "${formatMinutes(ieltsMinutes)} / 8h · 残り${formatMinutes(remaining)}",
            actionLabel = if (remaining == 0) "達成済み" else "40分完了",
            score = ieltsScore,
        )

        candidates += FocusItem(
            id = "german",
            emoji = "🇩🇪",
            title = if (germanDone) "ドイツ語 完了" else "ドイツ語 20分",
            detail = if (germanDone) "今日は完了" else "基礎語彙 + 音読を1セット",
            progress = if (germanDone) "DONE" else "今日の継続",
            actionLabel = if (germanDone) "完了済み" else "20分完了",
            score = if (germanDone) 10 else 42,
        )

        val ranked = candidates.sortedByDescending { it.score }
        val workoutCount = workoutCountThisWeek(context, today)
        val trainTarget = trainDays.size.coerceAtLeast(1)
        return FocusSnapshot(
            now = ranked[0],
            next = ranked[1],
            summary = "IELTS ${formatMinutes(ieltsMinutes)}/8h  ·  筋トレ $workoutCount/$trainTarget",
        )
    }

    fun complete(context: Context, id: String, today: LocalDate = LocalDate.now()) {
        when (id) {
            "workout" -> sp(context).edit().putBoolean(workoutKey(today), true).apply()
            "ielts" -> {
                val key = ieltsKey(today)
                val current = sp(context).getInt(key, 0).coerceAtLeast(0)
                sp(context).edit()
                    .putInt(key, (current + IELTS_SESSION_MIN).coerceAtMost(12 * 60))
                    .apply()
            }
            "german" -> sp(context).edit().putBoolean(germanKey(today), true).apply()
        }
    }

    private fun workoutDone(context: Context, date: LocalDate) =
        sp(context).getBoolean(workoutKey(date), false)

    private fun germanDone(context: Context, date: LocalDate) =
        sp(context).getBoolean(germanKey(date), false)

    private fun ieltsMinutes(context: Context, date: LocalDate) =
        sp(context).getInt(ieltsKey(date), 0).coerceAtLeast(0)

    private fun workoutKey(date: LocalDate) = "workout_$date"
    private fun germanKey(date: LocalDate) = "german_$date"

    private fun ieltsKey(date: LocalDate): String {
        val wf = WeekFields.ISO
        return "ielts_${date.get(wf.weekBasedYear())}_${date.get(wf.weekOfWeekBasedYear())}"
    }

    private fun workoutCountThisWeek(context: Context, today: LocalDate): Int {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return (0L..6L).count { workoutDone(context, monday.plusDays(it)) }
    }

    private fun daysUntilNextWorkout(today: LocalDate, trainDays: Set<Int>): Int {
        if (trainDays.isEmpty()) return 7
        for (offset in 1..7) {
            if (today.plusDays(offset.toLong()).dayOfWeek.value in trainDays) return offset
        }
        return 7
    }

    private fun nextWorkoutDay(today: LocalDate, trainDays: Set<Int>): DayOfWeek {
        val offset = daysUntilNextWorkout(today, trainDays)
        return today.plusDays(offset.toLong()).dayOfWeek
    }

    private fun nextWorkoutLabel(today: LocalDate, trainDays: Set<Int>): String {
        val days = daysUntilNextWorkout(today, trainDays)
        return when (days) {
            1 -> "明日"
            2 -> "明後日"
            else -> "${jp(nextWorkoutDay(today, trainDays))}曜"
        }
    }

    private fun workoutMenu(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Push · ベンチ / 肩 / 三頭"
        DayOfWeek.WEDNESDAY -> "Pull · ラットプル / ロー / 二頭"
        DayOfWeek.FRIDAY -> "Legs · スクワット / RDL / 腹"
        else -> "全身 · スクワット / プレス / ロー"
    }

    private fun ieltsSession(minutes: Int): Pair<String, String> {
        val index = (minutes / IELTS_SESSION_MIN) % 4
        return when (index) {
            0 -> "Reading" to "40分 · 1 passage + 復習"
            1 -> "Listening" to "40分 · 1 section + 聞き直し"
            2 -> "Writing" to "40分 · Task 2 を1本"
            else -> "Speaking" to "40分 · Part 2/3 + 録音レビュー"
        }
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h${m}m"
        }
    }

    private fun jp(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "月"
        DayOfWeek.TUESDAY -> "火"
        DayOfWeek.WEDNESDAY -> "水"
        DayOfWeek.THURSDAY -> "木"
        DayOfWeek.FRIDAY -> "金"
        DayOfWeek.SATURDAY -> "土"
        DayOfWeek.SUNDAY -> "日"
    }
}
