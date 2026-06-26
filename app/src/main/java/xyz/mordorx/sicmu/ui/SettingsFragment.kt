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
import android.preference.Preference
import android.preference.PreferenceFragment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.update
import xyz.mordorx.sicmu.BuildConfig
import xyz.mordorx.sicmu.R
import xyz.mordorx.sicmu.data.MediaScanner
import xyz.mordorx.sicmu.data.PrefKeys
import xyz.mordorx.sicmu.data.XPreferences.Companion.P
import xyz.mordorx.sicmu.media.MusicService
import xyz.mordorx.sicmu.media.MusicService.MusicBinder
import java.text.SimpleDateFormat
import java.util.Date

class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener,
    Preference.OnPreferenceClickListener {
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

        val playIntent = Intent(getActivity(), MusicService::class.java)
        getActivity().bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE)

        val sharedPreferences = getPreferenceScreen().getSharedPreferences()

        val thresholdKeys = PrefKeys.SHAKE_THRESHOLD.name
        val prefShakeThreshold = findPreference(thresholdKeys) as EditTextPreference
        val prefEnableShake = findPreference(PrefKeys.ENABLE_SHAKE.name) as CheckBoxPreference
        if (getActivity().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
        ) {
            prefShakeThreshold.setSummary(P.value.shakeThreshold.toString())
            prefEnableShake.setChecked(P.value.shakePlaysSongs)
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
        prefEnableRating.setChecked(true)

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
            P.value.textSizeNormal.toString()
        )
        findPreference(PrefKeys.TEXT_SIZE_BIG.name).setSummary(
            P.value.textSizeBig.toString()
        )
        findPreference(PrefKeys.TEXT_SIZE_RATIO.name).setSummary(
            P.value.rowGroupTextSizeRatio.toString()
        )

        val disablePitchCompensation =
            findPreference(PrefKeys.DISABLE_PITCH_COMPENSATION.name) as CheckBoxPreference
        disablePitchCompensation.setChecked(P.value.disablePitchCompensation)

        val rootFoldersKey = PrefKeys.ROOT_FOLDERS.name
        val prefRootFolders = findPreference(rootFoldersKey) as EditTextPreference
        prefRootFolders.setSummary("deprecated")
        if (!sharedPreferences.contains(rootFoldersKey)) prefRootFolders.setText(
            MediaScanner.getMusicStoragesStr(
                getActivity().getBaseContext()
            )
        )

        findPreference(PrefKeys.SLEEP_DELAY_M.name).setSummary(
            P.value.sleepDelayM.toString()
        )

        getActivity().onContentChanged()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (!serviceBound) return
        Log.d("MusicService", "onSharedPreferenceChanged: " + key)

        if (key == PrefKeys.DEFAULT_FOLD.name) {
        } else if (key == PrefKeys.TEXT_SIZE_NORMAL.name) {
            findPreference(key).setSummary(P.value.textSizeNormal.toString())
            getActivity().setResult(CHANGE_TEXT_SIZE)
        } else if (key == PrefKeys.TEXT_SIZE_BIG.name) {
            findPreference(key).setSummary(P.value.textSizeBig.toString())
            getActivity().setResult(CHANGE_TEXT_SIZE)
        } else if (key == PrefKeys.TEXT_SIZE_RATIO.name) {
            findPreference(key).setSummary(P.value.rowGroupTextSizeRatio.toString())
            getActivity().setResult(CHANGE_TEXT_SIZE)
        } else if (key == PrefKeys.ENABLE_SHAKE.name) {
            musicSrv!!.setEnableShake(P.value.shakePlaysSongs)
        } else if (key == PrefKeys.THEME.name) {
            getActivity().setResult(CHANGE_THEME)
            // restart activity to reload theme
            getActivity().finish()
            startActivity(getActivity().getIntent())
        } else if (key == PrefKeys.ENABLE_RATING.name) {
            musicSrv!!.setEnableRating(true)
        } else if (key == PrefKeys.SHAKE_THRESHOLD.name) {
            findPreference(key).setSummary(P.value.shakeThreshold.toString())
        } else if (key == PrefKeys.ROOT_FOLDERS.name) {
            val rootFolder = ""
            findPreference(key).setSummary(rootFolder)
        } else if (key == PrefKeys.SLEEP_DELAY_M.name) {
            val sleepDelayMinutes = P.value.sleepDelayM
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
        } else if (key == PrefKeys.DISABLE_PITCH_COMPENSATION.name) {
            musicSrv!!.applyPlaybackSpeed()
        }
    }


    fun getGithubSourceWebsiteIntent(): Intent {
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
                musicSrv!!.startSleepTimer(P.value.sleepDelayM)
            }
            setSleepTimerTitle()
        } else if (preference.getKey() == CHANGELOGS_KEY) {
            showChangelogs()
        } else if (preference.getKey() == TEXT_SIZE_TOGGLE_KEY) {
            P.update { p -> p.copy(enlargeText = !p.enlargeText) }
            setFontSizeIcon()
        } else if (preference.getKey() == GITHUB_SOURCE_URL_KEY) {
            startActivity(getGithubSourceWebsiteIntent())
        } else if (preference.getKey() == EXIT_APP_FORCEFULLY_KEY) {
            ExitActivity.Companion.exit(getContext())
        }
        return false
    }

    fun rescan() {
        MediaScanner.rescanWhole(getActivity().getBaseContext())
    }

    fun setFontSizeIcon() {
        val icon: Int = if (P.value.enlargeText) R.drawable.ic_menu_text_big else R.drawable.ic_menu_text_regular
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
