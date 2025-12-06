package souch.smp;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@UnstableApi
public class BypassAudioProcessor extends BaseAudioProcessor {
    boolean support = false;
    ByteBuffer outputBuffer = AudioProcessor.EMPTY_BUFFER;

    private void initOutputBuffer(ByteBuffer inputBuffer) {
        if (outputBuffer.capacity() < inputBuffer.remaining()) {
            outputBuffer = ByteBuffer.allocateDirect(inputBuffer.remaining()).order(ByteOrder.nativeOrder());
        } else {
            outputBuffer.clear();
        }
    }

    @NonNull
    @Override
    public AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws UnhandledAudioFormatException {
        support = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2);
        Log.d("BypassAudioProcessor", "Audio format: " + inputAudioFormat + " supported: " + support);
        if (support) {
            return inputAudioFormat;
        } else {
            return super.onConfigure(inputAudioFormat);
        }
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining == 0) return;
        initOutputBuffer(inputBuffer);
        if (handleBuffer(inputBuffer)) {
            replaceOutputBuffer(remaining).put(outputBuffer).flip();
        } else {
            replaceOutputBuffer(remaining).put(inputBuffer).flip();
        }
    }

    /**
     * @return true if you have written data to the outputBuffer. If not, return false,
     * causing a simple audio bypass, i.e. returning the input data as-is back to Android.
     * @implNote This is meant to be overridden by subclasses.
     */
    protected boolean handleBuffer(ByteBuffer inputBuffer) {
        return false;
    }
}
