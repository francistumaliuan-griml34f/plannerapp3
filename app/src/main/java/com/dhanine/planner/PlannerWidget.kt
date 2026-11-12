package com.dhanine.planner

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * Home screen widget (4x4). Page 0 is your full weekly schedule, then My Class Today,
 * My Current Class and My Incoming Tasks. Tap the arrows on either side to switch pages.
 */
class PlannerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, manager, id)
        scheduleTick(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT -> {
                Store.setWidgetPage(context, (Store.widgetPage(context) + 1) % Pages.PAGE_COUNT)
                refresh(context)
            }
            ACTION_PREV -> {
                Store.setWidgetPage(context, (Store.widgetPage(context) + Pages.PAGE_COUNT - 1) % Pages.PAGE_COUNT)
                refresh(context)
            }
            ACTION_TICK, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(context))
    }

    companion object {
        const val ACTION_NEXT = "com.dhanine.planner.WIDGET_NEXT"
        const val ACTION_PREV = "com.dhanine.planner.WIDGET_PREV"
        const val ACTION_TICK = "com.dhanine.planner.WIDGET_TICK"

        private const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE
        private val PAGE_IDS = intArrayOf(R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6)

        private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent {
            val i = Intent(ctx, PlannerWidget::class.java).setAction(action)
            return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE)
        }

        private fun tickIntent(ctx: Context) = broadcast(ctx, ACTION_TICK, 3)

        /** Re-reads the saved data and redraws every widget on the home screen. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlannerWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) render(context, manager, id)
            scheduleTick(context)
        }

        /** Wakes the widget at the next class change (at least every 15 minutes) so "ends in" stays fresh. */
        private fun scheduleTick(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()
            var at = now + 15 * 60 * 1000L
            val data = Pages.load(ctx)
            if (data != null) {
                val b = Pages.nextBoundary(data.slots)
                if (b != null) {
                    val delta = (b - Pages.nowMinutes()) * 60_000L + 2_000L
                    if (delta in 1_000L until (at - now)) at = now + delta
                }
            }
            am.set(AlarmManager.RTC, at, tickIntent(ctx))
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val dark = Store.isDark(ctx)
            val data = Pages.load(ctx)
            val page = Store.widgetPage(ctx).coerceIn(0, Pages.PAGE_COUNT - 1)
            val p = Pages.page(data, page)

            val head = if (dark) 0xFFF2B23C.toInt() else 0xFF9A5F00.toInt()
            val titleC = if (dark) 0xFFFFFFFF.toInt() else 0xFF141F45.toInt()
            val lineC = if (dark) 0xFFC9D2EE.toInt() else 0xFF3A4468.toInt()
            val mutedC = if (dark) 0xFF9BA6C7.toInt() else 0xFF586284.toInt()
            val arrowC = if (dark) 0xFF7D9CFF.toInt() else 0xFF2346C6.toInt()

            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            rv.setInt(R.id.root, "setBackgroundResource", if (dark) R.drawable.widget_bg else R.drawable.widget_bg_light)
            rv.setTextColor(R.id.btn_prev, arrowC)
            rv.setTextColor(R.id.btn_next, arrowC)
            rv.setTextColor(R.id.header, head)
            rv.setTextColor(R.id.pager, mutedC)
            rv.setTextViewText(R.id.header, p.header)
            rv.setTextViewText(
                R.id.pager,
                (0 until Pages.PAGE_COUNT).joinToString("  ") { if (it == page) "●" else "○" }
            )

            val showGrid = page == 0 && data != null
            if (showGrid) {
                val opts = mgr.getAppWidgetOptions(id)
                val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val wDp = if (land) opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300)
                else opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
                val hDp = if (land) opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300)
                else opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300)
                val d = ctx.resources.displayMetrics.density
                var w = ((wDp - 20) * d).toInt().coerceAtLeast(240)
                var h = ((hDp - 20 - 40) * d).toInt().coerceAtLeast(240)
                val cap = 1100f / maxOf(w, h)
                if (cap < 1f) {
                    w = (w * cap).toInt()
                    h = (h * cap).toInt()
                }
                val scale = d * (w / ((wDp - 20) * d).coerceAtLeast(1f)).coerceAtMost(1f)
                val bmp = ScheduleRenderer.draw(
                    data!!.slots, w, h, scale, dark,
                    Pages.dowOf(LocalDate.now()), Pages.nowMinutes()
                )
                rv.setImageViewBitmap(R.id.sched, bmp)
                rv.setViewVisibility(R.id.sched, View.VISIBLE)
                rv.setViewVisibility(R.id.text_page, View.GONE)
            } else {
                rv.setViewVisibility(R.id.sched, View.GONE)
                rv.setViewVisibility(R.id.text_page, View.VISIBLE)
                rv.setTextColor(R.id.title, titleC)
                if (p.title.isBlank()) {
                    rv.setViewVisibility(R.id.title, View.GONE)
                } else {
                    rv.setViewVisibility(R.id.title, View.VISIBLE)
                    rv.setTextViewText(R.id.title, p.title)
                }
                for (i in PAGE_IDS.indices) {
                    if (i < p.lines.size) {
                        rv.setTextViewText(PAGE_IDS[i], p.lines[i])
                        rv.setTextColor(PAGE_IDS[i], lineC)
                        rv.setViewVisibility(PAGE_IDS[i], View.VISIBLE)
                    } else {
                        rv.setViewVisibility(PAGE_IDS[i], View.GONE)
                    }
                }
            }

            rv.setOnClickPendingIntent(R.id.btn_prev, broadcast(ctx, ACTION_PREV, 1))
            rv.setOnClickPendingIntent(R.id.btn_next, broadcast(ctx, ACTION_NEXT, 2))
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.content, open)

            mgr.updateAppWidget(id, rv)
        }
    }
}
