package dev.victorc.sunup.core.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * What the phone allows, and where to send the user to fix it.
 * Used by onboarding's permission checklist (mockup C1) and the debug test bench.
 */
class AlarmPermissions(private val context: Context) {

    fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun exactAlarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Without this the alarm still rings, but only as a notification, not a full screen. */
    fun fullScreenAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun batteryUnrestricted(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun notificationSettings(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettings(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri())
        } else {
            null
        }

    fun fullScreenSettings(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri())
        } else {
            null
        }

    /** The list of apps and their battery setting; the user finds Sunup there. */
    fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** A one-tap "Let Sunup run in the background?" prompt. Allowed for alarm clocks. */
    @SuppressLint("BatteryLife")
    fun batteryRequest(): Intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())

    /**
     * Phones whose makers stop apps from starting by themselves (so an alarm can't wake the app)
     * until the user allows it. Null on other phones. Android can't tell whether it's allowed.
     */
    fun autostartSettings(): AutostartScreens? {
        val maker = Build.MANUFACTURER.lowercase()
        val screens = when (maker) {
            "xiaomi", "redmi", "poco" -> AutostartScreens(
                screens = listOf(component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                hint = "Find Sunup and switch Autostart on",
            )
            "samsung" -> AutostartScreens(
                screens = listOf(
                    component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ),
                hint = "Background usage limits: add Sunup to Never sleeping apps",
            )
            "oppo", "realme", "oneplus" -> AutostartScreens(
                screens = listOf(
                    component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    component("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
                ),
                hint = "Find Sunup and allow Auto launch",
            )
            "vivo", "iqoo" -> AutostartScreens(
                screens = listOf(
                    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                ),
                hint = "Find Sunup and allow Autostart",
            )
            "huawei", "honor" -> AutostartScreens(
                screens = listOf(
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ),
                hint = "Find Sunup and allow it to launch by itself",
            )
            else -> return null
        }
        // If the maker moved its screen, Sunup's own app info page is the next best place.
        return screens.copy(screens = screens.screens + appDetails())
    }

    private fun component(packageName: String, className: String): Intent =
        Intent().setComponent(ComponentName(packageName, className))

    private fun appDetails(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

    private fun packageUri(): Uri = Uri.parse("package:${context.packageName}")
}

/** Where a phone maker keeps its Autostart switch; try [screens] in order. */
data class AutostartScreens(val screens: List<Intent>, val hint: String)
