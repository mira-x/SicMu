/*
 * Copyright (C) 2008 The Android Open Source Project
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
 */
// This file has been copied from official android music player (Apache License, Version 2.0).
package xyz.mordorx.sicmu.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton

/**
 * A button that will repeatedly call a 'listener' method
 * as long as the button is pressed.
 * 
 * 
 * The name is inspired by Jetpack Compose's Modifier.combinedClickable.
 */
class CombinedClickableImage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.imageButtonStyle
) : AppCompatImageButton(context, attrs, defStyle) {
    private var mStartTime: Long = 0
    private var mRepeatCount = 0
    private var mListener: RepeatListener? = null
    private var mInterval: Long = 500

    /**
     * Sets the listener to be called while the button is pressed and
     * the interval in milliseconds with which it will be called.
     * @param l The listener that will be called
     * @param interval The interval in milliseconds for calls
     */
    fun setRepeatListener(l: RepeatListener?, interval: Long) {
        mListener = l
        mInterval = interval
    }

    override fun performLongClick(): Boolean {
        mStartTime = SystemClock.elapsedRealtime()
        mRepeatCount = 0
        post(mRepeater)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            // remove the repeater, but call the hook one more time
            removeCallbacks(mRepeater)
            if (mStartTime != 0L) {
                doRepeat(true)
                mStartTime = 0
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // need to call super to make long press work, but return
                // true so that the application doesn't get the down event.
                super.onKeyDown(keyCode, event)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // remove the repeater, but call the hook one more time
                removeCallbacks(mRepeater)
                if (mStartTime != 0L) {
                    doRepeat(true)
                    mStartTime = 0
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private val mRepeater: Runnable = object : Runnable {
        override fun run() {
            doRepeat(false)
            if (isPressed()) {
                postDelayed(this, mInterval)
            }
        }
    }

    init {
        setFocusable(true)
        setLongClickable(true)
    }

    private fun doRepeat(last: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (mListener != null) {
            mListener!!.onRepeat(this, now - mStartTime, if (last) -1 else mRepeatCount++)
        }
    }

    interface RepeatListener {
        /**
         * This method will be called repeatedly at roughly the interval
         * specified in setRepeatListener(), for as long as the button
         * is pressed.
         * @param v The button as a View.
         * @param duration The number of milliseconds the button has been pressed so far.
         * @param repeatcount The number of previous calls in this sequence.
         * If this is going to be the last call in this sequence (i.e. the user
         * just stopped pressing the button), the value will be -1.
         */
        fun onRepeat(v: View?, duration: Long, repeatcount: Int)
    }
}
