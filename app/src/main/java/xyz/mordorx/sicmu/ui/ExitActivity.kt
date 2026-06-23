package xyz.mordorx.sicmu.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import xyz.mordorx.sicmu.media.MusicService
import xyz.mordorx.sicmu.media.MusicService.MusicBinder

/**
 * Use the static method `ExitActivity.exit()` to cleanly kill the app. Cleanly means:
 * 
 *  * It kills the music service
 *  * It kills any visible activities
 *  * It removes the app from the "recently opened apps" screen in the OS
 *  * It kills the system process
 * 
 * 
 * 
 * The rationale to have an exit activity is this: Killing activities and services using code is trivial, but you cannot remove the app from the "recently opened apps" list solely using code. To achieve this, you need an Activity that has `android:excludeFromRecents="true"` set in the AndroidManifest.
 */
// @OptIn(markerClass = UnstableApi::class)
class ExitActivity : Activity() {
    private val musicConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicBinder
            val musicSrv = binder.service
            musicSrv.stopForeground(true)
            musicSrv.stopSelf()
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val playIntent = Intent(this, MusicService::class.java)
        bindService(playIntent, musicConnection, BIND_AUTO_CREATE)

        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(musicConnection)
        Process.killProcess(Process.myPid())
    }

    companion object {
        fun exit(ctx: Context) {
            val intent = Intent(ctx, ExitActivity::class.java)
            intent.addFlags(
                (Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            )
            ctx.startActivity(intent)
        }
    }
}
