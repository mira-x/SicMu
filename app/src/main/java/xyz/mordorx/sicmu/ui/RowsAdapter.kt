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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.transition.Visibility
import kotlinx.coroutines.flow.first
import xyz.mordorx.sicmu.Main
import xyz.mordorx.sicmu.R
import xyz.mordorx.sicmu.data.Row
import xyz.mordorx.sicmu.data.Row.Companion.convertDpToPixels
import xyz.mordorx.sicmu.data.RowGroup
import xyz.mordorx.sicmu.data.RowSong
import xyz.mordorx.sicmu.data.Rows
import xyz.mordorx.sicmu.data.XPreferences.Companion.P
import xyz.mordorx.sicmu.data.XRows
import xyz.mordorx.sicmu.media.MusicService
import kotlin.random.Random

/**
 * This adapter class allows to graphically represent a `Rows` object and all its
 * `Row` objects.
 */
class RowsAdapter(c: Context?, private val rows: Rows) : BaseAdapter() {
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

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var rowView = convertView
        // reuse views
        if (rowView == null) {
            // map to song layout
            rowView = songInf.inflate(R.layout.song, parent, false)
        }

        val r = rows.get(position) ?: return rowView

        val layout = rowView.findViewById<RelativeLayout?>(R.id.song_layout) ?: return rowView
        val text = rowView.findViewById<TextView?>(R.id.song_title) ?: return rowView
        val image = rowView.findViewById<ImageView?>(R.id.curr_play) ?: return rowView
        val duration = rowView.findViewById<TextView?>(R.id.song_duration) ?: return rowView
        val ratingStar = rowView.findViewById<ImageView?>(R.id.rating_star) ?: return rowView
        val setBackgroundColor = { backgroundColor: Int ->
            layout.setBackgroundColor(backgroundColor)
            text.setBackgroundColor(backgroundColor)
            image.setBackgroundColor(backgroundColor)
            duration.setBackgroundColor(backgroundColor)
            ratingStar.setBackgroundColor(backgroundColor)
        }

        text.setTypeface(null, Typeface.NORMAL)
        text.setPadding(convertDpToPixels(r.level * 14, layout.resources), 0, 0, 0)

        if (r is RowGroup) {
            val rg = r as RowGroup

            val heightFactor = if (rows.isLastRow(rg)) 3f else 1.5f
            layout.layoutParams.height = convertDpToPixels((RowGroup.textSize * heightFactor).toInt(), layout.resources)

            text.text = (if (rg.isFolded) "| " else "\\ ") + rg.name
            text.setTextColor(if (rg.isFolded && rg.isSelected) RowGroup.playingTextColor else RowGroup.normalTextColor)
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, RowGroup.textSize.toFloat())
            text.setTypeface(null, Typeface.BOLD)

            if (rg.isFolded) {
                if (rg.isSelected) duration.setTextColor(RowGroup.playingTextColor)
                else duration.setTextColor(RowGroup.normalTextColor)
                if (P.value.showGroupTotalTime) duration.text = msToTime(rg.totalDuration) + " |" + rg.stringOffset
                else duration.text = "${rg.songCount} |${rg.stringOffset}"
            } else {
                duration.text = "/${rg.stringOffset}"
                duration.setTextColor(RowGroup.normalTextColor)
            }
            duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, RowGroup.textSize.toFloat())
            duration.setTypeface(null, Typeface.BOLD)
            val params = duration.layoutParams as RelativeLayout.LayoutParams
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            duration.layoutParams = params

            image.setImageDrawable(null)

            ratingStar.visibility = INVISIBLE

            if (rg.overrideBackgroundColor && RowGroup.backgroundOverrideColor != 0) {
                setBackgroundColor(RowGroup.backgroundOverrideColor)
            } else {
                setBackgroundColor(Row.backgroundColor)
            }
        } else if (r is RowSong) {
            val rs = r as RowSong

            layout.layoutParams.height = convertDpToPixels((RowSong.textSize * 1.5f).toInt(), layout.resources)

            text.text = rs.text
            text.setTextColor(RowSong.normalSongTextColor)
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, RowSong.textSize.toFloat())

            duration.text = msToMinutesStripSecondIfLongDuration(rs.durationMs)
            duration.setTextColor(RowSong.normalSongDurationTextColor)
            duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, RowSong.textSize.toFloat())
            duration.setTypeface(null, rs.typeface)
            val params = duration.layoutParams as RelativeLayout.LayoutParams
            params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            duration.layoutParams = params

            // Normally this should show the "currently playing" indicator
            val selected = rows.currSong?.iD == rs.iD
            if (!selected) image.setImageResource(android.R.color.transparent)
            if (selected && rows.isPlaying) image.setImageResource(R.drawable.ic_curr_play)
            if (selected && !rows.isPlaying) image.setImageResource(R.drawable.ic_curr_pause)
            image.visibility = if (selected) VISIBLE else INVISIBLE

            ratingStar.setImageResource(rs.drawableStarFromRating)
            ratingStar.visibility = VISIBLE


            setBackgroundColor(RowSong.backgroundSongColor)
        }

        /*
CoroutineScope(Dispatchers.Default).launch {
        XRows.rowsFolded.onEach { newRows ->
            runOnUiThread {
                songAdt = ArrayAdapter(this@Main, R.layout.activity_main, newRows)
                songAdt?.notifyDataSetChanged()
            }
        }.collect()
    }
 */


        return rowView
    }

    fun msToTime(durationMs: Long): String {
        var seconds = durationMs / 1000
        var minutes = seconds / 60
        val hours = seconds / 3600
        if (seconds < 60) {
            return (if (seconds < 10) "0:0" else "0:") + seconds
        } else if (minutes < 60) {
            seconds %= 60
            return minutes.toString() + (if (seconds < 10) ":0" else ":") + seconds
        } else {
            seconds %= 60
            minutes %= 60
            return hours.toString() + (if (minutes < 10) ":0" else ":") + minutes +
                    (if (seconds < 10) ":0" else ":") + seconds
        }
    }

    fun msToMinutesStripSecondIfLongDuration(durationMs: Long): String {
        return msToMinutes(durationMs, durationMs < 100 * 60 * 1000)
    }

    @JvmOverloads
    fun msToMinutes(durationMs: Long, showSeconds: Boolean = true): String {
        var seconds = durationMs / 1000
        val minutes = seconds / 60
        if (showSeconds) {
            seconds %= 60
            return minutes.toString() + (if (seconds < 10) ":0" else ":") + seconds
        } else {
            return minutes.toString()
        }
    }
}
