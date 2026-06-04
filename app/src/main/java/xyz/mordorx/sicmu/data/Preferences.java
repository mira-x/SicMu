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

package xyz.mordorx.sicmu.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;

import androidx.core.util.Pair;

import java.util.Arrays;
import java.util.Comparator;

import xyz.mordorx.sicmu.media.Filter;
import xyz.mordorx.sicmu.R;
import xyz.mordorx.sicmu.media.RepeatMode;
import xyz.mordorx.sicmu.media.ShuffleMode;

public class Preferences {
    private final Context context;
    private final SharedPreferences read;
    private final SharedPreferences.Editor write;
    public Preferences(Context ctx) {
        this.context = ctx;
        this.read = PreferenceManager.getDefaultSharedPreferences(ctx);
        this.write = read.edit();
    }
    public boolean getFollowSong() {
        return read.getBoolean(PrefKeys.FOLLOW_SONG.name(), true);
    }

    public void setChooseTextSize(boolean big) {
        write.putBoolean(PrefKeys.TEXT_SIZE_CHOOSED.name(), big).commit();
    }
    public boolean getChoosedTextSize() {
        return read.getBoolean(PrefKeys.TEXT_SIZE_CHOOSED.name(),
                Boolean.parseBoolean(context.getString(R.string.settings_text_size_choosed_default)));
    }
    public int getBigTextSize() {
        return Integer.parseInt(read.getString(PrefKeys.TEXT_SIZE_BIG.name(),
                context.getString(R.string.settings_text_size_big_default)));
    }
    public int getNormalTextSize() {
        return Integer.parseInt(read.getString(PrefKeys.TEXT_SIZE_NORMAL.name(),
                context.getString(R.string.settings_text_size_regular_default)));
    }
    public float getTextSizeRatio() {
        return Float.parseFloat(read.getString(PrefKeys.TEXT_SIZE_RATIO.name(),
                context.getString(R.string.settings_text_size_ratio_default)));
    }
    public boolean getDisablePitchCompensation() {
        return read.getBoolean(PrefKeys.DISABLE_PITCH_COMPENSATION.name(), false);
    }


    public long getSongID() {
        return read.getLong(PrefKeys.SONG_ID.name(), -1);
    }
    public void setSongID(long songID) {
        write.putLong(PrefKeys.SONG_ID.name(), songID).commit();
    }

    public boolean getSaveSongPos() {
        return read.getBoolean(PrefKeys.SAVE_SONG_POS.name(), false);
    }

    public long getSongPos() {
        return read.getLong(PrefKeys.SONG_POS.name(), -1);
    }
    public void setSongPos(long songPos) {
        write.putLong(PrefKeys.SONG_POS.name(), songPos).commit();
    }
    public long getSongPosId() {
        return read.getLong(PrefKeys.SONG_POS_ID.name(), -1);
    }
    public void setSongPosId(long songPos) {
        write.putLong(PrefKeys.SONG_POS_ID.name(), songPos).commit();
    }

    public Filter getFilter() {
        return Filter.valueOf(read.getString(PrefKeys.FILTER.name(), Filter.TREE.name()));
    }
    public void setFilter(Filter filter) {
        write.putString(PrefKeys.FILTER.name(), filter.name()).commit();
    }

    public RepeatMode getRepeatMode() {
        return RepeatMode.valueOf(read.getString(PrefKeys.REPEAT_MODE.name(), RepeatMode.REPEAT_ALL.name()));
    }
    public void setRepeatMode(RepeatMode repeatMode) {
        write.putString(PrefKeys.REPEAT_MODE.name(), repeatMode.name()).commit();
    }

    public String getRootFolders() {
        return read.getString(PrefKeys.ROOT_FOLDERS.name(), Path.getMusicStoragesStr(context));
    }


    public int getDefaultFold() {
        return Integer.parseInt(read.getString(PrefKeys.DEFAULT_FOLD.name(), "0"));
    }

    public boolean getUnfoldSubGroup() {
        return read.getBoolean(PrefKeys.UNFOLD_SUBGROUP.name(), false);
    }

    public int getUnfoldSubGroupThreshold() {
        return Integer.parseInt(read.getString(PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name(),
                context.getString(R.string.settings_unfold_subgroup_threshold_default)));
    }

    public boolean getEnableShake() {
        return read.getBoolean(PrefKeys.ENABLE_SHAKE.name(), false);
    }

