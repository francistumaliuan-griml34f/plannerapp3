package com.dhanine.planner

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

/** One page of text for the widget. */
data class Page(val header: String, val title: String, val lines: List<String>)

/** A class, duty or weekly slot. day: Sunday = 0 ... Saturday = 6. Times are minutes after midnight. */
data class Slot(
    val title: String,
    val short: String,
    val room: String,
    val extra: String,
    val day: Int,
    val start: Int,
    val end: Int,
    val duty: Boolean,
    val hue: Int
)

data class Task(val title: String, val subject: String, val date: String, val time: String)

class WidgetData(val slots: List<Slot>, val tasks: List<Task>, val semName: String)

/** Builds the widget pages from the saved planner data. */
object Pages {
    const val PAGE_COUNT = 4 // 0 schedule, 1 today, 2 current, 3 tasks

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val longFmt = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
    private const val DOT = "·"

    fun clock(m: Int): String {
        val h = m / 60
        val mm = m % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${mm.toString().padStart(2, '0')} ${if (h >= 12) "PM" else "AM"}"
    }

    private fun span(m: Int): String {
        val h = m / 60
        val mm = m % 60
        return when {
            h > 0 && mm > 0 -> "${h}h ${mm}m"
            h > 0 -> "${h}h"
            else -> "${mm}m"
        }
    }

    private fun toMin(t: String): Int? {
        val p = t.split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** Sunday = 0 ... Saturday = 6, the same numbers the planner uses. */
    fun dowOf(d: LocalDate): Int = d.dayOfWeek.value % 7

    fun load(ctx: Context): WidgetData? {
        val raw = Store.raw(ctx) ?: return null
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return null
        }

        val slots = ArrayList<Slot>()
        val shortByCode = HashMap<String, String>()

        // short names of every subject in every semester, so old tasks keep their label
        val codes = root.optJSONObject("codes")
        if (codes != null) {
            val keys = codes.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                shortByCode[k] = codes.optString(k)
            }
        }

        val courses = root.optJSONArray("courses")
        if (courses != null) {
            for (i in 0 until courses.length()) {
                val o = courses.getJSONObject(i)
                shortByCode[o.optString("code")] = o.optString("short")
                slots.add(
                    Slot(
                        o.optString("title"), o.optString("short", o.optString("title")),
                        o.optString("room"), o.optString("inst"),
                        o.optInt("day"), o.optInt("start"), o.optInt("end"), false, o.optInt("h", 230)
                    )
                )
            }
        }
        val duties = root.optJSONArray("duties")
        if (duties != null) {
            for (i in 0 until duties.length()) {
                val o = duties.getJSONObject(i)
                slots.add(
                    Slot(
                        o.optString("title"), o.optString("short", o.optString("title")),
                        o.optString("room"), "",
                        o.optInt("day"), o.optInt("start"), o.optInt("end"), true, o.optInt("h", 185)
                    )
                )
            }
        }

