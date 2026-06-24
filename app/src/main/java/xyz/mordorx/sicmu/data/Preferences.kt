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
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.preference.PreferenceManager
import androidx.core.util.Pair
import xyz.mordorx.sicmu.BuildConfig
import xyz.mordorx.sicmu.R
import xyz.mordorx.sicmu.media.RepeatMode
import xyz.mordorx.sicmu.media.ShuffleMode
import java.util.Arrays

class Preferences(private val context: Context) {
    private val read: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val write: SharedPreferences.Editor = read.edit()

    val followSong: Boolean
        get() = read.getBoolean(PrefKeys.FOLLOW_SONG.name, true)

    fun setChooseTextSize(big: Boolean) {
        write.putBoolean(PrefKeys.ENLARGE_TEXT.name, big).apply()
    }

    val enlargeText: Boolean
        get() = read.getBoolean(
            PrefKeys.ENLARGE_TEXT.name,
            context.getString(R.string.settings_text_size_choosed_default)
                .toBoolean()
        )
    val bigTextSize: Int
        get() = read.getString(
            PrefKeys.TEXT_SIZE_BIG.name,
            context.getString(xyz.mordorx.sicmu.R.string.settings_text_size_big_default)
        )!!.toInt()
    val normalTextSize: Int
        get() = read.getString(
            PrefKeys.TEXT_SIZE_NORMAL.name,
            context.getString(xyz.mordorx.sicmu.R.string.settings_text_size_regular_default)
        )!!.toInt()
    val textSizeRatio: Float
        get() = read.getString(
            PrefKeys.TEXT_SIZE_RATIO.name,
            context.getString(xyz.mordorx.sicmu.R.string.settings_text_size_ratio_default)
        )!!.toFloat()
    val disablePitchCompensation: Boolean
        get() = read.getBoolean(PrefKeys.DISABLE_PITCH_COMPENSATION.name, false)


    var songID: Long
        get() = read.getLong(PrefKeys.SONG_ID.name, -1)
        set(songID) {
            write.putLong(PrefKeys.SONG_ID.name, songID).apply()
        }

    val saveSongPos: Boolean
        get() = read.getBoolean(PrefKeys.SAVE_SONG_POS.name, false)

    var songPos: Long
        get() = read.getLong(PrefKeys.SONG_POS.name, -1)
        set(songPos) {
            write.putLong(PrefKeys.SONG_POS.name, songPos).apply()
        }
    var songPosId: Long
        get() = read.getLong(PrefKeys.SONG_POS_ID.name, -1)
        set(songPos) {
            write.putLong(PrefKeys.SONG_POS_ID.name, songPos).apply()
        }

    var repeatMode: RepeatMode
        get() = RepeatMode.valueOf(
            read.getString(
                PrefKeys.REPEAT_MODE.name,
                xyz.mordorx.sicmu.media.RepeatMode.REPEAT_ALL.name
            )!!
        )
        set(repeatMode) {
            write.putString(PrefKeys.REPEAT_MODE.name, repeatMode.name).apply()
        }

    val rootFolders: String
        get() = read.getString(
            PrefKeys.ROOT_FOLDERS.name,
            MediaScanner.getMusicStoragesStr(context)
        )!!


    val defaultFold: Int
        get() = read.getString(PrefKeys.DEFAULT_FOLD.name, "0")!!.toInt()

    val unfoldSubGroup: Boolean
        get() = read.getBoolean(PrefKeys.UNFOLD_SUBGROUP.name, false)

    val unfoldSubGroupThreshold: Int
        get() = read.getString(
            PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name,
            context.getString(xyz.mordorx.sicmu.R.string.settings_unfold_subgroup_threshold_default)
        )!!.toInt()

    var enableShake: Boolean
        get() = read.getBoolean(PrefKeys.ENABLE_SHAKE.name, false)
        set(shakeEnabled) {
            write.putBoolean(PrefKeys.ENABLE_SHAKE.name, shakeEnabled).apply()
        }

    var enableRating: Boolean
        get() = read.getBoolean(PrefKeys.ENABLE_RATING.name, true)
        set(ratingEnabled) {
            write.putBoolean(PrefKeys.ENABLE_RATING.name, ratingEnabled).apply()
        }

    var minRating: Int
        get() = read.getInt(PrefKeys.MIN_RATING.name, 1)
        set(rating) {
            write.putInt(PrefKeys.MIN_RATING.name, rating).apply()
        }

