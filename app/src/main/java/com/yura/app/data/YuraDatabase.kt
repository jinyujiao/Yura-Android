package com.yura.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Book::class, Bookmark::class, DeletedBook::class, ReaderAnnotation::class, DeletedReaderAnnotation::class],
    version = 6,
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
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reader_annotations (id TEXT NOT NULL, book_id INTEGER NOT NULL, type TEXT NOT NULL, locator TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reader_annotations_book_id ON reader_annotations (book_id)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS deleted_reader_annotations (id TEXT NOT NULL, book_identifier TEXT NOT NULL, deleted_at INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_deleted_reader_annotations_book_identifier ON deleted_reader_annotations (book_identifier)")
            }
        }
    }
}
