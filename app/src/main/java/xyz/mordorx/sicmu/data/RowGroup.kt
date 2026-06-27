/*
 * SicMu Player - Lightweight music player for Android
 * Copyright (C) 2015  Mathieu Souchaud
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

import java.io.File

/**
 * This is a subclass of `Row` that is foldable/collapsable and may contain
 * children `Row` elements.
 */
class RowGroup(
    pos: Int, level: Int, val name: String?, path: String?, typeface: Int,
    overrideBackgroundColor: Boolean
) : Row(pos, level, typeface) {
    var isFolded: Boolean
    var isSelected: Boolean
    val overrideBackgroundColor: Boolean

    /** get number of songs (excluding RowGroup) inside this group */
    var songCount: Int = 0
        private set
    var path: String? = null
        private set(path) {
            field = ""
            if (path != null) {
                val f = File(path)
                if (f.exists()) {
                    if (f.isDirectory) field = f.absolutePath
                    else  // if path points to a file get the folder of that path
                        field = f.parentFile?.absolutePath
                }
            }
        }
    var totalDuration: Long = 0
        private set

    init {
        this.path = path
        this.isFolded = false
        this.isSelected = false
        this.overrideBackgroundColor = overrideBackgroundColor
    }

    fun increaseSongCount(n: Int) {
        this.songCount += n
    }

    fun incTotalDuration(totalDurationMs: Long) {
        this.totalDuration += totalDurationMs
    }

    override fun toString(): String {
        return "Group pos: $genuinePos level: $level name: $name"
    }

    companion object {
        var textSize: Int = 18

        // must be set outside before calling setText
        var normalTextColor: Int = 0
        var playingTextColor: Int = 0
        var backgroundOverrideColor: Int = 0
    }
}
