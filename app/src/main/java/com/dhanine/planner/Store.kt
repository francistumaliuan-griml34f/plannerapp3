package com.dhanine.planner

import android.content.Context
import android.content.res.Configuration

/** Keeps the planner data (sent by the web screen) so the widget can read it. */
object Store {
    private const val PREFS = "planner"
    private const val KEY = "data"
    private const val THEME = "theme"
    private const val PAGE = "widget_page"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(ctx: Context, json: String) {
        p(ctx).edit().putString(KEY, json).apply()
    }

    fun raw(ctx: Context): String? = p(ctx).getString(KEY, null)

    /** "auto", "light" or "dark" */
    fun theme(ctx: Context): String = p(ctx).getString(THEME, "auto") ?: "auto"

    fun saveTheme(ctx: Context, v: String) {
        p(ctx).edit().putString(THEME, v).apply()
    }

    fun systemDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun isDark(ctx: Context): Boolean = when (theme(ctx)) {
        "dark" -> true
        "light" -> false
        else -> systemDark(ctx)
    }

    fun widgetPage(ctx: Context): Int = p(ctx).getInt(PAGE, 0)

    fun setWidgetPage(ctx: Context, v: Int) {
        p(ctx).edit().putInt(PAGE, v).apply()
    }
}
