package com.dhanine.planner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Draws the whole week as a timetable picture for the 4x4 widget. */
object ScheduleRenderer {
    private val ORDER = intArrayOf(1, 2, 3, 4, 5, 6, 0)
    private val LABEL = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private class Placed(val s: Slot, val lane: Int, var lanes: Int)

    fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val r1: Float
        val g1: Float
        val b1: Float
        when {
            hp < 1f -> { r1 = c; g1 = x; b1 = 0f }
            hp < 2f -> { r1 = x; g1 = c; b1 = 0f }
            hp < 3f -> { r1 = 0f; g1 = c; b1 = x }
            hp < 4f -> { r1 = 0f; g1 = x; b1 = c }
            hp < 5f -> { r1 = x; g1 = 0f; b1 = c }
            else -> { r1 = c; g1 = 0f; b1 = x }
        }
        val m = l - c / 2f
        return Color.rgb(
            ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        )
    }

    private fun layoutDay(list: List<Slot>): List<Placed> {
        val sorted = list.sortedWith(compareBy({ it.start }, { it.end }))
        val out = ArrayList<Placed>()
        var cluster = ArrayList<Placed>()
        var clusterEnd = -1
        val laneEnds = ArrayList<Int>()
        for (s in sorted) {
            if (cluster.isNotEmpty() && s.start >= clusterEnd) {
                val n = laneEnds.size
                for (p in cluster) p.lanes = n
                out.addAll(cluster)
                cluster = ArrayList()
                laneEnds.clear()
                clusterEnd = -1
            }
            var lane = laneEnds.indexOfFirst { it <= s.start }
            if (lane < 0) {
                laneEnds.add(s.end)
                lane = laneEnds.size - 1
            } else {
                laneEnds[lane] = s.end
            }
            cluster.add(Placed(s, lane, 1))
            clusterEnd = max(clusterEnd, s.end)
        }
        val n = max(1, laneEnds.size)
        for (p in cluster) p.lanes = n
        out.addAll(cluster)
        return out
    }

    private fun hourLabel(h: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12${if (h >= 12) "p" else "a"}"
    }

    fun draw(slots: List<Slot>, w: Int, h: Int, d: Float, dark: Boolean, todayDow: Int, nowMin: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ink = if (dark) 0xFFE8ECF8.toInt() else 0xFF141F45.toInt()
        val muted = if (dark) 0xFF9BA6C7.toInt() else 0xFF586284.toInt()
        val line = if (dark) 0xFF2A3556.toInt() else 0xFFD8DDEC.toInt()
        val accent = if (dark) 0xFF7D9CFF.toInt() else 0xFF2346C6.toInt()
        val nowC = if (dark) 0xFFF2B23C.toInt() else 0xFFD98A00.toInt()

        val days = ArrayList<Int>()
        for (x in ORDER) if (x != 0 || slots.any { it.day == 0 }) days.add(x)

        var t0 = 7 * 60
        var t1 = 21 * 60
        if (slots.isNotEmpty()) {
            t0 = (slots.minOf { it.start } / 60) * 60
            t1 = ((slots.maxOf { it.end } + 59) / 60) * 60
        }
        if (t1 - t0 < 360) t1 = t0 + 360

        val gutter = 20f * d
        val headH = 17f * d
        val gridTop = headH
        val gridH = h - headH
        val pxMin = gridH / (t1 - t0).toFloat()
        val colW = (w - gutter) / days.size

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)

        // today column tint
        val ti = days.indexOf(todayDow)
        if (ti >= 0) {
            paint.style = Paint.Style.FILL
            paint.color = (accent and 0x00FFFFFF) or 0x1A000000
            c.drawRect(gutter + ti * colW, gridTop, gutter + (ti + 1) * colW, h.toFloat(), paint)
        }

        // hour lines and labels
        val hourPx = 60f * pxMin
        val every = if (hourPx >= 13f * d) 1 else 2
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, d * 0.6f)
        text.color = muted
        text.textSize = 7.5f * d
        text.textAlign = Paint.Align.RIGHT
        var hr = t0 / 60
        while (hr * 60 <= t1) {
            val y = gridTop + (hr * 60 - t0) * pxMin
            paint.color = line
            c.drawLine(gutter, y, w.toFloat(), y, paint)
            if ((hr - t0 / 60) % every == 0 && hr * 60 < t1) {
                c.drawText(hourLabel(hr % 24), gutter - 3f * d, y + 7f * d, text)
            }
            hr++
        }
        // column separators and day names
        text.textAlign = Paint.Align.CENTER
        text.textSize = 8.5f * d
        for ((i, dow) in days.withIndex()) {
            val x = gutter + i * colW
            paint.color = line
            c.drawLine(x, gridTop, x, h.toFloat(), paint)
            val isToday = dow == todayDow
            text.color = if (isToday) accent else muted
            text.typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            c.drawText(LABEL[dow], x + colW / 2f, headH - 5f * d, text)
        }

        if (slots.isEmpty()) {
            text.color = muted
            text.typeface = Typeface.DEFAULT
            text.textSize = 11f * d
            c.drawText("No schedule yet", w / 2f, h / 2f, text)
            return bmp
        }

        // class blocks
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG)
        title.typeface = Typeface.DEFAULT_BOLD
        title.textSize = 7.5f * d
        val room = TextPaint(Paint.ANTI_ALIAS_FLAG)
        room.typeface = Typeface.DEFAULT
        room.textSize = 6.5f * d
        val pad = 2f * d
        for ((i, dow) in days.withIndex()) {
            val placed = layoutDay(slots.filter { it.day == dow })
            for (p in placed) {
                val laneW = colW / p.lanes
                val left = gutter + i * colW + p.lane * laneW + 1f * d
                val right = left + laneW - 2f * d
                val top = gridTop + (p.s.start - t0) * pxMin + 1f
                val bottom = gridTop + (p.s.end - t0) * pxMin - 1f
                if (bottom - top < 4f || right - left < 8f) continue
                val r = RectF(left, top, right, bottom)
                val hue = p.s.hue.toFloat()
                val fill = if (dark) hsl(hue, 0.55f, 0.22f) else hsl(hue, 0.55f, 0.90f)
                val edge = if (dark) hsl(hue, 0.50f, 0.64f) else hsl(hue, 0.50f, 0.46f)
                val tc = if (dark) hsl(hue, 0.45f, 0.88f) else hsl(hue, 0.45f, 0.24f)
                paint.style = Paint.Style.FILL
                paint.color = fill
                c.drawRoundRect(r, 4f * d, 4f * d, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, d * 0.7f)
                paint.color = edge
                c.drawRoundRect(r, 4f * d, 4f * d, paint)
                drawBlockText(c, r, p.s, title, room, tc, pad)
            }
        }

        // "now" line
        if (ti >= 0 && nowMin in t0..t1) {
            val y = gridTop + (nowMin - t0) * pxMin
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * d
            paint.color = nowC
            c.drawLine(gutter + ti * colW, y, gutter + (ti + 1) * colW, y, paint)
        }
        return bmp
    }

    private fun drawBlockText(c: Canvas, r: RectF, s: Slot, title: TextPaint, room: TextPaint, color: Int, pad: Float) {
        val innerW = (r.width() - 2f * pad).toInt()
        if (innerW < 8) return
        title.color = color
        room.color = color
        val avail = r.height() - 2f * pad
        val lineH = title.fontSpacing
        val roomH = room.fontSpacing
        val maxLines = ((avail - roomH) / lineH).toInt().coerceIn(1, 4)
        val name = if (s.short.isNotBlank()) s.short else s.title
        val tl = StaticLayout.Builder.obtain(name, 0, name.length, title, innerW)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save()
        c.clipRect(r)
        c.translate(r.left + pad, r.top + pad)
        tl.draw(c)
        if (s.room.isNotBlank() && avail - tl.height >= roomH) {
            val rl = StaticLayout.Builder.obtain(s.room, 0, s.room.length, room, innerW)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            c.translate(0f, tl.height.toFloat())
            rl.draw(c)
        }
        c.restore()
    }
}
