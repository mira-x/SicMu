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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.util.UnstableApi;

import static android.os.Build.VERSION.SDK_INT;
import static android.widget.Toast.LENGTH_LONG;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.woheller69.freeDroidWarn.FreeDroidWarn;

@UnstableApi
public class Main extends AppCompatActivity {
    private Rows rows;
    private ListView songView;
    private RowsAdapter songAdt;
    ImageButton playButton;

    private MusicService musicSrv;
    private Intent playIntent;
    private boolean serviceBound = false;
    // the app is about to close
    private boolean finishing;

    private Timer timer;
    private SeekBar seekbar;
    // tell whether the seekbar is currently touch by a user
    private boolean touchSeekbar;
    private TextView duration;
    private TextView currDuration;

    private ImageButton posButton, toggleDetailsButton;

    // true if you want to keep the current song played visible
    private boolean followSong;

    private boolean seekButtonsOpened;
    private boolean detailsOpened;
    private boolean detailsToggledFollowAuto;
    private boolean hasCoverArt;

    private Parameters params;

    private Vibrator vibrator;

    private AnimationDrawable appAnimation;

    private LinearLayout detailsLayout;
    private LinearLayout seekButtonsLayout;
    private TextView playbackSpeedText;
    private LinearLayout warningLayout;

    private LinearLayout moreButtonsLayout;
    private Timer closeMoreButtonsTimer;

