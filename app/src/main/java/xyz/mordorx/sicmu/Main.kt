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
package xyz.mordorx.sicmu

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.AnimationDrawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.NumberPicker
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.jsibbold.zoomage.ZoomageView
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagField
import org.woheller69.freeDroidWarn.FreeDroidWarn
import xyz.mordorx.sicmu.data.AlbumArtLoader
import xyz.mordorx.sicmu.data.MediaScanner
import xyz.mordorx.sicmu.data.Row
import xyz.mordorx.sicmu.data.RowGroup
import xyz.mordorx.sicmu.data.RowSong
import xyz.mordorx.sicmu.data.RowSong.Companion.msToMinutes
import xyz.mordorx.sicmu.data.RowSong.LoadMetadataCallbackInterface
import xyz.mordorx.sicmu.data.Rows
import xyz.mordorx.sicmu.data.Rows.RateGroupCallbackInterface
import xyz.mordorx.sicmu.data.XPreferences
import xyz.mordorx.sicmu.media.MusicService
import xyz.mordorx.sicmu.media.MusicService.MusicBinder
import xyz.mordorx.sicmu.media.PlayerState
import xyz.mordorx.sicmu.media.RepeatMode
import xyz.mordorx.sicmu.media.ShuffleMode
import xyz.mordorx.sicmu.ui.ChangelogsActivity
import xyz.mordorx.sicmu.ui.CombinedClickableImage
import xyz.mordorx.sicmu.ui.CombinedClickableImage.RepeatListener
import xyz.mordorx.sicmu.ui.RowsAdapter
import xyz.mordorx.sicmu.ui.SettingsActivity
import xyz.mordorx.sicmu.ui.SettingsFragment
import java.net.URLEncoder
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.UnaryOperator
import java.util.regex.Pattern
import kotlin.math.floor
import kotlin.math.max
import androidx.core.view.isVisible
import androidx.media3.common.C
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import xyz.mordorx.sicmu.data.AudioHardwareID
import xyz.mordorx.sicmu.data.XPreferences.Companion.P

@UnstableApi
class Main : AppCompatActivity() {
    private var rows: Rows? = null
    private var songView: ListView? = null
    private var songAdt: RowsAdapter? = null
    var playButton: ImageButton? = null

    var musicSrv: MusicService? = null
        private set
    private var playIntent: Intent? = null
    private var serviceBound = false

    // the app is about to close
    private var finishing = false

    private var timer: Timer? = null
    private var seekbar: SeekBar? = null

    // tell whether the seekbar is currently touch by a user
    private var touchSeekbar = false
    private var duration: TextView? = null
    private var currDuration: TextView? = null

    private var posButton: ImageButton? = null
    private var toggleDetailsButton: ImageButton? = null

    private var scrollToSongUponStart = false

    private var seekButtonsOpened = false
    private var detailsOpened = false
    private var detailsToggledFollowAuto = false
    private var hasCoverArt = false


    private var vibrator: Vibrator? = null

    private var appAnimation: AnimationDrawable? = null

    private var detailsLayout: LinearLayout? = null
    private var seekButtonsLayout: LinearLayout? = null
    private var playbackSpeedText: NumberPicker? = null
    private var warningLayout: LinearLayout? = null

    private var moreButtonsLayout: LinearLayout? = null
    private var closeMoreButtonsTimer: Timer? = null

    private var albumImage: ZoomageView? = null
    private var songTitle: TextView? = null
    private var songAlbum: TextView? = null
    private var songArtist: TextView? = null
    private var songMime: TextView? = null
    private var warningText: TextView? = null
    val ratingButtons: ArrayList<ImageButton?> = ArrayList()
    private var details_rating_layout: LinearLayout? = null
    private var detailsBigCoverArt = false
    private val EXTERNAL_STORAGE_REQUEST_CODE = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Main", "onCreate")

