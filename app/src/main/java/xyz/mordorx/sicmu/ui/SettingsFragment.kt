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
package xyz.mordorx.sicmu.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.preference.CheckBoxPreference
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.util.Log
import android.widget.Toast
import xyz.mordorx.sicmu.BuildConfig
import xyz.mordorx.sicmu.R
import xyz.mordorx.sicmu.data.MediaScanner
import xyz.mordorx.sicmu.data.PrefKeys
import xyz.mordorx.sicmu.data.Preferences
import xyz.mordorx.sicmu.media.MusicService
import xyz.mordorx.sicmu.media.MusicService.MusicBinder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Formatter

class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener,
    Preference.OnPreferenceClickListener {
    private var preferences: Preferences? = null
    private var musicSrv: MusicService? = null
    private var serviceBound = false
    private val RESCAN_KEY = "RESCAN"
    private val DONATE_SOUCHAUD_KEY = "DONATE_SOUCHAUD"
    private val TEXT_SIZE_TOGGLE_KEY = "TEXT_SIZE_TOGGLE"
    private val START_SLEEP_TIMER_KEY = "START_SLEEP_TIMER"
    private val CHANGELOGS_KEY = "CHANGELOGS"
    private val GITHUB_SOURCE_URL_KEY = "GITHUB_SOURCE_URL"
    private val EXIT_APP_FORCEFULLY_KEY = "EXIT_APP_FORCEFULLY"

    // todo: improve preference default value
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)

        preferences = Preferences(getActivity().getApplicationContext())
        when (preferences!!.theme) {
            0 -> getActivity().setTheme(R.style.AppTheme)
            1 -> getActivity().setTheme(R.style.AppThemeDark)
            2 -> getActivity().setTheme(R.style.AppThemeWhite)
        }

        val playIntent = Intent(getActivity(), MusicService::class.java)
        getActivity().bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE)

        val sharedPreferences = getPreferenceScreen().getSharedPreferences()

        val thresholdKeys = PrefKeys.SHAKE_THRESHOLD.name
        val prefShakeThreshold = findPreference(thresholdKeys) as EditTextPreference
        val prefEnableShake = findPreference(PrefKeys.ENABLE_SHAKE.name) as CheckBoxPreference
        if (getActivity().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
        ) {
            prefShakeThreshold.setSummary(preferences!!.shakeThreshold.toString())
            prefEnableShake.setChecked(preferences!!.enableShake)
        } else {
            prefShakeThreshold.setEnabled(false)
            prefEnableShake.setEnabled(false)
            Toast.makeText(
                getActivity().getApplicationContext(),
                getResources().getString(R.string.settings_no_accelerometer),
                Toast.LENGTH_LONG
            ).show()
        }
        val prefEnableRating = findPreference(PrefKeys.ENABLE_RATING.name) as CheckBoxPreference
        prefEnableRating.setChecked(preferences!!.enableRating)

        findPreference(TEXT_SIZE_TOGGLE_KEY).setOnPreferenceClickListener(this)
        findPreference(RESCAN_KEY).setOnPreferenceClickListener(this)
        findPreference(DONATE_SOUCHAUD_KEY).setOnPreferenceClickListener(this)
        findPreference(CHANGELOGS_KEY).setOnPreferenceClickListener(this)
        findPreference(GITHUB_SOURCE_URL_KEY).setOnPreferenceClickListener(this)
        findPreference(EXIT_APP_FORCEFULLY_KEY).setOnPreferenceClickListener(this)
        findPreference(START_SLEEP_TIMER_KEY).setOnPreferenceClickListener(this)
        findPreference("SEMANTIC_VERSION").setSummary(BuildConfig.VERSION_NAME)
        setFontSizeIcon()

        findPreference(PrefKeys.TEXT_SIZE_NORMAL.name).setSummary(
            preferences!!.normalTextSize.toString()
        )
        findPreference(PrefKeys.TEXT_SIZE_BIG.name).setSummary(
            preferences!!.bigTextSize.toString()
        )
        findPreference(PrefKeys.TEXT_SIZE_RATIO.name).setSummary(
            preferences!!.textSizeRatio.toString()
        )

        val disablePitchCompensation =
            findPreference(PrefKeys.DISABLE_PITCH_COMPENSATION.name) as CheckBoxPreference
        disablePitchCompensation.setChecked(preferences!!.disablePitchCompensation)

        setUnfoldSubgroup()
        setUnfoldThresholdSummary()

        val rootFoldersKey = PrefKeys.ROOT_FOLDERS.name
        val prefRootFolders = findPreference(rootFoldersKey) as EditTextPreference
        prefRootFolders.setSummary(preferences!!.rootFolders)
        if (!sharedPreferences.contains(rootFoldersKey)) prefRootFolders.setText(
            MediaScanner.getMusicStoragesStr(
                getActivity().getBaseContext()
            )
        )

        findPreference(PrefKeys.SLEEP_DELAY_M.name).setSummary(
            preferences!!.sleepDelayM.toString()
        )

        setFoldSummary()
        setThemeSummary()
        setUninitializedDefaultRatingSummary()

        getActivity().onContentChanged()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (!serviceBound) return
        Log.d("MusicService", "onSharedPreferenceChanged: " + key)

        if (key == PrefKeys.DEFAULT_FOLD.name) {
            setFoldSummary()
        } else if (key == PrefKeys.TEXT_SIZE_NORMAL.name) {
            findPreference(key).setSummary(preferences!!.normalTextSize.toString())
            getActivity().setResult(CHANGE_TEXT_SIZE)
        } else if (key == PrefKeys.TEXT_SIZE_BIG.name) {
            findPreference(key).setSummary(preferences!!.bigTextSize.toString())
            getActivity().setResult(CHANGE_TEXT_SIZE)
        } else if (key == PrefKeys.TEXT_SIZE_RATIO.name) {
            findPreference(key).setSummary(preferences!!.textSizeRatio.toString())
            getActivity().setResult(CHANGE_TEXT_SIZE)
        } else if (key == PrefKeys.ENABLE_SHAKE.name) {
            musicSrv!!.setEnableShake(preferences!!.enableShake)
        } else if (key == PrefKeys.THEME.name) {
            setThemeSummary()
            getActivity().setResult(CHANGE_THEME)
            // restart activity to reload theme
            getActivity().finish()
            startActivity(getActivity().getIntent())
        } else if (key == PrefKeys.ENABLE_RATING.name) {
            musicSrv!!.setEnableRating(preferences!!.enableRating)
        } else if (key == PrefKeys.SHAKE_THRESHOLD.name) {
            val threshold = preferences!!.shakeThreshold
            musicSrv!!.setShakeThreshold(threshold)
            findPreference(key).setSummary(threshold.toString())
        } else if (key == PrefKeys.UNFOLD_SUBGROUP.name) {
            setUnfoldSubgroup()
        } else if (key == PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name) {
            setUnfoldThresholdSummary()
        } else if (key == PrefKeys.ROOT_FOLDERS.name) {
            val rootFolder = preferences!!.rootFolders
            findPreference(key).setSummary(rootFolder)
            if (!(File(rootFolder)).exists()) {
                val formatter = Formatter()
                formatter.format(
                    getResources().getString(R.string.settings_root_folder_summary),
                    rootFolder
                )
                Toast.makeText(
                    getActivity().getApplicationContext(),
                    formatter.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }
            val reinited = musicSrv!!.getRows().setRootFolders(rootFolder)
            if (reinited) musicSrv!!.setChanged()
        } else if (key == PrefKeys.SLEEP_DELAY_M.name) {
            val sleepDelayMinutes = preferences!!.sleepDelayM
            if (sleepDelayMinutes > 0) {
                findPreference(key).setSummary(sleepDelayMinutes.toString())
            } else {
                Toast.makeText(
                    getActivity().getApplicationContext(),
                    getResources().getString(R.string.settings_sleep_timer_delay_wrong),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (key == PrefKeys.SHOW_FILENAME.name) {
            musicSrv!!.getRows().reinit()
            musicSrv!!.setChanged()
        } else if (key == PrefKeys.UNINITIALIZED_DEFAULT_RATING.name) {
            setUninitializedDefaultRatingSummary()
        } else if (key == PrefKeys.DISABLE_PITCH_COMPENSATION.name) {
            musicSrv!!.applyPlaybackSpeed()
        }
    }

    private fun setUnfoldSubgroup() {
        findPreference(PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name).setEnabled(!preferences!!.unfoldSubGroup)
    }

    private fun setUnfoldThresholdSummary() {
        val formatter = Formatter()
        formatter.format(
            getResources().getString(R.string.settings_unfold_subgroup_threshold_summary),
            preferences!!.unfoldSubGroupThreshold
        )
        findPreference(PrefKeys.UNFOLD_SUBGROUP_THRESHOLD.name).setSummary(formatter.toString())
    }

    private fun setFoldSummary() {
        var idx = preferences!!.defaultFold
        val prefFold = findPreference(PrefKeys.DEFAULT_FOLD.name) as ListPreference
        val foldEntries = getResources().getStringArray(R.array.settings_fold_entries)
        if (idx >= foldEntries.size) idx = foldEntries.size - 1
        if (idx >= 0) prefFold.setSummary(foldEntries[idx])
    }

    private fun setUninitializedDefaultRatingSummary() {
        var idx = preferences!!.uninitializedDefaultRating
        val prefFold = findPreference(PrefKeys.UNINITIALIZED_DEFAULT_RATING.name) as ListPreference
        val foldEntries =
            getResources().getStringArray(R.array.uninitialized_default_rating_entries)
        idx--
        if (idx >= foldEntries.size) idx = foldEntries.size - 1
        if (idx >= 0) prefFold.setSummary(foldEntries[idx])
    }

    private fun setThemeSummary() {
        var idx = preferences!!.theme
        val pref = findPreference(PrefKeys.THEME.name) as ListPreference
        val entries = getResources().getStringArray(R.array.settings_theme_entries)
        if (idx >= entries.size) idx = entries.size - 1
        if (idx >= 0) pref.setSummary(entries[idx])
    }

    fun GetGithubSourceWebsiteIntent(): Intent {
        val url = findPreference(GITHUB_SOURCE_URL_KEY).getSummary().toString()
        val webIntent = Intent(Intent.ACTION_VIEW)
        webIntent.setData(Uri.parse(url))
        return webIntent
    }

    private fun showDonateWebsiteSouchaud() {
        startActivity(GetDonateWebsiteSouchaudIntent())
    }

    private fun showChangelogs() {
        val intent = Intent(getActivity(), ChangelogsActivity::class.java)
        startActivity(intent)
    }

    private val musicConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("Settings", "onServiceConnected")
            val binder = service as MusicBinder
            musicSrv = binder.service
            setSleepTimerTitle()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("Settings", "onServiceDisconnected")
            serviceBound = false
        }
    }

    override fun onDestroy() {
        getActivity().unbindService(musicConnection)
        serviceBound = false
        musicSrv = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        if (preference.getKey() == RESCAN_KEY) {
            rescan()
        } else if (preference.getKey() == DONATE_SOUCHAUD_KEY) {
            showDonateWebsiteSouchaud()
        } else if (preference.getKey() == START_SLEEP_TIMER_KEY) {
            if (musicSrv!!.sleepTimerScheduleMs > 0) {
                musicSrv!!.stopSleepTimer()
            } else {
                musicSrv!!.startSleepTimer(preferences!!.sleepDelayM)
            }
            setSleepTimerTitle()
        } else if (preference.getKey() == CHANGELOGS_KEY) {
            showChangelogs()
        } else if (preference.getKey() == TEXT_SIZE_TOGGLE_KEY) {
            val size = !preferences!!.enlargeText
            preferences!!.setChooseTextSize(size)
            setFontSizeIcon()
        } else if (preference.getKey() == GITHUB_SOURCE_URL_KEY) {
            startActivity(GetGithubSourceWebsiteIntent())
        } else if (preference.getKey() == EXIT_APP_FORCEFULLY_KEY) {
            ExitActivity.Companion.exit(getContext())
        }
        return false
    }

    fun rescan() {
        MediaScanner.rescanWhole(getActivity().getBaseContext())
    }

    fun setFontSizeIcon() {
        val icon: Int
        if (preferences!!.enlargeText) icon = R.drawable.ic_menu_text_big
        else icon = R.drawable.ic_menu_text_regular
        findPreference(TEXT_SIZE_TOGGLE_KEY).setIcon(icon)
    }

    private fun setSleepTimerTitle() {
        val stopTimerScheduleMs = musicSrv!!.sleepTimerScheduleMs
        if (stopTimerScheduleMs > 0) {
            findPreference(START_SLEEP_TIMER_KEY).setTitle(
                getResources().getString(R.string.settings_sleep_timer_stop)
            )
            val schedule = Date(stopTimerScheduleMs)
            val formatter = SimpleDateFormat.getTimeInstance()
            findPreference(START_SLEEP_TIMER_KEY).setSummary(
                getResources().getString(
                    R.string.settings_sleep_timer_remaining,
                    formatter.format(schedule)
                )
            )
        } else {
            findPreference(START_SLEEP_TIMER_KEY).setTitle(
                getResources().getString(R.string.settings_sleep_timer_start)
            )
            findPreference(START_SLEEP_TIMER_KEY).setSummary("")
        }
    }

    companion object {
        const val CHANGE_TEXT_SIZE: Int = 1
        const val CHANGE_THEME: Int = 2

        fun GetDonateWebsiteSouchaudIntent(): Intent {
            val webIntent = Intent(Intent.ACTION_VIEW)
            webIntent.setData(Uri.parse("https://www.paypal.com/donate/?hosted_button_id=QAPVFX7NZ8BTE"))
            return webIntent
        }
    }
}
