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

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.os.Build.VERSION.SDK_INT;

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

    private ImageButton posButton;

    // true if the user want to disable lockscreen
    private boolean noLock;

    // true if you want to keep the current song played visible
    private boolean followSong;

    private boolean seekButtonsOpened;
    private boolean detailsOpened;
    private boolean detailsToggledFollowAuto;
    private boolean hasCoverArt;

    private int menuToOpen;

    private Parameters params;

    private Vibrator vibrator;

    private AnimationDrawable appAnimation;

    private LinearLayout detailsLayout;
    private LinearLayout seekButtonsLayout;
    private TextView playbackSpeedText;
    private LinearLayout warningLayout;

    private LinearLayout moreButtonsLayout;

    private ImageButton albumImage;
    private TextView songTitle, songAlbum, songArtist, warningText;
    ArrayList<ImageButton> ratingButtons = new ArrayList<>();
    private LinearLayout details_rating_layout;
    private LinearLayout details_right_layout;
    private boolean detailsBigCoverArt;
    private int coverArtNum = 0;
    private final int EXTERNAL_STORAGE_REQUEST_CODE = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("Main", "onCreate");

        params = new ParametersImpl(this);

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

        songView = (ListView) findViewById(R.id.song_list);
        playButton = (ImageButton) findViewById(R.id.play_button);
        // useful only for testing
        playButton.setTag(R.drawable.ic_action_play);
        playButton.setOnTouchListener(touchListener);

        ImageButton gotoButton = (ImageButton) findViewById(R.id.goto_button);
        gotoButton.setOnTouchListener(touchListener);
        gotoButton.setOnLongClickListener(gotoSongLongListener);
//        ImageButton lockButton = (ImageButton) findViewById(R.id.lock_button);
//        lockButton.setOnTouchListener(touchListener);

        posButton = (ImageButton) findViewById(R.id.toggle_seek_buttons);
        seekButtonsOpened = false;
        posButton.setImageDrawable(null);
        seekButtonsLayout = (LinearLayout) findViewById(R.id.seek_buttons_layout);
        seekButtonsLayout.setVisibility(View.GONE);
        warningLayout = findViewById(R.id.warning_layout);
        warningLayout.setVisibility(View.GONE);
        warningLayout.setOnClickListener(view -> {
            hideWarning();
        });
        detailsLayout = (LinearLayout) findViewById(R.id.details_layout);
        detailsLayout.setVisibility(View.GONE);
        detailsToggledFollowAuto = true;

        final int repeatDelta = 260;
        ImageButton prevButton = (ImageButton) findViewById(R.id.prev_button);
        prevButton.setOnLongClickListener(prevGroupLongListener);
        prevButton.setOnTouchListener(touchListener);
        ImageButton nextButton = (ImageButton) findViewById(R.id.next_button);
        nextButton.setOnLongClickListener(nextGroupLongListener);
        nextButton.setOnTouchListener(touchListener);

        RepeatingImageButton seekButton;
        seekButton = (RepeatingImageButton) findViewById(R.id.m20_button);
        seekButton.setRepeatListener(rewindListener, repeatDelta);
        seekButton.setOnTouchListener(touchListener);
        seekButton = (RepeatingImageButton) findViewById(R.id.p20_button);
        seekButton.setRepeatListener(forwardListener, repeatDelta);
        seekButton.setOnTouchListener(touchListener);
        seekButton = (RepeatingImageButton) findViewById(R.id.m5_button);
        seekButton.setRepeatListener(rewindListener, repeatDelta);
        seekButton.setOnTouchListener(touchListener);
        seekButton = (RepeatingImageButton) findViewById(R.id.p5_button);
        seekButton.setRepeatListener(forwardListener, repeatDelta);
        seekButton.setOnTouchListener(touchListener);

        songTitle = findViewById(R.id.detail_title);
        songAlbum = findViewById(R.id.detail_album);
        songArtist = findViewById(R.id.detail_artist);
        warningText = findViewById(R.id.warning_text);

