package com.ulysses.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ulysses.app.data.db.entities.*

@Database(
    entities = [
        BlockListEntity::class,
        BlockListEntryEntity::class,
        BlockEntity::class,
        BlockListAssociation::class,
        BlockSessionEntity::class,
        TriggerEntity::class,
        DnsCategoryEntity::class,
        DnsBlockedDomainEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class UlyssesDatabase : RoomDatabase() {

    abstract fun blockListDao(): BlockListDao
    abstract fun blockDao(): BlockDao
    abstract fun sessionDao(): SessionDao
    abstract fun triggerDao(): TriggerDao
    abstract fun dnsCategoryDao(): DnsCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: UlyssesDatabase? = null

        fun getInstance(context: Context): UlyssesDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UlyssesDatabase::class.java,
                    "ulysses.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
