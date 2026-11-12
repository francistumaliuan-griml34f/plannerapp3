package com.dhanine.planner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/** Shows the planner screens (a bundled web page) full screen. */
class MainActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        applyBars(this, web, Store.isDark(this))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(this, web), "AndroidBridge")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                if (scheme == "http" || scheme == "https") {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    return true
                }
                return false
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onPause() {
        super.onPause()
        PlannerWidget.refresh(this)
    }
}

/** Colours the status bar, navigation bar and web view background to match the theme. */
@Suppress("DEPRECATION")
internal fun applyBars(act: Activity, web: WebView?, dark: Boolean) {
    val bg = if (dark) 0xFF0D1326.toInt() else 0xFFF1F3F9.toInt()
    val w = act.window
    w.statusBarColor = bg
    w.navigationBarColor = bg
    var f = w.decorView.systemUiVisibility
    f = if (dark) f and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() else f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    if (Build.VERSION.SDK_INT >= 26) {
        f = if (dark) f and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv() else f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    w.decorView.systemUiVisibility = f
    web?.setBackgroundColor(bg)
}

/** Lets the planner page hand its data to the native side for the widget. */
class Bridge(private val act: Activity, private val web: WebView) {
    private val ctx: Context = act.applicationContext

    @JavascriptInterface
    fun save(json: String) {
        Store.save(ctx, json)
        PlannerWidget.refresh(ctx)
    }

    @JavascriptInterface
    fun loadItems(): String {
        val raw = Store.raw(ctx) ?: return ""
        return try {
            JSONObject(raw).optJSONArray("items")?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Semesters and subjects, so they survive even if the web storage is cleared. */
    @JavascriptInterface
    fun loadMeta(): String {
        val raw = Store.raw(ctx) ?: return ""
        return try {
            val o = JSONObject(raw)
            val sems = o.optJSONArray("sems") ?: return ""
            JSONObject().put("sems", sems).put("active", o.optString("active")).toString()
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun systemDark(): Boolean = Store.systemDark(ctx)

    /** pref is "dark", "light" or "auto". */
    @JavascriptInterface
    fun setTheme(pref: String) {
        val p = if (pref == "dark" || pref == "light") pref else "auto"
        Store.saveTheme(ctx, p)
        val dark = Store.isDark(ctx)
        act.runOnUiThread { applyBars(act, web, dark) }
        PlannerWidget.refresh(ctx)
    }

    @JavascriptInterface
    fun copy(text: String) {
        act.runOnUiThread {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Planner backup", text))
        }
    }
}