    private ImageButton albumImage;
    private TextView songTitle, songAlbum, songArtist, songMime, warningText;
    ArrayList<ImageButton> ratingButtons = new ArrayList<>();
    private LinearLayout details_rating_layout;
    private boolean detailsBigCoverArt;
    private int coverArtNum = 0;
    private final int EXTERNAL_STORAGE_REQUEST_CODE = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("Main", "onCreate");

        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);

        params = new Parameters(this);

        hideSystemBars();

        switch (params.getTheme()) {
            case 1:
                setTheme(R.style.AppThemeDark);
                break;
            case 2:
                setTheme(R.style.AppThemeWhite);
                break;
        }

        setContentView(R.layout.activity_main);
        finishing = false;

        songView = findViewById(R.id.song_list);
        playButton = findViewById(R.id.play_button);
        // useful only for testing
        playButton.setTag(R.drawable.ic_action_play);
        playButton.setOnTouchListener(touchListener);

        ImageButton gotoButton = findViewById(R.id.goto_button);
        gotoButton.setOnTouchListener(touchListener);
        gotoButton.setOnLongClickListener(gotoSongLongListener);

        posButton = findViewById(R.id.toggle_seek_buttons);
        toggleDetailsButton = findViewById(R.id.toggle_details_button);
        seekButtonsOpened = false;
        posButton.setImageDrawable(null);
        seekButtonsLayout = findViewById(R.id.seek_buttons_layout);
        seekButtonsLayout.setVisibility(View.GONE);
        warningLayout = findViewById(R.id.warning_layout);
        warningLayout.setVisibility(View.GONE);
        warningLayout.setOnClickListener(view -> hideWarning());
        detailsLayout = findViewById(R.id.details_layout);
        detailsLayout.setVisibility(View.GONE);
        detailsToggledFollowAuto = true;

        final int repeatDelta = 260;
        ImageButton prevButton = findViewById(R.id.prev_button);
        prevButton.setOnLongClickListener(prevGroupLongListener);
        prevButton.setOnTouchListener(touchListener);
        ImageButton nextButton = findViewById(R.id.next_button);
        nextButton.setOnLongClickListener(nextGroupLongListener);
        nextButton.setOnTouchListener(touchListener);

        RepeatingImageButton seekButton;
        seekButton = findViewById(R.id.m20_button);
        seekButton.setRepeatListener(rewindListener, repeatDelta);
        seekButton.setOnTouchListener(touchListener);
        seekButton = findViewById(R.id.p20_button);
        seekButton.setRepeatListener(forwardListener, repeatDelta);
        seekButton.setOnTouchListener(touchListener);
        seekButton = findViewById(R.id.m5_button);
        seekButton.setRepeatListener(rewindListener, repeatDelta);
        seekButton.setOnTouchListener(touchListener);
        seekButton = findViewById(R.id.p5_button);
        seekButton.setRepeatListener(forwardListener, repeatDelta);
        seekButton.setOnTouchListener(touchListener);

        songTitle = findViewById(R.id.detail_title);
        songAlbum = findViewById(R.id.detail_album);
        songArtist = findViewById(R.id.detail_artist);
        songMime = findViewById(R.id.detail_mime);
        warningText = findViewById(R.id.warning_text);

        askPermission();
        // permission will be granted in onRequestPermissionsResult callback
        // and service will be started and bind in that function

        playIntent = new Intent(this, MusicService.class);
        startService(playIntent);
        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);

        duration = findViewById(R.id.duration);
        currDuration = findViewById(R.id.curr_duration);
        touchSeekbar = false;
        seekbar = findViewById(R.id.seek_bar);
        seekbar.setOnSeekBarChangeListener(seekBarChangeListener);

        followSong = false;

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        // tells the OS that the volume buttons should affect the "media" volume when your application is visible
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        var audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.registerAudioDeviceCallback(audioDeviceStereoConfigCallback, null);

        // set the color statically for speed (don't know another prettier method)
        Row.levelOffset = 14; // todo what?
        Row.backgroundColor = getColorFromAttr(R.attr.colorRowGroup);

        RowSong.backgroundSongColor = getColorFromAttr(R.attr.colorRowSong);
        RowSong.normalSongTextColor = getColorFromAttr(R.attr.colorTextNotPlaying);
        RowSong.normalSongDurationTextColor = getColorFromAttr(R.attr.colorTextNotPlaying);

        RowGroup.normalTextColor = getColorFromAttr(R.attr.colorTextNotPlaying);
        RowGroup.playingTextColor = getColorFromAttr(R.attr.colorTextPlaying);
        RowGroup.backgroundOverrideColor = getColorFromAttr(R.attr.colorRowGroup2nd);

        ImageView appButton = findViewById(R.id.app_button);
        appButton.setBackgroundResource(R.drawable.ic_actionbar_launcher_anim);
        appAnimation = (AnimationDrawable) appButton.getBackground();

        albumImage = findViewById(R.id.album_image);
        albumImage.setVisibility(View.VISIBLE);
        albumImage.setOnTouchListener(new OnSwipeTouchListener(getApplicationContext()) {
            public void onSwipeTop() {
                if (detailsBigCoverArt) {
                    detailsBigCoverArt = false;
                    applyBiggerCoverArt();
                } else
                    toggleDetails(null);
            }

            public void onSwipeBottom() {
                detailsBigCoverArt = true;
                applyBiggerCoverArt();
            }

            public void performClick() {
                toggleBiggerCoverArt(null);
            }
        });

        detailsBigCoverArt = false;

        ratingButtons.add(findViewById(R.id.rating_button_1));
        ratingButtons.add(findViewById(R.id.rating_button_2));
        ratingButtons.add(findViewById(R.id.rating_button_3));
        ratingButtons.add(findViewById(R.id.rating_button_4));
        ratingButtons.add(findViewById(R.id.rating_button_5));
        details_rating_layout = findViewById(R.id.details_rating);

        moreButtonsLayout = findViewById(R.id.more_buttons);
        moreButtonsLayout.setVisibility(View.GONE);
        setShuffleButton();
        setStereoButton();

        playbackSpeedText = findViewById(R.id.playBackSpeed);
        playbackSpeedText.setOnTouchListener(new OnSwipeTouchListener(getApplicationContext()) {
            public void onSwipeTop() {
                changePlaybackSpeed(0.1f);
            }

            public void onSwipeRight() {
                changePlaybackSpeed(0.2f);
            }

            public void onSwipeLeft() {
                changePlaybackSpeed(-0.2f);
            }

            public void onSwipeBottom() {
                changePlaybackSpeed(-0.1f);
            }

            public void performClick() {
                Toast.makeText(getApplicationContext(), R.string.explain_playback_speed, LENGTH_LONG).show();
            }
        });
    }

    private void askPermission() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            askPermissionAndroid11AndAbove();
        } else {
            askPermissionBelowAndroid11();
        }
    }

    private void askPermissionAndroid11AndAbove() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            //Toast.makeText(getApplicationContext(), "askPermissionAndroid11AndAbove", Toast.LENGTH_SHORT).show();
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, EXTERNAL_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, EXTERNAL_STORAGE_REQUEST_CODE);
                }
            }
        }
    }

    void askPermissionBelowAndroid11() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                Log.d("checkSelfPermission", "Permission *_EXTERNAL_STORAGE not granted! Show explanation.");
                showWarning();
            }
            Log.i("checkSelfPermission", "Permission *_EXTERNAL_STORAGE not granted! Request it.");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE
                            , Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    EXTERNAL_STORAGE_REQUEST_CODE);
        } else {
            Log.d("RequestPermissionResult", "Permission *_EXTERNAL_STORAGE already granted!");
        }
    }

    private void hideSystemBars() {
        if (params.getHideNavigationBar()) {
            WindowInsetsControllerCompat windowInsetsController =
                    ViewCompat.getWindowInsetsController(getWindow().getDecorView());
            if (windowInsetsController == null) {
                return;
            }
            // Configure the behavior of the hidden system bars
            windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
            // Hide the navigation bar
            windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars());
        }
    }

    public int getColorFromAttr(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return ContextCompat.getColor(this, typedValue.resourceId);
    }

    // connect to the service
    private final ServiceConnection musicConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("Main", "onServiceConnected");

            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicSrv = binder.getService();

            Database database = musicSrv.getDatabase();
            database.doesChangelogsMustBeShownAsync((mustBeShown) -> {
                if (mustBeShown) {
                    runOnUiThread(() -> showChangelogs());
                }
            });

            rows = musicSrv.getRows();
            songAdt = new RowsAdapter(Main.this, rows, Main.this);
            songView.setAdapter(songAdt);
            songView.setOnItemClickListener(
                    (AdapterView<?> parent, View view, int position, long id) -> {
                        if (!serviceBound)
                            return;

                        clickOnRow(position);
                    });
            songView.setOnItemLongClickListener(
                    (AdapterView<?> parent, View view, int position, long id) -> {
                        if (!serviceBound)
                            return false;

                        longClickOnRowEditMode(position);
                        return true;
                    });
            serviceBound = true;

            musicSrv.stopNotification();
            musicSrv.setMainIsVisible(true);

            // listView.getVisiblePosition() is wrong while the listview is not shown.
            // wait a bit that it is visible (should be replace by sth like onXXX)
            (new Timer()).schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(firstScroll);
                }
            }, 100);

            setRepeatButton();
            setSortButton();
            setMinRatingButton();

            // Associate app to music files (start music from a file browser)
            Intent intent = getIntent();
            Uri uri = intent.getData();
            String mimeType = intent.getType();
            if (uri != null && !uri.toString().isEmpty()) {
                Log.d("Main", "Receiving intent with uri: " + uri + ", mime: " + mimeType);
                rows = musicSrv.getRows();
                if (rows.setCurrPosFromUri(getApplicationContext(), uri)) {
                    playAlreadySelectedSong();
                }
            }
            setPlaybackSpeedText();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("Main", "onServiceDisconnected");
            serviceBound = false;
        }
    };

    private void clickOnRow(int position) {
        coverArtNum = 0;
        Row row = rows.get(position);
        if (row != null) {
            if (row.getClass() == RowGroup.class) {
                // vibrate when big font choosed
                if (params.getChoosedTextSize())
                    vibrate();

                rows.invertFold(position);
                songAdt.notifyDataSetChanged();
            } else {
                vibrate();

                rows.selectNearestSong(position);
                musicSrv.playSong();
                updatePlayButton();
                disableTrackLooper();
            }
            scrollToSong(position);
            updateRatings();
        }
    }

    private void startGroupPlayback(@NonNull RowGroup row, int position) {
        vibrate();

        coverArtNum = 0;
        var offset = 0;
        if (params.getShuffle().randomSongOrder()) {
            offset = (int)Math.floor((row.getSongCount() - 1 /* We already are playing the first song in this group */) * Math.random());
        }

        if (row.isFolded()) {
            rows.invertFold(position);
        }
        rows.selectNearestSong(position + offset);

        playAlreadySelectedSong();
        updateRatings();
    }

    private void longClickOnRowEditMode(int position) {
        Row row = rows.get(position);
        if (row != null) {
            if (row.getClass() == RowGroup.class) {
                openEditGroupMenu(position, (RowGroup) row);
            } else {
                openEditSongMenu(position, (RowSong) row);
            }
        }
    }

    private void playAlreadySelectedSong() {
        musicSrv.playSong();
        updatePlayButton();
        disableTrackLooper();
        unfoldAndscrollToCurrSong();
    }

    private void updateRatings() {
        if (serviceBound && MusicService.getEnableRating()) {
            rows.loadRatingsAsync(newRatingLoaded -> {
                if (newRatingLoaded) {
                    Log.d("Main", "newRatingLoaded");
                    runOnUiThread(() -> songAdt.notifyDataSetChanged());
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == EXTERNAL_STORAGE_REQUEST_CODE) {//Toast.makeText(getApplicationContext(), "EXTERNAL_STORAGE_REQUEST_CODE", Toast.LENGTH_SHORT).show();
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Main", "Permission READ_EXTERNAL_STORAGE granted");
                if (rows != null)
                    rows.reinit();
                if (songAdt != null)
                    songAdt.notifyDataSetChanged();
                unfoldAndscrollToCurrSong();
                hideWarning();

//                    playIntent = new Intent(Main.this, MusicService.class);
//                    startService(playIntent);
//                    bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            } else {
                Log.e("Main", "Permission READ_EXTERNAL_STORAGE refused!");
                showWarning();
            }
            if (grantResults.length > 1) {
                if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Main", "Permission WRITE_EXTERNAL_STORAGE granted");
                } else {
                    Log.w("Main", "Permission WRITE_EXTERNAL_STORAGE refused!");
                    showWarning();
                }
            }
//                playIntent = new Intent(Main.this, MusicService.class);
//                startService(playIntent);
//                bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int SET_RATING_REQUEST_CODE = 1024;
        if (requestCode == SET_RATING_REQUEST_CODE) {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.i("Main","Permission MANAGE_ALL_FILES_ACCESS_PERMISSION granted");
                } else {
                    Log.w("Main","Permission MANAGE_ALL_FILES_ACCESS_PERMISSION refused!");
                    showWarning();
                }
            }
        }
        else if (requestCode == SETTINGS_ACTION) {
            if (resultCode == SettingsPreferenceFragment.CHANGE_TEXT_SIZE)
                applyTextSize();
            else if (resultCode == SettingsPreferenceFragment.CHANGE_THEME) {
                // restart main activity
                finish();
                startActivity(getIntent());
            }
        }
    }

    private void showWarning() {
        warningText.setText(R.string.permission_needed);
        warningLayout.setVisibility(View.VISIBLE);
    }
    private void hideWarning() {
        warningLayout.setVisibility(View.GONE);
    }

    private void showChangelogs() {
        Intent intent = new Intent(this, ChangelogsActivity.class);
        startActivity(intent);
    }

    private final SeekBar.OnSeekBarChangeListener seekBarChangeListener
            = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (seekbar.getVisibility() == TextView.VISIBLE) {
                setCurrDuration(seekBar.getProgress());
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            touchSeekbar = true;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            final int states = PlayerState.Prepared |
                    PlayerState.Started |
                    PlayerState.Paused |
                    PlayerState.PlaybackCompleted;
            if (serviceBound && musicSrv.isInState(states)) {
                Log.d("Main", "onStopTrackingTouch setProgress" + RowSong.msToMinutes(seekBar.getProgress()));
                seekBar.setProgress(seekBar.getProgress());
                // valid state : {Prepared, Started, Paused, PlaybackCompleted}
                musicSrv.seekTo(seekBar.getProgress());
            }

            touchSeekbar = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("Main", "onStart");

        restore();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // updateInfo must be run in activity thread
                runOnUiThread(updateInfo);
            }
        }, 10, 500);

        if (serviceBound) {
            // if service not bound stopNotification and setMainIsVisible is called onServiceConnected
            musicSrv.stopNotification();
            musicSrv.setMainIsVisible(true);
        }
    }


    @Override
    protected void onResume(){
        super.onResume();
        Log.d("Main", "onResume");

        hideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        Log.d("Main", "onWindowFocusChanged");

        hideSystemBars();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("Main", "onStop");
        timer.cancel();

        if (serviceBound) {
            if (!finishing && musicSrv.playingLaunched())
                musicSrv.startNotification();

            musicSrv.setMainIsVisible(false);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("Main", "onDestroy");

        if (serviceBound) {
            // stop the service if not playing music
            if (!musicSrv.playingLaunched()) {
                musicSrv.stopService(playIntent);
            }
            unbindService(musicConnection);
            serviceBound = false;
            musicSrv = null;
        }
    }


    final Runnable updateInfo = new Runnable() {
        public void run() {
            if (!serviceBound)
                return;

            //Log.d("Main", "updateInfo");
            if (musicSrv.getRows().getAndSetFileToOpenFound()) {
                Log.d("Main", "Launching file to open");
                songAdt.notifyDataSetChanged();
                playAlreadySelectedSong();
            }

            if (musicSrv.getChanged()) {
                Log.d("Main", "updateInfo changed");
                vibrate();
                updatePlayButton();
                if (followSong)
                    unfoldAndscrollToCurrSong();
            } else {
                if (musicSrv.playingStopped()) {
                    stopPlayButton();
                } else if (!touchSeekbar && musicSrv.getSeekFinished()) {
                    long currPosMs = musicSrv.getCurrentPositionMs();
                    //Log.v("Main", "updateInfo setProgress" + RowSong.msToMinutes(currPosMs));
                    // getCurrentPosition {Idle, Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted}
                    seekbar.setProgress((int) currPosMs);
                }
            }
        }
    };

    final Runnable firstScroll = () -> {
        updatePlayButton();
        unfoldAndscrollToCurrSong();
    };


    private void updatePlayButton() {
        if (!serviceBound || musicSrv.playingStopped()) {
            // MediaPlayer has been destroyed or first start
            stopPlayButton();
        } else {
            openSeekButtons(seekButtonsOpened);
            if (!musicSrv.playingPaused()) {
                playButton.setImageResource(R.drawable.ic_action_pause);
                playButton.setTag(R.drawable.ic_action_pause);
                appAnimation.start();
            } else {
                playButton.setImageResource(R.drawable.ic_action_play);
                playButton.setTag(R.drawable.ic_action_play);
                appAnimation.stop();
            }

            RowSong rowSong = rows.getCurrSong();
            if (rowSong != null) {
                duration.setText(RowSong.msToMinutes(rowSong.getDurationMs()));
                duration.setVisibility(TextView.VISIBLE);
                seekbar.setMax((int) rowSong.getDurationMs());
                if (!touchSeekbar && musicSrv.getSeekFinished())
                    seekbar.setProgress((int) musicSrv.getCurrentPositionMs());
                seekbar.setVisibility(TextView.VISIBLE);
                setCurrDuration(musicSrv.getCurrentPositionMs());
            }
        }
        autoOpenCloseDetails();

        songAdt.notifyDataSetChanged();
    }

    private void setCurrDuration(long currDurationMs) {
        if (params.getShowRemainingTime()) {
            RowSong rowSong = rows.getCurrSong();
            if (rowSong != null) {
                currDuration.setText("- " +
                        RowSong.msToMinutes(rowSong.getDurationMs() - currDurationMs));
            }
        }
        else {
            currDuration.setText(RowSong.msToMinutes(currDurationMs));
        }
    }

    private void stopPlayButton() {
        duration.setVisibility(TextView.INVISIBLE);
        seekbar.setVisibility(TextView.INVISIBLE);
        currDuration.setText(R.string.app_name);
        playButton.setImageResource(R.drawable.ic_action_play);
        playButton.setTag(R.drawable.ic_action_play);
        if (!seekButtonsOpened)
            posButton.setImageDrawable(null);
        appAnimation.stop();
    }


    private void openSeekButtons(boolean open) {
        seekButtonsOpened = open;
        if (open) {
            posButton.setImageResource(R.drawable.ic_action_close_pos);
            seekButtonsLayout.setVisibility(View.VISIBLE);
        }
        else {
            posButton.setImageResource(R.drawable.ic_action_open_pos);
            seekButtonsLayout.setVisibility(View.GONE);
        }
    }

    public void toggleSeekButtons(View view) {
        openSeekButtons(!seekButtonsOpened);
    }


    private void openDetails(boolean open) {
        detailsOpened = open;
        if (open) {
            toggleDetailsButton.setImageResource(R.drawable.ic_action_close_pos);
            detailsLayout.setVisibility(View.VISIBLE);
        } else {
            toggleDetailsButton.setImageResource(R.drawable.ic_action_open_pos);
            detailsLayout.setVisibility(View.GONE);
        }
    }

    public void toggleDetails(View view) {
        openDetails(!detailsOpened);
        detailsToggledFollowAuto = hasCoverArt == detailsOpened;
    }

    private void setCoverArt(long rowSongId, Bitmap bitmap) {
        runOnUiThread(() -> {
            Log.d("Main", "setCoverArt rowSongId=" + rowSongId);
            // todo: check id and imageNum ?
            if (bitmap != null) {
                albumImage.setImageBitmap(bitmap);
            }
            else {
                albumImage.setImageResource(R.drawable.ic_default_coverart);
            }
        });
    }

    public void setDetails() {
        RowSong rowSong = rows.getCurrSong();
        if (rowSong == null) {
            return;
        }
        String title = rowSong.getTitle();
        int trackNum = rowSong.getTrack();
        if (trackNum > 0)
            title = trackNum + ". " + title;
        songTitle.setText(title);

        songArtist.setText(rowSong.getArtist());

        String album = rowSong.getAlbum();
        if (rowSong.getYear() > 1000)
            album = rowSong.getYear() + " - " + album;
        songAlbum.setText(album);

        songMime.setText(rowSong.getMime());

        new AlbumArtLoader(getApplicationContext(), rowSong).loadAsync(this::setCoverArt);

        setRatingDetails();

        clearMetadataTable();
        rowSong.loadMetadataAsync(this::showMetadataTable);
    }

    private void clearMetadataTable() {
        var c = ((TextView)findViewById(R.id.metadata_comment));
        c.setText("");
    }

    private void showMetadataTable(Tag tags) {
        runOnUiThread(() -> {
            // Comments
            var comments = new AtomicReference<String>("");
            try {
                tags.getFields(FieldKey.COMMENT).forEach(line -> comments.getAndUpdate(c -> c + line + "\n"));
            } catch (Exception ignored) { }

            var c = (TextView)findViewById(R.id.metadata_comment);
            c.setText(comments.get());
            c.setMovementMethod(LinkMovementMethod.getInstance());
        });
    }

    private void setRatingDetails() {
        if (!serviceBound)
            return;
        if (MusicService.getEnableRating()) {
            RowSong rowSong = rows.getCurrSong();
            if (rowSong != null) {
                rowSong.loadRatingAsync((rating, ratingChanged) ->
                        runOnUiThread(() ->setRatingButtonsDrawable(rating, rating > 0)));
            }
        }
        else {
            setRatingButtonsDrawable(0, false);
        }
    }

    private void setRatingButtonsDrawable(int rating, boolean highlight) {
//        if (rating <= 0) {
//            details_rating_layout.setVisibility(View.INVISIBLE);
//        } else {
        if (rating < 0)
            rating = 0;
        details_rating_layout.setVisibility(View.VISIBLE);
        for (int i = 0; i < ratingButtons.size(); i++) {
            int star0 = highlight ? R.drawable.ic_star_0_highlight : R.drawable.ic_star_0;
            int star5 = highlight ? R.drawable.ic_star_5_highlight : R.drawable.ic_star_5;
            ratingButtons.get(i).setImageResource(i < rating ? star5 : star0);
        }
//        }
    }

    public void autoOpenCloseDetails() {
        if (!serviceBound) {
            return;
        }
        RowSong rowSong = rows.getCurrSong();
        if (rowSong == null) {
            return;
        }
        new AlbumArtLoader(getApplicationContext(), rowSong).loadAsync(
                (rowSongId, bitmap) -> {
                    hasCoverArt = bitmap != null;
                    // the concept of detailsToggledFollowAuto (this is a bit not useful && fishy):
                    //   - auto mode is enable if details view state (opened or closed) is the same has
                    //     auto mode would have done.
                    if (detailsToggledFollowAuto)
                        runOnUiThread(() -> openDetails(hasCoverArt));

                    if (detailsToggledFollowAuto && !hasCoverArt) {
                        // set details later in order to not disturb details layouts close animation
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                runOnUiThread(() -> setDetails());
                            }
                        }, 500);
                    } else {
                        runOnUiThread(this::setDetails);
                    }
                });
    }


    public void toggleBiggerCoverArt(View view) {
        detailsBigCoverArt = !detailsBigCoverArt;
        applyBiggerCoverArt();
    }

    public void applyBiggerCoverArt() {
        ViewGroup.LayoutParams params = detailsLayout.getLayoutParams();
        if (detailsBigCoverArt) {
            // increase cover art size
            params.height = params.height * 2;
            detailsLayout.setLayoutParams(params);

            // hide text details
            albumImage.setLayoutParams(
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT, 0f));

            // click on image go back to normal details
            albumImage.setOnClickListener(this::toggleBiggerCoverArt);
        } else {
            // decrease cover art size
            params.height = params.height / 2;
            detailsLayout.setLayoutParams(params);

            // show text details
            albumImage.setLayoutParams(
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT, 1f));

            // click on image hide details
            albumImage.setOnClickListener(this::toggleDetails);
        }
    }

    public void deleteSongFile(@NonNull RowSong song) {
        if (song == rows.getCurrSong())
            return;
        String songTitle = song.getPath();
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_delete_song)
                .setMessage(getString(R.string.action_ask_delete_song, songTitle))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    if (rows.deleteSongFile(song)) {
                        songAdt.notifyDataSetChanged();
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.action_delete_song_ok, songTitle),
                                LENGTH_LONG).show();
                    }
                    else {
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.action_delete_song_nok, songTitle),
                                LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    public void ratingClick(View view) {
        for (int i = 0; i < ratingButtons.size(); i++) {
            if (view == ratingButtons.get(i)) {
                // we cannot unclick the first star, so 0 star means not initialized.
                rateCurrSong(i + 1);
            }
        }

//        int check = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
//        if (check == PackageManager.PERMISSION_GRANTED) {
//            rateCurrSong();
//        } else {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SET_RATING_REQUEST_CODE);
//        }
    }

    private void rateCurrSong(int rating) {
        if (!serviceBound || rows == null)
            return;
        rows.rateCurrSong(rating);
        setRatingDetails();
        songAdt.notifyDataSetChanged();
    }

