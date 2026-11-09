package com.focusguard.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.focusguard.model.DeviceContext
import com.focusguard.permissions.Permission
import com.focusguard.permissions.PermissionChecker

/**
 * Lee lo que las condiciones de los perfiles necesitan saber: red Wi-Fi, cargador y posición.
 * Todo se queda en el teléfono. Cada dato que no se puede leer (sin permiso, sin señal) queda vacío.
 */
class DeviceContextProvider(private val context: Context) {

    private val locationManager get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null
    private var lastLocation: Location? = null
    @Volatile private var cached = DeviceContext()
    @Volatile private var cachedAtMs = 0L

    private val permissions = PermissionChecker(context)

    // Los dispositivos Bluetooth conectados ahora, por nombre. Se llevan con los avisos de conexión del sistema.
    private val bluetoothConnected: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private var bluetoothReceiver: BroadcastReceiver? = null
    @Volatile private var bluetoothWanted = false

    // Hasta cargar los dispositivos que ya estaban conectados no se sabe qué hay: mientras tanto, la condición se da por cumplida.
    @Volatile private var bluetoothLoaded = false

    // Cada carga lleva su número: la que llega tarde, de un seguimiento ya parado, se ignora.
    @Volatile private var bluetoothGeneration = 0

    // Un aviso de conexión descarta la lectura guardada; se marca antes de leer de nuevo para no perder un aviso que llegue a medias.
    @Volatile private var cacheInvalidated = false

    val hasBluetoothPermission: Boolean get() = permissions.isGranted(Permission.BLUETOOTH)

    val hasLocationPermission: Boolean get() = permissions.isGranted(Permission.LOCATION)

    /** Sin permiso, o con la ubicación del sistema apagada, Android oculta la red y la posición. */
    private fun canReadLocation(): Boolean {
        if (!hasLocationPermission) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .any { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        }
    }

    /** Lectura con caché corta: se consulta en cada cambio de ventana y no hace falta más precisión. */
    fun current(nowMs: Long = System.currentTimeMillis()): DeviceContext {
        if (!cacheInvalidated && nowMs - cachedAtMs < CACHE_MS) return cached
        cacheInvalidated = false
        // Si el permiso de Bluetooth llegó después de crear el perfil, se empieza a seguir en cuanto está.
        if (bluetoothWanted) startBluetoothTracking()
        val location = bestLocation()
        cached = DeviceContext(
            wifiName = currentWifi(),
            bluetoothDevices = bluetoothConnected.toSet(),
            bluetoothAvailable = hasBluetoothPermission && bluetoothLoaded,
            charging = isCharging(),
            latitude = location?.latitude,
            longitude = location?.longitude,
            locationAvailable = canReadLocation(),
        )
        cachedAtMs = nowMs
        return cached
    }

