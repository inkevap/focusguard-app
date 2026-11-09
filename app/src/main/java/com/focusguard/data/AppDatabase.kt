package com.focusguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        ProfileAppEntity::class,
        ProfileWebEntity::class,
        ProfileIntervalEntity::class,
        ProfileUsageEntity::class,
        BlockEventEntity::class,
        ProfileWifiEntity::class,
        WebUsageEntity::class,
        CapturedNotificationEntity::class,
        ProfilePauseEntity::class,
        DayRecordEntity::class,
        UrgeEntity::class,
        JournalEntryEntity::class,
        PlannedActivityEntity::class,
        DayUsageEntity::class,
        MoodEntity::class,
        ReminderEntity::class,
        DnsQueryEntity::class,
        RecoveryStepEntity::class,
        AssessmentResultEntity::class,
        TargetUsageEntity::class,
    ],
    version = 20,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun usageDao(): UsageDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun webUsageDao(): WebUsageDao
    abstract fun capturedNotificationDao(): CapturedNotificationDao
    abstract fun profilePauseDao(): ProfilePauseDao
    abstract fun dayRecordDao(): DayRecordDao
    abstract fun urgeDao(): UrgeDao
    abstract fun journalDao(): JournalDao
    abstract fun dayUsageDao(): DayUsageDao
    abstract fun habitsDao(): HabitsDao
    abstract fun dnsQueryDao(): DnsQueryDao
    abstract fun recoveryDao(): RecoveryDao
    abstract fun assessmentDao(): AssessmentDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focusguard.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                .build()
    }
}
