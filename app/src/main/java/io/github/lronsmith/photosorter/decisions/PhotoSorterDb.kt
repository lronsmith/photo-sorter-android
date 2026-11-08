package io.github.lronsmith.photosorter.decisions

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Decision::class], version = 1, exportSchema = false)
abstract class PhotoSorterDb : RoomDatabase() {
    abstract fun decisions(): DecisionDao

    companion object {
        fun create(context: Context): PhotoSorterDb =
            Room.databaseBuilder(context.applicationContext, PhotoSorterDb::class.java, "photo-sorter.db").build()
    }
}
