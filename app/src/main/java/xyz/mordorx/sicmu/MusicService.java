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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

@UnstableApi
public class MusicService extends Service implements
        AudioManager.OnAudioFocusChangeListener, SensorEventListener
{
    // drive app from hardware key (from MediaButtonIntentReceiver)
    public static final String SERVICECMD = "souch.smp.musicservicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";

    // drive the app from another app
    public static final String TOGGLEPAUSE_ACTION = "souch.smp.musicservicecommand.togglepause";
    public static final String PAUSE_ACTION       = "souch.smp.musicservicecommand.pause";
    public static final String PREVIOUS_ACTION    = "souch.smp.musicservicecommand.previous";
    public static final String NEXT_ACTION        = "souch.smp.musicservicecommand.next";

    private Parameters params;
    private ExoPlayer player;
    private PowerManager.WakeLock wakeLock;
    private MergeAudioProcessor mergeAudioProcessor;

    //private MediaNotificationManager mediaNotificationManager;
    private MediaSessionCompat mediaSession;
    public static final String MediaSessionTag = "SMP_MediaSessionTag";

    private Rows rows;

    /// seek to last song pos on startup in milliseconds.
    /// if -1: disabled (do not seek to on startup)
    private long savedSongPos;
    /// save song pos id to avoid restoring song pos if not same track
    private long savedSongPosId;

    /// need for focus
    private boolean wasPlaying;
    /// sth happened and the Main do not know it: a song has finish to play, another app gain focus, ...
    private boolean changed;

    /// useful only for buggy android seek
    private long seekPosMsBug;

    /// a notification has been launched
    private boolean foreground;
    private static final int NOTIFICATION_ID = 1;

    private boolean mainIsVisible;
    public void setMainIsVisible(boolean visible) { mainIsVisible = visible; }

    private final IBinder musicBind = new MusicBinder();

    private ComponentName remoteControlResponder;
    private AudioManager audioManager;

    IntentFilter noisyReceiverFilter = null;

    /// current state of the MediaPlayer
    private PlayerState state;

    private Database database;

    /// set to false if seekTo() has been called but the seek is still not done
    private boolean seekFinished;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastUpdate;
    private boolean enableShake;
    private static boolean enableRating;
    private int minRating = 1;
    private float shakeThreshold;
    private float playbackSpeed = 1.0f;
    private double accelLast;
    private double accelCurrent;
    private double accel;

    private Scrobble scrobble;

    /// used for handling playback state when media session actions occur.
    private final MediaSessionCompat.Callback mMediaSessionCallback = new MediaSessionCompat.Callback() {

        @Override
        public void onPlay() {
            super.onPlay();
            handleCommand(CMDPLAY);
        }

        @Override
        public void onPause() {
            super.onPause();
            handleCommand(CMDPAUSE);
        }

        @Override
        public void onStop() {
            super.onStop();
            handleCommand(CMDSTOP);
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            handleCommand(CMDNEXT);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            handleCommand(CMDPREVIOUS);
        }

        @Override
        public void onSeekTo(long posMs) {
            super.onSeekTo(posMs);
            seekTo((int) posMs);
        }
    };

    private void initMediaSession() {
        ComponentName mediaButtonReceiver = new ComponentName(getApplicationContext(), MediaButtonReceiver.class);
        mediaSession = new MediaSessionCompat(getApplicationContext(), MediaSessionTag, mediaButtonReceiver, null);

        mediaSession.setCallback(mMediaSessionCallback);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, MediaButtonReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
                mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE);
        mediaSession.setMediaButtonReceiver(pendingIntent);
    }

    private static final long MEDIA_SESSION_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_SEEK_TO;
    private void updateMediaPlaybackState() {
        if (mediaSession == null)
            return;

        boolean isPlaying = playingLaunched();
        long currPosMs = getCurrentPositionMs();
        PlaybackStateCompat.Builder stateBuilder =
                new PlaybackStateCompat.Builder()
                        .setActions(MEDIA_SESSION_ACTIONS)
                        .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                                currPosMs,
                                playbackSpeed);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null)
            return;

        RowSong rowSong = rows.getCurrSong();
        if (rowSong != null) {
            new AlbumArtLoader(getApplicationContext(), rowSong).loadAsync(
                    (rowSongId, bitmap) -> {
                        // albumbmp will be in cache, so don't bother to pass bitmap param to getMediaMetadata
                        mediaSession.setMetadata(rowSong.getMediaMetadata(getApplicationContext()));
                    });
        }
    }

    private void initNoisyReceiver() {
        if (noisyReceiverFilter == null) {
            noisyReceiverFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            // Handles headphones coming unplugged. cannot be done through a manifest receiver
            registerReceiver(noisyReceiver, noisyReceiverFilter);
        }
    }

    private void unregisterNoisyReceiver() {
        if (noisyReceiverFilter != null) {
            unregisterReceiver(noisyReceiver);
            noisyReceiverFilter = null;
        }
    }

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleCommand(CMDPAUSE);
        }
    };

    public Rows getRows() { return rows; }

    public synchronized boolean getChanged() {
        boolean hasChanged = changed;
        changed = false;
        return hasChanged;
    }

    public synchronized void setChanged() {
        changed = true;
    }

    /*** SERVICE ***/

    @Override
    public void onCreate() {
        Log.d("MusicService", "onCreate()");
        super.onCreate();
        createNotificationChannel();

        state = new PlayerState();

        changed = false;
        seekFinished = true;
        seekPosMsBug = -1;
        wasPlaying = false;

        player = null;
        remoteControlResponder = null;
        audioManager = null;

        params = new Parameters(this);
        database = new Database(getApplicationContext());
        database.cleanupSongsDB();
        rows = new Rows(getApplicationContext(), getContentResolver(), params, getResources(),
                database);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "souch.smp:MusicService");

        // try sync if sth failed in the previous SMP session
        synchronizeFailedRatings();

        restore();

        remoteControlResponder = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.registerMediaButtonEventReceiver(remoteControlResponder);
        foreground = false;
        mainIsVisible = false;
        mergeAudioProcessor = new MergeAudioProcessor();
        mergeAudioProcessor.setStereo(params.getStereo());

        scrobble = new Scrobble(rows, params, getApplicationContext());
    }

    public Database getDatabase() {
        return database;
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return musicBind;
    }


    @Override
    public void onDestroy() {
        Log.d("MusicService", "onDestroy");
        save();
        rows.save();
        releaseAudio();

        if (!params.getMediaButtonStartAppShake())
            audioManager.unregisterMediaButtonEventReceiver(remoteControlResponder);
    }

    /*** PLAYER ***/

    /**
     * This gets the ExoPlayer, and creates one if neccessary. It also creates an AudioManager.
     * @return The ExoPlayer, fully initialized
     */
    private ExoPlayer getPlayer() {
        seekPosMsBug = -1;

        if (player == null) {
            Log.d("MusicService", "create player");

            initMediaSession();
            initNoisyReceiver();

            // This boilerplate is necessary in order to allow use of our custom AudioProcessor
            var renderersFactory = new DefaultRenderersFactory(this) {
                @Override
                protected AudioSink buildAudioSink(
                        @NonNull Context context,
                        boolean enableFloatOutput,
                        boolean enableAudioTrackPlaybackParams) {
                    return new DefaultAudioSink.Builder(context)
                            .setAudioProcessors(new AudioProcessor[]{mergeAudioProcessor})
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .build();
                }
            };

            player = new ExoPlayer.Builder(this)
                    .setRenderersFactory(renderersFactory)
                    .build();

            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .setUsage(C.USAGE_MEDIA)
                            .build(),
                    /* handleAudioFocus= */ true
            );

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        onPrepared(getPlayer());
                    } else if (playbackState == Player.STATE_ENDED) {
                        onCompletion(getPlayer());
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e("MusicService", error.toString());
                }

                @Override
                public void onPositionDiscontinuity(
                        Player.PositionInfo oldPosition,
                        Player.PositionInfo newPosition,
                        int reason) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        onSeekComplete(getPlayer());
                    }
                }
            });

