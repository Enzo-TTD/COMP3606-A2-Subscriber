package com.example.a2subscriber

object DatabaseContract {
    const val DATABASE_NAME = "SubscriberData.db"
    const val DATABASE_VERSION = 1

    object DataEntry {
        const val TABLE_NAME = "LocationData"
        const val COLUMN_ID = "id"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_TIMESTAMP = "timestamp"
    }
}