//    public void openSongFolder(View view) {
//        final RowSong song = rows.getCurrSong();
//        if (song == null)
//            return;
//
//        Uri uri = Uri.fromFile(new File(song.getPath()));
//        Toast.makeText(getApplicationContext(),
//                "Opening file " + uri, Toast.LENGTH_LONG).show();
//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        intent.setDataAndType(uri, "resource/folder");
//
//        if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
//            startActivity(intent);
//        }
//        else {
//            intent = new Intent(Intent.ACTION_GET_CONTENT);
//            intent.addCategory(Intent.CATEGORY_OPENABLE);
//            intent.setDataAndType(uri, "*/*");
//            try {
//                startActivity(intent);
//            }
//            catch (android.content.ActivityNotFoundException ex) {
//                Toast.makeText(getApplicationContext(),
//                        "Please install a File Manager.", Toast.LENGTH_LONG).show();
//            }
//        }
    ////        if (intent.resolveActivityInfo(getPackageManager(), 0) != null)
    ////            found = true;
    ////        if (!found) {
    ////            intent = new Intent(Intent.ACTION_GET_CONTENT);
    ////            intent.setDataAndType(selectedUri, "*/*");
    ////            List<ResolveInfo> apps =
    ////                    getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
    ////            if (apps.size() > 0)
    ////                found = true;
    ////        }
    ////
    ////        if (found) {
    ////            startActivity(Intent.createChooser(intent, "Open folder"));
    ////            //startActivity(intent);
    ////        }
