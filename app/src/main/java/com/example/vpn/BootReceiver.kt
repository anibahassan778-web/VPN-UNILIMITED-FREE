package com.example.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.SettingsRepository

class BootReceiver : BroadcastReceiver {
    constructor() : super()

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val settingsRepo = SettingsRepository(context)
            if (settingsRepo.settings.value.autoConnectOnLaunch) {
                VpnManager.log("System boot completed: Auto-Connect triggered")
                VpnManager.connect(context)
            }
        }
    }
}
