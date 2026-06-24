package com.casual.autoclicker

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 首次启动引导页 / 权限中心。
 *
 * 负责：
 *  - 清晰展示两类权限（悬浮窗、无障碍服务）的开启状态
 *  - 一键跳转到对应系统设置页
 *  - 提示侧载用户：开启无障碍前可能需要先在「应用信息」页「允许受限设置」
 *
 * onResume 时刷新状态，使用户从系统设置返回后即时看到最新结果。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var accessibilityStatus: TextView
    private lateinit var accessibilityButton: Button
    private lateinit var intervalValue: TextView
    private lateinit var intervalSeekBar: SeekBar
    private lateinit var readyHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayStatus = findViewById(R.id.overlay_status)
        overlayButton = findViewById(R.id.overlay_button)
        accessibilityStatus = findViewById(R.id.accessibility_status)
        accessibilityButton = findViewById(R.id.accessibility_button)
        intervalValue = findViewById(R.id.interval_value)
        intervalSeekBar = findViewById(R.id.interval_seekbar)
        readyHint = findViewById(R.id.ready_hint)

        overlayButton.setOnClickListener {
            PermissionManager.openOverlaySettings(this)
        }
        accessibilityButton.setOnClickListener {
            PermissionManager.openAccessibilitySettings(this)
        }

        setupIntervalSeekBar()
    }

    /** 频率面板：SeekBar 范围映射到 [MIN_INTERVAL, MAX_INTERVAL]，变更即保存。 */
    private fun setupIntervalSeekBar() {
        val range = SettingsRepository.MAX_INTERVAL - SettingsRepository.MIN_INTERVAL
        intervalSeekBar.max = range
        val current = SettingsRepository.getIntervalMs(this)
        intervalSeekBar.progress = current - SettingsRepository.MIN_INTERVAL
        updateIntervalLabel(current)

        intervalSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = SettingsRepository.MIN_INTERVAL + progress
                updateIntervalLabel(value)
                SettingsRepository.setIntervalMs(this@MainActivity, value)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateIntervalLabel(valueMs: Int) {
        intervalValue.text = getString(R.string.interval_value_fmt, valueMs)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    /** 根据当前权限状态刷新 UI。 */
    private fun refreshState() {
        val overlayGranted = PermissionManager.canDrawOverlay(this)
        val a11yEnabled = PermissionManager.isAccessibilityServiceEnabled(this)

        overlayStatus.text = getString(
            if (overlayGranted) R.string.status_granted else R.string.status_not_granted
        )
        overlayButton.isEnabled = !overlayGranted

        accessibilityStatus.text = getString(
            if (a11yEnabled) R.string.status_enabled else R.string.status_not_enabled
        )
        accessibilityButton.isEnabled = !a11yEnabled

        readyHint.text = if (overlayGranted && a11yEnabled) {
            getString(R.string.hint_all_ready)
        } else {
            getString(R.string.hint_restricted_settings)
        }
    }
}
