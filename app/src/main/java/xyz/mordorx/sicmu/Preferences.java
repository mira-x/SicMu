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

package xyz.mordorx.sicmu;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;

import androidx.core.util.Pair;

import java.util.Arrays;
import java.util.Comparator;

public class Preferences {
    private final SharedPreferences prefs;
    private final Context context;
    public Preferences(Context context) {
        var name = PreferenceManager.getDefaultSharedPreferencesName(context);
        this.prefs = context.getSharedPreferences(name, MODE_PRIVATE);
        this.context = context;
    }

    public int getLastSeenChangelogsVersion() {
        return prefs.getInt(PrefKeys.LAST_SEEN_CHANGELOGS_VERSION.name(), 0);
    }
    public void setLastSeenChangelogsVersion(int x) {
        prefs.edit().putInt(PrefKeys.LAST_SEEN_CHANGELOGS_VERSION.name(), x).apply();
    }
    
    public boolean getFollowSong() {
        return prefs.getBoolean(PrefKeys.FOLLOW_SONG.name(), true);
    }

    public void setChooseTextSize(boolean big) {
        prefs.edit().putBoolean(PrefKeys.TEXT_SIZE_CHOOSED.name(), big).apply();
    }
    public boolean getChoosedTextSize() {
        return prefs.getBoolean(PrefKeys.TEXT_SIZE_CHOOSED.name(),
                Boolean.parseBoolean(context.getString(R.string.settings_text_size_choosed_default)));
    }
    public int getBigTextSize() {
        return Integer.parseInt(prefs.getString(PrefKeys.TEXT_SIZE_BIG.name(),
                context.getString(R.string.settings_text_size_big_default)));
    }
    public int getNormalTextSize() {
        return Integer.parseInt(prefs.getString(PrefKeys.TEXT_SIZE_NORMAL.name(),
                context.getString(R.string.settings_text_size_regular_default)));
    }
    public float getTextSizeRatio() {
        return Float.parseFloat(prefs.getString(PrefKeys.TEXT_SIZE_RATIO.name(),
                context.getString(R.string.settings_text_size_ratio_default)));
    }

    public long getSongID() {
        return prefs.getLong(PrefKeys.SONG_ID.name(), -1);
    }
    public void setSongID(long songID) {
        prefs.edit().putLong(PrefKeys.SONG_ID.name(), songID).apply();
    }

    public boolean getSaveSongPos() {
        return prefs.getBoolean(PrefKeys.SAVE_SONG_POS.name(), false);
    }

    public long getSongPos() {
        return prefs.getLong(PrefKeys.SONG_POS.name(), -1);
    }
    public void setSongPos(long songPos) {
        prefs.edit().putLong(PrefKeys.SONG_POS.name(), songPos).apply();
    }
    public long getSongPosId() {
        return prefs.getLong(PrefKeys.SONG_POS_ID.name(), -1);
    }
    public void setSongPosId(long songPos) {
        prefs.edit().putLong(PrefKeys.SONG_POS_ID.name(), songPos).apply();
    }

    public Filter getFilter() {
        return Filter.valueOf(prefs.getString(PrefKeys.FILTER.name(), Filter.TREE.name()));
    }
    public void setFilter(Filter filter) {
        prefs.edit().putString(PrefKeys.FILTER.name(), filter.name()).apply();
    }

    public RepeatMode getRepeatMode() {
        return RepeatMode.valueOf(prefs.getString(PrefKeys.REPEAT_MODE.name(), RepeatMode.REPEAT_ALL.name()));
    }
    public void setRepeatMode(RepeatMode repeatMode) {
        prefs.edit().putString(PrefKeys.REPEAT_MODE.name(), repeatMode.name()).apply();
    }

    public String getRootFolders() {
        return prefs.getString(PrefKeys.ROOT_FOLDERS.name(), Path.getMusicStoragesStr(context));
    }


    public int getDefaultFold() {
        return Integer.parseInt(prefs.getString(PrefKeys.DEFAULT_FOLD.name(), "0"));
    }

