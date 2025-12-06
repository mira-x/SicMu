package souch.smp;

import android.util.Log;

import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

@UnstableApi
public class MergeAudioProcessor extends BypassAudioProcessor {
    private boolean stereo = true;

    @Override
    public boolean handleBuffer(ByteBuffer inputBuffer) {
        if (!support || stereo) return false;

        while (inputBuffer.remaining() >= 4) {
            int l = inputBuffer.getShort();
            int r = inputBuffer.getShort();
            short avg = (short) ((l + r) / 2);
            outputBuffer.putShort(avg);
            outputBuffer.putShort(avg);
        }
        outputBuffer.flip();
        return true;
    }

    public boolean isStereo() {
        return stereo;
    }

    public void setStereo(boolean stereo) {
        this.stereo = stereo;
    }
}
