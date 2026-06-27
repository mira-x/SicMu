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
package xyz.mordorx.sicmu.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.PositionInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.mordorx.sicmu.Main
import xyz.mordorx.sicmu.MediaButtonIntentReceiver
import xyz.mordorx.sicmu.R
import xyz.mordorx.sicmu.data.AlbumArtLoader
import xyz.mordorx.sicmu.data.AudioHardwareID
import xyz.mordorx.sicmu.data.RowSong.Companion.msToMinutes
import xyz.mordorx.sicmu.data.Rows
import xyz.mordorx.sicmu.data.SongDatabase
import xyz.mordorx.sicmu.data.XPreferences.Companion.P
import xyz.mordorx.sicmu.data.XRows
import kotlin.math.sqrt

@UnstableApi
class MusicService : LifecycleService(), AudioManager.OnAudioFocusChangeListener, SensorEventListener {
    private var player: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mergeAudioProcessor: MergeAudioProcessor? = null

    //private MediaNotificationManager mediaNotificationManager;
    private var mediaSession: MediaSessionCompat? = null
    private var rows: Rows? = null

    /** seek to last song pos on startup in milliseconds.
     * if -1: disabled (do not seek to on startup) */
    private var savedSongPos: Long = 0

    /** save song pos id to avoid restoring song pos if not same track */
    private var savedSongPosId: Long = 0

    /** need for focus */
    private var wasPlaying = false

    /** sth happened and the Main do not know it: a song has finish to play, another app gain focus, ... */
    private var changed = false

    /** useful only for buggy android seek */
    private var seekPosMsBug: Long = 0

    /** a notification has been launched */
    private var foreground = false
    private var mainIsVisible = false
    fun setMainIsVisible(visible: Boolean) {
        mainIsVisible = visible
    }

    private val musicBind: IBinder = MusicBinder()

    private var remoteControlResponder: ComponentName? = null
    private var audioManager: AudioManager? = null

    var noisyReceiverFilter: IntentFilter? = null

    /** current state of the MediaPlayer */
    private var state: PlayerState? = null

    private var db: SongDatabase? = null

    /** set to false if seekTo() has been called but the seek is still not done */
    var seekFinished: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackLooperRunnable: Runnable = object : Runnable {
        override fun run() {
            seekTo(trackLooperAPosMs)
        }
    }

    private val sleepTimerRunnable = Runnable {
        pause()
        setChanged()
        stopSleepTimer()
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastUpdate: Long = 0
    private var enableShake = false
    private var shakeThreshold = 0f
    private var playbackSpeed = 1.0f
    private var accelLast = 0.0
    private var accelCurrent = 0.0
    private var accel = 0.0

    /** used for handling playback state when media session actions occur. */
    private val mMediaSessionCallback: MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                handleCommand(CMDPLAY)
            }

            override fun onPause() {
                super.onPause()
                handleCommand(CMDPAUSE)
            }

            override fun onStop() {
                super.onStop()
                handleCommand(CMDSTOP)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                handleCommand(CMDNEXT)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                handleCommand(CMDPREVIOUS)
            }

