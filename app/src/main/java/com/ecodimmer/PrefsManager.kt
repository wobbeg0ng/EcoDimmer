package com.ecodimmer

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("ecodimmer_prefs", Context.MODE_PRIVATE)

    var isDimmingActive: Boolean
        get() = sharedPreferences.getBoolean("is_dimming_active", false)
        set(value) = sharedPreferences.edit().putBoolean("is_dimming_active", value).apply()

    var dimmingLevel: Float
        get() = sharedPreferences.getFloat("dimming_level", 0.5f)
        set(value) = sharedPreferences.edit().putFloat("dimming_level", value).apply()

    var isScheduleEnabled: Boolean
        get() = sharedPreferences.getBoolean("is_schedule_enabled", false)
        set(value) = sharedPreferences.edit().putBoolean("is_schedule_enabled", value).apply()

    var scheduleStartHour: Int
        get() = sharedPreferences.getInt("schedule_start_hour", 22) // Default 22 Uhr
        set(value) = sharedPreferences.edit().putInt("schedule_start_hour", value).apply()

    var scheduleStartMinute: Int
        get() = sharedPreferences.getInt("schedule_start_minute", 0)
        set(value) = sharedPreferences.edit().putInt("schedule_start_minute", value).apply()

    var scheduleEndHour: Int
        get() = sharedPreferences.getInt("schedule_end_hour", 7) // Default 7 Uhr
        set(value) = sharedPreferences.edit().putInt("schedule_end_hour", value).apply()

    var scheduleEndMinute: Int
        get() = sharedPreferences.getInt("schedule_end_minute", 0)
        set(value) = sharedPreferences.edit().putInt("schedule_end_minute", value).apply()

    var isShakeToRescueEnabled: Boolean
        get() = sharedPreferences.getBoolean("shake_to_rescue_enabled", false) // Standard: AUS
        set(value) = sharedPreferences.edit().putBoolean("shake_to_rescue_enabled", value).apply()
}