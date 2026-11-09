package com.focusguard.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val emoji: String,
    val enabled: Boolean,
    val mode: String,
    val pausedUntilMs: Long?,
    val limitKind: String? = null,
    val limitAmount: Int? = null,
    val limitPeriod: String? = null,
    /** SHARED o PER_TARGET; nulo en los perfiles anteriores a esta opción (cuentan como SHARED). */
    val limitScope: String? = null,
    @ColumnInfo(defaultValue = "NORMAL") val kind: String = "NORMAL",
    val activeUntilMs: Long? = null,
    val pomodoroStartMs: Long? = null,
    val pomodoroFocusMin: Int? = null,
    val pomodoroBreakMin: Int? = null,
    val pomodoroSessions: Int? = null,
    @ColumnInfo(defaultValue = "ALL") val conditionMode: String = "ALL",
    @ColumnInfo(defaultValue = "0") val requireCharging: Boolean = false,
    val geoLat: Double? = null,
    val geoLon: Double? = null,
    val geoRadiusM: Int? = null,
    @ColumnInfo(defaultValue = "0") val geoInverted: Boolean = false,
    /** Ids de las secciones bloqueadas dentro de apps, separados por comas. */
    @ColumnInfo(defaultValue = "") val subApps: String = "",
    /** Ids de las categorías bloqueadas, separados por comas. */
    @ColumnInfo(defaultValue = "") val categories: String = "",
    /** Nombres de dispositivos Bluetooth, uno por línea. */
    @ColumnInfo(defaultValue = "") val bluetooth: String = "",
    /** Cómo se puede terminar un Bloquear ya: ANYTIME, TIME_ONLY o CONDITIONS. */
    @ColumnInfo(defaultValue = "ANYTIME") val endPolicy: String = "ANYTIME",
    /** Redes Wi-Fi y dispositivos Bluetooth que lo levantan, uno por línea. */
    @ColumnInfo(defaultValue = "") val endWifi: String = "",
    @ColumnInfo(defaultValue = "") val endBluetooth: String = "",
    @ColumnInfo(defaultValue = "0") val endCharging: Boolean = false,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val endRadiusM: Int? = null,
    /** Desde cuándo se puede pedir terminarlo, si se eligió una espera. */
    val endWaitUntilMs: Long? = null,
)

