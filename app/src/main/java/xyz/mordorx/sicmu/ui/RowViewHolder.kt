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
package xyz.mordorx.sicmu.ui

import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import xyz.mordorx.sicmu.R

class RowViewHolder(view: View) {
    val layout: RelativeLayout?
    val text: TextView?
    val duration: TextView?
    val image: ImageView?
    val ratingStar: ImageView?

    init {
        layout = view.findViewById<RelativeLayout?>(R.id.song_layout)
        text = view.findViewById<TextView?>(R.id.song_title)
        image = view.findViewById<ImageView?>(R.id.curr_play)
        duration = view.findViewById<TextView?>(R.id.song_duration)
        ratingStar = view.findViewById<ImageView?>(R.id.rating_star)
    }
}
