package com.example.dysync

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val proxyEnabledPref = findPreference<androidx.preference.SwitchPreferenceCompat>("proxy_enabled")
        val proxyHostPref = findPreference<androidx.preference.EditTextPreference>("proxy_host")
        val proxyPortPref = findPreference<androidx.preference.EditTextPreference>("proxy_port")
        val targetUrlPref = findPreference<androidx.preference.EditTextPreference>("target_url")

        // 代理开关控制 host/port 可用性
        proxyEnabledPref?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = (newValue as Boolean)
            proxyHostPref?.isEnabled = enabled
            proxyPortPref?.isEnabled = enabled
            true
        }

        // 初始状态
        val enabled = proxyEnabledPref?.isChecked == true
        proxyHostPref?.isEnabled = enabled
        proxyPortPref?.isEnabled = enabled

        // 保存时同步到 MainActivity（通过 SharedPreferences 自动生效）
        targetUrlPref?.setOnPreferenceChangeListener { _, newValue ->
            val url = newValue.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                true
            } else {
                findPreference<androidx.preference.EditTextPreference>("target_url")?.setSummary("必须以 http:// 或 https:// 开头")
                false
            }
        }
    }
}