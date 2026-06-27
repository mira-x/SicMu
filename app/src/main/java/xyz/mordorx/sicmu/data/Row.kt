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
import xyz.mordorx.sicmu.Main
import xyz.mordorx.sicmu.ui.RowViewHolder

/**
 * This is a base class for rows in a hierarchical order.
 */
open class Row(
    /** position of the row within the unfolded rows array */
    var genuinePos: Int,
    /** level from the left */
    var level: Int, val typeface: Int
) {
    // null if no parent
    var parent: Row? = null

    open fun setView(holder: RowViewHolder, main: Main?, position: Int) {
        holder.text?.setTypeface(null, typeface)
        holder.text?.setPadding(
            convertDpToPixels(level * levelOffset, holder.layout!!.resources),
            0,
            0,
            0
        )
    }

    fun setBackgroundColor(holder: RowViewHolder, backgroundColor: Int) {
        holder.layout?.setBackgroundColor(backgroundColor)
        holder.text?.setBackgroundColor(backgroundColor)
        holder.image?.setBackgroundColor(backgroundColor)
        holder.duration?.setBackgroundColor(backgroundColor)
        holder.ratingStar?.setBackgroundColor(backgroundColor)
    }

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

        /** How many dp units a level difference is wide */
        const val levelOffset: Int = 14

        // cache result
        private val converted: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()
        public fun convertDpToPixels(dp: Int, resources: Resources): Int {
            val px: Int
            if (converted.containsKey(dp)) {
                px = converted.get(dp)!!
            } else {
                px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
                    resources.getDisplayMetrics()
                ).toInt()
                converted.put(dp, px)
            }
            return px
        }
    }
}
