package com.example.a2subscriber

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.android.gms.maps.model.LatLng

class DatabaseHelper(context: Context, factory: SQLiteDatabase.CursorFactory?) : SQLiteOpenHelper(
    context,
    DatabaseContract.DATABASE_NAME,
    factory,
    DatabaseContract.DATABASE_VERSION
) {
    init {
        // Force database creation on initialization
        writableDatabase.close()
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            val createTableQuery = """
                CREATE TABLE IF NOT EXISTS ${DatabaseContract.DataEntry.TABLE_NAME} (
                    autoId INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DatabaseContract.DataEntry.COLUMN_ID} INTEGER,
                    ${DatabaseContract.DataEntry.COLUMN_LATITUDE} REAL,
                    ${DatabaseContract.DataEntry.COLUMN_LONGITUDE} REAL,
                    ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP} INTEGER
                )
            """
            db.execSQL(createTableQuery)
            Log.d("DatabaseHelper", "Database table created successfully")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error creating database table", e)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            db.execSQL("DROP TABLE IF EXISTS ${DatabaseContract.DataEntry.TABLE_NAME}")
            onCreate(db)
            Log.d("DatabaseHelper", "Database upgraded successfully")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error upgrading database", e)
        }
    }

    fun deleteDb(){
        try {
            val db = readableDatabase
            db.execSQL("DROP TABLE IF EXISTS ${DatabaseContract.DataEntry.TABLE_NAME}")
            onCreate(db)
            Log.d("DatabaseHelper", "Database deleted successfully")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting database", e)
        }
    }

    fun insertData(id: Int, latitude: Double, longitude: Double, timestamp: Long): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(DatabaseContract.DataEntry.COLUMN_ID, id)
                put(DatabaseContract.DataEntry.COLUMN_LATITUDE, latitude)
                put(DatabaseContract.DataEntry.COLUMN_LONGITUDE, longitude)
                put(DatabaseContract.DataEntry.COLUMN_TIMESTAMP, timestamp)
            }

            val result = db.insert(DatabaseContract.DataEntry.TABLE_NAME, null, values)
            db.close()

            if (result != -1L) {
                Log.d("DatabaseHelper", "Data inserted successfully: ID=$id, Lat=$latitude, Long=$longitude")
                true
            } else {
                Log.e("DatabaseHelper", "Failed to insert data: ID=$id")
                false
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error inserting data", e)
            false
        }
    }

    private fun getAllData(): Cursor {
        val db = readableDatabase
        return db.query(
            DatabaseContract.DataEntry.TABLE_NAME,
            null, null, null, null, null, null
        )
    }

    fun getLocationDataById(phoneId: Int): MutableList<CustomMarkerPoints> {
        val db = readableDatabase
        val customMarkerPointsList = mutableListOf<CustomMarkerPoints>()

        val query = """
    SELECT ${DatabaseContract.DataEntry.COLUMN_LATITUDE}, 
           ${DatabaseContract.DataEntry.COLUMN_LONGITUDE}, 
           ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP}
    FROM ${DatabaseContract.DataEntry.TABLE_NAME}
    WHERE ${DatabaseContract.DataEntry.COLUMN_ID} = ?
    ORDER BY ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP} DESC
    """
        val cursor = db.rawQuery(query, arrayOf(phoneId.toString()))  // Ensure phoneId is passed as a String

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val latitude = it.getDouble(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_LATITUDE))
                    val longitude = it.getDouble(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_LONGITUDE))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_TIMESTAMP))

                    val point = LatLng(latitude, longitude)
                    customMarkerPointsList.add(
                        CustomMarkerPoints(
                            customMarkerPointsList.size + 1,
                            point,
                            timestamp
                        )
                    )
                } while (it.moveToNext())
            }
        }

        return customMarkerPointsList
    }


    fun getRecentLocationData(phoneId: Int): MutableList<CustomMarkerPoints> {
        val db = readableDatabase
        val customMarkerPointsList = mutableListOf<CustomMarkerPoints>()

        val currentTime = System.currentTimeMillis() / 1000 // Current time in seconds
        val fiveMinutesAgo = currentTime - 5 * 60 // Timestamp for 5 minutes ago

        // First try to get points from last 5 minutes
        var query = """
        SELECT ${DatabaseContract.DataEntry.COLUMN_LATITUDE}, 
               ${DatabaseContract.DataEntry.COLUMN_LONGITUDE}, 
               ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP}
        FROM ${DatabaseContract.DataEntry.TABLE_NAME}
        WHERE ${DatabaseContract.DataEntry.COLUMN_ID} = ? 
          AND ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP} >= ?
        ORDER BY ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP} DESC
    """

        var cursor = db.rawQuery(query, arrayOf(phoneId.toString(), fiveMinutesAgo.toString()))

        if (cursor.count == 0) {
            // If no recent points found, get the most recent point regardless of time
            cursor.close()
            query = """
            SELECT ${DatabaseContract.DataEntry.COLUMN_LATITUDE}, 
                   ${DatabaseContract.DataEntry.COLUMN_LONGITUDE}, 
                   ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP}
            FROM ${DatabaseContract.DataEntry.TABLE_NAME}
            WHERE ${DatabaseContract.DataEntry.COLUMN_ID} = ?
            ORDER BY ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP} DESC
            LIMIT 1
        """
            cursor = db.rawQuery(query, arrayOf(phoneId.toString()))
        }

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val latitude = it.getDouble(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_LATITUDE))
                    val longitude = it.getDouble(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_LONGITUDE))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_TIMESTAMP))

                    val point = LatLng(latitude, longitude)
                    customMarkerPointsList.add(
                        CustomMarkerPoints(
                            customMarkerPointsList.size + 1,
                            point,
                            timestamp
                        )
                    )
                } while (it.moveToNext())
            }
        }

        Log.d("Database", "ID: $phoneId - Found ${customMarkerPointsList.size} points")
        customMarkerPointsList.forEach { point ->
            Log.d("Database", "Point timestamp: ${point.time}, current time: $currentTime")
        }

        return customMarkerPointsList
    }

    fun getAllIds(): List<Int> {
        val db = readableDatabase
        val idList = mutableListOf<Int>()

        // SQL query to select distinct phoneIds
        val query = """
        SELECT DISTINCT ${DatabaseContract.DataEntry.COLUMN_ID}
        FROM ${DatabaseContract.DataEntry.TABLE_NAME}
    """

        val cursor = db.rawQuery(query, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getInt(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_ID))
                    idList.add(id)
                } while (it.moveToNext())
            }
        }

        return idList
    }

    fun logAllData() {
        val cursor = getAllData()
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getInt(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_ID))
                    val latitude = it.getDouble(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_LATITUDE))
                    val longitude = it.getDouble(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_LONGITUDE))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_TIMESTAMP))
                    Log.d("DatabaseTest", "ID: $id, Latitude: $latitude, Longitude: $longitude, Timestamp: $timestamp")
                } while (it.moveToNext())
            } else {
                Log.d("DatabaseTest", "No data found")
            }
        }
    }

    fun getDataInRange(phoneId: Int, startTime: Long, endTime: Long): MutableList<CustomMarkerPoints> {
        val db = readableDatabase
        val customMarkerPointsList = mutableListOf<CustomMarkerPoints>()

        // Query to fetch points within the specified time range
        val query = """
        SELECT ${DatabaseContract.DataEntry.COLUMN_LATITUDE}, 
               ${DatabaseContract.DataEntry.COLUMN_LONGITUDE}, 
               ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP}
        FROM ${DatabaseContract.DataEntry.TABLE_NAME}
        WHERE ${DatabaseContract.DataEntry.COLUMN_ID} = ? 
          AND ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP} BETWEEN ? AND ?
        ORDER BY ${DatabaseContract.DataEntry.COLUMN_TIMESTAMP} DESC
    """

        val cursor = db.rawQuery(query, arrayOf(phoneId.toString(), startTime.toString(), endTime.toString()))

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val latitude = it.getDouble(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_LATITUDE))
                    val longitude = it.getDouble(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_LONGITUDE))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(DatabaseContract.DataEntry.COLUMN_TIMESTAMP))

                    val point = LatLng(latitude, longitude)
                    customMarkerPointsList.add(
                        CustomMarkerPoints(
                            customMarkerPointsList.size + 1, // Assign a unique index
                            point,
                            timestamp
                        )
                    )
                } while (it.moveToNext())
            }
        }

        Log.d("Database", "ID: $phoneId - Found ${customMarkerPointsList.size} points in range $startTime to $endTime")
        customMarkerPointsList.forEach { point ->
            Log.d("Database", "Point: Lat=${point.point.latitude}, Lng=${point.point.longitude}, Time=${point.time}")
        }

        return customMarkerPointsList
    }



}

