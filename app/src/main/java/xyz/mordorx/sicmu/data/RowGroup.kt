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

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.support.v4.media.MediaMetadataCompat
import android.util.TypedValue
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import xyz.mordorx.sicmu.Main
import xyz.mordorx.sicmu.ui.RowViewHolder
import java.io.File

/**
 * This is a subclass of `Row` that is foldable/collapsable and may contain
 * children `Row` elements.
 */
class RowGroup(
    pos: Int, level: Int, val name: String?, path: String?, typeface: Int,
    overrideBackgroundColor: Boolean, preferences: Preferences
) : Row(pos, level, typeface) {
    var isFolded: Boolean
    var isSelected: Boolean
    private val overrideBackgroundColor: Boolean

    /** get number of songs (excluding RowGroup) inside this group */
    var songCount: Int = 0
        private set
    var path: String? = null
        private set(path) {
            field = ""
            if (path != null) {
                val f = File(path)
                if (f.exists()) {
                    if (f.isDirectory()) field = f.getAbsolutePath()
                    else  // if path points to a file get the folder of that path
                        field = f.getParentFile().getAbsolutePath()
                }
            }
        }
    var totalDuration: Long = 0
        private set
    private val preferences: Preferences

    init {
        this.path = path
        this.isFolded = false
        this.isSelected = false
        this.overrideBackgroundColor = overrideBackgroundColor
        this.preferences = preferences
    }

    fun increaseSongCount(n: Int) {
        this.songCount += n
    }

    fun incTotalDuration(totalDurationMs: Long) {
        this.totalDuration += totalDurationMs
    }

    override fun setView(holder: RowViewHolder, main: Main?, position: Int) {
        super.setView(holder, main, position)

        if (main == null) return

        var factor = 1.5f
        if (main.musicSrv!!.getRows().isLastRow(position)) factor = 3f
        holder.layout!!.getLayoutParams().height = Row.Companion.convertDpToPixels(
            (textSize * factor).toInt(),
            holder.layout!!.getResources()
        )

        setText(holder.text!!)
        setDuration(holder.duration!!)
        holder.image!!.setImageDrawable(null)

        holder.ratingStar!!.visibility = View.INVISIBLE
        val params = holder.duration.layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        holder.duration.layoutParams = params

        if (overrideBackgroundColor && backgroundOverrideColor != 0) {
            setBackgroundColor(holder, backgroundOverrideColor)
        } else {
            setBackgroundColor(holder, backgroundColor)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setText(text: TextView) {
        val prefix = if (this.isFolded) "| " else "\\ "
        text.text = prefix + name

        if (this.isFolded && this.isSelected) text.setTextColor(playingTextColor)
        else text.setTextColor(normalTextColor)

        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize.toFloat())
    }

    @SuppressLint("SetTextI18n")
    private fun setDuration(duration: TextView) {
        val rightSpace = stringOffset
        //super.setText(text);
        if (this.isFolded) {
            if (this.isSelected) duration.setTextColor(playingTextColor)
            else duration.setTextColor(normalTextColor)
            if (preferences.showGroupTotalTime) duration.text = msToTime(this.totalDuration) + " |" + rightSpace
            else duration.text = "$songCount |$rightSpace"
        } else {
            duration.text = "/$rightSpace"
            duration.setTextColor(normalTextColor)
        }

        duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize.toFloat())
        duration.setTypeface(null, if (typeface == Typeface.ITALIC) Typeface.NORMAL else typeface)
    }

    override fun toString(): String {
        return "Group pos: $genuinePos level: $level name: $name"
    }

    val mediaMetadata: MediaMetadataCompat?
        get() {
            val builder =
                MediaMetadataCompat.Builder()
            builder.putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                name
            )
            return builder.build()
        }

    companion object {
        var textSize: Int = 18

        // must be set outside before calling setText
        var normalTextColor: Int = 0
        var playingTextColor: Int = 0
        var backgroundOverrideColor: Int = 0

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
    }
}
