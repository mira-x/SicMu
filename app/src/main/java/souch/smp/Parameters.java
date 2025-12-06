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

package souch.smp;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;

import androidx.core.util.Pair;

import java.util.Arrays;
import java.util.Comparator;

public class Parameters {
    private final Context context;
    public Parameters(Context context) {
        this.context = context;
    }

    private SharedPreferences getPref() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
    private SharedPreferences.Editor getEditor() {
        return getPref().edit();
    }

    public boolean getFollowSong() {
        return getPref().getBoolean(PrefKeys.FOLLOW_SONG.name(), true);
    }

    public void setChooseTextSize(boolean big) {
        getEditor().putBoolean(PrefKeys.TEXT_SIZE_CHOOSED.name(), big).commit();
    }
    public boolean getChoosedTextSize() {
        return getPref().getBoolean(PrefKeys.TEXT_SIZE_CHOOSED.name(),
                Boolean.valueOf(context.getString(R.string.settings_text_size_choosed_default)));
    }
    public int getBigTextSize() {
        return Integer.valueOf(getPref().getString(PrefKeys.TEXT_SIZE_BIG.name(),
                context.getString(R.string.settings_text_size_big_default)));
    }
    public int getNormalTextSize() {
        return Integer.valueOf(getPref().getString(PrefKeys.TEXT_SIZE_NORMAL.name(),
                context.getString(R.string.settings_text_size_regular_default)));
    }
    public float getTextSizeRatio() {
        return Float.valueOf(getPref().getString(PrefKeys.TEXT_SIZE_RATIO.name(),
                context.getString(R.string.settings_text_size_ratio_default)));
    }


    public long getSongID() {
        return getPref().getLong(PrefKeys.SONG_ID.name(), -1);
    }
    public void setSongID(long songID) {
        getEditor().putLong(PrefKeys.SONG_ID.name(), songID).commit();
    }

    public boolean getSaveSongPos() {
        return getPref().getBoolean(PrefKeys.SAVE_SONG_POS.name(), false);
    }

    public long getSongPos() {
        return getPref().getLong(PrefKeys.SONG_POS.name(), -1);
    }
    public void setSongPos(long songPos) {
        getEditor().putLong(PrefKeys.SONG_POS.name(), songPos).commit();
    }
    public long getSongPosId() {
        return getPref().getLong(PrefKeys.SONG_POS_ID.name(), -1);
    }
    public void setSongPosId(long songPos) {
        getEditor().putLong(PrefKeys.SONG_POS_ID.name(), songPos).commit();
    }

    public Filter getFilter() {
        return Filter.valueOf(getPref().getString(PrefKeys.FILTER.name(), Filter.TREE.name()));
    }
    public void setFilter(Filter filter) {
        getEditor().putString(PrefKeys.FILTER.name(), filter.name()).commit();
    }

    public RepeatMode getRepeatMode() {
        return RepeatMode.valueOf(getPref().getString(PrefKeys.REPEAT_MODE.name(), RepeatMode.REPEAT_ALL.name()));
    }
    public void setRepeatMode(RepeatMode repeatMode) {
        getEditor().putString(PrefKeys.REPEAT_MODE.name(), repeatMode.name()).commit();
    }

    public String getRootFolders() {
        return getPref().getString(PrefKeys.ROOT_FOLDERS.name(), Path.getMusicStoragesStr(context));
    }


    public int getDefaultFold() {
        return Integer.valueOf(getPref().getString(PrefKeys.DEFAULT_FOLD.name(), "0"));
    }

    public boolean getUnfoldSubGroup() {
        return getPref().getBoolean(PrefKeys.UNFOLD_SUBGROUP.name(), false);
    }

    public int getUnfoldSubGroupThreshold() {
        return Integer.valueOf(getPref().getString(PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name(),
                context.getString(R.string.settings_unfold_subgroup_threshold_default)));
    }

    public boolean getEnableShake() {
        return getPref().getBoolean(PrefKeys.ENABLE_SHAKE.name(), false);
    }

    public void setEnableShake(boolean shakeEnabled) {
        getEditor().putBoolean(PrefKeys.ENABLE_SHAKE.name(), shakeEnabled).commit();
    }

    public boolean getEnableRating() {
        return getPref().getBoolean(PrefKeys.ENABLE_RATING.name(), true);
    }

    public void setEnableRating(boolean ratingEnabled) {
        getEditor().putBoolean(PrefKeys.ENABLE_RATING.name(), ratingEnabled).commit();
    }

    public int getMinRating() {
        return getPref().getInt(PrefKeys.MIN_RATING.name(),1);
    }

    public void setMinRating(int rating) {
        getEditor().putInt(PrefKeys.MIN_RATING.name(), rating).commit();
    }

    public float getShakeThreshold() {
        return Float.valueOf(getPref().getString(PrefKeys.SHAKE_THRESHOLD.name(),
                context.getString(R.string.settings_default_shake_threshold)));
    }

    public boolean getMediaButtonStartAppShake() {
        return getPref().getBoolean(PrefKeys.MEDIA_BUTTON_START_APP.name(), true);
    }

    public boolean getVibrate() {
        return getPref().getBoolean(PrefKeys.VIBRATE.name(), true);
    }

    public ShuffleMode getShuffle() {
        return ShuffleMode.valueOf(getPref().getInt(PrefKeys.SHUFFLE_V2.name(), ShuffleMode.SEQUENTIAL.num));
    }

    public void setShuffle(ShuffleMode shuffle) {
        getEditor().putInt(PrefKeys.SHUFFLE_V2.name(), shuffle.num).commit();
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
        Arrays.stream(outs)
                .map(dev -> new Pair<String, String>(dev.getAddress(), dev.getProductName().toString()))
                .distinct()
                .sorted(Comparator.comparing(Parameters::PairFirst).thenComparing(Parameters::PairSecond))
                .forEach(dev -> {s.append(dev.first); s.append(dev.second);});
        return s.toString().hashCode();
    }

    /// The stereo/mono setting is unique to each device.
    public boolean getStereo() {
        var key = PrefKeys.STEREO.name() + getAudioHardwareId();
        return getPref().getBoolean(key, true);
    }

    /// The stereo/mono setting is unique to each device.
    public void setStereo(boolean stereo) {
        var key = PrefKeys.STEREO.name() + getAudioHardwareId();
        getEditor().putBoolean(key, stereo).commit();
    }

    public boolean getScrobble() {
        return getPref().getBoolean(PrefKeys.SCROBBLE.name(), false);
    }

    public int getSleepDelayM() {
        return Integer.parseInt(getPref().getString(PrefKeys.SLEEP_DELAY_M.name(), "60"));
    }

    public boolean getShowFilename() {
        return getPref().getBoolean(PrefKeys.SHOW_FILENAME.name(), false);
    }

    public boolean getShowRemainingTime() {
        return getPref().getBoolean(PrefKeys.SHOW_REMAINING_TIME.name(), false);
    }

    public Integer getTheme() {
        return Integer.valueOf(getPref().getString(PrefKeys.THEME.name(), "0"));
    }

    public int getUninitializedDefaultRating() {
        return Integer.parseInt(getPref().getString(PrefKeys.UNINITIALIZED_DEFAULT_RATING.name(), "3"));
    }

    public boolean getHideNavigationBar() {
        return getPref().getBoolean(PrefKeys.HIDE_NAVIGATION_BAR.name(), false);
    }

    public boolean getShowGroupTotalTime() {
        return getPref().getBoolean(PrefKeys.SHOW_GROUP_TOTAL_TIME.name(), false);
    }
}
