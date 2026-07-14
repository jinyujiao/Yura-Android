package com.yura.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YuraDatabaseMigrationTest {
    @Test
    fun migration3To4CreatesDeletedBooksTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val database = helper.writableDatabase

        YuraDatabase.MIGRATION_3_4.migrate(database)

        database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'deleted_books'").use { cursor ->
            assertEquals(1, cursor.count)
        }
        helper.close()
    }

    @Test
    fun migration4To5CreatesReaderAnnotationsTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val database = helper.writableDatabase

        YuraDatabase.MIGRATION_4_5.migrate(database)

        database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'reader_annotations'").use { cursor ->
            assertEquals(1, cursor.count)
        }
        database.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_reader_annotations_book_id'").use { cursor ->
            assertEquals(1, cursor.count)
        }
        helper.close()
    }
    @Test
    fun migration5To6CreatesDeletedReaderAnnotationsTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val database = helper.writableDatabase

        YuraDatabase.MIGRATION_5_6.migrate(database)

        database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'deleted_reader_annotations'").use { cursor ->
            assertEquals(1, cursor.count)
        }
        database.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_deleted_reader_annotations_book_identifier'").use { cursor ->
            assertEquals(1, cursor.count)
        }
        helper.close()
    }
}