//    }

    private void openEditGroupMenu(int position, @NonNull RowGroup row) {
        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(R.drawable.ic_action_edit);
        altBld.setTitle(getString(R.string.ic_action_edit_folder, cutLongStringAndDots(row.getName())));
        final CharSequence[] items = {
                getString(R.string.action_play),
                getString(R.string.action_rate_group),
                getString(R.string.action_rate_group_overwrite),
                getString(R.string.action_rescan),
        };

        altBld.setItems(items, (DialogInterface dialog, int item) -> {
            if (musicSrv != null) {
                switch (item) {
                    case 0:
                        startGroupPlayback(row, position);
                        break;
                    case 1:
                        openRateRowMenu(row.getName(), position, false);
                        break;
                    case 2:
                        openRateRowMenu(row.getName(), position, true);
                        break;
                    case 3:
                        rescan(row);
                        break;
                }
            }
        });
        AlertDialog alert = altBld.create();
        alert.show();
    }

    private String cutLongStringAndDots(String str) {
        final String dots = "...";
        if (str.length() > 40) {
            str = str.substring(0, 40 - dots.length());
            str += dots;
        }
        return str;
    }

    private void openRateRowMenu(String rowName, int pos, boolean overwriteRating) {
        if (musicSrv == null)
            return;
        Row row = rows.get(pos);
        if (row == null) {
            return;
        }
        final boolean isRowGroup = row.getClass() == RowGroup.class;

        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(R.drawable.ic_star_5_highlight);
        altBld.setTitle(getString(isRowGroup ? R.string.action_set_rating_folder :  R.string.action_set_rating_song,
                cutLongStringAndDots(rowName)));
        CharSequence[] items = {
                "1", "2", "3", "4", "5"
        };
        // show song's current rating
        if (!isRowGroup) {
            int rate = ((RowSong) row).getRating();
            int idx = rate - 1;
            if (idx >= 0 && idx < items.length)
                items[idx] += " <- " + getString(R.string.current_rating_idx);
        }
        altBld.setItems(items, (DialogInterface dialog, int itemPos) -> rows.rateSongs(pos, itemPos + 1, overwriteRating,
                (nbChanged, errorMsg) -> runOnUiThread(() -> {
                    if (errorMsg.isEmpty()) {
                        if (nbChanged > 0) {
                            setRatingDetails();
                            songAdt.notifyDataSetChanged();
                        }

                        if (isRowGroup)
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.songs_have_been_rated, nbChanged),
                                    Toast.LENGTH_SHORT).show();
                    }
                    else {
                        Toast.makeText(getApplicationContext(), errorMsg, LENGTH_LONG).show();
                    }
                })));
        AlertDialog alert = altBld.create();
        alert.show();
    }

    private void openEditSongMenu(int position, @NonNull RowSong row) {
        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(R.drawable.ic_action_edit);
        altBld.setTitle(getString(R.string.ic_action_edit_song,
                cutLongStringAndDots(row.getTitle())));
        ArrayList<String> list = new ArrayList<>();
        list.add(getString(R.string.action_play));
        list.add(getString(R.string.action_rate_song));
        list.add(getString(R.string.show_song_details));
        list.add(getString(R.string.action_genius_lyrics));
        //getString(R.string.add_to_playlist),
        if (row != rows.getCurrSong())
            list.add(getString(R.string.action_delete_song));

        altBld.setItems(list.toArray(new CharSequence[list.size()]), (DialogInterface dialog, int item) -> {
            if (musicSrv != null) {
                switch (item) {
                    case 0:
                        clickOnRow(position);
                        break;
                    case 1:
                        openRateRowMenu(row.getTitle(), position, true);
                        break;
                    case 2:
                        showPopupSongInfo(row);
                        break;
                    case 3:
                        openGeniusLyrics(row);
                        break;
                    case 4:
                        deleteSongFile(row);
                        break;
                }
            }
        });
        AlertDialog alert = altBld.create();
        alert.show();
    }

    private void openGeniusLyrics(RowSong song) {
        var searchTerm = "";
        if (song.getTitle().isBlank() || song.getTitle().equals("<unknown>") || song.getArtist().isBlank() || song.getArtist().equals("<unknown>")) {
            searchTerm = song.getFilename();
            // remove extension (any dot that is in the last 5 characters)
            if (searchTerm.contains(".") && searchTerm.lastIndexOf('.') >= searchTerm.length() - 5) {
                searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf('.'));
            }
        } else {
            searchTerm = song.getArtist() + " " + song.getTitle();
            Log.d("SearchTerm", searchTerm);
        }


        // Remove dashes
        searchTerm = searchTerm.replaceAll(" - ", " ");
        // Remove remixes, like "Simply Red - Something got me started (Hourleys House Remix)"
        // We only delete those at the end of the string, for instance, to keep this song title intact:
        // "(This song is just) six words long.mp3" (A song from 'Weird Al' Yankovic)
        searchTerm = searchTerm.replaceAll("\\(([^)]+)\\)$", "");
        // Remove leading numbers (01. some song, 2. some song)
        searchTerm = searchTerm.replaceAll("^(\\d+).", "");
        // Remove braces, Genius will cut off the string beginning at the first brace
        searchTerm = searchTerm.replaceAll("\\(", "");
        searchTerm = searchTerm.replaceAll("\\)", "");

        searchTerm = URLEncoder.encode(searchTerm);

        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://genius.com/search?q=" + searchTerm));
            startActivity(browserIntent);
        } catch (Exception e) {
            Log.e("sicmu", "Error while creating genius.com URL: " + e);
        }
    }

    private void showPopupSongInfo(RowSong rowSong) {
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_song_details, null);

        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

        popupWindow.showAtLocation(findViewById(R.id.main_layout), Gravity.CENTER, 0, 0);
        ((TextView) popupView.findViewById(R.id.detail_artist)).setText(
                getString(R.string.popup_song_artist, rowSong.getArtist()));
        ((TextView) popupView.findViewById(R.id.detail_album)).setText(
                getString(R.string.popup_song_album, rowSong.getAlbum()));
        ((TextView) popupView.findViewById(R.id.detail_title)).setText(
                getString(R.string.popup_song_title, rowSong.getTitle()));
        ((TextView) popupView.findViewById(R.id.detail_track)).setText(
                getString(R.string.popup_song_track, rowSong.getTrack()));
        ((TextView) popupView.findViewById(R.id.detail_year)).setText(
                getString(R.string.popup_song_year, rowSong.getYear()));
        ((TextView) popupView.findViewById(R.id.detail_rating)).setText(
                getString(R.string.popup_song_rating, rowSong.getRating()));
        ((TextView) popupView.findViewById(R.id.detail_mime)).setText(
                getString(R.string.popup_song_mime, rowSong.getMime()));
        ((TextView) popupView.findViewById(R.id.detail_path)).setText(
                getString(R.string.popup_song_path, rowSong.getPath()));
        popupView.setOnTouchListener((view,  event) -> {
            popupWindow.dismiss();
            return true;
        });
    }

    private void rescan(@NonNull RowGroup rowGroup) {
        Toast.makeText(getApplicationContext(),
                getString(R.string.start_rescan) + rowGroup.getPath(),
                Toast.LENGTH_SHORT).show();
        Path.scanMediaFolder(getApplicationContext(), rowGroup.getPath(), (String path, Uri uri) ->
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(), getString(R.string.rescanned) + path, LENGTH_LONG).show();
                    if (rows != null)
                        rows.reinit();
                    unfoldAndscrollToCurrSong();
                })
        );
    }

    private void openSortMenu() {
        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(getSortResId());
        altBld.setTitle(getString(R.string.action_sort));
        final CharSequence[] items = {
                getString(R.string.action_sort_tree),
                getString(R.string.action_sort_folder),
                getString(R.string.action_sort_artist)
        };

        int checkedItem;
        if (rows.getFilter() == Filter.TREE)
            checkedItem = 0;
        else if (rows.getFilter() == Filter.FOLDER)
            checkedItem = 1;
        else
            checkedItem = 2;

        altBld.setSingleChoiceItems(items, checkedItem, (DialogInterface dialog, int item) -> {
            if (musicSrv != null) {
                Filter oldFilter = rows.getFilter();
                switch (item) {
                    case 0:
                        rows.setFilter(Filter.TREE);
                        break;
                    case 1:
                        rows.setFilter(Filter.FOLDER);
                        break;
                    case 2:
                        rows.setFilter(Filter.ARTIST);
                        break;
                }
                if (oldFilter != rows.getFilter()) {
                    songAdt.notifyDataSetChanged();
                    unfoldAndscrollToCurrSong();
                    setSortButton();
                }
                dialog.dismiss(); // dismiss the alertbox after chose option
            }
        });
        AlertDialog alert = altBld.create();
        alert.show();
    }


    private void openRepeatMenu() {
        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(getRepeatResId());
        altBld.setTitle(getString(R.string.action_repeat_title));
        final CharSequence[] items = {
                getString(R.string.action_repeat_all),
                getString(R.string.action_repeat_group),
                getString(R.string.action_repeat_one),
                getString(R.string.action_repeat_not),
                getString(R.string.action_stop_at_end_of_track),
        };

        int checkedItem;
        if (rows.getRepeatMode() == RepeatMode.REPEAT_ALL)
            checkedItem = 0;
        else if (rows.getRepeatMode() == RepeatMode.REPEAT_GROUP)
            checkedItem = 1;
        else if (rows.getRepeatMode() == RepeatMode.REPEAT_ONE)
            checkedItem = 2;
        else if (rows.getRepeatMode() == RepeatMode.REPEAT_NOT)
            checkedItem = 3;
        else
            checkedItem = 4;

        altBld.setSingleChoiceItems(items, checkedItem, (DialogInterface dialog, int item) -> {
            if (musicSrv != null) {
                switch (item) {
                    case 0:
                        rows.setRepeatMode(RepeatMode.REPEAT_ALL);
                        break;
                    case 1:
                        rows.setRepeatMode(RepeatMode.REPEAT_GROUP);
                        break;
                    case 2:
                        rows.setRepeatMode(RepeatMode.REPEAT_ONE);
                        break;
                    case 3:
                        rows.setRepeatMode(RepeatMode.REPEAT_NOT);
                        break;
                    case 4:
                        rows.setRepeatMode(RepeatMode.STOP_AT_END_OF_TRACK);
                        break;
                }
                dialog.dismiss(); // dismiss the alertbox after chose option
                setRepeatButton();
            }
        });
        AlertDialog alert = altBld.create();
        alert.show();
    }

    private void openRatingMenu() {
        if (musicSrv == null)
            return;

        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(getMinRatingResId());
        altBld.setTitle(getString(R.string.action_min_rating));
        final CharSequence[] items = {
                "1", "2", "3", "4", "5"
        };

        altBld.setSingleChoiceItems(items, musicSrv.getMinRating() - 1,
                (DialogInterface dialog, int item) -> {
                    if (musicSrv != null) {
                        musicSrv.setMinRating(item + 1);
                        setMinRatingButton();
                        dialog.dismiss(); // dismiss the alertbox after chose option
                    }
                });
        AlertDialog alert = altBld.create();
        alert.show();
    }

    public void openSearchDialog(View view) {
        if (musicSrv == null)
            return;

        // Init dialog
        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(R.drawable.ic_action_search);
        altBld.setTitle(getString(R.string.action_search));

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);

        // Set up text box
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(prefs.getString("search_query", ""));
        altBld.setView(input);

        Context ctx = this;

        // Set up the search button
        altBld.setPositiveButton("OK", (dialog, which) -> {
            String query = input.getText().toString();
            // Save query for quick access later
            prefs.edit().putString("search_query", query).apply();

            Row row = rows.getNextSongByKeyword(query);
            if (row == null) {
                Snackbar.make(view, R.string.search_unsuccessful, BaseTransientBottomBar.LENGTH_LONG).show();
                return;
            }

            // clickOnRow(...) only works using folded indexes, so we have
            // to unfold the parent groups first, and then play the song itself

            // Collect parent elements, then unfold them in reverse order
            // (topmost group/folder -> deepest group/folder)
            ArrayList<RowGroup> parents = new ArrayList<>();
            for(RowGroup group = (RowGroup)row.getParent(); group != null; group = (RowGroup)group.getParent()) {
                parents.add(group);
            }
            for(int i = parents.size() - 1; i >= 0; i--) {
                RowGroup parent = parents.get(i);
                if(parent.isFolded())
                    clickOnRow(rows.getFoldedIndex(parent));
            }

            // Don't click (=close) unfolded groups! Just scroll to them.
            if(row instanceof RowGroup && !((RowGroup) row).isFolded()) {
                scrollToSong(rows.getFoldedIndex(row));
            } else { // Click it! (i.e. open a group or play a song)
                clickOnRow(rows.getFoldedIndex(row));
            }
        });

        altBld.show();
    }

    private int getRepeatResId() {
        int res;
        switch (rows.getRepeatMode()) {
            case REPEAT_ONE: res = R.drawable.ic_menu_repeat_one; break;
            case REPEAT_GROUP: res = R.drawable.ic_menu_repeat_group; break;
            case REPEAT_ALL: res = R.drawable.ic_menu_repeat_all; break;
            case REPEAT_NOT: res = R.drawable.ic_menu_repeat_not; break;
            default: res = R.drawable.ic_menu_stop_at_end_of_track;
        }
        return res;
    }

    private int getTextSizeResId() {
        if (params.getChoosedTextSize())
            return R.drawable.ic_menu_text_big;
        else
            return R.drawable.ic_menu_text_regular;
    }

    private int getSortResId() {
        int res;
        switch (rows.getFilter()) {
            case ARTIST: res = R.drawable.ic_menu_artist; break;
            case FOLDER: res = R.drawable.ic_menu_folder; break;
            default: res = R.drawable.ic_menu_tree;
        }
        return res;
    }

    private int getMinRatingResId() {
        int res;
        switch (musicSrv.getMinRating()) {
            case 5: res = R.drawable.ic_star_5_highlight; break;
            case 4: res = R.drawable.ic_star_4_highlight; break;
            case 3: res = R.drawable.ic_star_3_highlight; break;
            case 2: res = R.drawable.ic_star_2_highlight; break;
            default: res = R.drawable.ic_star_1_highlight;
        }
        return res;
    }

    private int getShuffleResId() {
        switch(params.getShuffle()) {
            case SEQUENTIAL:
                return R.drawable.ic_menu_no_shuffle;
            case RANDOM:
                return R.drawable.ic_menu_shuffle;
            case RADIO:
                return R.drawable.ic_menu_shuffle_radio;
        }
        throw new IllegalStateException("Shuffle mode is invalid");
    }

    private void setRepeatButton() {
        ImageView img = findViewById(R.id.repeat_button);
        img.setImageResource(getRepeatResId());
    }

    private void setSortButton() {
        ImageView img = findViewById(R.id.sort_button);
        img.setImageResource(getSortResId());
    }

    ///  Sets the stereo button image to reflect the Mono/Stereo setting
    private void setStereoButton() {
        var stereo = params.getStereo();
        ImageButton btn = findViewById(R.id.stereo_button);
        if (stereo) {
            btn.setImageResource(R.drawable.ic_stereo);
        } else {
            btn.setImageResource(R.drawable.ic_mono);
        }
    }

    ///  This contacts the music service to apply the stereo setting
    private void applyStereo() {
        if(musicSrv == null) {
            return;
        }
        musicSrv.applyStereo(params.getStereo());
    }

    private void setShuffleButton() {
        ImageButton shuffleButton = findViewById(R.id.shuffle_button);
        shuffleButton.setImageResource(getShuffleResId());
    }

    private void setMinRatingButton() {
        ImageView img = findViewById(R.id.rating_button);
        img.setImageResource(getMinRatingResId());
    }

    public void fold() {
        if(musicSrv != null) {
            rows.fold();
            songAdt.notifyDataSetChanged();
            unfoldAndscrollToCurrSong();
        }
    }

    public void unfold() {
        if(musicSrv != null) {
            rows.unfold();
            songAdt.notifyDataSetChanged();
            scrollToCurrSong();
        }
    }

    public void playOrPause(View view) {
        if(!serviceBound)
            return;

        if (musicSrv.isInState(PlayerState.Started)) {
            // valid state {Started, Paused, PlaybackCompleted}
            // if the player is between idle and prepared state, it will not be paused!
            musicSrv.pause();
        }
        else {
            if (musicSrv.isInState(PlayerState.Paused)) {
                // previously paused. Valid state {Prepared, Started, Paused, PlaybackCompleted}
                musicSrv.start();
            }
            else {
                musicSrv.playSong();
            }
        }

        updatePlayButton();
    }

    public void playNext(View view){
        if(!serviceBound)
            return;

        coverArtNum = 0;
        musicSrv.playNext();
        updatePlayButton();
        disableTrackLooper();
        if(followSong)
            unfoldAndscrollToCurrSong();
    }

    public void playPrev(View view){
        if(!serviceBound)
            return;

        coverArtNum = 0;
        musicSrv.playPrev();
        updatePlayButton();
        disableTrackLooper();
        if(followSong)
            unfoldAndscrollToCurrSong();
    }

    public void seek(View view){
        if(!serviceBound)
            return;
        long newPosMs = musicSrv.getCurrentPositionMs();
        var id = view.getId();
        if (id == R.id.m5_button) {
            newPosMs -= 5*1000;
        } else if (id == R.id.p5_button) {
            newPosMs += 5*1000;
        } else if (id == R.id.m20_button || id == R.id.m20_text) {
            newPosMs -= 20*1000;
        } else if (id == R.id.p20_button || id == R.id.p20_text) {
            newPosMs += 20*1000;
        }

        newPosMs = newPosMs < 0 ? 0 : newPosMs;

        if (newPosMs >= musicSrv.getDurationMs())
            playNext(null);
        else
            musicSrv.seekTo(newPosMs);
    }

    private final long trackLooperDisabledVal = -1;
    private long trackLooperAPosMs = trackLooperDisabledVal;
    private long trackLooperBPosMs = trackLooperDisabledVal;
    public void trackLooperClick(View view)
    {
        if (!serviceBound)
            return;
        ImageButton trackLooperBtn = findViewById(R.id.track_looper_button);
        if (trackLooperAPosMs == trackLooperDisabledVal) {
            trackLooperAPosMs = musicSrv.getCurrentPositionMs();
            trackLooperBtn.setImageResource(R.drawable.ic_track_looper_a);
        }
        else if (trackLooperBPosMs == trackLooperDisabledVal) {
            trackLooperBPosMs = musicSrv.getCurrentPositionMs();
            musicSrv.enableTrackLooper(trackLooperAPosMs, trackLooperBPosMs);
            trackLooperBtn.setImageResource(R.drawable.ic_track_looper_ab);
        }
        else {
            disableTrackLooper();
        }
    }

    public void disableTrackLooper()
    {
        trackLooperAPosMs = trackLooperDisabledVal;
        trackLooperBPosMs = trackLooperDisabledVal;
        if (serviceBound)
            musicSrv.disableTrackLooper();
        ImageButton trackLooperBtn = findViewById(R.id.track_looper_button);
        trackLooperBtn.setImageResource(R.drawable.ic_track_looper);
    }

    private void changePlaybackSpeed(float step) {
        if (serviceBound) {
            musicSrv.changePlaybackSpeed(step);
            setPlaybackSpeedText();
        }
    }
    private void setPlaybackSpeedText() {
        if (serviceBound) {
            DecimalFormat decimalFormat = new DecimalFormat("#0.0x");
            decimalFormat.setDecimalSeparatorAlwaysShown(true);
            playbackSpeedText.setText(decimalFormat.format(musicSrv.getPlaybackSpeed()));
        }
    }

    private final View.OnTouchListener touchListener = (v, event) -> {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            vibrate();
        }
        return false;
    };

    private final View.OnLongClickListener gotoSongLongListener = v -> {
        fold();
        return true;
    };

    private final View.OnLongClickListener nextGroupLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if(!serviceBound)
                return false;

            coverArtNum = 0;
            musicSrv.playNextGroup();
            updatePlayButton();
            disableTrackLooper();
            if(followSong)
                unfoldAndscrollToCurrSong();

            return true;
        }
    };

    private final View.OnLongClickListener prevGroupLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if(!serviceBound)
                return false;

            coverArtNum = 0;
            musicSrv.playPrevGroup();
            updatePlayButton();
            disableTrackLooper();
            if(followSong)
                unfoldAndscrollToCurrSong();

            return true;
        }
    };

    private final RepeatingImageButton.RepeatListener rewindListener =
            new RepeatingImageButton.RepeatListener() {
                /**
                 * This method will be called repeatedly at roughly the interval
                 * specified in setRepeatListener(), for as long as the button
                 * is pressed.
                 *
                 * @param view           The button as a View.
                 * @param duration    The number of milliseconds the button has been pressed so far.
                 * @param repeatcount The number of previous calls in this sequence.
                 *                    If this is going to be the last call in this sequence (i.e. the user
                 *                    just stopped pressing the button), the value will be -1.
                 */
                public void onRepeat(View view, long duration, int repeatcount) {
                    Log.d("Main", "-- repeatcount: " + repeatcount + " duration: " + duration);
                    if (repeatcount <= 0)
                        return;

                    long newPosMs = musicSrv.getCurrentPositionMs() - getSeekOffsetSec(view, duration);
                    Log.d("Main", "<-- currpos: " + musicSrv.getCurrentPositionMs() + " seekto: " + newPosMs);
                    newPosMs = newPosMs < 0 ? 0 : newPosMs;
                    musicSrv.seekTo(newPosMs);
                }
            };

    private long getSeekOffsetSec(View view, long duration) {
        long offsetMs = 0;
        var id = view.getId();
        if (id == R.id.m5_button || id == R.id.p5_button) {
            offsetMs = 5000;
        } else if (id == R.id.m20_button || id == R.id.m20_text || id == R.id.p20_button || id == R.id.p20_text) {
            if (duration < 5000) {
                // seek at 10x speed for the first 5 seconds
                offsetMs = duration * 10;
            } else {
                // seek at 40x after that
                offsetMs = 50000 + (duration - 5000) * 40;
            }
        }
        return offsetMs;
    }

    private final RepeatingImageButton.RepeatListener forwardListener =
            new RepeatingImageButton.RepeatListener() {
                public void onRepeat(View view, long duration, int repeatcount) {
                    Log.d("Main", "-- repeatcount: " + repeatcount + " duration: " + duration);

                    if (repeatcount <= 0)
                        return;

                    long newPosMs = musicSrv.getCurrentPositionMs() + getSeekOffsetSec(view, duration);
                    Log.d("Main", "--> currpos: " + musicSrv.getCurrentPositionMs() + " seekto: " + newPosMs);
                    if (newPosMs >= musicSrv.getDurationMs())
                        playNext(null);
                    else
                        musicSrv.seekTo(newPosMs);
                }
            };

    public void gotoCurrSong(View view) {
        unfoldAndscrollToCurrSong();
    }

    public void toggleMoreButtons(View view) {
        //ImageButton more_button = findViewById(R.id.more_button);
        if (isEditModeEnabled()) {
            stopCloseMoreButtonsTimer();

            moreButtonsLayout.setVisibility(View.GONE);
            //more_button.setImageResource(R.drawable.ic_action_note);
        } else {
            moreButtonsLayout.setVisibility(View.VISIBLE);

            startCloseMoreButtonsTimer();
            //more_button.setImageResource(R.drawable.ic_action_edit);
        }
    }
    private boolean isEditModeEnabled() {
        return moreButtonsLayout.getVisibility() == View.VISIBLE;
    }

    private void startCloseMoreButtonsTimer() {
        stopCloseMoreButtonsTimer();

        closeMoreButtonsTimer = new Timer();
        closeMoreButtonsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> moreButtonsLayout.setVisibility(View.GONE));
            }
        }, 16 * 1000);
    }
    private void stopCloseMoreButtonsTimer() {
        if (closeMoreButtonsTimer != null) {
            closeMoreButtonsTimer.cancel();
            closeMoreButtonsTimer = null;
        }
    }


    private final int SETTINGS_ACTION = 1;
    public void openSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, SETTINGS_ACTION);
        startCloseMoreButtonsTimer();
    }


    public void openSort(View view) {
        openSortMenu();
        startCloseMoreButtonsTimer();
    }

    public void openRepeat(View view) {
        openRepeatMenu();
        startCloseMoreButtonsTimer();
    }

    public void changeShuffle(View view) {
        var mode = params.getShuffle().next();
        params.setShuffle(mode);
        setShuffleButton();
        mode.showExplainSnackbar(view);
        startCloseMoreButtonsTimer();
    }

    public void toggleStereo(View view) {
        var stereo = !params.getStereo();
        params.setStereo(stereo);
        setStereoButton();
        showStereoSnackbar(view);
        applyStereo();
    }

    /// Shows a Snackbar showing "Stereo" or "Mono"
    public void showStereoSnackbar(View v) {
        int toastText;
        if (params.getStereo()) {
            toastText = R.string.settings_stereo_on;
        } else {
            toastText = R.string.settings_stereo_off;
        }
        Snackbar.make(v, toastText, BaseTransientBottomBar.LENGTH_SHORT).show();
    }

    /*
        public void openTextSize(View view) {
            params.setChooseTextSize(!params.getChoosedTextSize());
            ImageButton img = findViewById(R.id.text_size_button);
            if (params.getChoosedTextSize()) {
                img.setImageResource(R.drawable.ic_menu_text_big);
            }
            else {
                img.setImageResource(R.drawable.ic_menu_text_regular);
            }
            applyTextSize();
            songAdt.notifyDataSetChanged();
            setTextSizeButton();
            startCloseMoreButtonsTimer();
        }
    */
    public void openMinRating(View view) {
        openRatingMenu();
        startCloseMoreButtonsTimer();
    }

    public void unfoldAndscrollToCurrSong() {
        if (rows == null)
            return;
        if(rows.unfoldCurrPos())
            songAdt.notifyDataSetChanged();
        scrollToSong(rows.getCurrPos());
        updateRatings();
    }

    public void scrollToCurrSong() {
        scrollToSong(rows.getCurrPos());
    }

    // this method could be improved, code is a bit obscure :-)
    public void scrollToSong(int gotoSong) {
        Log.d("Main", "scrollToSong getCurrPos:" + gotoSong);

        if(rows.size() == 0 || gotoSong < 0 || gotoSong >= rows.size())
            return;

        int first = songView.getFirstVisiblePosition();
        int last = songView.getLastVisiblePosition();
        int nbRow = last - first;
        // on ListView startup getVisiblePosition gives strange result
        if (nbRow < 0) {
            nbRow = 1;
            last = first + 1;
        }
        Log.d("Main", "scrollToSong first: " + first + " last: " + last + " nbRow: " + nbRow);

        // to show a bit of songItems before or after the cur song
        int showAroundTop = nbRow / 5;
        showAroundTop = Math.max(showAroundTop, 1);
        // show more song after the gotoSong
        int showAroundBottom = nbRow / 2;
        showAroundBottom = Math.max(showAroundBottom, 1);
        Log.d("Main", "scrollToSong showAroundTop: " + showAroundTop + " showAroundBottom: " + showAroundBottom);


        // how far from top or bottom border the song is
        int offset = 0;
        if(gotoSong > last)
            offset = gotoSong - last;
        if(gotoSong < first)
            offset = first - gotoSong;

        // deactivate smooth if too far
        int smoothMaxOffset = 50;
        if(offset > smoothMaxOffset) {
            // setSelection set position at top of the screen
            gotoSong -= showAroundTop;
            if(gotoSong < 0)
                gotoSong = 0;
            songView.setSelection(gotoSong);
        }
        else {
            // smoothScrollToPosition only make position visible
            if(gotoSong + showAroundBottom >= last) {
                gotoSong += showAroundBottom;
                if(gotoSong >= rows.size())
                    gotoSong = rows.size() - 1;
            }
            else {
                gotoSong -= showAroundTop;
                if(gotoSong < 0)
                    gotoSong = 0;
            }
            songView.smoothScrollToPosition(gotoSong);
        }

        Log.d("Main", "scrollToSong position:" + gotoSong);
    }



    public MusicService getMusicSrv() {
        return musicSrv;
    }

    public void applyTextSize() {
        int textSize;
        if (!params.getChoosedTextSize())
            textSize = params.getNormalTextSize();
        else
            textSize = params.getBigTextSize();

        RowSong.textSize = textSize;
        RowGroup.textSize = (int) (textSize * params.getTextSizeRatio());
        if (songAdt != null)
            songAdt.notifyDataSetChanged();
    }

    private void restore() {
        followSong = params.getFollowSong();
        applyTextSize();
    }

    private void vibrate() {
        if (params.getVibrate())
            vibrator.vibrate(20);
    }

    /// This callback is used for audio channel config. Each different set of audio output
    /// devices generates a hardware ID, each having a stereo config. This allows us to
    /// have different configs, for instance, for our AirPods and our Phone speaker.
    private AudioDeviceCallback audioDeviceStereoConfigCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            setStereoButton();
            applyStereo();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            setStereoButton();
            applyStereo();
        }
    };
}

