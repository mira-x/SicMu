/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// This file has been heavily copied from official android music player (Apache License, Version 2.0).
// modified to remove shuffle mode
package xyz.mordorx.sicmu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import xyz.mordorx.sicmu.media.MusicService


class MediaButtonIntentReceiver : BroadcastReceiver() {
    //    private static boolean mDown = false;
    // souch: disable shuffle mode support
    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.getAction()
        if (Intent.ACTION_MEDIA_BUTTON == intentAction) {
            val event = intent.getParcelableExtra<KeyEvent?>(Intent.EXTRA_KEY_EVENT)

            if (event == null) {
                return
            }

            val keycode = event.getKeyCode()
            val action = event.getAction()
            val eventtime = event.getEventTime()

            // TODO: Check if this works. Or if it works with bluetooth headsets.
            // single quick press: pause/resume.
            // double press: next track
            // long press: start auto-shuffle mode.
            var command: String? = null
            when (keycode) {
                KeyEvent.KEYCODE_MEDIA_STOP -> command = MusicService.Companion.CMDSTOP
                KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> command =
                    MusicService.Companion.CMDTOGGLEPAUSE

                KeyEvent.KEYCODE_MEDIA_NEXT -> command = MusicService.Companion.CMDNEXT
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> command = MusicService.Companion.CMDPREVIOUS
                KeyEvent.KEYCODE_MEDIA_PAUSE -> command = MusicService.Companion.CMDPAUSE
                KeyEvent.KEYCODE_MEDIA_PLAY -> command = MusicService.Companion.CMDPLAY
            }

            if (command != null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    /*if (mDown) {
                        if ((MusicService.CMDTOGGLEPAUSE.equals(command) ||
                                MusicService.CMDPLAY.equals(command))
                                && mLastClickTime != 0
                                && eventtime - mLastClickTime > LONG_PRESS_DELAY) {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT, context));
                        }
                    }
                    else */
                    if (event.getRepeatCount() == 0) {
                        // only consider the first event in a sequence, not the repeat events,
                        // so that we don't trigger in cases where the first event went to
                        // a different app (e.g. when the user ends a phone call by
                        // long pressing the headset button)

                        // The service may or may not be running, but we need to send it
                        // a command.

                        val i = Intent(context, MusicService::class.java)
                        i.setAction(MusicService.Companion.SERVICECMD)

                        if (keycode == KeyEvent.KEYCODE_HEADSETHOOK && eventtime - mLastClickTime < 300) {
                            i.putExtra(
                                MusicService.Companion.CMDNAME,
                                MusicService.Companion.CMDNEXT
                            )
                            context.startService(i)
                            mLastClickTime = 0
                        } else {
                            i.putExtra(MusicService.Companion.CMDNAME, command)
                            context.startService(i)
                            mLastClickTime = eventtime
                        }
                    }
                } /* else {
                    //mHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                    mDown = false;
                }
                if (isOrderedBroadcast()) {
                    abortBroadcast();
                }
                */
            }
        }
    }

    companion object {
        private var mLastClickTime: Long = 0
    }
}