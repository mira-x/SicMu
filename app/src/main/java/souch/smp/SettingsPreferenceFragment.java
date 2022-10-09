/*
 * SicMu Player - Lightweight music player for Android
 * Copyright (C) 2022  Mathieu Souchaud
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;

public class SettingsPreferenceFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        Preference.OnPreferenceClickListener {
    private Parameters params;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean serviceBound = false;
    private final String RESCAN_KEY = "RESCAN";
    private final String DONATE_KEY = "DONATE";
    private final String START_SLEEP_TIMER_KEY = "START_SLEEP_TIMER";
    private final String CHANGELOGS_KEY = "CHANGELOGS";
    private final String RATINGS_NOT_WRITTEN_KEY = "RATINGS_NOT_WRITTEN_KEY";
    static public final int CHANGE_TEXT_SIZE = 1;
    static public final int CHANGE_THEME = 2;

    // todo: improve preference default value
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        params = new ParametersImpl(getActivity().getApplicationContext());
        switch (params.getTheme()) {
            case 0:
                getActivity().setTheme(R.style.AppTheme);
                break;
            case 1:
                getActivity().setTheme(R.style.AppThemeDark);
                break;
            case 2:
                getActivity().setTheme(R.style.AppThemeWhite);
                break;
        }

        playIntent = new Intent(getActivity(), MusicService.class);
        getActivity().bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();

        String thresholdKeys = PrefKeys.SHAKE_THRESHOLD.name();
        EditTextPreference prefShakeThreshold = (EditTextPreference) findPreference(thresholdKeys);
        CheckBoxPreference prefEnableShake = (CheckBoxPreference) findPreference(PrefKeys.ENABLE_SHAKE.name());
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
            prefShakeThreshold.setSummary(String.valueOf(params.getShakeThreshold()));
            prefEnableShake.setChecked(params.getEnableShake());
        } else {
            prefShakeThreshold.setEnabled(false);
            prefEnableShake.setEnabled(false);
            Toast.makeText(getActivity().getApplicationContext(),
                    getResources().getString(R.string.settings_no_accelerometer),
                    Toast.LENGTH_LONG).show();
        }
        CheckBoxPreference prefEnableRating = (CheckBoxPreference) findPreference(PrefKeys.ENABLE_RATING.name());
        prefEnableRating.setChecked(params.getEnableRating());

        findPreference(PrefKeys.TEXT_SIZE_NORMAL.name()).setSummary(String.valueOf(params.getNormalTextSize()));
        findPreference(PrefKeys.TEXT_SIZE_BIG.name()).setSummary(String.valueOf(params.getBigTextSize()));
        findPreference(PrefKeys.TEXT_SIZE_RATIO.name()).setSummary(String.valueOf(params.getTextSizeRatio()));

        Preference rescan = findPreference(RESCAN_KEY);
        rescan.setOnPreferenceClickListener(this);

        Preference donate = findPreference(DONATE_KEY);
        donate.setOnPreferenceClickListener(this);
        if (Flavor.isFlavorPro(getActivity().getBaseContext())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // fix me: This is also available for below API 26 with the androidx support library.
                donate.getParent().removePreference(donate);
            }
            else {
                donate.setEnabled(false);
            }
        }

        Preference changelogs = findPreference(CHANGELOGS_KEY);
        changelogs.setOnPreferenceClickListener(this);

        Preference ratings_not_written = findPreference(RATINGS_NOT_WRITTEN_KEY);
        ratings_not_written.setOnPreferenceClickListener(this);

        ListPreference theme = (ListPreference) findPreference(PrefKeys.THEME.name());
        if (Flavor.isFlavorFreeware(getActivity().getBaseContext())) {
            theme.setEnabled(false);
            theme.setTitle(R.string.settings_theme_title_free);
        }

        Preference sleepTimer = findPreference(START_SLEEP_TIMER_KEY);
        sleepTimer.setOnPreferenceClickListener(this);

        setUnfoldSubgroup();
        setUnfoldThresholdSummary();

        String rootFoldersKey = PrefKeys.ROOT_FOLDERS.name();
        EditTextPreference prefRootFolders = (EditTextPreference) findPreference(rootFoldersKey);
        prefRootFolders.setSummary(params.getRootFolders());
        if (!sharedPreferences.contains(rootFoldersKey))
            prefRootFolders.setText(Path.getMusicStoragesStr(getActivity().getBaseContext()));

        findPreference(PrefKeys.SLEEP_DELAY_M.name()).setSummary(String.valueOf(params.getSleepDelayM()));

        setFoldSummary();
        setThemeSummary();
        setUninitializedDefaultRatingSummary();

        getActivity().onContentChanged();
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!serviceBound)
            return;
        Log.d("MusicService", "onSharedPreferenceChanged: " + key);

        if (key.equals(PrefKeys.DEFAULT_FOLD.name())) {
            setFoldSummary();
        } else if (key.equals(PrefKeys.TEXT_SIZE_NORMAL.name())) {
            findPreference(key).setSummary(String.valueOf(params.getNormalTextSize()));
            getActivity().setResult(CHANGE_TEXT_SIZE);
        } else if (key.equals(PrefKeys.TEXT_SIZE_BIG.name())) {
            findPreference(key).setSummary(String.valueOf(params.getBigTextSize()));
            getActivity().setResult(CHANGE_TEXT_SIZE);
        } else if (key.equals(PrefKeys.TEXT_SIZE_RATIO.name())) {
            findPreference(key).setSummary(String.valueOf(params.getTextSizeRatio()));
            getActivity().setResult(CHANGE_TEXT_SIZE);
        } else if (key.equals(PrefKeys.ENABLE_SHAKE.name())) {
            musicSrv.setEnableShake(params.getEnableShake());
        } else if (key.equals(PrefKeys.THEME.name())) {
            setThemeSummary();
            getActivity().setResult(CHANGE_THEME);
            // restart activity to reload theme
            getActivity().finish();
            startActivity(getActivity().getIntent());
        } else if (key.equals(PrefKeys.ENABLE_RATING.name())) {
            musicSrv.setEnableRating(params.getEnableRating());
        } else if (key.equals(PrefKeys.SHAKE_THRESHOLD.name())) {
            final float threshold = params.getShakeThreshold();
            musicSrv.setShakeThreshold(threshold);
            findPreference(key).setSummary(String.valueOf(threshold));
        } else if (key.equals(PrefKeys.UNFOLD_SUBGROUP.name())) {
            setUnfoldSubgroup();
        } else if (key.equals(PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name())) {
            setUnfoldThresholdSummary();
        } else if (key.equals(PrefKeys.ROOT_FOLDERS.name())) {
            final String rootFolder = params.getRootFolders();
            findPreference(key).setSummary(rootFolder);
            if (!(new File(rootFolder)).exists()) {
                Formatter formatter = new Formatter();
                formatter.format(getResources().getString(R.string.settings_root_folder_summary),
                        rootFolder);
                Toast.makeText(getActivity().getApplicationContext(),
                        formatter.toString(),
                        Toast.LENGTH_LONG).show();
            }
            boolean reinited = musicSrv.getRows().setRootFolders(rootFolder);
            if (reinited)
                musicSrv.setChanged();
        } else if (key.equals(PrefKeys.SLEEP_DELAY_M.name())) {
            final int sleepDelayMinutes = params.getSleepDelayM();
            if (sleepDelayMinutes > 0) {
                findPreference(key).setSummary(String.valueOf(sleepDelayMinutes));
            } else {
                Toast.makeText(getActivity().getApplicationContext(),
                        getResources().getString(R.string.settings_sleep_timer_delay_wrong),
                        Toast.LENGTH_LONG).show();
            }
        } else if (key.equals(PrefKeys.SHOW_FILENAME.name())) {
            musicSrv.setChanged();
        } else if (key.equals(PrefKeys.UNINITIALIZED_DEFAULT_RATING.name())) {
            setUninitializedDefaultRatingSummary();
        }
    }

    private void setUnfoldSubgroup() {
        findPreference(PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name()).setEnabled(!params.getUnfoldSubGroup());
    }

    private void setUnfoldThresholdSummary() {
        Formatter formatter = new Formatter();
        formatter.format(getResources().getString(R.string.settings_unfold_subgroup_threshold_summary),
                params.getUnfoldSubGroupThreshold());
        findPreference(PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name()).setSummary(formatter.toString());
    }

    private void setFoldSummary() {
        int idx = params.getDefaultFold();
        ListPreference prefFold = (ListPreference) findPreference(PrefKeys.DEFAULT_FOLD.name());
        String[] foldEntries = getResources().getStringArray(R.array.settings_fold_entries);
        if (idx >= foldEntries.length)
            idx = foldEntries.length - 1;
        if (idx >= 0)
            prefFold.setSummary(foldEntries[idx]);
    }

    private void setUninitializedDefaultRatingSummary() {
        int idx = params.getUninitializedDefaultRating();
        ListPreference prefFold = (ListPreference) findPreference(PrefKeys.UNINITIALIZED_DEFAULT_RATING.name());
        String[] foldEntries = getResources().getStringArray(R.array.uninitialized_default_rating_entries);
        idx--;
        if (idx >= foldEntries.length)
            idx = foldEntries.length - 1;
        if (idx >= 0)
            prefFold.setSummary(foldEntries[idx]);
    }

    private void setThemeSummary() {
        int idx = params.getTheme();
        ListPreference pref = (ListPreference) findPreference(PrefKeys.THEME.name());
        String[] entries = getResources().getStringArray(R.array.settings_theme_entries);
        if (idx >= entries.length)
            idx = entries.length - 1;
        if (idx >= 0)
            pref.setSummary(entries[idx]);
    }
    static public Intent GetDonateWebsiteIntent() {
        Intent webIntent = new Intent(Intent.ACTION_VIEW);
        webIntent.setData(Uri.parse("https://www.paypal.com/donate/?hosted_button_id=QAPVFX7NZ8BTE"));
        return webIntent;
    }
    static public Intent GetProWebsiteIntent() {
        Intent webIntent = new Intent(Intent.ACTION_VIEW);
        webIntent.setData(Uri.parse("https://play.google.com/store/apps/details?id=souch.smp.pro")); // todo change sicmuplayer to pro
        return webIntent;
    }

    private void showDonateWebsite() {
        startActivity(GetDonateWebsiteIntent());
    }

    private void showChangelogs() {
        Intent intent = new Intent(getActivity(), ChangelogsActivity.class);
        startActivity(intent);
    }

    private ServiceConnection musicConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("Settings", "onServiceConnected");
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicSrv = binder.getService();
            setSleepTimerTitle();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("Settings", "onServiceDisconnected");
            serviceBound = false;
        }
    };

    @Override
    public void onDestroy() {
        getActivity().unbindService(musicConnection);
        serviceBound = false;
        musicSrv = null;
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private int nbDonateClick = 0;
    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(RESCAN_KEY)) {
            rescan();
        } else if (preference.getKey().equals(DONATE_KEY)) {
            showDonateWebsite();
            nbDonateClick++;
            if (nbDonateClick >= 3) {
                musicSrv.getDatabase().disableShowDonate();
                Toast.makeText(getActivity().getBaseContext(),
                        getResources().getString(R.string.show_donate_disabled), Toast.LENGTH_SHORT).show();
            }
        } else if (preference.getKey().equals(START_SLEEP_TIMER_KEY)) {
            if (musicSrv.getSleepTimerScheduleMs() > 0) {
                musicSrv.stopSleepTimer();
            } else {
                musicSrv.startSleepTimer(params.getSleepDelayM());
            }
            setSleepTimerTitle();
        } else if (preference.getKey().equals(CHANGELOGS_KEY)) {
            showChangelogs();
        } else if (preference.getKey().equals(RATINGS_NOT_WRITTEN_KEY)) {
            musicSrv.getDatabase().getRatingsToSynchronizeAsync(songs -> {
                if (songs.isEmpty())
                    songs = getResources().getString(R.string.settings_ratings_are_in_sync);
                final String msg = songs;
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getActivity().getBaseContext(), msg, Toast.LENGTH_LONG).show());
            });
        }
        return false;
    }

    public void rescan() {
        Path.rescanWhole(getActivity().getBaseContext());
    }

    private void setSleepTimerTitle() {
        long stopTimerScheduleMs = musicSrv.getSleepTimerScheduleMs();
        if (stopTimerScheduleMs > 0) {
            findPreference(START_SLEEP_TIMER_KEY).setTitle(
                    getResources().getString(R.string.settings_sleep_timer_stop));
            Date schedule = new Date(stopTimerScheduleMs);
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
            findPreference(START_SLEEP_TIMER_KEY).setSummary(
                    getResources().getString(R.string.settings_sleep_timer_remaining, formatter.format(schedule)));
        } else {
            findPreference(START_SLEEP_TIMER_KEY).setTitle(
                    getResources().getString(R.string.settings_sleep_timer_start));
            findPreference(START_SLEEP_TIMER_KEY).setSummary("");
        }
    }
}
