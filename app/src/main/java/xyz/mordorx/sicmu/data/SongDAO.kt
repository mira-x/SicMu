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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SongDAO {
    @get:Query("SELECT * FROM songs")
    val all: MutableList<SongORM>

    @Query("SELECT * FROM songs WHERE path == :aPath LIMIT 1")
    fun findByPath(aPath: String): SongORM?

    @get:Query("SELECT * FROM songs WHERE ratingSynchronized == 0")
    val songsWithUnsynchronizedRatings: MutableList<SongORM>

    @Insert
    fun insert(item: SongORM)

    @Update
    fun update(item: SongORM)

    @Delete
    fun delete(item: SongORM)
}

