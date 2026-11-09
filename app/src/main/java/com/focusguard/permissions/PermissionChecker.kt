package com.focusguard.permissions

import android.app.AppOpsManager
import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.focusguard.admin.FocusDeviceAdminReceiver
import com.focusguard.service.FocusAccessibilityService
import androidx.core.app.NotificationManagerCompat

class PermissionChecker(private val context: Context) {

    fun isGranted(permission: Permission): Boolean = when (permission) {
        Permission.ACCESSIBILITY -> isAccessibilityEnabled()
        Permission.USAGE_STATS -> isUsageStatsGranted()
        Permission.OVERLAY -> Settings.canDrawOverlays(context)
        Permission.BATTERY -> isIgnoringBatteryOptimizations()
        Permission.ADMIN -> isAdminActive()
        Permission.LOCATION -> hasRuntime(Manifest.permission.ACCESS_FINE_LOCATION)
        Permission.BLUETOOTH -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasRuntime(Manifest.permission.BLUETOOTH_CONNECT)
        Permission.NOTIFICATION_ACCESS -> NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        Permission.BACKGROUND_LOCATION ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasRuntime(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun hasRuntime(name: String) =
        context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED

    fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Permisos obligatorios que faltan. Los recomendados no cuentan. */
    fun missing(): List<Permission> = Permission.entries.filter { it.required && !isGranted(it) }

    /** Página de la app en ajustes: ahí está el inicio automático en la mayoría de fabricantes. */
    fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun settingsIntent(permission: Permission): Intent = when (permission) {
        Permission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Permission.USAGE_STATS -> Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
        Permission.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        Permission.BATTERY -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        Permission.ADMIN -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Impide desinstalar FocusGuard sin desactivarlo antes.")
        Permission.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        // Los permisos de ubicación se piden con el diálogo del sistema; si se negaron, solo queda la página de la app.
        Permission.LOCATION, Permission.BACKGROUND_LOCATION, Permission.BLUETOOTH -> appDetailsIntent()
    }.apply { if (permission.opensInNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    private fun adminComponent() = ComponentName(context, FocusDeviceAdminReceiver::class.java)

    private fun isAdminActive(): Boolean =
        (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComponent())

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val self = ComponentName(context, FocusAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
    }

    @Suppress("DEPRECATION")
    private fun isUsageStatsGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