            override fun onSeekTo(posMs: Long) {
                super.onSeekTo(posMs)
                seekTo(posMs.toInt().toLong())
            }
        }

    private fun initMediaSession() {
        val mediaButtonReceiver =
            ComponentName(getApplicationContext(), MediaButtonReceiver::class.java)
        mediaSession =
            MediaSessionCompat(getApplicationContext(), MEDIA_SESSION_TAG, mediaButtonReceiver, null)

        mediaSession!!.setCallback(mMediaSessionCallback)

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setClass(this, MediaButtonReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0,
            mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession!!.setMediaButtonReceiver(pendingIntent)
    }

    private fun updateMediaPlaybackState() {
        if (mediaSession == null) return

        val isPlaying = playingLaunched()
        rows?.isPlaying = isPlaying
        val currPosMs = this.currentPositionMs
        val stateBuilder =
            PlaybackStateCompat.Builder()
                .setActions(MEDIA_SESSION_ACTIONS)
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    currPosMs,
                    playbackSpeed
                )
        mediaSession!!.setPlaybackState(stateBuilder.build())
    }

    private fun updateMediaSessionMetadata() {
        if (mediaSession == null) return

        val rowSong = rows!!.currSong
        if (rowSong != null) {
            AlbumArtLoader(applicationContext, rowSong).loadAsync(
                { rowSongId: Long, bitmap: Bitmap? ->
                    // albumbmp will be in cache, so don't bother to pass bitmap param to getMediaMetadata
                    mediaSession!!.setMetadata(rowSong.getMediaMetadata(applicationContext))
                })
        }
    }

    private fun initNoisyReceiver() {
        if (noisyReceiverFilter == null) {
            noisyReceiverFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            // Handles headphones coming unplugged. cannot be done through a manifest receiver
            registerReceiver(noisyReceiver, noisyReceiverFilter)
        }
    }

    private fun unregisterNoisyReceiver() {
        if (noisyReceiverFilter != null) {
            unregisterReceiver(noisyReceiver)
            noisyReceiverFilter = null
        }
    }

    private val noisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleCommand(CMDPAUSE)
        }
    }

    fun getRows(): Rows {
        return rows!!
    }

    @Synchronized
    fun getChanged(): Boolean {
        val hasChanged = changed
        changed = false
        return hasChanged
    }

    @Synchronized
    fun setChanged() {
        changed = true
    }

    /*** SERVICE  */
    override fun onCreate() {
        Log.d("MusicService", "onCreate()")
        super.onCreate()
        createNotificationChannel()
        XRows.appContext = applicationContext
        XRows.initRowsFromMediaStore()

        state = PlayerState()

        changed = false
        seekFinished = true
        seekPosMsBug = -1
        wasPlaying = false

        player = null
        remoteControlResponder = null
        audioManager = null

        db = SongDatabase.Companion.init(applicationContext)
        // try sync if sth failed in the previous SicMu session
        db!!.synchronizeRatingsAsync()

        rows = Rows(applicationContext, contentResolver, db!!.songDAO!!)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xyz.mordorx.sicmu:MusicService")

        restore()

        lifecycleScope.launch {
            merge(P, AudioHardwareID.currentId).collect {
                val s = P.value.stereo.getOrDefault(AudioHardwareID.currentId.value, true)
                mergeAudioProcessor?.isStereo = s
            }
        }

        remoteControlResponder =
            ComponentName(packageName, MediaButtonIntentReceiver::class.java.name)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager?
        audioManager!!.registerMediaButtonEventReceiver(remoteControlResponder)
        foreground = false
        mainIsVisible = false
        mergeAudioProcessor = MergeAudioProcessor()
        mergeAudioProcessor!!.isStereo = P.value.stereo.getOrDefault(AudioHardwareID.currentId.value, true)
    }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return musicBind
    }


    override fun onDestroy() {
        Log.d("MusicService", "onDestroy")
        super.onDestroy()
        rows!!.terminate()
        rows!!.save()
        stopSleepTimer()
        unregisterNoisyReceiver()
        if (sensorManager != null) {
            sensorManager!!.unregisterListener(this)
        }
        releaseAudio()
        db!!.close()

        audioManager!!.unregisterMediaButtonEventReceiver(
            remoteControlResponder
        )
    }

    /*** PLAYER  */
    /**
     * This gets the ExoPlayer, and creates one if neccessary. It also creates an AudioManager.
     * @return The ExoPlayer, fully initialized
     */
    private fun getPlayer(): ExoPlayer? {
        seekPosMsBug = -1

        if (player == null) {
            Log.d("MusicService", "create player")

            initMediaSession()
            initNoisyReceiver()

            // This boilerplate is necessary in order to allow use of our custom AudioProcessor
            val renderersFactory: DefaultRenderersFactory? =
                object : DefaultRenderersFactory(this) {
                    override fun buildAudioSink(
                        context: Context,
                        enableFloatOutput: Boolean,
                        enableAudioTrackPlaybackParams: Boolean
                    ): AudioSink {
                        return DefaultAudioSink.Builder(context)
                            .setAudioProcessors(arrayOf(mergeAudioProcessor!!))
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .build()
                    }
                }

            player = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory!!)
                .build()

            player!!.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),  /* handleAudioFocus= */
                true
            )

            player!!.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        onPrepared(getPlayer()!!)
                    } else if (playbackState == Player.STATE_ENDED) {
                        onCompletion(getPlayer())
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("MusicService", error.toString())
                }

                override fun onPositionDiscontinuity(
                    oldPosition: PositionInfo,
                    newPosition: PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        onSeekComplete(getPlayer())
                    }
                }
            })

            if (!wakeLock!!.isHeld()) wakeLock!!.acquire(60 * 1000L /*1 minute*/)
        }
        return player
    }

    private fun releaseAudio() {
        Log.d("MusicService", "releaseAudio")

        state!!.state = (PlayerState.Nope)
        seekFinished = true
        setChanged()
        wasPlaying = false

        P.update { p -> p.copy(lastPlayedSongID = rows?.currSong?.iD) }

        if (player != null) {
            if (player!!.isPlaying) {
                player!!.stop()
            }
            player!!.release()
            player = null
            if (wakeLock!!.isHeld) wakeLock!!.release()
        }

        stopSensor()

        stopNotification()
        if (mediaSession != null) {
            mediaSession!!.setActive(false)
            mediaSession!!.release()
            mediaSession = null
        }

        audioManager!!.abandonAudioFocus(this)
        unregisterNoisyReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleCommand(intent)
        if (mediaSession != null) MediaButtonReceiver.handleIntent(mediaSession, intent)

        // show the notification if MusicService has been started from the MediaButtonIntentReceiver
        if (!mainIsVisible && !foreground && changed && isInState(PlayerState.Companion.Started)) startNotification()

        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleCommand(intent: Intent?) {
        if (intent == null) return

        val action = intent.getAction()
        var cmd = intent.getStringExtra("command")
        Log.d("MusicService", "intentReceiver.onReceive" + action + " / " + cmd)
        if (NEXT_ACTION == action) cmd = CMDNEXT
        else if (PREVIOUS_ACTION == action) cmd = CMDPREVIOUS
        else if (TOGGLEPAUSE_ACTION == action) cmd = CMDTOGGLEPAUSE
        else if (PAUSE_ACTION == action) cmd = CMDPAUSE
        handleCommand(cmd)
    }

    private fun handleCommand(cmd: String?) {
        if (CMDNEXT == cmd) {
            playNext()
            setChanged()
        } else if (CMDPREVIOUS == cmd) {
            playPrev()
            setChanged()
        } else if (CMDTOGGLEPAUSE == cmd) {
            if (isInState(PlayerState.Companion.Started)) {
                pause()
            } else {
                if (isInState(PlayerState.Companion.Paused)) start()
                else playSong()
            }
            setChanged()
        } else if (CMDSTOP == cmd || CMDPAUSE == cmd) {
            if (isInState(PlayerState.Companion.Started)) {
                pause()
                setChanged()
            }
        } else if (CMDPLAY == cmd) {
            if (isInState(PlayerState.Companion.Paused)) start()
            else playSong()
            setChanged()
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN ->                 // resume playback
                if (wasPlaying) {
                    start()
                    setChanged()
                }

            AudioManager.AUDIOFOCUS_LOSS ->                 // Lost focus for an unbounded amount of time: stop playback and release media player
                releaseAudio()

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->                 // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (getPlayer()!!.isPlaying()) {
                    //player.setVolume(0.1f, 0.1f);
                    pause()
                    wasPlaying = true
                    setChanged()
                } else {
                    wasPlaying = false
                    stopSensor()
                }
        }
    }

    private var seekPosNbLoop = 0
    fun onSeekComplete(mp: ExoPlayer?) {
        if (seekPosMsBug != -1L) {
            // todo: make it thread safe?
            seekPosNbLoop = 15

            mainHandler.postDelayed(Runnable {
                if (seekPosNbLoop-- > 0 || this.currentPositionMs >= seekPosMsBug) {
                    seekFinished = true
                    seekPosMsBug = -1
                }
            }, 300)
        } else {
            seekFinished = true
        }

        startTrackLooperRewinder()

        updateMediaPlaybackState()
        Log.d("MusicService", "onSeekComplete setProgress" + msToMinutes(this.currentPositionMs))
    }

    fun playSong() {
        val oldState = state!!.state
        val rowSong = rows!!.currSong ?: return

        rows!!.save()

        startSensor()
        disableTrackLooper()

        getPlayer()!!.stop()
        getPlayer()!!.clearMediaItems()
        state!!.state = (PlayerState.Idle)

        try {
            state!!.state = (PlayerState.Preparing)
            val audio = MediaItem.fromUri(rowSong.externalContentUri)
            val dur = getRows().currSong!!.durationMs
            var startTime = 0L

            if (P.value.shuffle.startMidSong() && oldState != PlayerState.PlaybackCompleted
            ) {
                // If a song is started for the first time, i.e. this is not the automatic follow up
                // to a previously played song, and we are in radio FM mode, we want to start playback at a
                // random point in time.
                startTime = (Math.random() * dur.toFloat()).toLong()
            }

            getPlayer()!!.setMediaItem(audio, startTime)
            getPlayer()!!.prepare()
        } catch (e: Exception) {
            Log.e("MusicService", "Error setting data source", e)
            state!!.state = (PlayerState.Companion.Error)
            // todo: improve error handling
            return
        }
        state!!.state = (PlayerState.Companion.Initialized)

        updateMediaPlaybackState()
        updateMediaSessionMetadata()
        if (foreground) startNotification()
    }

    fun onCompletion(ignored: ExoPlayer?) {
        state!!.state = (PlayerState.Companion.PlaybackCompleted)
        setChanged()

        // loop only to same track if not asked to change track (i.e. loop only on completion)
        if (P.value.repeatMode == RepeatMode.REPEAT_ONE) playSong()
        else {
            if (P.value.repeatMode == RepeatMode.STOP_AT_END_OF_TRACK ||
                (P.value.repeatMode == RepeatMode.STOP_AT_END_OF_FOLDER && rows!!.currPosIsLastSongInGroup())
            ) {
                state!!.state = (PlayerState.Stopped)
                setChanged()
            } else {
                playNext()
            }
        }
    }

    /** When we want to play a song, we "prepare" it first. This loads the sound data from our mp3 files,
     * and prepares the player. If that is done, this callback fires. We then want to actually
     * start the sound file. */
    fun onPrepared(mp: ExoPlayer) {
        // if a songPos has been stored
        Log.d(
            "MusicService",
            "savedSongPosId: " + savedSongPosId + "getDuration" + mp.getDuration()
        )
        if (savedSongPos > 0 && savedSongPosId == mp.getDuration() && savedSongPos < mp.getDuration()) {
            // seek to it
            mp.seekTo(savedSongPos)
        }

        savedSongPos = mp.currentPosition
        savedSongPosId = 0

        applyPlaybackSpeed(playbackSpeed)

        // start playback
        mp.play()
        state!!.state = (PlayerState.Companion.Started)
    }

    /** This re-loads the currently selected playback speed. This is used for real time reload of the
     * "Disable pitch compensation" setting
     */
    fun applyPlaybackSpeed() {
        val s = getPlaybackSpeed()
        applyPlaybackSpeed(s)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        try {
            var playback = getPlayer()!!.playbackParameters.withSpeed(speed).withPitch(1f)
            if (P.value.disablePitchCompensation) {
                playback = playback.withPitch(speed)
            }

            getPlayer()!!.playbackParameters = playback
        } catch (e: Exception) {
            Log.e("MusicService", "setPlaySpeed: ", e)
        }
    }

    fun applyStereo(stereo: Boolean) {
        mergeAudioProcessor!!.isStereo = stereo
    }

    val currentPositionMs: Long
        /*** PLAY ACTION  */
        get() {
            if (player == null) return 0
            return player!!.getCurrentPosition()
        }

    val durationMs: Long
        // get current song total duration
        get() {
            if (player == null) return 0
            return player!!.getDuration()
        }

    fun seekTo(posMs: Long) {
        if (player == null) return

        seekFinished = false

        player!!.seekTo(posMs.toInt().toLong())
    }

    private var trackLooperEnabled = false
    private var trackLooperAPosMs: Long = 0
    private var trackLooperBPosMs: Long = 0
    fun enableTrackLooper(APosMs: Long, BPosMs: Long) {
        trackLooperAPosMs = APosMs
        trackLooperBPosMs = BPosMs
        trackLooperEnabled = true
        seekTo(trackLooperAPosMs)
    }

    fun disableTrackLooper() {
        trackLooperEnabled = false
        cancelTrackLooperRewinder()
    }

    fun startTrackLooperRewinder() {
        if (trackLooperEnabled) {
            val diffMs = trackLooperBPosMs - this.currentPositionMs
            if (diffMs <= 0) seekTo(trackLooperAPosMs)
            else {
                mainHandler.removeCallbacks(trackLooperRunnable)
                mainHandler.postDelayed(trackLooperRunnable, diffMs)
            }
        }
    }

    fun cancelTrackLooperRewinder() {
        mainHandler.removeCallbacks(trackLooperRunnable)
    }

    // unpause
    fun start() {
        applyPlaybackSpeed(playbackSpeed)
        getPlayer()!!.play()
        state!!.state = (PlayerState.Companion.Started)
        startSensor()
        startTrackLooperRewinder()

        updateMediaPlaybackState()
        updateMediaSessionMetadata()
        if (foreground) startNotification()
    }

    fun pause() {
        if (player == null) return

        player!!.pause()
        state!!.state = (PlayerState.Paused)
        stopSensor()
        cancelTrackLooperRewinder()

        P.update { p -> p.copy(lastPlayedSongID = rows?.currSong?.iD) }

        updateMediaPlaybackState()
        updateMediaSessionMetadata()
        if (foreground) startNotification()
    }

    fun playPrev() {
        if (P.value.shuffle.randomSongOrder()) rows!!.moveToRandomSongBack()
        else rows!!.moveToPrevSong()

        playSong()
    }

    fun playNext() {
        if (P.value.shuffle.randomSongOrder()) rows!!.moveToRandomSong()
        else rows!!.moveToNextSong()

        playSong()
    }

    fun playPrevGroup() {
        if (P.value.shuffle.randomSongOrder()) rows!!.moveToRandomSongBack()
        else rows!!.moveToPrevGroup()

        playSong()
    }

    fun playNextGroup() {
        if (P.value.shuffle.randomSongOrder()) rows!!.moveToRandomSong()
        else rows!!.moveToNextGroup()

        playSong()
    }

    /*** STATE  */
    fun isInState(states: Int): Boolean {
        return state!!.compare(states)
    }

    // !playingStopped == playingLaunched || playingPaused
    fun playingLaunched(): Boolean {
        val states: Int = PlayerState.Companion.Initialized or
                PlayerState.Companion.Idle or
                PlayerState.Companion.PlaybackCompleted or
                PlayerState.Companion.Prepared or
                PlayerState.Companion.Preparing or
                PlayerState.Companion.Started
        return state!!.compare(states)
    }

    fun playingStopped(): Boolean {
        val states: Int = PlayerState.Companion.Nope or
                PlayerState.Companion.Error or
                PlayerState.Companion.Stopped or
                PlayerState.Companion.End
        return state!!.compare(states)
    }

    fun playingPaused(): Boolean {
        return state!!.compare(PlayerState.Companion.Paused)
    }

    val isPlaying: Boolean
        get() = player != null && player!!.isPlaying()


    /*** NOTIFICATION  */
    fun startNotification() {
        val rowSong = rows!!.currSong ?: return

        val openApp = Intent(this, Main::class.java)
        openApp.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val builder: NotificationCompat.Builder? =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        builder!!.setContentTitle(rowSong.title)
            .setContentText(rowSong.artist)
            .setSubText(rowSong.album)
            .setSmallIcon(R.drawable.ic_stat_music_note) // R.drawable.ic_notification
            .setLargeIcon(AlbumArtLoader(applicationContext, rows!!.currSong!!).load())
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    applicationContext,
                    PlaybackStateCompat.ACTION_STOP
                )
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (playingLaunched()) builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_notif_pause,
                getString(R.string.action_pause),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
        )
        else builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_notif_play_arrow,
                getString(R.string.action_play),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_notif_skip_previous,
                getString(R.string.action_prev),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_notif_skip_next,
                getString(R.string.action_next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
        )

        if (mediaSession != null) builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession!!.sessionToken)
        )
        //NotificationManagerCompat.from(MusicService.this).notify(NOTIFICATION_ID, builder.build());
        foreground = true
        startForeground(NOTIFICATION_ID, builder.build())
    }


    fun stopNotification() {
        if (foreground) stopForeground(true)
        //NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        foreground = false
    }

    private fun createNotificationChannel() {
        val name: CharSequence = "SicMuNeo Channel"
        val description = "SicMuNeo Channel"
        val importance = NotificationManager.IMPORTANCE_LOW
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = description
        mChannel.enableLights(true) // todo: useful ?
        mChannel.lightColor = Color.RED // todo: useful ?
        NotificationManagerCompat.from(this@MusicService).createNotificationChannel(mChannel)
    }

    /*** PREFERENCES  */
    private fun restore() {
        enableShake = P.value.shakePlaysSongs
        shakeThreshold = P.value.shakeThreshold / 10
    }

    /*** SENSORS  */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event)
        }
    }

    private fun getAccelerometer(event: SensorEvent) {
        val values = event.values
        // Movement
        val x = values[0]
        val y = values[1]
        val z = values[2]

        // algo found here : http://stackoverflow.com/questions/2317428/android-i-want-to-shake-it
        accelLast = accelCurrent
        accelCurrent = sqrt((x * x + y * y + z * z).toDouble())
        val delta = accelCurrent - accelLast
        accel = accel * 0.9f + delta // perform low-cut filter

        if (accel > shakeThreshold) {
            val actualTime = event.timestamp
            val minShakePeriod = 1000 * 1000 * 1000
            if (actualTime - lastUpdate < minShakePeriod) {
                return
            }
            lastUpdate = actualTime

            Log.d(
                "MusicService",
                "Device was shaken. Acceleration: " + String.format("%.1f", accel) +
                        " x: " + String.format("%.1f", x * x) +
                        " y: " + String.format("%.1f", y * y) +
                        " z: " + String.format("%.1f", z * z)
            )

            // goes to next song
            if (playingLaunched()) {
                playNext()
                setChanged()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun setEnableShake(shake: Boolean) {
        enableShake = shake
        if (enableShake) startSensor()
        else stopSensor()
        P.update { p -> p.copy(shakePlaysSongs = enableShake) }
    }

    fun setEnableRating(rating: Boolean) {
        // TODO: Stub
        /*
        enableRating = rating
        setChanged()
        preferences!!.enableRating = (enableRating)
         */
    }

    fun getMinRating(): Int {
        // TODO: Stub
        //return minRating
        return 1
    }

    fun setMinRating(rating: Int) {
        // TODO: Stub
/*
        Log.d("MusicService", "set min rating to " + rating)
        minRating = rating
        preferences!!.minRating = (minRating)
        setChanged()*/
    }

    fun setPlaybackSpeed(v: Float) {
        if (v <= 0) {
            return
        }
        playbackSpeed = v
        updateMediaPlaybackState()
        if (player != null && playingLaunched()) applyPlaybackSpeed(playbackSpeed)
    }

    fun getPlaybackSpeed(): Float {
        return playbackSpeed
    }

    // can be called twice
    private fun startSensor() {
        if (enableShake && sensorManager == null) {
            accelLast = SensorManager.GRAVITY_EARTH.toDouble()
            accel = 0.00
            accelCurrent = SensorManager.GRAVITY_EARTH.toDouble()
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
            accelerometer = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            sensorManager!!.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            lastUpdate = System.currentTimeMillis()
        }
    }

    // can be called twice
    private fun stopSensor() {
        if (sensorManager != null) {
            sensorManager!!.unregisterListener(this)
            sensorManager = null
            accelerometer = null
        }
    }

    // return > 0 if sleep timer has been started
    var sleepTimerScheduleMs: Long = 0
        private set

    fun startSleepTimer(delayMinutes: Int) {
        if (delayMinutes <= 0) return

        stopSleepTimer()
        val delayMillis = delayMinutes.toLong() * 60 * 1000
        sleepTimerScheduleMs = System.currentTimeMillis() + delayMillis
        mainHandler.postDelayed(sleepTimerRunnable, delayMillis)
    }

    fun stopSleepTimer() {
        mainHandler.removeCallbacks(sleepTimerRunnable)
        sleepTimerScheduleMs = 0
    }

    companion object {
        // drive app from hardware key (from MediaButtonIntentReceiver)
        const val SERVICECMD: String = "xyz.mordorx.sicmu.musicservicecommand"
        const val CMDNAME: String = "command"
        const val CMDTOGGLEPAUSE: String = "togglepause"
        const val CMDSTOP: String = "stop"
        const val CMDPAUSE: String = "pause"
        const val CMDPLAY: String = "play"
        const val CMDPREVIOUS: String = "previous"
        const val CMDNEXT: String = "next"

        // drive the app from another app
        const val TOGGLEPAUSE_ACTION: String = "xyz.mordorx.sicmu.musicservicecommand.togglepause"
        const val PAUSE_ACTION: String = "xyz.mordorx.sicmu.musicservicecommand.pause"
        const val PREVIOUS_ACTION: String = "xyz.mordorx.sicmu.musicservicecommand.previous"
        const val NEXT_ACTION: String = "xyz.mordorx.sicmu.musicservicecommand.next"

        const val MEDIA_SESSION_TAG: String = "SicMuNeo_MediaSessionTag"

        private const val NOTIFICATION_ID = 1

        var enableRating: Boolean = false
            private set
        private val MEDIA_SESSION_ACTIONS = (PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_SEEK_TO)
        const val CHANNEL_ID: String = "SicMuNeo_channelid"
    }
}
