package xyz.mordorx.sicmu.data

import android.content.Context
import android.util.Log
import androidx.datastore.dataStore
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import xyz.mordorx.sicmu.BuildConfig
import xyz.mordorx.sicmu.collections.autoSerializer
import xyz.mordorx.sicmu.media.RepeatMode
import xyz.mordorx.sicmu.media.ShuffleMode

@Serializable
data class XPreferences(
    val vibrate: Boolean = true,
    val shuffle: ShuffleMode = ShuffleMode.SEQUENTIAL,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_GROUP,
    val stereo: PersistentMap<AudioHardwareID, Boolean> = persistentMapOf(),
    val sleepDelayM: Int = 60,
    val showRemainingTime: Boolean = false,
    val showGroupTotalTime: Boolean = false,
    val playbackSpeedFactor: Float = 1.0f,
    val disablePitchCompensation: Boolean = false,
    val lastSeenChangelogVersion: Int = BuildConfig.VERSION_CODE,
    val shakePlaysSongs: Boolean = false,
    val shakeThreshold: Float = 100.0f,
    /** The MediaStore Audio ID of the last played song, saved for auto-resume on app restart. No guarantee that this is accurate during app runtime. It will only be set on app destroyment */
    val lastPlayedSongID: Long? = null,
    val scrollToSongUponStart: Boolean = true,

    /** How much bigger groups are displayed compared to songs/default font size */
    val rowGroupTextSizeRatio: Float = 1.1f,
    val textSizeNormal: Int = 15,
    val textSizeBig: Int = 19,
    val enlargeText: Boolean = false,
) {

    companion object {
        /** Global XPreferences Singleton */
        var P = MutableStateFlow(XPreferences())

        suspend fun load(appContext: Context) {
            Log.d("XPreferences", "Loading prefs")
            P.update {appContext.preferencesDataStore.data.first() }
        }

        suspend fun save(appContext: Context) {
            Log.d("XPreferences", "Saving prefs")
            appContext.preferencesDataStore.updateData { P.first() }
        }
    }
}

private val Context.preferencesDataStore by dataStore(
    fileName = "preferences_v1.json",
    serializer = autoSerializer(XPreferences()),
)