//        if (SDK_INT >= Build.VERSION_CODES.R) {
//            if (!Environment.isExternalStorageManager()) {
//                try {
//                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
//                    intent.addCategory("android.intent.category.DEFAULT");
//                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
//                    startActivityForResult(intent, EXTERNAL_STORAGE_REQUEST_CODE);
//                } catch (Exception e) {
//                    Intent intent = new Intent();
//                    intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
//                    startActivityForResult(intent, EXTERNAL_STORAGE_REQUEST_CODE);
//                }
//            }
//        } else {
        // below android 11
        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//                        || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                Log.d("checkSelfPermission", "Permission *_EXTERNAL_STORAGE not granted! Show explanation.");
                showWarning();
            }
            Log.i("checkSelfPermission", "Permission *_EXTERNAL_STORAGE not granted! Request it.");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE
//                                , Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    EXTERNAL_STORAGE_REQUEST_CODE);
        } else {
            Log.d("RequestPermissionResult", "Permission *_EXTERNAL_STORAGE already granted!");
        }
//        }

        playIntent = new Intent(this, MusicService.class);
        startService(playIntent);
        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);

        duration = (TextView) findViewById(R.id.duration);
        currDuration = (TextView) findViewById(R.id.curr_duration);
        touchSeekbar = false;
        seekbar = (SeekBar) findViewById(R.id.seek_bar);
        seekbar.setOnSeekBarChangeListener(seekBarChangeListener);

        followSong = false;

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        // tells the OS that the volume buttons should affect the "media" volume when your application is visible
        setVolumeControlStream(AudioManager.STREAM_MUSIC);


        // set the color statically for speed (don't know another prettier method)
        Row.levelOffset = 14; // todo what?
        Row.backgroundColor = getColorFromAttr(R.attr.colorRowGroup);

        RowSong.backgroundSongColor = getColorFromAttr(R.attr.colorRowSong);
        RowSong.normalSongTextColor = getColorFromAttr(R.attr.colorTextNotPlaying);
        RowSong.normalSongDurationTextColor = getColorFromAttr(R.attr.colorTextNotPlaying);

        RowGroup.normalTextColor = getColorFromAttr(R.attr.colorTextNotPlaying);
        RowGroup.playingTextColor = getColorFromAttr(R.attr.colorTextPlaying);
        RowGroup.backgroundOverrideColor = getColorFromAttr(R.attr.colorRowGroup2nd);

        ImageView appButton = (ImageView) findViewById(R.id.app_button);
        appButton.setBackgroundResource(R.drawable.ic_actionbar_launcher_anim);
        appAnimation = (AnimationDrawable) appButton.getBackground();

        albumImage = (ImageButton) findViewById(R.id.album_image);
        albumImage.setVisibility(View.VISIBLE);
        albumImage.setOnTouchListener(new OnSwipeTouchListener(getApplicationContext()) {
            public void onSwipeTop() {
                if (detailsBigCoverArt == true) {
                    detailsBigCoverArt = false;
                    applyBiggerCoverArt();
                } else
                    toggleDetails(null);
            }

            public void onSwipeRight() {
                if (coverArtNum > 0) {
                    coverArtNum--;
                    setDetails();
                }
            }

            public void onSwipeLeft() {
                RowSong rowSong = rows.getCurrSong();
                if (rowSong != null)
                    rowSong.getAlbumBmpAsync(getApplicationContext(), coverArtNum + 1,
                            (rowSongId, imageNum, bitmap) -> {
                                coverArtNum++;
                                setCoverArt(rowSongId, imageNum, bitmap);
                            });
            }

            public void onSwipeBottom() {
                detailsBigCoverArt = true;
                applyBiggerCoverArt();
            }

            public void performClick() {
                toggleBiggerCoverArt(null);
            }
        });

        details_right_layout = (LinearLayout) findViewById(R.id.details_right_layout);
        detailsBigCoverArt = false;

        ratingButtons.add((ImageButton) findViewById(R.id.rating_button_1));
        ratingButtons.add((ImageButton) findViewById(R.id.rating_button_2));
        ratingButtons.add((ImageButton) findViewById(R.id.rating_button_3));
        ratingButtons.add((ImageButton) findViewById(R.id.rating_button_4));
        ratingButtons.add((ImageButton) findViewById(R.id.rating_button_5));
        details_rating_layout = (LinearLayout) findViewById(R.id.details_rating);

        moreButtonsLayout = findViewById(R.id.more_buttons);
        moreButtonsLayout.setVisibility(View.GONE);
        setShuffleButton();
        setTextSizeButton();

        playbackSpeedText = (TextView) findViewById(R.id.playBackSpeed);
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
                Toast.makeText(getApplicationContext(), R.string.explain_playback_speed, Toast.LENGTH_LONG).show();
            }
        });
    }

    public int getColorFromAttr(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return ContextCompat.getColor(this, typedValue.resourceId);
    }

    // connect to the service
    private ServiceConnection musicConnection = new ServiceConnection() {

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
                else {
                    // show donate if changelogs activity has not be launched
                    database.doesDonateMustBeShownAsync((donateMustBeShown) -> {
                        if (donateMustBeShown)
                            runOnUiThread(() -> showDonate());
                    });
                }
            });

            rows = musicSrv.getRows();
            songAdt = new RowsAdapter(Main.this, rows, Main.this);
            songView.setAdapter(songAdt);
            songView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {
                    if (!serviceBound)
                        return;

                    coverArtNum = 0;
                    Row row = rows.get(position);
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
            });
            songView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view,
                                               int position, long id) {
                    if (!serviceBound)
                        return false;

                    vibrate();

                    coverArtNum = 0;
                    rows.selectNearestSong(position);
                    playAlreadySelectedSong();
                    updateRatings();

                    return true;
                }
            });
            serviceBound = true;

            musicSrv.stopNotification();
            musicSrv.setMainIsVisible(true);

            // listView.getVisiblePosition() is wrong while the listview is not shown.
            // wait a bit that it is visible (should be replace by sthg like onXXX)
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
                Log.d("Main", "Receiving intent with uri: " + uri.toString() + ", mime: " + mimeType);
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
        switch (requestCode) {
            case EXTERNAL_STORAGE_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Main","Permission READ_EXTERNAL_STORAGE granted");
                    rows.reinit();
                    songAdt.notifyDataSetChanged();
                    unfoldAndscrollToCurrSong();
                    hideWarning();
                }  else {
                    Log.e("Main","Permission READ_EXTERNAL_STORAGE refused!");
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
                return;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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

    private void showDonate() {
        Intent intent = new Intent(this, DonateActivity.class);
        startActivity(intent);
    }

    private void showChangelogs() {
        Intent intent = new Intent(this, ChangelogsActivity.class);
        startActivity(intent);
    }

    private SeekBar.OnSeekBarChangeListener seekBarChangeListener
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
        applyLock();

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

/*
    @Override
    protected void onResume(){
        super.onResume();
        Log.d("Main", "onResume");
    }


    @Override
    protected void onPause(){
        super.onPause();
        Log.d("Main", "onPause");
    }
*/

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("Main", "onStop");
        timer.cancel();
        save();

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
                if (Flavor.isFlavorFDroid(getApplicationContext())) {
                    Toast.makeText(getApplicationContext(),
                            getResources().getString(R.string.app_name) + " destroyed.",
                            Toast.LENGTH_SHORT).show();
                }
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
            detailsLayout.setVisibility(View.VISIBLE);
        }
        else {
            detailsLayout.setVisibility(View.GONE);
        }
    }

    public void toggleDetails(View view) {
        openDetails(!detailsOpened);
        detailsToggledFollowAuto = hasCoverArt == detailsOpened;
    }

    private void setCoverArt(long rowSongId, int imageNum, Bitmap bitmap) {
        runOnUiThread(() -> {
            Log.d("Main", "setCoverArt rowSongId=" + rowSongId + " imageNum=" + imageNum);
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
        if (rowSong != null) {
            String title = rowSong.getTrack() + ". " + rowSong.getTitle();
            songTitle.setText(title);
            songArtist.setText(rowSong.getArtist());
            String album = rowSong.getAlbum();
            if (rowSong.getYear() > 1000)
                album += " - " + rowSong.getYear();
            songAlbum.setText(album);
            rowSong.getAlbumBmpAsync(getApplicationContext(), coverArtNum,
                    (rowSongId, imageNum, bitmap) -> setCoverArt(rowSongId, imageNum, bitmap));

            setRatingDetails();
        }
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
        if (rating <= 0) {
            details_rating_layout.setVisibility(View.INVISIBLE);
        } else {
            details_rating_layout.setVisibility(View.VISIBLE);
            for (int i = 0; i < ratingButtons.size(); i++) {
                int star0 = highlight ? R.drawable.ic_star_0_highlight : R.drawable.ic_star_0;
                int star5 = highlight ? R.drawable.ic_star_5_highlight : R.drawable.ic_star_5;
                ratingButtons.get(i).setImageResource(i < rating ? star5 : star0);
            }
        }
    }

    public void autoOpenCloseDetails() {
        if (!serviceBound)
            return;
        RowSong rowSong = rows.getCurrSong();
        if (rowSong != null) {
            rowSong.getAlbumBmpAsync(getApplicationContext(), coverArtNum,
                    (rowSongId, imageNum, bitmap) -> {
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
                            runOnUiThread(() -> setDetails());
                        }
                    });
        }
    }


    public void toggleBiggerCoverArt(View view) {
        detailsBigCoverArt = !detailsBigCoverArt;
        applyBiggerCoverArt();
    }

    public void applyBiggerCoverArt() {
        if (detailsBigCoverArt) {
            // increase cover art size
            ViewGroup.LayoutParams params = detailsLayout.getLayoutParams();
            params.height = params.height * 2;
            detailsLayout.setLayoutParams(params);

            // hide text details
            albumImage.setLayoutParams(
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                            LinearLayout.LayoutParams.FILL_PARENT, 0f));

            // click on image go back to normal details
            albumImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleBiggerCoverArt(v);
                }
            });
        } else {
            // decrease cover art size
            ViewGroup.LayoutParams params = detailsLayout.getLayoutParams();
            params.height = params.height / 2;
            detailsLayout.setLayoutParams(params);

            // show text details
            albumImage.setLayoutParams(
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                            LinearLayout.LayoutParams.FILL_PARENT, 1f));

            // click on image hide details
            albumImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleDetails(v);
                }
            });
        }
    }


    public void deleteSong(View view) {
        final RowSong song = rows.getCurrSong();
        if (song == null) {
            // err msg ?
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete ")
                .setMessage("Do you really want to delete " + song.getPath() + " ?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (song.delete(getApplicationContext()))
                            Toast.makeText(getApplicationContext(),
                                    "del ok", Toast.LENGTH_SHORT).show();
                        else
                            Toast.makeText(getApplicationContext(),
                                    "del NOK!", Toast.LENGTH_SHORT).show();
                    }})
                .setNegativeButton(android.R.string.no, null).show();
    }

    private final int SET_RATING_REQUEST_CODE = 1024;
    public void ratingClick(View view) {
//        for (int i = 0; i < ratingButtons.size(); i++) {
//            if (view == ratingButtons.get(i)) {
//                // we cannot unclick the first star, so 0 star means not initialized.
//                rateCurrSong(i + 1);
//            }
//        }

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
//        try {
//            checkFilePermissions(new File("/storage/emulated/0/README.md"), false);
//        } catch (Exception e) {
//            Log.i("Main", "Unable to write:");
//        }
        RowSong rowSong = rows.getCurrSong();
        if (rowSong != null && rowSong.setRating(rating)) {
            setRatingDetails();
            songAdt.notifyDataSetChanged();
        } else {
            Toast.makeText(getApplicationContext(), "rating song " + rowSong.toString() + " failed!", Toast.LENGTH_LONG).show();
        }
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
//              Toast.makeText(getApplicationContext(),
//                  getString(R.string.action_sort) + " " + items[item], Toast.LENGTH_SHORT).show();
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
        };

        int checkedItem;
        if (rows.getRepeatMode() == RepeatMode.REPEAT_ALL)
            checkedItem = 0;
        else if (rows.getRepeatMode() == RepeatMode.REPEAT_GROUP)
            checkedItem = 1;
        else if (rows.getRepeatMode() == RepeatMode.REPEAT_ONE)
            checkedItem = 2;
        else
            checkedItem = 3;

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
                }
                dialog.dismiss(); // dismiss the alertbox after chose option
                setRepeatButton();
