package xyz.mordorx.sicmu.media

import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** This can downmix stereo into mono in real-time.
 * 
 * @author LoliBall ([...](https://github.com/WhichWho)) on 2022-12-22
 */
@UnstableApi
class MergeAudioProcessor : BypassAudioProcessor() {
    var isStereo: Boolean = true

    public override fun handleBuffer(inputBuffer: ByteBuffer): Boolean {
        if (!support || this.isStereo) return false

        while (inputBuffer.remaining() >= 4) {
            val l = inputBuffer.getShort().toInt()
            val r = inputBuffer.getShort().toInt()
            val avg = ((l + r) / 2).toShort()
            outputBuffer.putShort(avg)
            outputBuffer.putShort(avg)
        }
        outputBuffer.flip()
        return true
    }
}
