package com.example

import android.app.Application
import com.example.data.SettingsRepository
import com.example.vpn.VpnManager

class WireShieldApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        VpnManager.init(this)

        val settings = SettingsRepository(this).settings.value
        if (settings.autoConnectOnLaunch) {
            VpnManager.log("Application started: Auto-Connect option is enabled")
        }
    }
}
