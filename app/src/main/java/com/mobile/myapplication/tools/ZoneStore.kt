package com.mobile.myapplication.tools

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ZoneRecord(
    val id: Long = 0L,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val safe: Boolean,
    val alertOnEnter: Boolean,
    val alertOnExit: Boolean,
    val enabled: Boolean = true
)

/** Small local store for saved zones; keeps geofence IDs stable across process restarts. */
class ZoneStore private constructor(context: Context) : SQLiteOpenHelper(context, "saved_zones.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE zones (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            address TEXT NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            radius_m INTEGER NOT NULL,
            safe INTEGER NOT NULL,
            alert_enter INTEGER NOT NULL,
            alert_exit INTEGER NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1
        )""".trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun all(): List<ZoneRecord> = readableDatabase.rawQuery("SELECT * FROM zones ORDER BY id DESC", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toZone())
        }
    }

    @Synchronized
    fun get(id: Long): ZoneRecord? = readableDatabase.query("zones", null, "id=?", arrayOf(id.toString()), null, null, null).use { cursor ->
        if (cursor.moveToFirst()) cursor.toZone() else null
    }

    @Synchronized
    fun save(zone: ZoneRecord): Long {
        val values = zone.toValues()
        return if (zone.id == 0L) writableDatabase.insertOrThrow("zones", null, values)
        else {
            writableDatabase.update("zones", values, "id=?", arrayOf(zone.id.toString()))
            zone.id
        }
    }

    @Synchronized
    fun setEnabled(id: Long, enabled: Boolean) {
        writableDatabase.update("zones", ContentValues().apply { put("enabled", enabled) }, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("zones", "id=?", arrayOf(id.toString()))
    }

    private fun ContentValues.putBoolean(key: String, value: Boolean) = put(key, if (value) 1 else 0)

    private fun ZoneRecord.toValues() = ContentValues().apply {
        put("name", name); put("address", address); put("latitude", latitude); put("longitude", longitude)
        put("radius_m", radiusMeters); putBoolean("safe", safe); putBoolean("alert_enter", alertOnEnter)
        putBoolean("alert_exit", alertOnExit); putBoolean("enabled", enabled)
    }

    private fun Cursor.toZone() = ZoneRecord(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        address = getString(getColumnIndexOrThrow("address")),
        latitude = getDouble(getColumnIndexOrThrow("latitude")),
        longitude = getDouble(getColumnIndexOrThrow("longitude")),
        radiusMeters = getInt(getColumnIndexOrThrow("radius_m")),
        safe = getInt(getColumnIndexOrThrow("safe")) != 0,
        alertOnEnter = getInt(getColumnIndexOrThrow("alert_enter")) != 0,
        alertOnExit = getInt(getColumnIndexOrThrow("alert_exit")) != 0,
        enabled = getInt(getColumnIndexOrThrow("enabled")) != 0
    )

    companion object {
        @Volatile private var instance: ZoneStore? = null
        fun get(context: Context): ZoneStore = instance ?: synchronized(this) {
            instance ?: ZoneStore(context.applicationContext).also { instance = it }
        }
    }
}
