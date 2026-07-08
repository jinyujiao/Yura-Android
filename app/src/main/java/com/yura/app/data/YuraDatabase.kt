package com.yura.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Book::class, Bookmark::class, Highlight::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(HighlightConverters::class)
abstract class YuraDatabase : RoomDatabase() {
    abstract fun yuraDao(): YuraDao

    companion object {
        @Volatile
        private var instance: YuraDatabase? = null

        fun get(context: Context): YuraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    YuraDatabase::class.java,
                    "yura.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE ${Book.TABLE_NAME} ADD COLUMN ${Book.LAST_READ_DATE} INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE ${Book.TABLE_NAME} SET ${Book.LAST_READ_DATE} = ${Book.CREATION_DATE} WHERE ${Book.LAST_READ_DATE} = 0"
                )
            }
        }
    }
}
