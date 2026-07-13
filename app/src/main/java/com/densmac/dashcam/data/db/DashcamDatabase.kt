package com.densmac.dashcam.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.densmac.dashcam.data.db.dao.DeviceDao
import com.densmac.dashcam.data.db.dao.DownloadDao
import com.densmac.dashcam.domain.model.DownloadStatus

@Database(
    entities = [DownloadEntity::class, DeviceEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DashcamConverters::class)
abstract class DashcamDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun deviceDao(): DeviceDao
}

class DashcamConverters {
    @TypeConverter fun downloadStatusToString(value: DownloadStatus): String = value.name
    @TypeConverter fun stringToDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
}
