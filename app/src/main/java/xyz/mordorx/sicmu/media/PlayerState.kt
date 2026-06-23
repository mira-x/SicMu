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
package xyz.mordorx.sicmu.media

class PlayerState {
    var state: Int

    init {
        state = Nope
    }

    fun compare(states: Int): Boolean {
        return (state and states) != 0
    }

    companion object {
        const val Nope: Int = 1
        const val Idle: Int = 2
        const val Initialized: Int = 4
        const val Preparing: Int = 8
        const val Prepared: Int = 16
        const val Started: Int = 32
        const val Paused: Int = 64
        const val PlaybackCompleted: Int = 128
        const val Stopped: Int = 256
        const val End: Int = 512
        const val Error: Int = 1024
    }
}
