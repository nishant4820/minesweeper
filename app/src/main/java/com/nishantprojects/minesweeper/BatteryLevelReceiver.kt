package com.nishantprojects.minesweeper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class BatteryLevelReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Toast.makeText(context, "Battery Low! Close the Game", Toast.LENGTH_LONG).show()
    }
}