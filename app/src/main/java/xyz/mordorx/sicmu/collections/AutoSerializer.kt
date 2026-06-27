package xyz.mordorx.sicmu.collections

/**
 * This file is 100% AI-generated.
 */

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.typeOf
import kotlinx.serialization.modules.SerializersModule

val persistentCollectionsModule = SerializersModule {
    contextual(PersistentMap::class) { args ->
        @Suppress("UNCHECKED_CAST")
        PersistentMapSerializer(
            args[0] as KSerializer<Any>,
            args[1] as KSerializer<Any>,
        )
    }
}

inline fun <reified T> autoSerializer(
    defaultValue: T,
    json: Json = Json {
        ignoreUnknownKeys = true
        serializersModule = persistentCollectionsModule  // ← neu
    },
): Serializer<T> = object : Serializer<T> {

    @Suppress("UNCHECKED_CAST")
    val kSerializer: KSerializer<T> = run {
        val type = typeOf<T>()
        if (type.classifier == PersistentMap::class) {
            PersistentMapSerializer(
                serializer(type.arguments[0].type!!),
                serializer(type.arguments[1].type!!),
            ) as KSerializer<T>
        } else {
            serializer<T>()
        }
    }

    override val defaultValue = defaultValue

    override suspend fun readFrom(input: InputStream): T = withContext(Dispatchers.IO) {
        try {
            json.decodeFromString(kSerializer, input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            defaultValue
        } catch (e: IOException) {
            throw CorruptionException("Corrupted settings", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream): Unit = withContext(Dispatchers.IO) {
        output.write(json.encodeToString(kSerializer, t).encodeToByteArray())
    }
}
