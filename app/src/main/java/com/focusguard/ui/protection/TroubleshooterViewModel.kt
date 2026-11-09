package com.focusguard.ui.protection

import com.focusguard.decision.EndRules
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ProfileRepository
import com.focusguard.data.ServiceHealth
import com.focusguard.data.TrustedClock
import com.focusguard.decision.HealthSnapshot
import com.focusguard.decision.Problem
import com.focusguard.decision.Troubleshooter
import com.focusguard.permissions.Permission
import com.focusguard.permissions.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Reúne lo que dice el teléfono y lo que dice el servicio, y se lo da a [Troubleshooter]. */
class TroubleshooterViewModel(
    private val checker: PermissionChecker,
    private val health: ServiceHealth,
    private val profiles: ProfileRepository,
    private val clock: TrustedClock,
) : ViewModel() {

    /** null mientras se comprueba por primera vez. */
    var problems by mutableStateOf<List<Problem>?>(null)
        private set

    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val all = profiles.profiles.first()
            problems = withContext(Dispatchers.Default) {
                Troubleshooter.problems(
                HealthSnapshot(
                    accessibilityEnabled = checker.isGranted(Permission.ACCESSIBILITY),
                    usageStats = checker.isGranted(Permission.USAGE_STATS),
                    overlay = checker.isGranted(Permission.OVERLAY),
                    batteryUnrestricted = checker.isGranted(Permission.BATTERY),
                    adminActive = checker.isGranted(Permission.ADMIN),
                    notificationsEnabled = checker.notificationsEnabled(),
                    locationNeeded = all.any { it.wifiNames.isNotEmpty() || it.geofence != null || EndRules.watchesPlace(listOf(it)) || (EndRules.watches(it) && it.endConditions.wifiNames.isNotEmpty()) },
                    locationGranted = checker.isGranted(Permission.LOCATION),
                    bluetoothNeeded = all.any { it.bluetoothNames.isNotEmpty() } || EndRules.watchesBluetooth(all),
                    bluetoothGranted = checker.isGranted(Permission.BLUETOOTH),
                    lastHeartbeat = health.lastBeat,
                    now = clock.now(),
                    manufacturer = Build.MANUFACTURER,
                ),
                )
            }
        }
    }
}
