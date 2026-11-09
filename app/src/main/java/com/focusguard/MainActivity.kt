package com.focusguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focusguard.permissions.Permission
import com.focusguard.permissions.PermissionChecker
import com.focusguard.ui.AppNavigation
import com.focusguard.lock.LockActivity
import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockReason
import com.focusguard.ui.OnboardingScreen
import com.focusguard.ui.OnboardingViewModel
import com.focusguard.ui.PinGateScreen
import com.focusguard.ui.Routes
import com.focusguard.ui.settings.SettingsViewModel
import com.focusguard.ui.theme.FocusGuardTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    /** Pantalla a la que abre un atajo del icono o el panel rápido; la navegación la borra al usarla. */
    private var startRoute by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startRoute = requestedRoute(intent)
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        /** La actividad es pública: cualquier app podría pedir una pantalla, así que solo se admiten las de [Routes.externalTargets]. */
        private fun requestedRoute(intent: Intent) = intent.getStringExtra(EXTRA_ROUTE)?.takeIf { it in Routes.externalTargets }

        fun intent(context: Context, route: String) =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startRoute = requestedRoute(intent)
        val checker = PermissionChecker(this)
        // Android 13+: los avisos de fase del Pomodoro necesitan este permiso. Es opcional.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val locationRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        var locationAsked = false
        var bluetoothAsked = false
        setContent {
            FocusGuardTheme {
                var granted by remember { mutableStateOf(snapshot(checker)) }
                // Se vuelve a comprobar al regresar de los ajustes del sistema.
                LifecycleResumeEffect(Unit) {
                    granted = snapshot(checker)
                    onPauseOrDispose { }
                }

                val settingsViewModel: SettingsViewModel = koinViewModel()
                val onboarding: OnboardingViewModel = koinViewModel()
                val onboardingNeeded by onboarding.needed.collectAsState()
                val pinRequired by settingsViewModel.appPinEnabled.collectAsState()
                var unlocked by remember { mutableStateOf(false) }
                // Al salir de la app se vuelve a pedir el PIN.
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false }

                if (pinRequired && !unlocked) {
                    PinGateScreen(verify = settingsViewModel::verifyPin, onUnlock = { unlocked = true })
                } else {
                    val grant: (Permission) -> Unit = {
                        // El diálogo del sistema solo sale la primera vez; después, hay que ir a los ajustes.
                        if (it == Permission.LOCATION && !locationAsked) {
                            locationAsked = true
                            locationRequest.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        } else if (it == Permission.BLUETOOTH && !bluetoothAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothAsked = true
                            locationRequest.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                        } else {
                            startActivity(checker.settingsIntent(it))
                        }
                    }
                    // Un atajo del icono (pánico, quitar bloqueos…) salta la bienvenida: quien lo usa ya conoce la app.
                    LaunchedEffect(startRoute) { if (startRoute != null) onboarding.skip() }
                    when {
                        onboardingNeeded == null && startRoute == null -> Unit
                        onboardingNeeded == true && startRoute == null -> OnboardingScreen(
                            granted = granted,
                            onGrant = grant,
                            onTestLock = {
                                startActivity(LockActivity.intent(this, BlockDecision.Block(BlockReason.PROFILE_APP, 0, ""), getString(R.string.onboarding_sample_app)))
                            },
                            onFinish = onboarding::finish,
                            onSkip = onboarding::skip,
                        )
                        else -> AppNavigation(
                            granted = granted,
                            onGrant = grant,
                            onOpenAppDetails = { startActivity(checker.appDetailsIntent()) },
                            startRoute = startRoute,
                            onRouteConsumed = { startRoute = null },
                        )
                    }
                }
            }
        }
    }

    private fun snapshot(checker: PermissionChecker): Map<Permission, Boolean> =
        Permission.entries.associateWith(checker::isGranted)
}
