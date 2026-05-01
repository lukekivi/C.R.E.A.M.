package com.lucaskivi.cream.institution

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for the CREAM app. Holds institutions, financial accounts, and transactions.
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
     * Returns the DAO for institution, account, and transaction operations.
     */
    abstract fun institutionDao(): InstitutionDao

    companion object {

        /**
         * Lazily initialised, application-wide database singleton.
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
                        "cream_database",
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
