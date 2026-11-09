package com.focusguard.permissions

enum class Permission(val required: Boolean) {
    ACCESSIBILITY(required = true),
    USAGE_STATS(required = true),
    OVERLAY(required = true),

    /** Recomendado: sin él, algunos fabricantes matan el servicio en segundo plano. */
    BATTERY(required = false),

    /** Imprescindible: con él activo Android no deja desinstalar la app (ni por `adb uninstall`) sin desactivarlo antes. */
    ADMIN(required = true),

    /** Recomendado: para los perfiles que se activan con una red Wi-Fi o un lugar (Android oculta el nombre de la red sin él). */
    LOCATION(required = false),

    /** Recomendado: sin "Permitir siempre", la posición solo se actualiza con la app abierta. */
    BACKGROUND_LOCATION(required = false),

    /** Recomendado: para los perfiles que se activan con un dispositivo Bluetooth conectado (Android 12 o superior lo pide). */
    BLUETOOTH(required = false),

    /** Opcional: para quitar y guardar las notificaciones de apps bloqueadas y esconder el aviso de segundo plano. */
    NOTIFICATION_ACCESS(required = false),
    ;

    /**
     * ¿Se abre su pantalla del sistema en una tarea nueva? Todas, salvo el administrador del dispositivo: `DeviceAdminAdd` se niega a abrirse
     * así («Cannot start ADD_DEVICE_ADMIN as a new task») y se cierra al momento, con lo que el botón parecía no hacer nada.
     */
    val opensInNewTask: Boolean get() = this != ADMIN
}
