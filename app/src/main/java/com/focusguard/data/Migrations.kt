package com.focusguard.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 → v2: límites de uso en los perfiles y tabla con el uso acumulado. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `limitKind` TEXT")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `limitAmount` INTEGER")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `limitPeriod` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile_usage` (`profileId` INTEGER NOT NULL, " +
                "`periodStartMs` INTEGER NOT NULL, `timeMs` INTEGER NOT NULL, `opens` INTEGER NOT NULL, " +
                "`continuousMs` INTEGER NOT NULL, `lastActiveMs` INTEGER NOT NULL, PRIMARY KEY(`profileId`))",
        )
    }
}

/** v2 → v3: perfiles temporales (Quick Block y Pomodoro). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'NORMAL'")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `activeUntilMs` INTEGER")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `pomodoroStartMs` INTEGER")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `pomodoroFocusMin` INTEGER")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `pomodoroBreakMin` INTEGER")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `pomodoroSessions` INTEGER")
    }
}

/** v3 → v4: historial de bloqueos para las estadísticas. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `block_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`atMs` INTEGER NOT NULL, `profileId` INTEGER NOT NULL, `reason` TEXT NOT NULL, `target` TEXT NOT NULL)",
        )
    }
}

/** v4 → v5: condiciones por Wi-Fi, cargador y lugar. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `conditionMode` TEXT NOT NULL DEFAULT 'ALL'")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `requireCharging` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `geoLat` REAL")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `geoLon` REAL")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `geoRadiusM` INTEGER")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `geoInverted` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile_wifis` (`profileId` INTEGER NOT NULL, `ssid` TEXT NOT NULL, " +
                "PRIMARY KEY(`profileId`, `ssid`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_wifis_profileId` ON `profile_wifis` (`profileId`)")
    }
}

/** v5 → v6: secciones bloqueadas dentro de apps (Shorts, Reels…). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `subApps` TEXT NOT NULL DEFAULT ''")
    }
}

/** v6 → v7: categorías de apps y webs bloqueadas por el perfil. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `categories` TEXT NOT NULL DEFAULT ''")
    }
}

/** v7 → v8: estadísticas de webs y de incógnito. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `web_usage` (`day` TEXT NOT NULL, `host` TEXT NOT NULL, `incognito` INTEGER NOT NULL, " +
                "`visits` INTEGER NOT NULL, `millis` INTEGER NOT NULL, PRIMARY KEY(`day`, `host`, `incognito`))",
        )
    }
}

/** v8 → v9: notificaciones de apps bloqueadas, quitadas y guardadas. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `captured_notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`atMs` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL)",
        )
    }
}

/** v9 → v10: historial de pausas de los perfiles. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile_pauses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`profileId` INTEGER NOT NULL, `startedMs` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, " +
                "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_pauses_profileId` ON `profile_pauses` (`profileId`)")
    }
}

/** v10 → v11: un registro por día para las rachas. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `day_records` (`epochDay` INTEGER NOT NULL, `protectedMs` INTEGER NOT NULL, " +
                "`pauses` INTEGER NOT NULL, `longestPauseMinutes` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))",
        )
    }
}

/** v11 → v12: impulsos, diario y actividades planificadas. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `urges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedMs` INTEGER NOT NULL, " +
                "`endedMs` INTEGER, `outcome` TEXT NOT NULL, `trigger` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_entries` (`epochDay` INTEGER NOT NULL, `text` TEXT NOT NULL, `updatedMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`epochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `planned_activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, `doneMs` INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_activities_epochDay` ON `planned_activities` (`epochDay`)")
    }
}

/** v12 → v13: el uso de cada día terminado, para el informe semanal. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `day_usage` (`epochDay` INTEGER NOT NULL, `usageMs` INTEGER NOT NULL, `unlocks` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))")
    }
}

/** v13 → v14: estados de ánimo y recordatorios propios. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `moods` (`epochDay` INTEGER NOT NULL, `atMs` INTEGER NOT NULL, `level` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `minuteOfDay` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, `enabled` INTEGER NOT NULL)",
        )
    }
}

/** v14 → v15: dispositivos Bluetooth como condición de un perfil. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `bluetooth` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `dns_queries` (`day` INTEGER NOT NULL, `host` TEXT NOT NULL, `blocked` INTEGER NOT NULL, `count` INTEGER NOT NULL, `lastMs` INTEGER NOT NULL, PRIMARY KEY(`day`, `host`, `blocked`))")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `recovery_steps` (`step` INTEGER NOT NULL, `doneDay` INTEGER NOT NULL, `answer` TEXT NOT NULL, PRIMARY KEY(`step`))")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `assessment_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `takenMs` INTEGER NOT NULL, `score` INTEGER NOT NULL, `uploaded` INTEGER NOT NULL)")
    }
}

/** v18 → v19: cómo se puede terminar un Bloquear ya (solo al acabar el tiempo, o antes si se cumple algo). */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `endPolicy` TEXT NOT NULL DEFAULT 'ANYTIME'")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `endWifi` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `endBluetooth` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `endCharging` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `endLat` REAL")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `endLon` REAL")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `endRadiusM` INTEGER")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `endWaitUntilMs` INTEGER")
    }
}

/** v19 → v20: límite compartido o por app, y el uso de cada app en los límites por app. */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `limitScope` TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS `target_usage` (`profileId` INTEGER NOT NULL, `target` TEXT NOT NULL, `periodStartMs` INTEGER NOT NULL, `timeMs` INTEGER NOT NULL, `opens` INTEGER NOT NULL, `continuousMs` INTEGER NOT NULL, `lastActiveMs` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `target`))")
    }
}