        val tasks = ArrayList<Task>()
        val items = root.optJSONArray("items")
        if (items != null) {
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val kind = o.optString("kind")
                if (kind == "weekly") {
                    val s = toMin(o.optString("start"))
                    val e = toMin(o.optString("end"))
                    val d = o.optInt("day", -1)
                    if (s != null && e != null && d in 0..6) {
                        val t = o.optString("title")
                        slots.add(Slot(t, t, o.optString("note"), "", d, s, e, true, 185))
                    }
                } else if ((kind == "assignment" || kind == "task") && !o.optBoolean("done", false)) {
                    tasks.add(
                        Task(
                            o.optString("title"),
                            shortByCode[o.optString("subject")] ?: "",
                            o.optString("date"),
                            o.optString("start")
                        )
                    )
                }
            }
        }
        return WidgetData(slots, tasks, root.optString("semName"))
    }

    private fun placeholder(header: String): Page =
        Page(header, "Nothing loaded yet", listOf("Open the Planner app once to load your schedule."))

    /** index: 0 schedule (grid is drawn separately), 1 today, 2 current, 3 tasks */
    fun page(data: WidgetData?, index: Int): Page = when (index) {
        0 -> if (data == null) placeholder("MY SCHEDULE") else
            Page(if (data.semName.isNotBlank()) "MY SCHEDULE $DOT ${data.semName}" else "MY SCHEDULE", "", emptyList())
        1 -> if (data == null) placeholder("MY CLASS TODAY") else todayPage(data.slots)
        2 -> if (data == null) placeholder("MY CURRENT CLASS") else currentPage(data.slots)
        else -> if (data == null) placeholder("MY INCOMING TASKS") else taskPage(data.tasks)
    }

    /** First upcoming slot (offset 0 = today), or null. */
    private fun findNext(slots: List<Slot>): Pair<Int, Slot>? {
        val nowMin = nowMinutes()
        val today = LocalDate.now()
        val dow0 = dowOf(today)
        for (offset in 0..7) {
            val dow = (dow0 + offset) % 7
            val list = slots.filter { it.day == dow }.sortedBy { it.start }
            val s = (if (offset == 0) list.firstOrNull { it.start > nowMin } else list.firstOrNull()) ?: continue
            return Pair(offset, s)
        }
        return null
    }

    private fun currentPage(slots: List<Slot>): Page {
        val nowMin = nowMinutes()
        val dow = dowOf(LocalDate.now())
        val live = slots.filter { it.day == dow && it.start <= nowMin && nowMin < it.end }
            .sortedWith(compareBy({ it.duty }, { it.start }))
            .firstOrNull()
        if (live != null) {
            val total = (live.end - live.start).coerceAtLeast(1)
            val pct = ((nowMin - live.start) * 100 / total).coerceIn(0, 100)
            val filled = pct / 10
            val bar = "█".repeat(filled) + "░".repeat(10 - filled) + "  $pct%"
            val lines = ArrayList<String>()
            lines.add("${clock(live.start)} to ${clock(live.end)}")
            if (live.room.isNotBlank()) lines.add(live.room)
            if (live.extra.isNotBlank()) lines.add(live.extra)
            lines.add(bar)
            lines.add("Ends in ${span(live.end - nowMin)}")
            return Page(if (live.duty) "MY CURRENT DUTY" else "MY CURRENT CLASS", live.title, lines)
        }
        val nxt = findNext(slots)
        if (nxt == null) return Page("MY CURRENT CLASS", "No class right now", listOf("No upcoming classes."))
        val (offset, s) = nxt
        val today = LocalDate.now()
        val whenLabel = when (offset) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> today.plusDays(offset.toLong()).format(dayFmt)
        }
        val lines = ArrayList<String>()
        lines.add("Next: ${s.title}")
        lines.add("$whenLabel $DOT ${clock(s.start)} to ${clock(s.end)}")
        if (s.room.isNotBlank()) lines.add(s.room)
        if (offset == 0) lines.add("Starts in ${span(s.start - nowMin)}")
        return Page("MY CURRENT CLASS", "No class right now", lines)
    }

    private fun todayPage(slots: List<Slot>): Page {
        val nowMin = nowMinutes()
        val today = LocalDate.now()
        val list = slots.filter { it.day == dowOf(today) }.sortedBy { it.start }
        val title = today.format(longFmt)
        if (list.isEmpty()) {
            return Page("MY CLASS TODAY", title, listOf("No classes today."))
        }
        val shown = if (list.size > 6) list.take(5) else list
        val lines = ArrayList<String>()
        for (s in shown) {
            val mark = when {
                nowMin >= s.end -> "✓"
                nowMin >= s.start -> "▶"
                else -> "•"
            }
            val where = if (s.room.isNotBlank()) " $DOT ${s.room}" else ""
            lines.add("$mark ${clock(s.start)} – ${clock(s.end)}\n${s.title}$where")
        }
        if (list.size > 6) lines.add("+${list.size - 5} more")
        return Page("MY CLASS TODAY", title, lines)
    }

    private fun dueLabel(t: Task, today: LocalDate): String {
        if (t.date.isBlank()) return "No due date"
        val d = try {
            LocalDate.parse(t.date)
        } catch (e: Exception) {
            return "No due date"
        }
        val time = toMin(t.time)?.let { " " + clock(it) } ?: ""
        val diff = ChronoUnit.DAYS.between(today, d)
        return when {
            diff < 0L -> "Overdue $DOT ${d.format(dayFmt)}"
            diff == 0L -> "Due today$time"
            diff == 1L -> "Due tomorrow$time"
            else -> "Due ${d.format(dayFmt)}$time"
        }
    }

    private fun taskPage(tasks: List<Task>): Page {
        val today = LocalDate.now()
        if (tasks.isEmpty()) {
            return Page("MY INCOMING TASKS", "Nothing pending", listOf("You are all caught up."))
        }
        val sorted = tasks.sortedWith(
            compareBy<Task>({ if (it.date.isBlank()) "9999-99-99" else it.date }, { it.time })
        )
        val late = sorted.count { it.date.isNotBlank() && it.date < today.toString() }
        val title = "${sorted.size} pending" + if (late > 0) " $DOT $late overdue" else ""
        val lines = ArrayList<String>()
        for (t in sorted.take(5)) {
            val meta = listOf(t.subject, dueLabel(t, today)).filter { it.isNotBlank() }.joinToString(" $DOT ")
            lines.add("${t.title}\n$meta")
        }
        if (sorted.size > 5) lines.add("+${sorted.size - 5} more")
        return Page("MY INCOMING TASKS", title, lines)
    }

    /** Minute of the day when the widget text will next change (class start or end), or null. */
    fun nextBoundary(slots: List<Slot>): Int? {
        val nowMin = nowMinutes()
        val dow = dowOf(LocalDate.now())
        var best: Int? = null
        for (s in slots) {
            if (s.day != dow) continue
            for (m in intArrayOf(s.start, s.end)) {
                if (m > nowMin && (best == null || m < best!!)) best = m
            }
        }
        return best
    }
}