    val shakeThreshold: Float
        get() = read.getString(
            PrefKeys.SHAKE_THRESHOLD.name,
            context.getString(xyz.mordorx.sicmu.R.string.settings_default_shake_threshold)
        )!!.toFloat()

    val mediaButtonStartAppShake: Boolean
        get() = read.getBoolean(PrefKeys.MEDIA_BUTTON_START_APP.name, true)

    val vibrate: Boolean
        get() = read.getBoolean(PrefKeys.VIBRATE.name, true)

    var shuffle: ShuffleMode
        get() = ShuffleMode.Companion.valueOf(
            read.getInt(
                PrefKeys.SHUFFLE_V2.name,
                ShuffleMode.SEQUENTIAL.num
            )
        )
        set(shuffle) {
            write.putInt(PrefKeys.SHUFFLE_V2.name, shuffle.num).apply()
        }

    @get:SuppressLint("WrongConstant")
    private val audioHardwareId: Int
        /** This generates an ID for the current audio output devices hardware. It is used so that
         * we can have distinct audio channel configurations for different devices. */
        get() {
            val aman =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outs =
                aman.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val s = StringBuilder()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Arrays.stream(outs)
                    .map { dev: AudioDeviceInfo? ->
                        Pair(
                            dev!!.address,
                            dev.productName.toString()
                        )
                    }
                    .distinct()
                    .sorted(Comparator.comparing(Companion::PairFirst).thenComparing(Companion::PairSecond))
                    .forEach { dev ->
                        s.append(dev!!.first)
                        s.append(dev.second)
                    }
            }
            return s.toString().hashCode()
        }

    var stereo: Boolean
        /** The stereo/mono setting is unique to each device. */
        get() {
            val key = PrefKeys.STEREO.name + this.audioHardwareId
            return read.getBoolean(key, true)
        }
        /** The stereo/mono setting is unique to each device. */
        set(stereo) {
            val key = PrefKeys.STEREO.name + this.audioHardwareId
            write.putBoolean(key, stereo).apply()
        }

    val scrobble: Boolean
        get() = read.getBoolean(PrefKeys.SCROBBLE.name, false)

    val sleepDelayM: Int
        get() = read.getString(PrefKeys.SLEEP_DELAY_M.name, "60")!!.toInt()

    val showFilename: Boolean
        get() = read.getBoolean(PrefKeys.SHOW_FILENAME.name, false)

    val showRemainingTime: Boolean
        get() = read.getBoolean(PrefKeys.SHOW_REMAINING_TIME.name, false)

    val theme: Int
        get() = read.getString(PrefKeys.THEME.name, "0")!!.toInt()

    val uninitializedDefaultRating: Int
        get() = read.getString(PrefKeys.UNINITIALIZED_DEFAULT_RATING.name, "3")!!.toInt()

    val hideNavigationBar: Boolean
        get() = read.getBoolean(PrefKeys.HIDE_NAVIGATION_BAR.name, false)

    val showGroupTotalTime: Boolean
        get() = read.getBoolean(PrefKeys.SHOW_GROUP_TOTAL_TIME.name, false)

    var playbackSpeedFactor: Float
        get() = read.getFloat(PrefKeys.PLAYBACK_SPEED_FACTOR.name, 1f)
        set(x) {
            write.putFloat(PrefKeys.PLAYBACK_SPEED_FACTOR.name, x).apply()
        }

    var lastDatabasePurgeMillis: Long
        get() = read.getLong(PrefKeys.LAST_DATABASE_PURGE_MILLIS.name, 0)
        set(m) {
            write.putLong(PrefKeys.LAST_DATABASE_PURGE_MILLIS.name, m).apply()
        }

    val lastSeenChangelogVersion: Int
        get() = read.getInt(PrefKeys.LAST_SEEN_CHANGELOG_VERSION.name, 0)
    val isLastSeenChangelogVersionOutdated: Boolean
        get() = this.lastSeenChangelogVersion < BuildConfig.VERSION_CODE

    fun setLastSeenChangelogVersionToCurrent() {
        write.putInt(PrefKeys.LAST_SEEN_CHANGELOG_VERSION.name, BuildConfig.VERSION_CODE).apply()
    }

    companion object {
        private fun PairFirst(p: Pair<String, String>): String {
            return p.first
        }

        private fun PairSecond(p: Pair<String, String>): String {
            return p.second
        }
    }
}
