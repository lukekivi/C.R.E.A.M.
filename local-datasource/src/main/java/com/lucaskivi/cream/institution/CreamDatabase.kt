package com.lucaskivi.cream.institution

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for the CREAM app. Holds linked institutions, the financial accounts each
 * institution owns, and the transactions each account owns.
 *
 * Obtain the application-wide instance via [getInstance].
 */
@Database(
    entities = [
        LinkedInstitutionEntity::class,
        FinancialAccountEntity::class,
        TransactionEntity::class,
    ],
    version = 1,
)
@TypeConverters(LocalDateConverter::class)
abstract class CreamDatabase : RoomDatabase() {

    /**
     * Returns the DAO for linked institutions and the accounts and transactions they own.
     */
    abstract fun institutionDao(): InstitutionDao

    companion object {

        /**
         * File name of the database on disk.
         */
        private const val DATABASE_NAME = "cream_database"

        /**
         * Lazily initialized, application-wide database singleton.
         */
        @Volatile
        private var instance: CreamDatabase? = null

        /**
         * Returns the application-wide [CreamDatabase] instance, creating it on first call.
         *
         * @param context Used to locate the database file; the application context is always used
         *   internally regardless of what is passed.
         */
        fun getInstance(context: Context): CreamDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        CreamDatabase::class.java,
                        DATABASE_NAME,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
