package com.yura.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Book::class, Bookmark::class, DeletedBook::class],
    version = 4,
    exportSchema = false,
)
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
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS highlights")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS deleted_books (identifier TEXT NOT NULL, deleted_at INTEGER NOT NULL, PRIMARY KEY(identifier))")
            }
        }
    }
}