//            player.setWakeMode(getApplicationContext(),
//                    PowerManager.PARTIAL_WAKE_LOCK);

            if (!wakeLock.isHeld())
                wakeLock.acquire();
        }
        return player;
    }

    private void releaseAudio() {
        Log.d("MusicService", "releaseAudio");

        if (params.getSaveSongPos() &&
                player != null &&
                state.getState() != PlayerState.Nope &&
                state.getState() != PlayerState.Idle &&
                state.getState() != PlayerState.Error)
        {
            params.setSongPos(player.getCurrentPosition());
            params.setSongPosId(player.getDuration());
        }

        state.setState(PlayerState.Nope);
        seekFinished = true;
        setChanged();
        wasPlaying = false;

        scrobble.send(Scrobble.SCROBBLE_COMPLETE);

        if (player != null) {
            if (player.isPlaying()) {
                player.stop();
            }
            player.release();
            player = null;
        if (wakeLock.isHeld())
            wakeLock.release();
        }

        // try sync when releasing audio
        synchronizeFailedRatings();

        stopSensor();

        stopNotification();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession.setActive(false); // should be put in stop() ?
        }

        unregisterNoisyReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        if (mediaSession != null)
            MediaButtonReceiver.handleIntent(mediaSession, intent);

        // show the notification if MusicService has been started from the MediaButtonIntentReceiver
        if (!mainIsVisible && !foreground && changed && isInState(PlayerState.Started))
            startNotification();

        return super.onStartCommand(intent, flags, startId);
    }

    private void handleCommand(Intent intent) {
        if (intent == null)
            return;

        String action = intent.getAction();
        String cmd = intent.getStringExtra("command");
        Log.d("MusicService", "intentReceiver.onReceive" + action + " / " + cmd);
        if (NEXT_ACTION.equals(action))
            cmd = CMDNEXT;
        else if (PREVIOUS_ACTION.equals(action))
            cmd = CMDPREVIOUS;
        else if (TOGGLEPAUSE_ACTION.equals(action))
            cmd = CMDTOGGLEPAUSE;
        else if (PAUSE_ACTION.equals(action))
            cmd = CMDPAUSE;
        handleCommand(cmd);
    }

    private void handleCommand(String cmd) {
        if (CMDNEXT.equals(cmd)) {
            playNext();
            setChanged();
        } else if (CMDPREVIOUS.equals(cmd)) {
            playPrev();
            setChanged();
        } else if (CMDTOGGLEPAUSE.equals(cmd)) {
            if (isInState(PlayerState.Started)) {
                pause();
            }
            else {
                if (isInState(PlayerState.Paused))
                    start();
                else
                    playSong();
            }
            setChanged();
        } else if (CMDSTOP.equals(cmd) || CMDPAUSE.equals(cmd)) {
            if (isInState(PlayerState.Started)) {
                pause();
                setChanged();
            }
        } else if (CMDPLAY.equals(cmd)) {
            if (isInState(PlayerState.Paused))
                start();
            else
                playSong();
            setChanged();
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (wasPlaying) {
                    start();
                    setChanged();
                }
                //player.setVolume(1.0f, 1.0f);
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                releaseAudio();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (getPlayer().isPlaying()) {
                    //player.setVolume(0.1f, 0.1f);
                    pause();
                    wasPlaying = true;
                    setChanged();
                }
                else {
                    wasPlaying = false;
                    stopSensor();
                }
                break;
        }
    }

    private int seekPosNbLoop;
    private Timer trackLooperTimer = null;
    public void onSeekComplete(ExoPlayer mp) {
        // on a 4.1 phone no bug : calling getCurrentPosition now gives the new seeked position
        // on My 2.3.6 phone, the phone seems bugged : calling now getCurrentPosition gives
        // last position. So wait the seekpos goes after the asked seekpos.
        if(seekPosMsBug != -1) {
            // todo: make it thread safe?
            seekPosNbLoop = 15;

            final Timer seekPosTimer = new Timer();
            seekPosTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (seekPosNbLoop-- > 0 || getCurrentPositionMs() >= seekPosMsBug) {
                        seekFinished = true;
                        seekPosMsBug = -1;
                        seekPosTimer.cancel();
                    }
                }
            }, 300);
        }
        else {
            seekFinished = true;
        }

        startTrackLooperRewinder();

        updateMediaPlaybackState();
        Log.d("MusicService", "onSeekComplete setProgress" + RowSong.msToMinutes(getCurrentPositionMs()));
    }

    public void playSong() {
        var oldState = state.getState();
        RowSong rowSong = rows.getCurrSong();
        if (rowSong == null)
            return;

        rows.save();

        startSensor();
        disableTrackLooper();

        getPlayer().stop();
        getPlayer().clearMediaItems();
        state.setState(PlayerState.Idle);

        try{
            state.setState(PlayerState.Preparing);
            var audio = MediaItem.fromUri(rowSong.getExternalContentUri());
            var dur = getRows().getCurrSong().getDurationMs();
            var startTime = 0L;

            if (params.getShuffle().startMidSong() && oldState != PlayerState.PlaybackCompleted) {
                // If a song is started for the first time, i.e. this is not the automatic follow up
                // to a previously played song, and we are in radio FM mode, we want to start playback at a
                // random point in time.
                startTime = (long)(Math.random() * (float)dur);
            }

            getPlayer().setMediaItem(audio, startTime);
            getPlayer().prepare();
        }
        catch(Exception e){
            Log.e("MusicService", "Error setting data source", e);
            state.setState(PlayerState.Error);
            // todo: improve error handling
            return;
        }
        state.setState(PlayerState.Initialized);

        updateMediaPlaybackState();
        updateMediaSessionMetadata();
        if(foreground)
            startNotification();

        // try sync at each new song
        synchronizeFailedRatings();
    }

    // failed ratings occurs usually when rating song that are currently playing
    // so failed rating should be resync when another song is playing
    // todo: use an Observer design pattern ?
    private void synchronizeFailedRatings() {
        rows.synchronizeFailedRatings();
    }

    public void onCompletion(ExoPlayer mp) {
        state.setState(PlayerState.PlaybackCompleted);
        setChanged();

        // try sync on song completion
        synchronizeFailedRatings();

        // loop only to same track if not asked to change track (i.e. loop only on completion)
        if (rows.getRepeatMode() == RepeatMode.REPEAT_ONE)
            playSame();
        else {
            if (rows.getRepeatMode() == RepeatMode.STOP_AT_END_OF_TRACK ||
                    (rows.getRepeatMode() == RepeatMode.REPEAT_NOT && rows.currPosIsLastSongInGroup()))
            {
                state.setState(PlayerState.Stopped);
                setChanged();
            }
            else {
                playNext();
            }
        }
    }

    /// When we want to play a song, we "prepare" it first. This loads the sound data from our mp3 files,
    /// and prepares the player. If that is done, this callback fires. We then want to actually
    /// start the sound file.
    public void onPrepared(ExoPlayer mp) {
        // if a songPos has been stored
        Log.d("MusicService", "savedSongPosId: " + savedSongPosId + "getDuration" + mp.getDuration());
        if (savedSongPos > 0 &&
                savedSongPosId == mp.getDuration() &&
                savedSongPos < mp.getDuration()) {
            // seek to it
            mp.seekTo(savedSongPos);
        }

        savedSongPos = mp.getCurrentPosition();
        savedSongPosId = 0;
        params.setSongPos(savedSongPos);
        params.setSongPosId(savedSongPosId);


        applyPlaybackSpeed(playbackSpeed);

        // start playback
        mp.play();
        state.setState(PlayerState.Started);

        scrobble.send(Scrobble.SCROBBLE_COMPLETE);
        scrobble.send(Scrobble.SCROBBLE_START);
    }

    private boolean applyPlaybackSpeed(float speed) {
        try {
            var params = getPlayer().getPlaybackParameters().withSpeed(speed);
            getPlayer().setPlaybackParameters(params);
            return true;
        } catch (Exception e) {
            Log.e("MusicService", "setPlaySpeed: ", e);
            return false;
        }
    }

    public void applyStereo(boolean stereo) {
        mergeAudioProcessor.setStereo(stereo);
    }

    /*** PLAY ACTION ***/

    public long getCurrentPositionMs(){
        if(player == null)
            return 0;
        return player.getCurrentPosition();
    }

    // get current song total duration
    public long getDurationMs(){
        if(player == null)
            return 0;
        return player.getDuration();
    }

    public void seekTo(long posMs){
        if(player == null)
            return;

        seekFinished = false;

        player.seekTo((int) posMs);
    }

    public boolean getSeekFinished() {
        return seekFinished;
    }

    private boolean trackLooperEnabled = false;
    private long trackLooperAPosMs;
    private long trackLooperBPosMs;
    public void enableTrackLooper(long APosMs, long BPosMs)
    {
        trackLooperAPosMs = APosMs;
        trackLooperBPosMs = BPosMs;
        trackLooperEnabled = true;
        seekTo(trackLooperAPosMs);
    }

    public void disableTrackLooper()
    {
        trackLooperEnabled = false;
        cancelTrackLooperRewinder();
    }

    public void startTrackLooperRewinder() {
        if (trackLooperEnabled) {
            long diffMs = trackLooperBPosMs - getCurrentPositionMs();
            if (diffMs <= 0)
                seekTo(trackLooperAPosMs);
            else {
                if (trackLooperTimer != null)
                    trackLooperTimer.cancel();
                trackLooperTimer = new Timer();
                trackLooperTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        seekTo(trackLooperAPosMs);
                    }
                }, diffMs);
            }
        }
    }

    public void cancelTrackLooperRewinder() {
        if (trackLooperTimer != null)
            trackLooperTimer.cancel();
    }

    // unpause
    public void start() {
        applyPlaybackSpeed(playbackSpeed);
        getPlayer().play();
        state.setState(PlayerState.Started);
        startSensor();
        scrobble.send(Scrobble.SCROBBLE_RESUME);
        startTrackLooperRewinder();

        updateMediaPlaybackState();
        updateMediaSessionMetadata();
        if(foreground)
            startNotification();
    }

    public void pause() {
        if(player == null)
            return;

        player.pause();
        state.setState(PlayerState.Paused);
        stopSensor();
        scrobble.send(Scrobble.SCROBBLE_PAUSE);
        cancelTrackLooperRewinder();

        if (params.getSaveSongPos()) {
            params.setSongPos(player.getCurrentPosition());
            params.setSongPosId(player.getDuration());
        }

        updateMediaPlaybackState();
        updateMediaSessionMetadata();
        if(foreground)
            startNotification();
    }

    public void playPrev() {
        if (params.getShuffle().randomSongOrder())
            rows.moveToRandomSongBack();
        else
            rows.moveToPrevSong();

        playSong();
    }

    public void playNext() {
        if (params.getShuffle().randomSongOrder())
            rows.moveToRandomSong();
        else
            rows.moveToNextSong();

        playSong();
    }

    public void playPrevGroup() {
        if (params.getShuffle().randomSongOrder())
            rows.moveToRandomSongBack();
        else
            rows.moveToPrevGroup();

        playSong();
    }

    public void playNextGroup() {
        if (params.getShuffle().randomSongOrder())
            rows.moveToRandomSong();
        else
            rows.moveToNextGroup();

        playSong();
    }

    public void playSame() {
        playSong();
    }

    /*** STATE ***/

    public boolean isInState(int states) {
        return state.compare(states);
    }

    // !playingStopped == playingLaunched || playingPaused

    public boolean playingLaunched() {
        final int states = PlayerState.Initialized |
                PlayerState.Idle |
                PlayerState.PlaybackCompleted |
                PlayerState.Prepared |
                PlayerState.Preparing |
                PlayerState.Started;
        return state.compare(states);
    }

    public boolean playingStopped() {
        final int states = PlayerState.Nope |
              PlayerState.Error |
              PlayerState.Stopped |
              PlayerState.End;
        return state.compare(states);
    }

    public boolean playingPaused() {
        return state.compare(PlayerState.Paused);
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }


    /*** NOTIFICATION ***/

    public void startNotification() {
        RowSong rowSong = rows.getCurrSong();
        if(rowSong == null)
            return;

        Intent openApp = new Intent(this, Main.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channel_id);
        builder.setContentTitle(rowSong.getTitle())
                .setContentText(rowSong.getArtist())
                .setSubText(rowSong.getAlbum())
                .setSmallIcon(R.drawable.ic_stat_music_note) // R.drawable.ic_notification
                .setLargeIcon(new AlbumArtLoader(getApplicationContext(), rows.getCurrSong()).load())
                .setContentIntent(PendingIntent.getActivity(this, 0, openApp,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(getApplicationContext(),
                        PlaybackStateCompat.ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (builder == null)
            return;
        if (playingLaunched())
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_notif_pause,
                    getString(R.string.action_pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        else
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_notif_play_arrow,
                    getString(R.string.action_play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        builder.addAction(new NotificationCompat.Action(R.drawable.ic_notif_skip_previous,
                getString(R.string.action_prev),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));
        builder.addAction(new NotificationCompat.Action(R.drawable.ic_notif_skip_next,
                getString(R.string.action_next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));

        if (mediaSession != null)
            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                   .setShowActionsInCompactView(0, 1, 2).setMediaSession(mediaSession.getSessionToken()));
        //NotificationManagerCompat.from(MusicService.this).notify(NOTIFICATION_ID, builder.build());
        foreground = true;
        startForeground(NOTIFICATION_ID, builder.build());
    }


    public void stopNotification() {
        if (foreground)
            stopForeground(true);
        //NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        foreground = false;
    }

    public static final String channel_id = "smp_channelid";
    private void createNotificationChannel()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "SMP Channel";
            String description = "SicmuPlayer channel";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(channel_id, name, importance);
            mChannel.setDescription(description);
            mChannel.enableLights(true); // todo: useful ?
            mChannel.setLightColor(Color.RED); // todo: useful ?
            NotificationManagerCompat.from(MusicService.this).createNotificationChannel(mChannel);
        }
    }

    /*** PREFERENCES ***/

    private void restore() {
        enableShake = params.getEnableShake();
        shakeThreshold = params.getShakeThreshold() / 10;
        if (params.getSaveSongPos()) {
            savedSongPos = params.getSongPos();
            savedSongPosId = params.getSongPosId();
        }
        else {
            savedSongPos = -1;
            savedSongPosId = -1;
        }
        enableRating = params.getEnableRating();
        minRating = params.getMinRating();
    }

    private void save() {
        params.setEnableShake(enableShake);
    }


    /*** SENSORS ***/

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event);
        }
    }

    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;
        // Movement
        float x = values[0];
        float y = values[1];
        float z = values[2];

        // algo found here : http://stackoverflow.com/questions/2317428/android-i-want-to-shake-it
        accelLast = accelCurrent;
        accelCurrent = Math.sqrt((x*x + y*y + z*z));
        double delta = accelCurrent - accelLast;
        accel = accel * 0.9f + delta; // perform low-cut filter

        if (accel > shakeThreshold) {
            final long actualTime = event.timestamp;
            int MIN_SHAKE_PERIOD = 1000 * 1000 * 1000;
            if (actualTime - lastUpdate < MIN_SHAKE_PERIOD) {
                return;
            }
            lastUpdate = actualTime;

            Log.d("MusicService", "Device was shuffed. Acceleration: " +
                    String.format("%.1f", accel) +
                    " x: " + String.format("%.1f", x*x) +
                    " y: " + String.format("%.1f", y*y) +
                    " z: " + String.format("%.1f", z*z));

            // goes to next song
            if(playingLaunched()) {
                playNext();
                setChanged();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void setEnableShake(boolean shake) {
        enableShake = shake;
        if(enableShake)
            startSensor();
        else
            stopSensor();
        params.setEnableShake(enableShake);
    }

    public boolean getEnableShake() { return enableShake; }

    public static boolean getEnableRating() {
        return enableRating;
    }

    public void setEnableRating(boolean rating) {
        enableRating = rating;
        setChanged();
        params.setEnableRating(enableRating);
    }

    public int getMinRating() {
        return minRating;
    }
    public void setMinRating(int rating) {
        Log.d("MusicService", "set min rating to " + rating);
        minRating = rating;
        params.setMinRating(minRating);
        setChanged();
    }

    public void setShakeThreshold(float threshold) {
        shakeThreshold = threshold / 10;
    }

    public void changePlaybackSpeed(float step) {
        if (playbackSpeed + step <= 0)
            return;
        playbackSpeed = playbackSpeed + step;
        updateMediaPlaybackState();
        if (player != null && playingLaunched())
            applyPlaybackSpeed(playbackSpeed);
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    // can be called twice
    private void startSensor() {
        if(enableShake && sensorManager == null) {
            accelLast = SensorManager.GRAVITY_EARTH;
            accel = 0.00f;
            accelCurrent = SensorManager.GRAVITY_EARTH;
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            lastUpdate = System.currentTimeMillis();
        }
    }

    // can be called twice
    private void stopSensor() {
        if(sensorManager != null) {
            sensorManager.unregisterListener(this);
            sensorManager = null;
            accelerometer = null;
        }
    }

    private Timer sleepTimer = null;
    private long sleepTimerScheduleMs = 0;

    // return > 0 if sleep timer has been started
    public long getSleepTimerScheduleMs() {
        return sleepTimerScheduleMs;
    }

    public void startSleepTimer(int delayMinutes) {
        if (delayMinutes <= 0)
            return;

        stopSleepTimer();
        final long delayMillis = (long) delayMinutes * 60 * 1000;
        sleepTimerScheduleMs = System.currentTimeMillis() + delayMillis;
        sleepTimer = new Timer();
        sleepTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    pause();
                    setChanged();
                    stopSleepTimer();
                }
            }, delayMillis);
    }

    public void stopSleepTimer() {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        sleepTimerScheduleMs = 0;
    }
}