//              Toast.makeText(getApplicationContext(),
//                  getString(R.string.action_repeat_title) + " " + items[item], Toast.LENGTH_SHORT).show();
            }
        });
        AlertDialog alert = altBld.create();
        alert.show();
    }

    private void openShuffleMenu() {
        if (musicSrv == null)
            return;

        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(getShuffleResId());
        altBld.setTitle(getString(R.string.settings_shuffle_title));
        final CharSequence[] items = {
                getString(R.string.disabled), getString(R.string.enabled)
        };
        altBld.setSingleChoiceItems(items, params.getShuffle() ? 1 : 0, (DialogInterface dialog, int item) -> {
                params.setShuffle(item == 1);
                dialog.dismiss(); // dismiss the alertbox after chose option
                setShuffleButton();
//              Toast.makeText(getApplicationContext(),
//                   getString(R.string.settings_shuffle_title) + " " + items[item],
//                   Toast.LENGTH_SHORT).show();
            }
        );
        AlertDialog alert = altBld.create();
        alert.show();
    }

    private void openRatingMenu() {
        if (musicSrv == null)
            return;

        AlertDialog.Builder altBld = new AlertDialog.Builder(this);
        altBld.setIcon(getMinRatingResId());
        altBld.setTitle(getString(R.string.action_rating));
        final CharSequence[] items = {
                "1", "2", "3", "4", "5"
        };

        altBld.setSingleChoiceItems(items, musicSrv.getMinRating() - 1,
                (DialogInterface dialog, int item) -> {
            if (musicSrv != null) {
                musicSrv.setMinRating(item + 1);
                setMinRatingButton();
                dialog.dismiss(); // dismiss the alertbox after chose option
//              Toast.makeText(getApplicationContext(),
//                  getString(R.string.action_rating) + " " + items[item],
//                  Toast.LENGTH_SHORT).show();
            }
        });
        AlertDialog alert = altBld.create();
        alert.show();
    }

    private int getRepeatResId() {
        int res;
        switch (rows.getRepeatMode()) {
            case REPEAT_ONE: res = R.drawable.ic_menu_repeat_one; break;
            case REPEAT_GROUP: res = R.drawable.ic_menu_repeat_group; break;
            case REPEAT_ALL: res = R.drawable.ic_menu_repeat_all; break;
            default: res = R.drawable.ic_menu_repeat_not;
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
        if (params.getShuffle())
            return R.drawable.ic_menu_shuffle;
        else
            return R.drawable.ic_menu_no_shuffle;
    }

    private void setRepeatButton() {
        ImageView img = findViewById(R.id.repeat_button);
        img.setImageResource(getRepeatResId());
    }

    private void setTextSizeButton() {
        ImageView img = findViewById(R.id.text_size_button);
        img.setImageResource(getTextSizeResId());
    }

    private void setSortButton() {
        ImageView img = findViewById(R.id.sort_button);
        img.setImageResource(getSortResId());
    }

    private void setShuffleButton() {
        ImageButton shuffleButton = findViewById(R.id.shuffle_button);
        shuffleButton.setImageResource(getShuffleResId());
    }

    private void setMinRatingButton() {
        ImageView img = findViewById(R.id.rating_button);
        img.setImageResource(getMinRatingResId());
    }

    public void applyLock() {
//        ImageButton lockButton = (ImageButton) findViewById(R.id.lock_button);
        if(noLock) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
//            lockButton.setImageResource(R.drawable.ic_action_unlocked);
        }
        else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
//            lockButton.setImageResource(R.drawable.ic_action_locked);
        }
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
        switch (view.getId()) {
            case R.id.m5_button:
                newPosMs -= 5*1000;
                break;
            case R.id.p5_button:
                newPosMs += 5*1000;
                break;
            case R.id.m20_button:
            case R.id.m20_text:
                newPosMs -= 20*1000;
                break;
            case R.id.p20_button:
            case R.id.p20_text:
                newPosMs += 20*1000;
                break;
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

    private View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                vibrate();
            }
            return false;
        }
    };

    private View.OnLongClickListener gotoSongLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            fold();
            return true;
        }
    };

    private View.OnLongClickListener nextGroupLongListener = new View.OnLongClickListener() {
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

    private View.OnLongClickListener prevGroupLongListener = new View.OnLongClickListener() {
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

    private RepeatingImageButton.RepeatListener rewindListener =
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
            switch (view.getId()) {
                case R.id.m5_button:
                case R.id.p5_button:
                    offsetMs = 5000;
                    break;
                case R.id.m20_button:
                case R.id.m20_text:
                case R.id.p20_button:
                case R.id.p20_text:
                    if (duration < 5000) {
                        // seek at 10x speed for the first 5 seconds
                        offsetMs = duration * 10;
                    } else {
                        // seek at 40x after that
                        offsetMs = 50000 + (duration - 5000) * 40;
                    }
                    break;
            }
            return offsetMs;
        }

    private RepeatingImageButton.RepeatListener forwardListener =
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
        ImageButton more_button = findViewById(R.id.more_button);
        if (moreButtonsLayout.getVisibility() == View.VISIBLE) {
            moreButtonsLayout.setVisibility(View.GONE);
            more_button.setImageResource(R.drawable.ic_action_note);
        } else {
            moreButtonsLayout.setVisibility(View.VISIBLE);
            more_button.setImageResource(R.drawable.ic_action_edit);
        }
    }

    private final int SETTINGS_ACTION = 1;
    public void openSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, SETTINGS_ACTION);
    }


    public void openSort(View view) {
        openSortMenu();
    }

    public void openRepeat(View view) {
        openRepeatMenu();
    }

    public void openShuffle(View view) {
        openShuffleMenu();
    }

    public void openTextSize(View view) {
        params.setChooseTextSize(!params.getChoosedTextSize());
        ImageButton img = findViewById(R.id.text_size_button);
        int txtChoosed;
        if (params.getChoosedTextSize()) {
            img.setImageResource(R.drawable.ic_menu_text_big);
            txtChoosed = R.string.settings_text_size_bold;
        }
        else {
            img.setImageResource(R.drawable.ic_menu_text_regular);
            txtChoosed = R.string.settings_text_size_small;
        }
        applyTextSize();
//        Toast.makeText(getApplicationContext(),
//                getString(R.string.settings_text_size) + getString(txtChoosed),
//                Toast.LENGTH_LONG).show();
        songAdt.notifyDataSetChanged();
        setTextSizeButton();
    }

    public void openMinRating(View view) {
        openRatingMenu();
    }

    public void unfoldAndscrollToCurrSong() {
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
        showAroundTop = showAroundTop < 1 ? 1 : showAroundTop;
        // show more song after the gotoSong
        int showAroundBottom = nbRow / 2;
        showAroundBottom = showAroundBottom < 1 ? 1 : showAroundBottom;
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

/*
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                Log.d("Main", "Exit app");
                finishing = true;
                finish();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
*/

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
        noLock = params.getNoLock();
        followSong = params.getFollowSong();
        applyTextSize();
    }

    private void save() {
        params.setNoLock(noLock);
    }

    private void vibrate() {
        if (params.getVibrate())
            vibrator.vibrate(20);
    }
}


