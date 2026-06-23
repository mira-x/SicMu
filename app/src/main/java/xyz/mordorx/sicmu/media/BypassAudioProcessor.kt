package xyz.mordorx.sicmu.media

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** This can be used to either:
 * - Bypass audio (data coming in goes out unchanged)
 * - Modify audio by overriding handleBuffer
 * 
 * @author LoliBall ([...](https://github.com/WhichWho)) on 2023-01-17
 */
@UnstableApi
open class BypassAudioProcessor : BaseAudioProcessor() {
    var support: Boolean = false
    var outputBuffer: ByteBuffer = EMPTY_BUFFER

    private fun initOutputBuffer(inputBuffer: ByteBuffer) {
        if (outputBuffer.capacity() < inputBuffer.remaining()) {
            outputBuffer =
                ByteBuffer.allocateDirect(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
    }

    @Throws(UnhandledAudioFormatException::class)
    public override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        support =
            (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2)
        Log.d(
            "BypassAudioProcessor",
            "Audio format: " + inputAudioFormat + " supported: " + support
        )
        if (support) {
            return inputAudioFormat
        } else {
            return super.onConfigure(inputAudioFormat)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        initOutputBuffer(inputBuffer)
        if (handleBuffer(inputBuffer)) {
            replaceOutputBuffer(remaining).put(outputBuffer).flip()
        } else {
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
        }
    }

    /**
     * @return true if you have written data to the outputBuffer. If not, return false,
     * causing a simple audio bypass, i.e. returning the input data as-is back to Android.
     * @implNote This is meant to be overridden by subclasses.
     */
    protected open fun handleBuffer(inputBuffer: ByteBuffer): Boolean {
        return false
    }
}