        runBlocking {
            XPreferences.load(applicationContext)
        }

        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)

        setTheme(R.style.AppTheme)

        setContentView(R.layout.activity_main)
        finishing = false

        songView = findViewById<ListView?>(R.id.song_list)
        playButton = findViewById<ImageButton?>(R.id.play_button)
        // useful only for testing
        playButton!!.setTag(R.drawable.ic_action_play)
        playButton!!.setOnTouchListener(touchListener)

        val gotoButton = findViewById<ImageButton>(R.id.goto_button)
        gotoButton.setOnTouchListener(touchListener)
        gotoButton.setOnLongClickListener(gotoSongLongListener)

        posButton = findViewById<ImageButton>(R.id.toggle_seek_buttons)
        toggleDetailsButton = findViewById<ImageButton>(R.id.toggle_details_button)
        seekButtonsOpened = false
        posButton!!.setImageDrawable(null)
        seekButtonsLayout = findViewById<LinearLayout>(R.id.seek_buttons_layout)
        seekButtonsLayout!!.setVisibility(View.GONE)
        warningLayout = findViewById<LinearLayout>(R.id.warning_layout)
        warningLayout!!.setVisibility(View.GONE)
        warningLayout!!.setOnClickListener(View.OnClickListener { view: View? -> hideWarning() })
        detailsLayout = findViewById<LinearLayout>(R.id.details_layout)
        detailsLayout!!.setVisibility(View.GONE)
        detailsToggledFollowAuto = true

        val repeatDelta = 260
        val prevButton = findViewById<ImageButton>(R.id.prev_button)
        prevButton.setOnLongClickListener(prevGroupLongListener)
        prevButton.setOnTouchListener(touchListener)
        val nextButton = findViewById<ImageButton>(R.id.next_button)
        nextButton.setOnLongClickListener(nextGroupLongListener)
        nextButton.setOnTouchListener(touchListener)

        var seekButton: CombinedClickableImage
        seekButton = findViewById<CombinedClickableImage>(R.id.m20_button)
        seekButton.setRepeatListener(rewindListener, repeatDelta.toLong())
        seekButton.setOnTouchListener(touchListener)
        seekButton = findViewById<CombinedClickableImage>(R.id.p20_button)
        seekButton.setRepeatListener(forwardListener, repeatDelta.toLong())
        seekButton.setOnTouchListener(touchListener)
        seekButton = findViewById<CombinedClickableImage>(R.id.m5_button)
        seekButton.setRepeatListener(rewindListener, repeatDelta.toLong())
        seekButton.setOnTouchListener(touchListener)
        seekButton = findViewById<CombinedClickableImage>(R.id.p5_button)
        seekButton.setRepeatListener(forwardListener, repeatDelta.toLong())
        seekButton.setOnTouchListener(touchListener)

        songTitle = findViewById<TextView>(R.id.detail_title)
        songAlbum = findViewById<TextView>(R.id.detail_album)
        songArtist = findViewById<TextView>(R.id.detail_artist)
        songMime = findViewById<TextView>(R.id.detail_mime)
        warningText = findViewById<TextView>(R.id.warning_text)

        askPermission()

        // permission will be granted in onRequestPermissionsResult callback
        // and service will be started and bind in that function
        playIntent = Intent(this, MusicService::class.java)
        startService(playIntent)
        bindService(playIntent!!, musicConnection, BIND_AUTO_CREATE)

        duration = findViewById<TextView?>(R.id.duration)
        currDuration = findViewById<TextView?>(R.id.curr_duration)
        touchSeekbar = false
        seekbar = findViewById<SeekBar?>(R.id.seek_bar)
        seekbar!!.setOnSeekBarChangeListener(seekBarChangeListener)

        scrollToSongUponStart = false

        vibrator = this.getSystemService(VIBRATOR_SERVICE) as Vibrator

        // tells the OS that the volume buttons should affect the "media" volume when your application is visible
        setVolumeControlStream(AudioManager.STREAM_MUSIC)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceStereoConfigCallback, null)

        // set the color statically for speed (don't know another prettier method)
        Row.Companion.backgroundColor = getColorFromAttr(R.attr.colorRowGroup)

        RowSong.Companion.backgroundSongColor = getColorFromAttr(R.attr.colorRowSong)
        RowSong.Companion.normalSongTextColor = getColorFromAttr(R.attr.colorTextNotPlaying)
        RowSong.Companion.normalSongDurationTextColor = getColorFromAttr(R.attr.colorTextNotPlaying)

        RowGroup.Companion.normalTextColor = getColorFromAttr(R.attr.colorTextNotPlaying)
        RowGroup.Companion.playingTextColor = getColorFromAttr(R.attr.colorTextPlaying)
        RowGroup.Companion.backgroundOverrideColor = getColorFromAttr(R.attr.colorRowGroup2nd)

        val appButton = findViewById<ImageView>(R.id.app_button)
        appButton.setBackgroundResource(R.drawable.ic_actionbar_launcher_anim)
        appAnimation = appButton.getBackground() as AnimationDrawable?

        albumImage = findViewById<ZoomageView?>(R.id.album_image)
        albumImage!!.setVisibility(View.VISIBLE)
        albumImage!!.setOnSingleTapListener(Runnable { this.toggleBiggerCoverArt() })

        detailsBigCoverArt = false

        ratingButtons.add(findViewById<ImageButton?>(R.id.rating_button_1))
        ratingButtons.add(findViewById<ImageButton?>(R.id.rating_button_2))
        ratingButtons.add(findViewById<ImageButton?>(R.id.rating_button_3))
        ratingButtons.add(findViewById<ImageButton?>(R.id.rating_button_4))
        ratingButtons.add(findViewById<ImageButton?>(R.id.rating_button_5))
        details_rating_layout = findViewById<LinearLayout>(R.id.details_rating)

        moreButtonsLayout = findViewById<LinearLayout>(R.id.more_buttons)
        moreButtonsLayout!!.setVisibility(View.GONE)
        setShuffleButton()
        setStereoButton()

        playbackSpeedText = findViewById<NumberPicker>(R.id.playBackSpeed)
        playbackSpeedText!!.setMinValue(1)
        playbackSpeedText!!.setFormatter(NumberPicker.Formatter { v: Int ->
            String.format(
                "%3d%%",
                v
            )
        })
        playbackSpeedText!!.setValue(100)
        playbackSpeedText!!.setMaxValue(200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            playbackSpeedText!!.setTextColor(getResources().getColor(R.color.Blood, getTheme()))
        }
        playbackSpeedText!!.setOnValueChangedListener({ picker: NumberPicker?, _: Int, _: Int ->
            val factor = picker!!.value / 100.0f
            setPlaybackSpeed(factor)
            P.getAndUpdate { p -> p.copy(playbackSpeedFactor = factor) }
        })

        try {
            /*
             * Since 10+ years, Android has a bug where the initial value of a NumberPicker does not
             * use the formatter. This is a hacky fix. See: https://stackoverflow.com/a/19104078
             */
            val method = playbackSpeedText!!.javaClass.getDeclaredMethod(
                "changeValueByOne",
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(playbackSpeedText, true)
        } catch (ignored: Exception) {
        }

        if (P.value.lastSeenChangelogVersion != BuildConfig.VERSION_CODE) {
            showChangelogs()
            P.update { p -> p.copy(lastSeenChangelogVersion = BuildConfig.VERSION_CODE) }
        }
    }

    private fun askPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            askPermissionAndroid11AndAbove()
        } else {
            askPermissionBelowAndroid11()
        }
    }

    private fun askPermissionAndroid11AndAbove() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Toast.makeText(getApplicationContext(), "askPermissionAndroid11AndAbove", Toast.LENGTH_SHORT).show();
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.setData(
                        Uri.parse(
                            String.format(
                                "package:%s",
                                getApplicationContext().getPackageName()
                            )
                        )
                    )
                    startActivityForResult(intent, EXTERNAL_STORAGE_REQUEST_CODE)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, EXTERNAL_STORAGE_REQUEST_CODE)
                }
            }
        }
    }

    fun askPermissionBelowAndroid11() {
        if (ContextCompat.checkSelfPermission(
                getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                || ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                Log.d(
                    "checkSelfPermission",
                    "Permission *_EXTERNAL_STORAGE not granted! Show explanation."
                )
                showWarning()
            }
            Log.i("checkSelfPermission", "Permission *_EXTERNAL_STORAGE not granted! Request it.")
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                EXTERNAL_STORAGE_REQUEST_CODE
            )
        } else {
            Log.d("RequestPermissionResult", "Permission *_EXTERNAL_STORAGE already granted!")
        }
    }

    fun getColorFromAttr(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return ContextCompat.getColor(this, typedValue.resourceId)
    }

    // connect to the service
    private val musicConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("Main", "onServiceConnected")
            if (songView == null) return

            val binder = service as MusicBinder
            musicSrv = binder.service

            rows = musicSrv!!.getRows()
            songAdt = RowsAdapter(this@Main, rows!!)
            songView!!.adapter = songAdt
            songView!!.onItemClickListener =
                AdapterView.OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                    if (!serviceBound) return@OnItemClickListener
                    clickOnRow(position)
                }
            songView!!.onItemLongClickListener =
                AdapterView.OnItemLongClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                    if (!serviceBound) return@OnItemLongClickListener false
                    longClickOnRowEditMode(position)
                    true
                }
            serviceBound = true

            musicSrv!!.stopNotification()
            musicSrv!!.setMainIsVisible(true)

            // listView.getVisiblePosition() is wrong while the listview is not shown.
            // wait a bit that it is visible (should be replace by sth like onXXX)
            (Timer()).schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread(firstScroll)
                }
            }, 100)

            setRepeatButton()
            setMinRatingButton()

            // Associate app to music files (start music from a file browser)
            val intent = getIntent()
            val uri = intent.data
            val mimeType = intent.type
            if (uri != null && !uri.toString().isEmpty()) {
                Log.d("Main", "Receiving intent with uri: $uri, mime: $mimeType")
                rows = musicSrv!!.getRows()
                if (rows!!.setCurrPosFromUri(applicationContext, uri)) {
                    playAlreadySelectedSong()
                }
            }

            setPlaybackSpeed(P.value.playbackSpeedFactor)
            setPlaybackSpeedText()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("Main", "onServiceDisconnected")
            serviceBound = false
        }
    }

    private fun clickOnRow(position: Int) {
        val row = rows!!.get(position)
        if (row != null) {
            if (row.javaClass == RowGroup::class.java) {
                // vibrate when big font chosen
                if (P.value.enlargeText) vibrate()

                rows!!.invertFold(position)
                songAdt!!.notifyDataSetChanged()
            } else {
                vibrate()

                rows!!.selectNearestSong(position)
                musicSrv!!.playSong()
                updatePlayButton()
                disableTrackLooper()
            }
            scrollToSong(position)
            updateRatings()
        }
    }

    private fun startGroupPlayback(row: RowGroup, position: Int) {
        vibrate()

        var offset = 0
        if (P.value.shuffle.randomSongOrder()) {
            offset =
                floor((row.songCount - 1 /* We already are playing the first song in this group */) * Math.random()).toInt()
        }

        if (row.isFolded) {
            rows!!.invertFold(position)
        }
        rows!!.selectNearestSong(position + offset)

        playAlreadySelectedSong()
        updateRatings()
    }

    private fun longClickOnRowEditMode(position: Int) {
        val row = rows!!.get(position)
        if (row != null) {
            if (row.javaClass == RowGroup::class.java) {
                openEditGroupMenu(position, row as RowGroup)
            } else {
                openEditSongMenu(position, row as RowSong)
            }
        }
    }

    private fun playAlreadySelectedSong() {
        musicSrv!!.playSong()
        updatePlayButton()
        disableTrackLooper()
        unfoldAndScrollToCurrSong()
    }

    private fun updateRatings() {
        if (serviceBound && MusicService.enableRating) {
            rows!!.loadRatingsAsync { newRatingLoaded: Boolean ->
                if (newRatingLoaded) {
                    Log.d("Main", "newRatingLoaded")
                    runOnUiThread(Runnable { songAdt!!.notifyDataSetChanged() })
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == EXTERNAL_STORAGE_REQUEST_CODE) { //Toast.makeText(getApplicationContext(), "EXTERNAL_STORAGE_REQUEST_CODE", Toast.LENGTH_SHORT).show();
            // If request is cancelled, the result arrays are empty.
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Main", "Permission READ_EXTERNAL_STORAGE granted")
                if (rows != null) rows!!.reinit()
                if (songAdt != null) songAdt!!.notifyDataSetChanged()
                unfoldAndScrollToCurrSong()
                hideWarning()
            } else {
                Log.e("Main", "Permission READ_EXTERNAL_STORAGE refused!")
                showWarning()
            }
            if (grantResults.size > 1) {
                if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Main", "Permission WRITE_EXTERNAL_STORAGE granted")
                } else {
                    Log.w("Main", "Permission WRITE_EXTERNAL_STORAGE refused!")
                    showWarning()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val SET_RATING_REQUEST_CODE = 1024
        if (requestCode == SET_RATING_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.i("Main", "Permission MANAGE_ALL_FILES_ACCESS_PERMISSION granted")
                } else {
                    Log.w("Main", "Permission MANAGE_ALL_FILES_ACCESS_PERMISSION refused!")
                    showWarning()
                }
            }
        } else if (requestCode == SETTINGS_ACTION) {
            if (resultCode == SettingsFragment.CHANGE_TEXT_SIZE) applyTextSize()
            else if (resultCode == SettingsFragment.CHANGE_THEME) {
                // restart main activity
                finish()
                startActivity(getIntent())
            }
        }
    }

    private fun showWarning() {
        warningText!!.setText(R.string.permission_needed)
        warningLayout!!.visibility = View.VISIBLE
    }

    private fun hideWarning() {
        warningLayout!!.visibility = View.GONE
    }

    private fun showChangelogs() {
        val intent = Intent(this, ChangelogsActivity::class.java)
        startActivity(intent)
    }

    private val seekBarChangeListener
            : SeekBar.OnSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (seekbar != null && seekbar!!.isVisible) {
                setCurrDuration(seekBar.progress.toLong())
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            touchSeekbar = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            val states: Int = PlayerState.Prepared or
                    PlayerState.Started or
                    PlayerState.Paused or
                    PlayerState.PlaybackCompleted
            if (serviceBound && musicSrv!!.isInState(states)) {
                Log.d(
                    "Main",
                    "onStopTrackingTouch setProgress" + msToMinutes(seekBar.progress.toLong())
                )
                seekBar.progress = seekBar.progress
                // valid state : {Prepared, Started, Paused, PlaybackCompleted}
                musicSrv!!.seekTo(seekBar.progress.toLong())
            }

            touchSeekbar = false
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("Main", "onStart")

        restore()

        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                // updateInfo must be run in activity thread
                runOnUiThread(updateInfo)
            }
        }, 10, 500)

        if (serviceBound) {
            // if service not bound stopNotification and setMainIsVisible is called onServiceConnected
            musicSrv!!.stopNotification()
            musicSrv!!.setMainIsVisible(true)
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d("Main", "onResume")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("Main", "onWindowFocusChanged")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Main", "onStop")
        timer!!.cancel()

        if (serviceBound) {
            if (!finishing && musicSrv!!.playingLaunched()) musicSrv!!.startNotification()

            musicSrv!!.setMainIsVisible(false)
        }
    }

    override fun onPause() {
        super.onPause()
        runBlocking { XPreferences.save(applicationContext) }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Main", "onDestroy")

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceStereoConfigCallback)
        stopCloseMoreButtonsTimer()
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }

        if (serviceBound) {
            musicSrv!!.setMainIsVisible(false)
            // stop the service if not playing music
            if (!musicSrv!!.playingLaunched()) {
                musicSrv!!.stopService(playIntent)
            }
            unbindService(musicConnection)
            serviceBound = false
            musicSrv = null
        }

    }


    val updateInfo: Runnable = object : Runnable {
        override fun run() {
            if (!serviceBound || songAdt == null) return

            //Log.d("Main", "updateInfo");
            if (musicSrv!!.getRows().getAndSetFileToOpenFound()) {
                Log.d("Main", "Launching file to open")
                songAdt!!.notifyDataSetChanged()
                playAlreadySelectedSong()
            }

            if (musicSrv!!.getChanged()) {
                Log.d("Main", "updateInfo changed")
                vibrate()
                updatePlayButton()
                if (scrollToSongUponStart) unfoldAndScrollToCurrSong()
            } else {
                if (musicSrv!!.playingStopped()) {
                    stopPlayButton()
                } else if (!touchSeekbar && musicSrv!!.seekFinished) {
                    val currPosMs = musicSrv!!.currentPositionMs
                    //Log.v("Main", "updateInfo setProgress" + RowSong.msToMinutes(currPosMs));
                    // getCurrentPosition {Idle, Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted}
                    seekbar!!.progress = currPosMs.toInt()
                }
            }
        }
    }

    val firstScroll: Runnable = Runnable {
        if (songAdt == null) return@Runnable
        updatePlayButton()
        unfoldAndScrollToCurrSong()
    }


    private fun updatePlayButton() {
        if (!serviceBound || songAdt == null || musicSrv!!.playingStopped()) {
            // MediaPlayer has been destroyed or first start
            stopPlayButton()
        } else {
            openSeekButtons(seekButtonsOpened)
            if (!musicSrv!!.playingPaused()) {
                playButton!!.setImageResource(R.drawable.ic_action_pause)
                playButton!!.setTag(R.drawable.ic_action_pause)
                appAnimation!!.start()
            } else {
                playButton!!.setImageResource(R.drawable.ic_action_play)
                playButton!!.setTag(R.drawable.ic_action_play)
                appAnimation!!.stop()
            }

            val rowSong = rows!!.currSong
            if (rowSong != null) {
                duration!!.setText(msToMinutes(rowSong.durationMs))
                duration!!.setVisibility(TextView.VISIBLE)
                seekbar!!.setMax(rowSong.durationMs.toInt())
                if (!touchSeekbar && musicSrv!!.seekFinished) seekbar!!.setProgress(
                    musicSrv!!.currentPositionMs.toInt()
                )
                seekbar!!.setVisibility(TextView.VISIBLE)
                setCurrDuration(musicSrv!!.currentPositionMs)
            }
        }
        autoOpenCloseDetails()

        songAdt!!.notifyDataSetChanged()
    }

    @SuppressLint("SetTextI18n")
    private fun setCurrDuration(currDurationMs: Long) {
        if (currDuration == null) return
        if (P.value.showRemainingTime) {
            val rowSong = rows!!.currSong
            if (rowSong != null) {
                currDuration!!.text = "- " +
                        msToMinutes(rowSong.durationMs - currDurationMs)
            }
        } else {
            currDuration?.text = msToMinutes(currDurationMs)
        }
    }

    private fun stopPlayButton() {
        duration!!.visibility = TextView.INVISIBLE
        seekbar!!.visibility = TextView.INVISIBLE
        currDuration!!.setText(R.string.app_name)
        playButton!!.setImageResource(R.drawable.ic_action_play)
        playButton!!.tag = R.drawable.ic_action_play
        if (!seekButtonsOpened) posButton!!.setImageDrawable(null)
        appAnimation!!.stop()
    }


    private fun openSeekButtons(open: Boolean) {
        seekButtonsOpened = open
        if (open) {
            posButton!!.setImageResource(R.drawable.ic_action_close_pos)
            seekButtonsLayout!!.visibility = View.VISIBLE
        } else {
            posButton!!.setImageResource(R.drawable.ic_action_open_pos)
            seekButtonsLayout!!.visibility = View.GONE
        }
    }

    fun toggleSeekButtons(view: View?) {
        openSeekButtons(!seekButtonsOpened)
    }


    private fun openDetails(open: Boolean) {
        detailsOpened = open
        if (open) {
            toggleDetailsButton!!.setImageResource(R.drawable.ic_action_close_pos)
            detailsLayout!!.visibility = View.VISIBLE
        } else {
            toggleDetailsButton!!.setImageResource(R.drawable.ic_action_open_pos)
            detailsLayout!!.visibility = View.GONE
        }
    }

    fun toggleDetails(view: View?) {
        openDetails(!detailsOpened)
        detailsToggledFollowAuto = hasCoverArt == detailsOpened
    }

    private fun setCoverArt(rowSongId: Long, bitmap: Bitmap?) {
        runOnUiThread(Runnable {
            Log.d("Main", "setCoverArt rowSongId=" + rowSongId)
            // todo: check id and imageNum ?
            if (bitmap != null) {
                albumImage!!.setImageBitmap(bitmap)
            } else {
                albumImage!!.setImageResource(R.drawable.ic_default_coverart)
            }
        })
    }

    fun setDetails() {
        val rowSong = rows!!.currSong ?: return
        var title = rowSong.title
        val trackNum = rowSong.track
        if (trackNum > 0) title = "$trackNum. $title"
        songTitle!!.text = title

        songArtist!!.text = rowSong.artist

        var album = rowSong.album
        if (rowSong.year > 1000) album = rowSong.year.toString() + " - " + album
        songAlbum!!.text = album

        songMime!!.text = rowSong.mime

        AlbumArtLoader(
            applicationContext,
            rowSong
        ).loadAsync({ rowSongId: Long, bitmap: Bitmap? ->
            this.setCoverArt(
                rowSongId,
                bitmap
            )
        })

        setRatingDetails()

        clearMetadataTable()
        rowSong.loadMetadataAsync({ tags: Tag? ->
            this.showMetadataTable(
                tags!!
            )
        })
    }

    private fun clearMetadataTable() {
        val c = (findViewById<View?>(R.id.metadata_comment) as TextView)
        c.text = ""
    }

    private fun showMetadataTable(tags: Tag) {
        runOnUiThread(Runnable {
            // Comments
            val comments = AtomicReference<String?>("")
            try {
                tags.getFields(FieldKey.COMMENT).forEach(Consumer { line: TagField? ->
                    comments.getAndUpdate(
                        UnaryOperator { c: String? -> c + line + "\n" })
                })
            } catch (ignored: Exception) {
            }

            val c = findViewById<View?>(R.id.metadata_comment) as TextView
            c.text = comments.get()
            c.movementMethod = LinkMovementMethod.getInstance()
        })
    }

    private fun setRatingDetails() {
        if (!serviceBound) return
        if (MusicService.enableRating) {
            val rowSong = rows!!.currSong
            rowSong?.loadRatingAsync{ rating: Int, ratingChanged: Boolean ->
                runOnUiThread { setRatingButtonsDrawable(rating, rating > 0) }
            }
        } else {
            setRatingButtonsDrawable(0, false)
        }
    }

    private fun setRatingButtonsDrawable(rating: Int, highlight: Boolean) {
//        if (rating <= 0) {
//            details_rating_layout.setVisibility(View.INVISIBLE);
//        } else {
        var rating = rating
        if (rating < 0) rating = 0
        details_rating_layout!!.setVisibility(View.VISIBLE)
        for (i in ratingButtons.indices) {
            val star0 = if (highlight) R.drawable.ic_star_0_highlight else R.drawable.ic_star_0
            val star5 = if (highlight) R.drawable.ic_star_5_highlight else R.drawable.ic_star_5
            ratingButtons.get(i)!!.setImageResource(if (i < rating) star5 else star0)
        }
        //        }
    }

    fun autoOpenCloseDetails() {
        if (!serviceBound) {
            return
        }
        val rowSong = rows!!.currSong ?: return
        AlbumArtLoader(applicationContext, rowSong).loadAsync(
            AlbumArtLoader.Callback { rowSongId: Long, bitmap: Bitmap? ->
                hasCoverArt = bitmap != null
                // the concept of detailsToggledFollowAuto (this is a bit not useful && fishy):
                //   - auto mode is enable if details view state (opened or closed) is the same has
                //     auto mode would have done.
                if (detailsToggledFollowAuto) runOnUiThread(Runnable { openDetails(hasCoverArt) })
                if (detailsToggledFollowAuto && !hasCoverArt) {
                    // set details later in order to not disturb details layouts close animation
                    timer!!.schedule(object : TimerTask() {
                        override fun run() {
                            runOnUiThread(Runnable { setDetails() })
                        }
                    }, 500)
                } else {
                    runOnUiThread(Runnable { this.setDetails() })
                }
            })
    }


    fun toggleBiggerCoverArt() {
        detailsBigCoverArt = !detailsBigCoverArt
        applyBiggerCoverArt()
    }

    fun applyBiggerCoverArt() {
        val params = detailsLayout!!.getLayoutParams()
        if (detailsBigCoverArt) {
            // increase cover art size
            params.height = params.height * 2
            detailsLayout!!.setLayoutParams(params)

            // hide text details
            albumImage!!.setLayoutParams(
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT, 0f
                )
            )
        } else {
            // decrease cover art size
            params.height /= 2
            detailsLayout!!.layoutParams = params

            // show text details
            albumImage!!.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f
            )
        }
        albumImage!!.setScaleType(ImageView.ScaleType.FIT_CENTER)
    }

    fun deleteSongFile(song: RowSong) {
        if (song === rows!!.currSong) return
        val songTitle = song.path
        AlertDialog.Builder(this)
            .setTitle(R.string.action_delete_song)
            .setMessage(getString(R.string.action_ask_delete_song, songTitle))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(
                android.R.string.ok,
                DialogInterface.OnClickListener { dialog: DialogInterface?, whichButton: Int ->
                    if (rows!!.deleteSongFile(song)) {
                        songAdt!!.notifyDataSetChanged()
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.action_delete_song_ok, songTitle),
                            Toast.LENGTH_LONG
                        ).show()
                        scrollToCurrSong()
                    } else {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.action_delete_song_nok, songTitle),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    fun ratingClick(view: View?) {
        if (!serviceBound || rows == null) return

        for (i in ratingButtons.indices) {
            if (view === ratingButtons.get(i)) {
                // we cannot unclick the first star, so 0 star means not initialized.
                rows!!.rateCurrSong(i + 1)
                setRatingDetails()
                songAdt!!.notifyDataSetChanged()
            }
        }
    }

    private fun openEditGroupMenu(position: Int, row: RowGroup) {
        val altBld = AlertDialog.Builder(this)
        altBld.setIcon(R.drawable.ic_action_edit)
        altBld.setTitle(
            getString(
                R.string.ic_action_edit_folder,
                cutLongStringAndDots(row.name!!)
            )
        )
        val items = arrayOf<CharSequence?>(
            getString(R.string.action_play),
            getString(R.string.action_rate_group),
            getString(R.string.action_rate_group_overwrite),
            getString(R.string.action_rescan),
        )

        altBld.setItems(
            items,
            DialogInterface.OnClickListener { dialog: DialogInterface?, item: Int ->
                if (musicSrv != null) {
                    when (item) {
                        0 -> startGroupPlayback(row, position)
                        1 -> openRateRowMenu(row.name!!, position, false)
                        2 -> openRateRowMenu(row.name!!, position, true)
                        3 -> rescan(row)
                    }
                }
            })
        val alert = altBld.create()
        alert.show()
    }

    private fun cutLongStringAndDots(str: String): String {
        var str = str
        val dots = "..."
        if (str.length > 40) {
            str = str.substring(0, 40 - dots.length)
            str += dots
        }
        return str
    }

    private fun openRateRowMenu(rowName: String, pos: Int, overwriteRating: Boolean) {
        if (musicSrv == null) return
        val row = rows!!.get(pos) ?: return
        val isRowGroup = row.javaClass == RowGroup::class.java

        val altBld = AlertDialog.Builder(this)
        altBld.setIcon(R.drawable.ic_star_5_highlight)
        altBld.setTitle(
            getString(
                if (isRowGroup) R.string.action_set_rating_folder else R.string.action_set_rating_song,
                cutLongStringAndDots(rowName)
            )
        )
        val items = arrayOf<CharSequence?>(
            "1", "2", "3", "4", "5"
        )
        // show song's current rating
        if (!isRowGroup) {
            val rate = (row as RowSong).rating
            val idx = rate - 1
            if (idx >= 0 && idx < items.size) items[idx] = items[idx]!!.toString() + " <- " + getString(R.string.current_rating_idx)
        }
        altBld.setItems(
            items,
            DialogInterface.OnClickListener { dialog: DialogInterface?, itemPos: Int ->
                rows!!.rateSongs(
                    pos, itemPos + 1, overwriteRating,
                    RateGroupCallbackInterface { nbChanged: Int, errorMsg: String? ->
                        runOnUiThread(Runnable {
                            if (errorMsg!!.isEmpty()) {
                                if (nbChanged > 0) {
                                    setRatingDetails()
                                    songAdt!!.notifyDataSetChanged()
                                }

                                if (isRowGroup) Toast.makeText(
                                    getApplicationContext(),
                                    getString(
                                        R.string.songs_have_been_rated,
                                        nbChanged
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(getApplicationContext(), errorMsg, Toast.LENGTH_LONG)
                                    .show()
                            }
                        })
                    })
            })
        val alert = altBld.create()
        alert.show()
    }

    private fun openEditSongMenu(position: Int, row: RowSong) {
        val altBld = AlertDialog.Builder(this)
        altBld.setIcon(R.drawable.ic_action_edit)
        altBld.setTitle(
            getString(
                R.string.ic_action_edit_song,
                cutLongStringAndDots(row.title)
            )
        )
        val alert = AtomicReference<AlertDialog?>(null)
        val list = ArrayList<String?>()
        val comment = AtomicReference<String?>("")

        val updateAndShowItems = Runnable {
            val youtubeVideoURL: String? = extractYouTubeUrl(comment.get())
            list.clear()
            list.add(getString(R.string.action_play))
            list.add(getString(R.string.action_rate_song))
            list.add(getString(R.string.show_song_details))
            list.add(getString(R.string.action_genius_lyrics))
            if (youtubeVideoURL!!.isEmpty()) {
                list.add(getString(R.string.action_youtube_search))
            } else {
                list.add(getString(R.string.action_youtube_open))
            }

            //getString(R.string.add_to_playlist),
            if (row !== rows!!.currSong) list.add(getString(R.string.action_delete_song))

            altBld.setItems(
                list.toTypedArray<CharSequence?>(),
                DialogInterface.OnClickListener { dialog: DialogInterface?, item: Int ->
                    if (musicSrv != null) {
                        when (item) {
                            0 -> clickOnRow(position)
                            1 -> openRateRowMenu(row.title, position, true)
                            2 -> showPopupSongInfo(row)
                            3 -> openGeniusLyrics(row)
                            4 -> if (youtubeVideoURL.isEmpty()) {
                                openYouTubeSearch(row)
                            } else {
                                try {
                                    val browserIntent =
                                        Intent(Intent.ACTION_VIEW, Uri.parse(youtubeVideoURL))
                                    startActivity(browserIntent)
                                } catch (e: Exception) {
                                    Log.e(
                                        "sicmu",
                                        "Error while parsing user-provided metadata youtube video URL: " + e
                                    )
                                }
                            }

                            5 -> deleteSongFile(row)
                        }
                    }
                })

            if (alert.get() != null) {
                alert.get()!!.hide()
            }
            val newAlert = altBld.create()
            newAlert.show()
            alert.set(newAlert)
        }

        row.loadMetadataAsync(LoadMetadataCallbackInterface { t: Tag? ->
            t!!.getFields(FieldKey.COMMENT).forEach(Consumer { line: TagField? ->
                comment.getAndUpdate(
                    UnaryOperator { c: String? -> c + line + "\n" })
            })
            updateAndShowItems.run()
        })

        updateAndShowItems.run()
    }

    private fun openGeniusLyrics(song: RowSong) {
        val searchTerm = URLEncoder.encode(song.searchTerm)

        try {
            val browserIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://genius.com/search?q=" + searchTerm))
            startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e("sicmu", "Error while creating genius.com URL: " + e)
        }
    }

    private fun openYouTubeSearch(song: RowSong) {
        val searchTerm = URLEncoder.encode(song.searchTerm)

        try {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + searchTerm)
            )
            startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e("sicmu", "Error while creating youtube.com search URL: " + e)
        }
    }

    private fun showPopupSongInfo(rowSong: RowSong) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_song_details, null)

        val width = LinearLayout.LayoutParams.WRAP_CONTENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val focusable = true // lets taps outside the popup also dismiss it
        val popupWindow = PopupWindow(popupView, width, height, focusable)

        popupWindow.showAtLocation(
            findViewById<View?>(R.id.main_layout),
            Gravity.CENTER,
            0,
            0
        )
        (popupView.findViewById<View?>(R.id.detail_artist) as TextView).setText(
            getString(R.string.popup_song_artist, rowSong.artist)
        )
        (popupView.findViewById<View?>(R.id.detail_album) as TextView).setText(
            getString(R.string.popup_song_album, rowSong.album)
        )
        (popupView.findViewById<View?>(R.id.detail_title) as TextView).setText(
            getString(R.string.popup_song_title, rowSong.title)
        )
        (popupView.findViewById<View?>(R.id.detail_track) as TextView).setText(
            getString(R.string.popup_song_track, rowSong.track)
        )
        (popupView.findViewById<View?>(R.id.detail_year) as TextView).setText(
            getString(R.string.popup_song_year, rowSong.year)
        )
        (popupView.findViewById<View?>(R.id.detail_rating) as TextView).setText(
            getString(R.string.popup_song_rating, rowSong.rating)
        )
        (popupView.findViewById<View?>(R.id.detail_mime) as TextView).setText(
            getString(R.string.popup_song_mime, rowSong.mime)
        )
        (popupView.findViewById<View?>(R.id.detail_path) as TextView).setText(
            getString(R.string.popup_song_path, rowSong.path)
        )
        popupView.setOnTouchListener(View.OnTouchListener { view: View?, event: MotionEvent? ->
            popupWindow.dismiss()
            true
        })
    }

    private fun rescan(rowGroup: RowGroup) {
        Toast.makeText(
            getApplicationContext(),
            getString(R.string.start_rescan) + rowGroup.path,
            Toast.LENGTH_SHORT
        ).show()
        MediaScanner.scanMediaFolder(
            getApplicationContext(),
            rowGroup.path,
            MediaScannerConnection.OnScanCompletedListener { path: String?, uri: Uri? ->
                runOnUiThread(
                    Runnable {
                        Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.rescanned) + path,
                            Toast.LENGTH_LONG
                        ).show()
                        if (rows != null) rows!!.reinit()
                        unfoldAndScrollToCurrSong()
                    })
            }
        )
    }


    private fun openRepeatMenu() {
        val altBld = AlertDialog.Builder(this)
        altBld.setIcon(this.repeatResId)
        altBld.setTitle(getString(R.string.action_repeat_title))
        val items = arrayOf<CharSequence?>(
            getString(R.string.action_repeat_all),
            getString(R.string.action_repeat_group),
            getString(R.string.action_repeat_one),
            getString(R.string.action_repeat_not),
            getString(R.string.action_stop_at_end_of_track),
        )
        val checkedItem: Int = if (P.value.repeatMode == RepeatMode.REPEAT_ALL) 0
        else if (P.value.repeatMode == RepeatMode.REPEAT_GROUP) 1
        else if (P.value.repeatMode == RepeatMode.REPEAT_ONE) 2
        else if (P.value.repeatMode == RepeatMode.STOP_AT_END_OF_FOLDER) 3
        else 4

        altBld.setSingleChoiceItems(
            items,
            checkedItem,
            { dialog: DialogInterface?, item: Int ->
                if (musicSrv != null) {
                    val newMode = when (item) {
                        0 -> (RepeatMode.REPEAT_ALL)
                        1 -> (RepeatMode.REPEAT_GROUP)
                        2 -> (RepeatMode.REPEAT_ONE)
                        3 -> (RepeatMode.STOP_AT_END_OF_FOLDER)
                        4 -> (RepeatMode.STOP_AT_END_OF_TRACK)
                        else -> return@setSingleChoiceItems
                    }
                    P.update { p -> p.copy(repeatMode = newMode) }
                    dialog!!.dismiss() // dismiss the alertbox after chose option
                    setRepeatButton()
                }
            })
        val alert = altBld.create()
        alert.show()
    }

    private fun openRatingMenu() {
        if (musicSrv == null) return

        val altBld = AlertDialog.Builder(this)
        altBld.setIcon(this.minRatingResId)
        altBld.setTitle(getString(R.string.action_min_rating))
        val items = arrayOf<CharSequence?>(
            "1", "2", "3", "4", "5"
        )

        altBld.setSingleChoiceItems(
            items, musicSrv!!.getMinRating() - 1,
            DialogInterface.OnClickListener { dialog: DialogInterface?, item: Int ->
                if (musicSrv != null) {
                    musicSrv!!.setMinRating(item + 1)
                    setMinRatingButton()
                    dialog!!.dismiss() // dismiss the alertbox after chose option
                }
            })
        val alert = altBld.create()
        alert.show()
    }

    fun openSearchDialog(view: View) {
        if (musicSrv == null) return

        // Init dialog
        val altBld = AlertDialog.Builder(this)
        altBld.setIcon(R.drawable.ic_action_search)
        altBld.setTitle(getString(R.string.action_search))

        val prefs = getPreferences(MODE_PRIVATE)

        // Set up text box
        val input = EditText(this)
        val showKeyboard = Runnable {
            input.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        input.setInputType(InputType.TYPE_CLASS_TEXT)
        input.setFocusable(true)
        input.setFocusableInTouchMode(true)
        input.setCompoundDrawablesWithIntrinsicBounds(
            0,
            0,
            R.drawable.ic_close,
            0
        )
        input.setOnTouchListener(View.OnTouchListener { v: View?, event: MotionEvent? ->
            showKeyboard.run()
            if (event!!.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (input.getRight() - input.getCompoundDrawables()[2].getBounds()
                        .width())
                ) {
                    input.setText("")
                    return@OnTouchListener true
                }
            }
            false
        })
        input.setText(prefs.getString("search_query", ""))
        altBld.setView(input)

        val ctx: Context = this

        // Set up the search button
        altBld.setPositiveButton(
            "OK",
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                val query = input.getText().toString()
                // Save query for quick access later
                prefs.edit().putString("search_query", query).apply()

                val row = rows!!.getNextSongByKeyword(query)
                if (row == null) {
                    Snackbar.make(
                        view,
                        R.string.search_unsuccessful,
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                    return@OnClickListener
                }

                // clickOnRow(...) only works using folded indexes, so we have
                // to unfold the parent groups first, and then play the song itself

                // Collect parent elements, then unfold them in reverse order
                // (topmost group/folder -> deepest group/folder)
                val parents = ArrayList<RowGroup>()
                var group = row.parent as RowGroup?
                while (group != null) {
                    parents.add(group)
                    group = group.parent as RowGroup?
                }
                for (i in parents.indices.reversed()) {
                    val parent = parents.get(i)
                    if (parent.isFolded) clickOnRow(rows!!.getFoldedIndex(parent))
                }

                // Don't click (=close) unfolded groups! Just scroll to them.
                if (row is RowGroup && !row.isFolded) {
                    scrollToSong(rows!!.getFoldedIndex(row))
                } else { // Click it! (i.e. open a group or play a song)
                    clickOnRow(rows!!.getFoldedIndex(row))
                }
            })

        val dialog = altBld.create()
        val win = dialog.window
        win?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener { e: DialogInterface? -> showKeyboard.run() }
        dialog.show()
    }

    private val repeatResId: Int
        get() {
            val res: Int
            when (P.value.repeatMode) {
                RepeatMode.REPEAT_ONE -> res =
                    R.drawable.ic_menu_repeat_one

                RepeatMode.REPEAT_GROUP -> res =
                    R.drawable.ic_menu_repeat_group

                RepeatMode.REPEAT_ALL -> res =
                    R.drawable.ic_menu_repeat_all

                RepeatMode.STOP_AT_END_OF_FOLDER -> res =
                    R.drawable.ic_menu_repeat_not

                else -> res = R.drawable.ic_menu_stop_at_end_of_track
            }
            return res
        }

    private val minRatingResId: Int
        get() {
            val res: Int
            when (musicSrv!!.getMinRating()) {
                5 -> res = R.drawable.ic_star_5_highlight
                4 -> res = R.drawable.ic_star_4_highlight
                3 -> res = R.drawable.ic_star_3_highlight
                2 -> res = R.drawable.ic_star_2_highlight
                else -> res = R.drawable.ic_star_1_highlight
            }
            return res
        }

    private val shuffleResId: Int
        get() {
            when (P.value.shuffle) {
                ShuffleMode.SEQUENTIAL -> return R.drawable.ic_menu_no_shuffle
                ShuffleMode.RANDOM -> return R.drawable.ic_menu_shuffle
                ShuffleMode.RADIO -> return R.drawable.ic_menu_shuffle_radio
            }
            throw IllegalStateException("Shuffle mode is invalid")
        }

    private fun setRepeatButton() {
        val img = findViewById<ImageView>(R.id.repeat_button)
        img.setImageResource(this.repeatResId)
    }


    /**  Sets the stereo button image to reflect the Mono/Stereo setting */
    private fun setStereoButton() {
        val stereo = P.value.stereo.getOrDefault(AudioHardwareID.get(this), true)
        val btn = findViewById<ImageButton>(R.id.stereo_button)
        if (stereo) {
            btn.setImageResource(R.drawable.ic_stereo)
        } else {
            btn.setImageResource(R.drawable.ic_mono)
        }
    }

    /**  This contacts the music service to apply the stereo setting */
    private fun applyStereo() {
        if (musicSrv == null) {
            return
        }
        musicSrv!!.applyStereo(P.value.stereo.getOrDefault(AudioHardwareID.get(this), true))
    }

    private fun setShuffleButton() {
        val shuffleButton = findViewById<ImageButton>(R.id.shuffle_button)
        shuffleButton.setImageResource(this.shuffleResId)
    }

    private fun setMinRatingButton() {
        val img = findViewById<ImageView>(R.id.rating_button)
        img.setImageResource(this.minRatingResId)
    }

    fun fold() {
        if (musicSrv != null) {
            rows!!.fold()
            songAdt!!.notifyDataSetChanged()
            unfoldAndScrollToCurrSong()
        }
    }

    fun unfold() {
        if (musicSrv != null) {
            rows!!.unfold()
            songAdt!!.notifyDataSetChanged()
            scrollToCurrSong()
        }
    }

    fun playOrPause(view: View?) {
        if (!serviceBound) return

        if (musicSrv!!.isInState(PlayerState.Companion.Started)) {
            // valid state {Started, Paused, PlaybackCompleted}
            // if the player is between idle and prepared state, it will not be paused!
            musicSrv!!.pause()
        } else {
            if (musicSrv!!.isInState(PlayerState.Companion.Paused)) {
                // previously paused. Valid state {Prepared, Started, Paused, PlaybackCompleted}
                musicSrv!!.start()
            } else {
                musicSrv!!.playSong()
            }
        }

        updatePlayButton()
    }

    fun playNext(view: View?) {
        if (!serviceBound) return

        musicSrv!!.playNext()
        updatePlayButton()
        disableTrackLooper()
        if (scrollToSongUponStart) unfoldAndScrollToCurrSong()
    }

    fun playPrev(view: View?) {
        if (!serviceBound) return

        musicSrv!!.playPrev()
        updatePlayButton()
        disableTrackLooper()
        if (scrollToSongUponStart) unfoldAndScrollToCurrSong()
    }

    fun seek(view: View) {
        if (!serviceBound) return
        var newPosMs = musicSrv!!.currentPositionMs
        val id = view.getId()
        if (id == R.id.m5_button) {
            newPosMs -= (5 * 1000).toLong()
        } else if (id == R.id.p5_button) {
            newPosMs += (5 * 1000).toLong()
        } else if (id == R.id.m20_button || id == R.id.m20_text) {
            newPosMs -= (20 * 1000).toLong()
        } else if (id == R.id.p20_button || id == R.id.p20_text) {
            newPosMs += (20 * 1000).toLong()
        }

        newPosMs = if (newPosMs < 0) 0 else newPosMs

        if (newPosMs >= musicSrv!!.durationMs) playNext(null)
        else musicSrv!!.seekTo(newPosMs)
    }

    private val trackLooperDisabledVal: Long = -1
    private var trackLooperAPosMs = trackLooperDisabledVal
    private var trackLooperBPosMs = trackLooperDisabledVal
    fun trackLooperClick(view: View?) {
        if (!serviceBound) return
        val trackLooperBtn = findViewById<ImageButton>(R.id.track_looper_button)
        if (trackLooperAPosMs == trackLooperDisabledVal) {
            trackLooperAPosMs = musicSrv!!.currentPositionMs
            trackLooperBtn.setImageResource(R.drawable.ic_track_looper_a)
        } else if (trackLooperBPosMs == trackLooperDisabledVal) {
            trackLooperBPosMs = musicSrv!!.currentPositionMs
            musicSrv!!.enableTrackLooper(trackLooperAPosMs, trackLooperBPosMs)
            trackLooperBtn.setImageResource(R.drawable.ic_track_looper_ab)
        } else {
            disableTrackLooper()
        }
    }

    fun disableTrackLooper() {
        trackLooperAPosMs = trackLooperDisabledVal
        trackLooperBPosMs = trackLooperDisabledVal
        if (serviceBound) musicSrv!!.disableTrackLooper()
        val trackLooperBtn = findViewById<ImageButton>(R.id.track_looper_button)
        trackLooperBtn.setImageResource(R.drawable.ic_track_looper)
    }

    private fun setPlaybackSpeed(v: Float) {
        if (serviceBound) {
            musicSrv!!.setPlaybackSpeed(v)
        }
    }

    private fun setPlaybackSpeedText() {
        if (serviceBound) {
            playbackSpeedText!!.setValue((musicSrv!!.getPlaybackSpeed() * 100f).toInt())
        }
    }

    private val touchListener = View.OnTouchListener { v: View?, event: MotionEvent? ->
        if (event!!.getAction() == MotionEvent.ACTION_DOWN) {
            vibrate()
        }
        false
    }

    private val gotoSongLongListener = View.OnLongClickListener { v: View? ->
        fold()
        true
    }

    private val nextGroupLongListener: View.OnLongClickListener =
        object : View.OnLongClickListener {
            override fun onLongClick(v: View?): Boolean {
                if (!serviceBound) return false

                musicSrv!!.playNextGroup()
                updatePlayButton()
                disableTrackLooper()
                if (scrollToSongUponStart) unfoldAndScrollToCurrSong()

                return true
            }
        }

    private val prevGroupLongListener: View.OnLongClickListener =
        object : View.OnLongClickListener {
            override fun onLongClick(v: View?): Boolean {
                if (!serviceBound) return false

                musicSrv!!.playPrevGroup()
                updatePlayButton()
                disableTrackLooper()
                if (scrollToSongUponStart) unfoldAndScrollToCurrSong()

                return true
            }
        }

    private val rewindListener: RepeatListener = object : RepeatListener {
        /**
         * This method will be called repeatedly at roughly the interval
         * specified in setRepeatListener(), for as long as the button
         * is pressed.
         * 
         * @param view           The button as a View.
         * @param duration    The number of milliseconds the button has been pressed so far.
         * @param repeatcount The number of previous calls in this sequence.
         * If this is going to be the last call in this sequence (i.e. the user
         * just stopped pressing the button), the value will be -1.
         */
        override fun onRepeat(view: View?, duration: Long, repeatcount: Int) {
            Log.d("Main", "-- repeatcount: $repeatcount duration: $duration")
            if (view == null || repeatcount <= 0) return

            var newPosMs = musicSrv!!.currentPositionMs - getSeekOffsetSec(view, duration)
            Log.d(
                "Main",
                "<-- currpos: " + musicSrv!!.currentPositionMs + " seekto: " + newPosMs
            )
            newPosMs = if (newPosMs < 0) 0 else newPosMs
            musicSrv!!.seekTo(newPosMs)
        }
    }

    private fun getSeekOffsetSec(view: View, duration: Long): Long {
        var offsetMs: Long = 0
        val id = view.getId()
        if (id == R.id.m5_button || id == R.id.p5_button) {
            offsetMs = 5000
        } else if (id == R.id.m20_button || id == R.id.m20_text || id == R.id.p20_button || id == R.id.p20_text) {
            if (duration < 5000) {
                // seek at 10x speed for the first 5 seconds
                offsetMs = duration * 10
            } else {
                // seek at 40x after that
                offsetMs = 50000 + (duration - 5000) * 40
            }
        }
        return offsetMs
    }

    private val forwardListener: RepeatListener = object : RepeatListener {
        override fun onRepeat(view: View?, duration: Long, repeatcount: Int) {
            Log.d("Main", "-- repeatcount: $repeatcount duration: $duration")

            if (view == null || repeatcount <= 0) return

            val newPosMs = musicSrv!!.currentPositionMs + getSeekOffsetSec(view, duration)
            Log.d(
                "Main",
                "--> currpos: " + musicSrv!!.currentPositionMs + " seekto: " + newPosMs
            )
            if (newPosMs >= musicSrv!!.durationMs) playNext(null)
            else musicSrv!!.seekTo(newPosMs)
        }
    }

    fun gotoCurrSong(view: View?) {
        unfoldAndScrollToCurrSong()
    }

    fun toggleMoreButtons(view: View?) {
        //ImageButton more_button = findViewById(R.id.more_button);
        if (this.isEditModeEnabled) {
            stopCloseMoreButtonsTimer()

            moreButtonsLayout!!.visibility = View.GONE
            //more_button.setImageResource(R.drawable.ic_action_note);
        } else {
            moreButtonsLayout!!.visibility = View.VISIBLE

            startCloseMoreButtonsTimer()
            //more_button.setImageResource(R.drawable.ic_action_edit);
        }
    }

    private val isEditModeEnabled: Boolean
        get() = moreButtonsLayout!!.isVisible

    private fun startCloseMoreButtonsTimer() {
        stopCloseMoreButtonsTimer()

        closeMoreButtonsTimer = Timer()
        closeMoreButtonsTimer!!.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread(Runnable { moreButtonsLayout!!.setVisibility(View.GONE) })
            }
        }, (16 * 1000).toLong())
    }

    private fun stopCloseMoreButtonsTimer() {
        if (closeMoreButtonsTimer != null) {
            closeMoreButtonsTimer!!.cancel()
            closeMoreButtonsTimer = null
        }
    }


    private val SETTINGS_ACTION = 1
    fun openSettings(view: View?) {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivityForResult(intent, SETTINGS_ACTION)
        startCloseMoreButtonsTimer()
    }

    fun openRepeat(view: View?) {
        openRepeatMenu()
        startCloseMoreButtonsTimer()
    }

    fun changeShuffle(view: View) {
        lateinit var appliedShuffle: ShuffleMode
        P.update { p ->
            val newShuffle = p.shuffle.next()
            appliedShuffle = newShuffle
            p.copy(shuffle = newShuffle)
        }
        setShuffleButton()
        appliedShuffle.showExplainSnackbar(view)
        startCloseMoreButtonsTimer()
    }

    fun toggleStereo(view: View) {
        val hid = AudioHardwareID.get(this)
        var appliedMode = true
        P.update { p ->
            val newMode = !p.stereo.getOrDefault(hid, true)
            val newMap = p.stereo.put(hid, newMode)
            appliedMode = newMode
            p.copy(stereo = newMap)
        }

        setStereoButton()
        applyStereo()

        val toastText = if (appliedMode) {
            R.string.settings_stereo_on
        } else {
            R.string.settings_stereo_off
        }
        Snackbar.make(view, toastText, BaseTransientBottomBar.LENGTH_SHORT).show()
    }

    fun openMinRating(view: View?) {
        openRatingMenu()
        startCloseMoreButtonsTimer()
    }

    fun unfoldAndScrollToCurrSong() {
        if (rows == null || songAdt == null) return
        if (rows!!.unfoldCurrPos()) songAdt!!.notifyDataSetChanged()
        scrollToSong(rows!!.currPosFolded)
        updateRatings()
    }

    fun scrollToCurrSong() {
        scrollToSong(rows!!.currPosFolded)
    }

    // this method could be improved, code is a bit obscure :-)
    fun scrollToSong(gotoSong: Int) {
        var gotoSong = gotoSong
        if (songView == null) return
        Log.d("Main", "scrollToSong getCurrPos:$gotoSong")

        if (rows!!.size() == 0 || gotoSong < 0 || gotoSong >= rows!!.size()) return

        val first = songView!!.firstVisiblePosition
        var last = songView!!.lastVisiblePosition
        var nbRow = last - first
        // on ListView startup getVisiblePosition gives strange result
        if (nbRow < 0) {
            nbRow = 1
            last = first + 1
        }
        Log.d("Main", "scrollToSong first: $first last: $last nbRow: $nbRow")

        // to show a bit of songItems before or after the cur song
        var showAroundTop = nbRow / 5
        showAroundTop = max(showAroundTop, 1)
        // show more song after the gotoSong
        var showAroundBottom = nbRow / 2
        showAroundBottom = max(showAroundBottom, 1)
        Log.d(
            "Main",
            "scrollToSong showAroundTop: " + showAroundTop + " showAroundBottom: " + showAroundBottom
        )


        // how far from top or bottom border the song is
        var offset = 0
        if (gotoSong > last) offset = gotoSong - last
        if (gotoSong < first) offset = first - gotoSong

        // deactivate smooth if too far
        val smoothMaxOffset = 50
        if (offset > smoothMaxOffset) {
            // setSelection set position at top of the screen
            gotoSong -= showAroundTop
            if (gotoSong < 0) gotoSong = 0
            songView!!.setSelection(gotoSong)
        } else {
            // smoothScrollToPosition only make position visible
            if (gotoSong + showAroundBottom >= last) {
                gotoSong += showAroundBottom
                if (gotoSong >= rows!!.size()) gotoSong = rows!!.size() - 1
            } else {
                gotoSong -= showAroundTop
                if (gotoSong < 0) gotoSong = 0
            }
            songView!!.smoothScrollToPosition(gotoSong)
        }

        Log.d("Main", "scrollToSong position: $gotoSong")
    }

    fun applyTextSize() {
        val textSize = if (P.value.enlargeText) P.value.textSizeBig else P.value.textSizeNormal

        RowSong.textSize = textSize
        RowGroup.textSize = (textSize * P.value.rowGroupTextSizeRatio).toInt()
        if (songAdt != null) songAdt!!.notifyDataSetChanged()
    }

    private fun restore() {
        scrollToSongUponStart = P.value.scrollToSongUponStart
        applyTextSize()
    }

    private fun vibrate() {
        if (P.value.vibrate) vibrator!!.vibrate(20)
    }

    /** This callback is used for audio channel config. Each different set of audio output
     * devices generates a hardware ID, each having a stereo config. This allows us to
     * have different configs, for instance, for our AirPods and our Phone speaker. */
    private val audioDeviceStereoConfigCallback: AudioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo?>?) {
                setStereoButton()
                applyStereo()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo?>?) {
                setStereoButton()
                applyStereo()
            }
        }

    companion object {
        /**
         * Extracts a YouTube URL from arbitrary text.
         * Supports various YouTube URL formats.
         * 
         * @param text The text to search
         * @return The found YouTube URL or an empty string
         * @author Claude Sonnet 4.5
         */
        private fun extractYouTubeUrl(text: String?): String? {
            if (text.isNullOrEmpty()) {
                return ""
            }

            // Regex pattern for various YouTube URL formats:
            // - https://www.youtube.com/watch?v=VIDEO_ID
            // - https://youtu.be/VIDEO_ID
            // - https://m.youtube.com/watch?v=VIDEO_ID
            // - http variants
            val pattern =
                "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})(?:[&?][^\\s]*)?"

            val regexPattern = Pattern.compile(pattern)
            val matcher = regexPattern.matcher(text)

            if (matcher.find()) {
                // Returns the complete found URL
                return matcher.group(0)
            }

            return ""
        }
    }
}

