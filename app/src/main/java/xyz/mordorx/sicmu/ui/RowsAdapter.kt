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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import xyz.mordorx.sicmu.Main
import xyz.mordorx.sicmu.R
import xyz.mordorx.sicmu.data.Rows

/**
 * This adapter class allows to graphically represent a `Rows` object and all its
 * `Row` objects.
 */
class RowsAdapter(c: Context?, private val rows: Rows, private val main: Main?) : BaseAdapter() {
    private val songInf: LayoutInflater


    init {
        songInf = LayoutInflater.from(c)
    }

    override fun getCount(): Int {
        return rows.size()
    }

    override fun getItem(arg0: Int): Any? {
        // TODO Auto-generated method stub
        return null
    }

    override fun getItemId(arg0: Int): Long {
        // TODO Auto-generated method stub
        return 0
    }


    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var rowView = convertView
        // reuse views
        if (rowView == null) {
            // map to song layout
            rowView = songInf.inflate(R.layout.song, parent, false)
            // configure view holder
            val viewHolder = RowViewHolder(rowView)
            rowView.setTag(viewHolder)
        }

        val holder = rowView.getTag() as RowViewHolder

        rows.get(position)?.setView(holder, main, position)

        return rowView
    }
}