    public boolean getUnfoldSubGroup() {
        return prefs.getBoolean(PrefKeys.UNFOLD_SUBGROUP.name(), false);
    }

    public int getUnfoldSubGroupThreshold() {
        return Integer.parseInt(prefs.getString(PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name(),
                context.getString(R.string.settings_unfold_subgroup_threshold_default)));
    }

    public boolean getEnableShake() {
        return prefs.getBoolean(PrefKeys.ENABLE_SHAKE.name(), false);
    }

    public void setEnableShake(boolean shakeEnabled) {
        prefs.edit().putBoolean(PrefKeys.ENABLE_SHAKE.name(), shakeEnabled).apply();
    }

    public boolean getEnableRating() {
        return prefs.getBoolean(PrefKeys.ENABLE_RATING.name(), true);
    }

    public void setEnableRating(boolean ratingEnabled) {
        prefs.edit().putBoolean(PrefKeys.ENABLE_RATING.name(), ratingEnabled).apply();
    }

    public int getMinRating() {
        return prefs.getInt(PrefKeys.MIN_RATING.name(),1);
    }

    public void setMinRating(int rating) {
        prefs.edit().putInt(PrefKeys.MIN_RATING.name(), rating).apply();
    }

    public float getShakeThreshold() {
        return Float.parseFloat(prefs.getString(PrefKeys.SHAKE_THRESHOLD.name(),
                context.getString(R.string.settings_default_shake_threshold)));
    }

    public boolean getMediaButtonStartAppShake() {
        return prefs.getBoolean(PrefKeys.MEDIA_BUTTON_START_APP.name(), true);
    }

    public boolean getVibrate() {
        return prefs.getBoolean(PrefKeys.VIBRATE.name(), true);
    }

    public ShuffleMode getShuffle() {
        return ShuffleMode.valueOf(prefs.getInt(PrefKeys.SHUFFLE_V2.name(), ShuffleMode.SEQUENTIAL.num));
    }

    public void setShuffle(ShuffleMode shuffle) {
        prefs.edit().putInt(PrefKeys.SHUFFLE_V2.name(), shuffle.num).apply();
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
                .sorted(Comparator.comparing(Preferences::PairFirst).thenComparing(Preferences::PairSecond))
                .forEach(dev -> {s.append(dev.first); s.append(dev.second);});
        return s.toString().hashCode();
    }

    /// The stereo/mono setting is unique to each device.
    public boolean getStereo() {
        var key = PrefKeys.STEREO.name() + getAudioHardwareId();
        return prefs.getBoolean(key, true);
    }

    /// The stereo/mono setting is unique to each device.
    public void setStereo(boolean stereo) {
        var key = PrefKeys.STEREO.name() + getAudioHardwareId();
        prefs.edit().putBoolean(key, stereo).apply();
    }

    public boolean getScrobble() {
        return prefs.getBoolean(PrefKeys.SCROBBLE.name(), false);
    }

    public int getSleepDelayM() {
        return Integer.parseInt(prefs.getString(PrefKeys.SLEEP_DELAY_M.name(), "60"));
    }

    public boolean getShowFilename() {
        return prefs.getBoolean(PrefKeys.SHOW_FILENAME.name(), false);
    }

    public boolean getShowRemainingTime() {
        return prefs.getBoolean(PrefKeys.SHOW_REMAINING_TIME.name(), false);
    }

    public Integer getTheme() {
        return Integer.valueOf(prefs.getString(PrefKeys.THEME.name(), "0"));
    }

    public int getUninitializedDefaultRating() {
        return Integer.parseInt(prefs.getString(PrefKeys.UNINITIALIZED_DEFAULT_RATING.name(), "3"));
    }

    public boolean getHideNavigationBar() {
        return prefs.getBoolean(PrefKeys.HIDE_NAVIGATION_BAR.name(), false);
    }

    public boolean getShowGroupTotalTime() {
        return prefs.getBoolean(PrefKeys.SHOW_GROUP_TOTAL_TIME.name(), false);
    }
}
