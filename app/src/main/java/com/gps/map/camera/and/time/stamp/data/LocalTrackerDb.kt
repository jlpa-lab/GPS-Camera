package com.gps.map.camera.and.time.stamp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalTrackerDb(context: Context) : SQLiteOpenHelper(context, "tracker_local.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cache (cache_key TEXT PRIMARY KEY NOT NULL, cache_value TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS cache"); onCreate(db)
    }

    fun read(key: String): String? = readableDatabase.query(
        "cache",
        arrayOf("cache_value"),
        "cache_key = ?",
        arrayOf(key),
        null,
        null,
        null
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun write(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "cache",
            null,
            ContentValues().apply {
                put("cache_key", key); put(
                "cache_value",
                value
            ); put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
}
