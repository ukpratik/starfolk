// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data.personal

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** private.db: excluded from backup and device transfer by the include-list rules (D-017, D-032). */
@Database(entities = [CheckinEntity::class, StarNameEntity::class], version = 1, exportSchema = true)
abstract class PrivateDatabase : RoomDatabase() {
    abstract fun dao(): PrivateDao

    companion object {
        const val NAME = "private.db"

        fun open(context: Context): PrivateDatabase =
            Room.databaseBuilder(context, PrivateDatabase::class.java, NAME).build()
    }
}