@Entity(
    tableName = "profile_apps",
    primaryKeys = ["profileId", "packageName"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class ProfileAppEntity(val profileId: Long, val packageName: String)

@Entity(
    tableName = "profile_webs",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class ProfileWebEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val value: String,
    val matchType: String,
)

@Entity(
    tableName = "profile_wifis",
    primaryKeys = ["profileId", "ssid"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class ProfileWifiEntity(val profileId: Long, val ssid: String)

@Entity(
    tableName = "profile_intervals",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class ProfileIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** 1 = lunes … 7 = domingo (java.time.DayOfWeek.value). */
    val day: Int,
    val startMinute: Int,
    val endMinute: Int,
)

@Entity(tableName = "profile_usage")
data class ProfileUsageEntity(
    @PrimaryKey val profileId: Long,
    val periodStartMs: Long,
    val timeMs: Long,
    val opens: Int,
    val continuousMs: Long,
    val lastActiveMs: Long,
)

/** Uso de cada app o web en un perfil con límite por app (`LimitScope.PER_TARGET`). */
@Entity(tableName = "target_usage", primaryKeys = ["profileId", "target"])
data class TargetUsageEntity(
    val profileId: Long,
    val target: String,
    val periodStartMs: Long,
    val timeMs: Long,
    val opens: Int,
    val continuousMs: Long,
    val lastActiveMs: Long,
)

/** Notificación de una app bloqueada, quitada y guardada para verla después (J1). Solo título y un texto corto. */
@Entity(tableName = "captured_notifications")
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val packageName: String,
    val title: String,
    val text: String,
)

/** Una pausa de un perfil (E9), para el historial de su vista previa. Se borra con el perfil. */
@Entity(
    tableName = "profile_pauses",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class ProfilePauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startedMs: Long,
    val minutes: Int,
)

/** Visitas y tiempo por web y día (K2). En incógnito [host] va vacío: no se guarda qué se visita. */
@Entity(tableName = "web_usage", primaryKeys = ["day", "host", "incognito"])
data class WebUsageEntity(
    val day: String,
    val host: String,
    val incognito: Boolean,
    val visits: Int,
    val millis: Long,
)

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val profileId: Long,
    val reason: String,
    val target: String,
)

data class ProfileWithDetails(
    @Embedded val profile: ProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profileId") val apps: List<ProfileAppEntity>,
    @Relation(parentColumn = "id", entityColumn = "profileId") val webs: List<ProfileWebEntity>,
    @Relation(parentColumn = "id", entityColumn = "profileId") val intervals: List<ProfileIntervalEntity>,
    @Relation(parentColumn = "id", entityColumn = "profileId") val wifis: List<ProfileWifiEntity>,
)

/** Un día para las rachas (L1): el tiempo que el bloqueo estuvo protegiendo y las pausas que hubo. [epochDay] es el día lógico. */
@Entity(tableName = "day_records")
data class DayRecordEntity(
    @PrimaryKey val epochDay: Long,
    val protectedMs: Long = 0,
    val pauses: Int = 0,
    val longestPauseMinutes: Int = 0,
)

/** Un impulso (L3): cuándo empezó, cómo acabó y, si el usuario lo dijo, qué lo desencadenó. Solo se guarda en el teléfono. */
@Entity(tableName = "urges")
data class UrgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedMs: Long,
    val endedMs: Long? = null,
    val outcome: String,
    val trigger: String? = null,
)

/** La entrada del diario de un día (L4). Solo se guarda en el teléfono. */
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val epochDay: Long,
    val text: String,
    val updatedMs: Long,
)

/** Una actividad planificada para un día (L4). No se edita ni se borra: solo se marca como hecha. */
@Entity(tableName = "planned_activities", indices = [Index("epochDay")])
data class PlannedActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val text: String,
    val doneMs: Long? = null,
)

/** Lo que se usó el teléfono un día ya terminado (K4): el sistema solo guarda unos días de eventos y el informe semanal necesita dos semanas. */
@Entity(tableName = "day_usage")
data class DayUsageEntity(
    @PrimaryKey val epochDay: Long,
    val usageMs: Long,
    val unlocks: Int,
)

/** Cómo se sentía el usuario en un momento (K5). Solo se guarda en el teléfono. */
@Entity(tableName = "moods")
data class MoodEntity(
    /** El día lógico (con el inicio del día de los ajustes), el mismo que usan las rachas y el diario. Un ánimo por día: el último. */
    @PrimaryKey val epochDay: Long,
    val atMs: Long,
    val level: Int,
)

/** Un recordatorio propio, diario a una hora fija (J4). */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minuteOfDay: Int,
    val text: String,
    val enabled: Boolean = true,
)

/** Cuántas veces se consultó un dominio un día y si se bloqueó (D8). Solo se guarda en el teléfono, 30 días. */
@Entity(tableName = "dns_queries", primaryKeys = ["day", "host", "blocked"])
data class DnsQueryEntity(
    /** Días desde 1970 en la hora local. */
    val day: Long,
    val host: String,
    val blocked: Boolean,
    val count: Int,
    val lastMs: Long,
)

/** Un paso hecho del programa de recuperación (L5): el día (lógico) en que se hizo y lo que el usuario contestó. Solo en el teléfono. */
@Entity(tableName = "recovery_steps")
data class RecoveryStepEntity(
    @PrimaryKey val step: Int,
    val doneDay: Long,
    val answer: String,
)

/** Un resultado del test de adicción (L6): cuándo se hizo y la puntuación total. Las respuestas no se guardan. */
@Entity(tableName = "assessment_results")
data class AssessmentResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenMs: Long,
    val score: Int,
    /** Si ya se guardó en la cuenta del servidor (solo si el usuario lo pidió). */
    val uploaded: Boolean = false,
)
