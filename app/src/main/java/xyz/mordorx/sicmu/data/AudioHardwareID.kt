package xyz.mordorx.sicmu.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.util.Collections

/**
 * This class/companion object offers a hash/ID for the current audio device configuration. It is updated automatically, so if a user connects AirPods or disconnects a JBL speaker, the ID will automatically be updated. Using this ID, you can have separate audio configurations for different setups/devices.
 * <p>
 * To use this, you must register the audio device change listener first. While unregistered, the audio ID is always 0.
 */
@JvmInline
@Serializable
value class AudioHardwareID(val hashCode: Int) {
    companion object {
        private var currentIdMutable: MutableStateFlow<AudioHardwareID> = MutableStateFlow(AudioHardwareID(0))
        private var currentDevices = Collections.synchronizedList(mutableListOf<AudioDeviceInfo>())
        /// TODO: Maybe this field should be renamed so one can use it as direct import (w/o 'AudioHardwareID.' prefix) and still guess its meaning.
        var currentId: StateFlow<AudioHardwareID> = currentIdMutable

        /** This generates an ID for the current audio output devices hardware. It is used so that we can have distinct audio channel configurations for different devices. */
        @SuppressLint("WrongConstant")
        private fun calculateID(devices: List<AudioDeviceInfo>): AudioHardwareID {
            val hash = devices
                .flatMap { dev -> listOf(dev.address.hashCode(), dev.productName.hashCode()) }
                .distinct()
                .sum()
            return AudioHardwareID(hash)
        }

        /**
         * This registers a listener that automatically updates the `currentId`, whenever the
         * audio devices setup changes, and also once now.
         * <p>
         * Use your application context.
         */
        @SuppressLint("WrongConstant")
        fun registerListener(context: Context) {
            val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
            currentDevices.addAll(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            currentIdMutable.update { calculateID(currentDevices) }
            audioManager.registerAudioDeviceCallback(audioDeviceStereoConfigCallback, null)
        }

        /**
         * This unregisters the listener and sets `currentId` to `0`.
         */
        fun unregisterListener(context: Context) {
            val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.unregisterAudioDeviceCallback(audioDeviceStereoConfigCallback)
            currentIdMutable.update { AudioHardwareID(0) }
        }

        private val audioDeviceStereoConfigCallback: AudioDeviceCallback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    currentDevices.addAll(addedDevices)
                    currentIdMutable.update { calculateID(currentDevices) }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    removedDevices.forEach { removed ->
                        val success =
                            currentDevices.removeIf { current -> current.id == removed.id }
                        if (!success) {
                            Log.w(
                                "AudioHardwareID",
                                "onAudioDevicesRemoved() received an Audio Device that wasn't on my internal device list. This is a hint for state corruption."
                            )
                        }
                    }

                    currentIdMutable.update { calculateID(currentDevices) }
                }
            }
    }
}
