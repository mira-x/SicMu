/*
 * SicMu Player - Lightweight music player for Android
 * Copyright (C) 2022  Mathieu Souchaud
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package xyz.mordorx.sicmu.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(entities = [SongORM::class], version = 3, exportSchema = false)
abstract class SongDatabase : RoomDatabase() {
    abstract val songDAO: SongDAO?

    /**
     * We cannot save the rating of a currently playing file to disk, thus this should be called
     * regularly. Also, note that this runs in a thread, so you should not call this in onDestroy().
     */
    fun synchronizeRatingsAsync() {
        val thread = Thread(Runnable {
            val retMsg = StringBuilder()
            val songORMs = this.songDAO?.songsWithUnsynchronizedRatings ?: return@Runnable
            var nbSyncSucceed = 0
            var nbSyncTried = 0
            for (songORM in songORMs) {
                if (!File(songORM!!.path).exists()) {
                    Log.d(
                        "Database",
                        "Could not synchronize rating for deleted file at path=" + songORM.path
                    )
                    continue
                }

                Log.d("Database", "Trying synchronize rating of path=" + songORM.path)
                var msg =
                    "trySyncronizeRating: synchronize rating of " + songORM.path + " to " + songORM.rating
                if (RowSong.Companion.WriteRatingToFile(songORM.path, songORM.rating)) {
                    msg += " succeed\n\n"
                    songORM.lastModifiedMs = (File(songORM.path)).lastModified()
                    songORM.ratingSynchronized = true
                    this.songDAO!!.update(songORM)
                    nbSyncSucceed++
                } else {
                    msg += " failed !\n\n"
                }
                retMsg.append(msg)
                Log.d("Database", msg)
                nbSyncTried++
            }
            val msg = "Synchronized rating " + nbSyncSucceed + "/" + nbSyncTried + " succeed"
            Log.d("Database", msg)
            retMsg.append(msg)
        })
        thread.start()
    }

    companion object {
        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE songs "
                            + " ADD COLUMN ratingSynchronized INTEGER DEFAULT 1 NOT NULL"
                )
            }
        }
        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE configuration")
            }
        }

        fun init(ctx: Context): SongDatabase {
            return databaseBuilder<SongDatabase>(ctx, SongDatabase::class.java, "database-SicMuNeo")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3) //.allowMainThreadQueries()
                .build()
        }
    }
}