    public void setEnableShake(boolean shakeEnabled) {
        write.putBoolean(PrefKeys.ENABLE_SHAKE.name(), shakeEnabled).commit();
    }

    public boolean getEnableRating() {
        return read.getBoolean(PrefKeys.ENABLE_RATING.name(), true);
    }

    public void setEnableRating(boolean ratingEnabled) {
        write.putBoolean(PrefKeys.ENABLE_RATING.name(), ratingEnabled).commit();
    }

    public int getMinRating() {
        return read.getInt(PrefKeys.MIN_RATING.name(),1);
    }

    public void setMinRating(int rating) {
        write.putInt(PrefKeys.MIN_RATING.name(), rating).commit();
    }

    public float getShakeThreshold() {
        return Float.parseFloat(read.getString(PrefKeys.SHAKE_THRESHOLD.name(),
                context.getString(R.string.settings_default_shake_threshold)));
    }

    public boolean getMediaButtonStartAppShake() {
        return read.getBoolean(PrefKeys.MEDIA_BUTTON_START_APP.name(), true);
    }

    public boolean getVibrate() {
        return read.getBoolean(PrefKeys.VIBRATE.name(), true);
    }

    public ShuffleMode getShuffle() {
        return ShuffleMode.valueOf(read.getInt(PrefKeys.SHUFFLE_V2.name(), ShuffleMode.SEQUENTIAL.num));
    }

    public void setShuffle(ShuffleMode shuffle) {
        write.putInt(PrefKeys.SHUFFLE_V2.name(), shuffle.num).commit();
    }

    private static String PairFirst(Pair<String, String> p) {
        return p.first;
    }
    private static String PairSecond(Pair<String, String> p) {
        return p.second;
    }
    /// This generates an ID for the current audio output devices hardware. It is used so that
    /// we can have distinct audio channel configurations for different devices.
    private int getAudioHardwareId() {
        var aman = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        var outs = aman.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS);
        var s = new StringBuilder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Arrays.stream(outs)
                    .map(dev -> new Pair<>(dev.getAddress(), dev.getProductName().toString()))
                    .distinct()
                    .sorted(Comparator.comparing(Preferences::PairFirst).thenComparing(Preferences::PairSecond))
                    .forEach(dev -> {s.append(dev.first); s.append(dev.second);});
        }
        return s.toString().hashCode();
    }

    /// The stereo/mono setting is unique to each device.
    public boolean getStereo() {
        var key = PrefKeys.STEREO.name() + getAudioHardwareId();
        return read.getBoolean(key, true);
    }

    /// The stereo/mono setting is unique to each device.
    public void setStereo(boolean stereo) {
        var key = PrefKeys.STEREO.name() + getAudioHardwareId();
        write.putBoolean(key, stereo).commit();
    }

    public boolean getScrobble() {
        return read.getBoolean(PrefKeys.SCROBBLE.name(), false);
    }

    public int getSleepDelayM() {
        return Integer.parseInt(read.getString(PrefKeys.SLEEP_DELAY_M.name(), "60"));
    }

    public boolean getShowFilename() {
        return read.getBoolean(PrefKeys.SHOW_FILENAME.name(), false);
    }

    public boolean getShowRemainingTime() {
        return read.getBoolean(PrefKeys.SHOW_REMAINING_TIME.name(), false);
    }

    public Integer getTheme() {
        return Integer.valueOf(read.getString(PrefKeys.THEME.name(), "0"));
    }

    public int getUninitializedDefaultRating() {
        return Integer.parseInt(read.getString(PrefKeys.UNINITIALIZED_DEFAULT_RATING.name(), "3"));
    }

    public boolean getHideNavigationBar() {
        return read.getBoolean(PrefKeys.HIDE_NAVIGATION_BAR.name(), false);
    }

    public boolean getShowGroupTotalTime() {
        return read.getBoolean(PrefKeys.SHOW_GROUP_TOTAL_TIME.name(), false);
    }

    public float getPlaybackSpeedFactor() {
        return read.getFloat(PrefKeys.PLAYBACK_SPEED_FACTOR.name(), 1f);
    }

    public void setPlaybackSpeedFactor(float x) {
        write.putFloat(PrefKeys.PLAYBACK_SPEED_FACTOR.name(), x).apply();
    }
}
