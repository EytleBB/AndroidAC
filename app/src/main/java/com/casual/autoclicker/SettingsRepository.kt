package com.casual.autoclicker

import android.content.Context
import android.graphics.Point
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 本地保存点击间隔与按编号排列的点击点；兼容只有间隔设置的旧版本。
 */
object SettingsRepository {

    private const val PREF_NAME = "autoclicker_prefs"
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_POINTS = "click_points"

    const val DEFAULT_INTERVAL = 100
    const val MIN_INTERVAL = 20      // 过快无意义且易被系统/应用丢弃
    const val MAX_INTERVAL = 2000
    const val MAX_POINTS = 50

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 读取点击间隔(ms)，自动夹在合法范围内。 */
    fun getIntervalMs(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL, DEFAULT_INTERVAL)
            .coerceIn(MIN_INTERVAL, MAX_INTERVAL)

    /** 写入点击间隔(ms)。 */
    fun setIntervalMs(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_INTERVAL, value.coerceIn(MIN_INTERVAL, MAX_INTERVAL))
            .apply()
    }

    /** 无已保存的有效位置时返回空列表，由服务创建默认的 1 号点击点。 */
    fun getClickPoints(context: Context): List<Point> {
        val saved = prefs(context).getString(KEY_POINTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(saved)
            buildList {
                for (i in 0 until minOf(array.length(), MAX_POINTS)) {
                    val item = array.optJSONObject(i) ?: continue
                    val x = item.optInt("x", -1)
                    val y = item.optInt("y", -1)
                    if (x >= 0 && y >= 0) add(Point(x, y))
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    fun setClickPoints(context: Context, points: List<Point>) {
        val array = JSONArray()
        points.take(MAX_POINTS).forEach { point ->
            array.put(JSONObject().put("x", point.x).put("y", point.y))
        }
        prefs(context).edit().putString(KEY_POINTS, array.toString()).apply()
    }
}
