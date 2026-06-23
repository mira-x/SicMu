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

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "songs")
class SongORM(
    @JvmField @field:PrimaryKey val path: String,
    @JvmField var rating: Int, // set to true if DB's rating is synchronized to file's rating
    @JvmField var ratingSynchronized: Boolean
) {
    @JvmField
    var lastModifiedMs: Long // ms since 1970

    init {
        this.lastModifiedMs = (File(path)).lastModified()
    }
}

