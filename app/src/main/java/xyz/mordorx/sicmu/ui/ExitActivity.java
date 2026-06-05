package xyz.mordorx.sicmu.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import xyz.mordorx.sicmu.media.MusicService;

/**
 * Use the static method <code>ExitActivity.exit()</code> to cleanly kill the app. Cleanly means:
 * <ul>
 * <li>It kills the music service</li>
 * <li>It kills any visible activities</li>
 * <li>It removes the app from the "recently opened apps" screen in the OS</li>
 * <li>It kills the system process</li>
 * </ul>
 * <p>
 * The rationale to have an exit activity is this: Killing activities and services using code is trivial, but you cannot remove the app from the "recently opened apps" list solely using code. To achieve this, you need an Activity that has <code>android:excludeFromRecents="true"</code> set in the AndroidManifest.
 */
@OptIn(markerClass = UnstableApi.class)
public class ExitActivity extends Activity {
    private final ServiceConnection musicConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            var binder = (MusicService.MusicBinder) service;
            var musicSrv = binder.getService();
            musicSrv.stopForeground(true);
            musicSrv.stopSelf();
        }

        public void onServiceDisconnected(ComponentName name) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent playIntent = new Intent(this, MusicService.class);
        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);

        finishAndRemoveTask();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(musicConnection);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public static void exit(Context ctx) {
        Intent intent = new Intent(ctx, ExitActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        );
        ctx.startActivity(intent);
    }
}
