package xyz.mordorx.sicmu.collections

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

inline fun <reified T> autoSerializer(
    defaultValue: T,
    json: Json = Json { ignoreUnknownKeys = true },
): Serializer<T> = object : Serializer<T> {
    override val defaultValue = defaultValue

    override suspend fun readFrom(input: InputStream): T = withContext(Dispatchers.IO) {
        try {
            json.decodeFromString(input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            defaultValue
        } catch (e: IOException) {
            throw CorruptionException("Corrupted settings", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream): Unit = withContext(Dispatchers.IO) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}