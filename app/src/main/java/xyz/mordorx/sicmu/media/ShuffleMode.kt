package xyz.mordorx.sicmu.media

import android.view.View
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import xyz.mordorx.sicmu.R

enum class ShuffleMode(
    /** Radio is basically random selection (above) plus random song start time when playing the first song */
    val num: Int
) {
    SEQUENTIAL(0),
    RANDOM(1),
    RADIO(2);

    /** This returns the next shuffle mode in sequence, and loops when reaching the last one. */
    fun next(): ShuffleMode {
        val modes = entries.size
        val next = (this.num + 1) % modes
        return valueOf(next)
    }

    /** Whether to start playback mid-song. This applies to RADIO. */
    fun startMidSong(): Boolean {
        return (this == ShuffleMode.RADIO)
    }

    fun randomSongOrder(): Boolean {
        return (this == ShuffleMode.RANDOM || this == ShuffleMode.RADIO)
    }

    fun showExplainSnackbar(v: View) {
        val ctx = v.getContext()
        var txt = ""
        when (this) {
            ShuffleMode.SEQUENTIAL -> txt =
                ctx.getString(R.string.settings_shuffle_explainer_sequential)

            ShuffleMode.RANDOM -> txt = ctx.getString(R.string.settings_shuffle_explainer_random)
            ShuffleMode.RADIO -> txt = ctx.getString(R.string.settings_shuffle_explainer_radio)
        }

        Snackbar.make(v, txt, BaseTransientBottomBar.LENGTH_SHORT).show()
    }

    companion object {
        fun valueOf(a: Int): ShuffleMode {
            for (m in entries) {
                if (m.num == a) {
                    return m
                }
            }
            throw IllegalArgumentException("Shuffle mode is invalid")
        }
    }
}