    /** Nombre de la red actual, o null si no hay Wi-Fi o Android lo oculta por falta de permiso de ubicación. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun currentWifi(): String? {
        if (!hasLocationPermission) return null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ssid = wifi.connectionInfo?.ssid ?: return null
        return ssid.trim('"').takeUnless { it.isBlank() || it == UNKNOWN_SSID }
    }

    /** Algún perfil depende de Bluetooth: se sigue qué hay conectado mientras lo haga. Sin permiso no se puede, y la condición se da por cumplida. */
    fun setBluetoothWanted(wanted: Boolean) {
        bluetoothWanted = wanted
        if (wanted) startBluetoothTracking() else stopBluetoothTracking()
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothTracking() {
        if (bluetoothReceiver != null || !hasBluetoothPermission) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON)) {
                        // Con el Bluetooth apagado no hay nada conectado (y se sabe), aunque no llegue un aviso por cada dispositivo.
                        BluetoothAdapter.STATE_OFF -> {
                            bluetoothGeneration++
                            bluetoothConnected.clear()
                            bluetoothLoaded = true
                        }
                        // Al encenderlo se vuelve a mirar lo que se conecta solo.
                        BluetoothAdapter.STATE_ON -> loadConnectedBluetooth()
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                        val name = displayName(device) ?: return
                        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) bluetoothConnected += name else bluetoothConnected -= name
                    }
                }
                // La condición cambia al momento: la lectura guardada ya no vale.
                cacheInvalidated = true
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        bluetoothReceiver = receiver
        loadConnectedBluetooth()
    }

    private fun stopBluetoothTracking() {
        bluetoothReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        bluetoothReceiver = null
        // Lo que aún estuviera cargando ya no vale: sus respuestas se descartan.
        bluetoothGeneration++
        bluetoothConnected.clear()
        bluetoothLoaded = false
    }

    /**
     * Lo que ya estaba conectado antes de empezar a escuchar, porque los avisos de conexión solo cuentan lo nuevo: audio y manos libres
     * por sus perfiles, y los dispositivos de Bluetooth de bajo consumo (GATT). Lo que use otro perfil y ya estuviera conectado no se ve
     * hasta que se reconecte. Con el Bluetooth apagado no hay nada que cargar.
     */
    @SuppressLint("MissingPermission")
    private fun loadConnectedBluetooth() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            bluetoothLoaded = true
            return
        }
        val generation = ++bluetoothGeneration
        bluetoothLoaded = false
        runCatching { manager.getConnectedDevices(BluetoothProfile.GATT).mapNotNull(::displayName).forEach { bluetoothConnected += it } }
        val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        val pending = java.util.concurrent.atomic.AtomicInteger(profiles.size)
        fun answered() {
            if (pending.decrementAndGet() == 0 && generation == bluetoothGeneration) {
                bluetoothLoaded = true
                cacheInvalidated = true
            }
        }
        for (profile in profiles) {
            val asked = runCatching {
                adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(type: Int, proxy: BluetoothProfile) {
                        // Una respuesta tardía de una carga anterior (se paró el seguimiento mientras tanto) no toca nada.
                        if (generation == bluetoothGeneration) runCatching { proxy.connectedDevices.mapNotNull(::displayName).forEach { bluetoothConnected += it } }
                        adapter.closeProfileProxy(type, proxy)
                        answered()
                    }

                    override fun onServiceDisconnected(type: Int) = Unit
                }, profile)
            }.getOrDefault(false)
            if (!asked) answered()
        }
    }

    /** Los dispositivos emparejados, para elegirlos al crear un perfil; vacío sin permiso. */
    @SuppressLint("MissingPermission")
    fun pairedBluetooth(): List<String> {
        if (!hasBluetoothPermission) return emptyList()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return emptyList()
        return runCatching { adapter.bondedDevices.mapNotNull(::displayName).distinct().sorted() }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    private fun displayName(device: BluetoothDevice): String? =
        // El nombre del propio dispositivo, no el alias: el alias se cambia en los ajustes y bastaría para dejar de cumplir la condición.
        runCatching { device.name }.getOrNull()?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }

    fun isCharging(): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    /** Empieza a recibir posiciones solo mientras haya algún perfil con lugar. Idempotente. */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (listener != null || !hasLocationPermission) return
        val manager = locationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return
        val newListener = LocationListener { lastLocation = it }
        listener = newListener
        runCatching {
            manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, UPDATE_DISTANCE_M, newListener, Looper.getMainLooper())
        }.onFailure { listener = null }
    }

    fun stopLocationUpdates() {
        listener?.let { runCatching { locationManager.removeUpdates(it) } }
        listener = null
        lastLocation = null
    }

    /** Última posición conocida, también para rellenar «usar mi ubicación actual» en el editor. */
    @SuppressLint("MissingPermission")
    fun bestLocation(): Location? {
        if (!hasLocationPermission) return null
        // Con las actualizaciones activas, una posición reciente evita consultar a cada proveedor.
        lastLocation?.takeIf { listener != null && System.currentTimeMillis() - it.time < FRESH_LOCATION_MS }?.let { return it }
        val known = runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { locationManager.getLastKnownLocation(it) }
        }.getOrDefault(emptyList())
        return (known + listOfNotNull(lastLocation)).maxByOrNull { it.time }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val CACHE_MS = 5_000L
        const val UPDATE_INTERVAL_MS = 60_000L
        const val FRESH_LOCATION_MS = 5 * 60_000L
        const val UPDATE_DISTANCE_M = 50f
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
