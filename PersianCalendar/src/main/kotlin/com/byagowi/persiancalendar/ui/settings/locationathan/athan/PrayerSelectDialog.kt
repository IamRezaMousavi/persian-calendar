package com.byagowi.persiancalendar.ui.settings.locationathan.athan

import androidx.activity.ComponentActivity
import androidx.core.content.edit
import com.byagowi.persiancalendar.ATHANS_LIST
import com.byagowi.persiancalendar.FAJR_KEY
import com.byagowi.persiancalendar.PREF_ATHAN_ALARM
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.utils.appPrefs
import com.byagowi.persiancalendar.utils.getPrayTimeName
import com.byagowi.persiancalendar.utils.splitFilterNotEmpty
import com.byagowi.persiancalendar.utils.startAthan

fun showPrayerSelectDialog(activity: ComponentActivity) {
    val alarms = (activity.appPrefs.getString(PREF_ATHAN_ALARM, null) ?: "")
        .splitFilterNotEmpty(",").toMutableSet()

    val checked = ATHANS_LIST.map { it in alarms }.toBooleanArray()
    val prayTimesNames = ATHANS_LIST.map { activity.getString(getPrayTimeName(it)) }.toTypedArray()
    androidx.appcompat.app.AlertDialog.Builder(activity)
        .setTitle(R.string.athan_alarm)
        .setMultiChoiceItems(prayTimesNames, checked) { _, which, isChecked ->
            val key = ATHANS_LIST.getOrNull(which) ?: FAJR_KEY
            if (isChecked) alarms.add(key) else alarms.remove(key)
        }
        .setPositiveButton(R.string.accept) { _, _ ->
            activity.appPrefs.edit { putString(PREF_ATHAN_ALARM, alarms.joinToString(",")) }
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

fun showPrayerSelectPreviewDialog(activity: ComponentActivity) {
    val prayTimesNames = ATHANS_LIST.map { activity.getString(getPrayTimeName(it)) }.toTypedArray()
    androidx.appcompat.app.AlertDialog.Builder(activity)
        .setTitle(R.string.preview)
        .setItems(prayTimesNames) { _, which ->
            startAthan(activity, ATHANS_LIST.getOrNull(which) ?: FAJR_KEY, null)
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
