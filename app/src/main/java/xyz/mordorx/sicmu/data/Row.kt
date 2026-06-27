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

import android.content.res.Resources
import android.util.TypedValue

/**
 * This is a base class for rows in a hierarchical order.
 */
sealed class Row(
    /** position of the row within the unfolded rows array */
    var genuinePos: Int,
    /** level from the left */
    var level: Int,
    val typeface: Int
) {
    // null if no parent
    var parent: Row? = null

    val stringOffset: String
        get() {
            val offset = StringBuilder()
            val s = " "
            for (i in level downTo 1) {
                offset.append(s)
            }
            return offset.toString()
        }

    companion object {
        /** Must be set outside before calling setText */
        var backgroundColor: Int = 0

        // cache result
        private val converted: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()
        public fun convertDpToPixels(dp: Int, resources: Resources): Int {
            val px: Int
            if (converted.containsKey(dp)) {
                px = converted[dp]!!
            } else {
                px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
                    resources.displayMetrics
                ).toInt()
                converted[dp] = px
            }
            return px
        }
    }
}
