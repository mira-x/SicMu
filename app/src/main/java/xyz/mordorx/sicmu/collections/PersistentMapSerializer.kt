package xyz.mordorx.sicmu.collections

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * This is a custom serializer for Kotlin Immutable Collections' PersistentMap, because the library does not offer its own serializer. There is a stale GitHub [issue](https://github.com/Kotlin/kotlinx.collections.immutable/issues/63) about this from 2019, and a stale [PR](https://github.com/Kotlin/kotlinx.collections.immutable/pull/179) from 2024. That serializer is not coming soon.
 * <p>
 * This helper class is 100% AI-generated.
 */
class PersistentMapSerializer<K, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>,
) : KSerializer<PersistentMap<K, V>> {

    private val delegate = MapSerializer(keySerializer, valueSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: PersistentMap<K, V>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): PersistentMap<K, V> =
        delegate.deserialize(decoder).toPersistentHashMap()
}
