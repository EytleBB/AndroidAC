package com.casual.autoclicker

import android.content.Context

/**
 * 简单设置存储：点击间隔(ms)，基于 SharedPreferences 持久化。
 * 服务每次启动点击循环时读取，做到「面板改完即生效」。
 */
object SettingsRepository {

    private const val PREF_NAME = "autoclicker_prefs"
    private const val KEY_INTERVAL = "interval_ms"

    const val DEFAULT_INTERVAL = 100
    const val MIN_INTERVAL = 20      // 过快无意义且易被系统/应用丢弃
    const val MAX_INTERVAL = 2000

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
}
