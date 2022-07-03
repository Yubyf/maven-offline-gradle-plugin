package io.github.yubyf.mavenoffline.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Throws(RemoteFileNotFoundException::class, RemoteFileDownloadException::class, IOException::class)
internal suspend fun String.downloadString(
    username: String? = null,
    password: String? = null,
): String? = downloadData(username, password) { body, length ->
    ByteArrayOutputStream(length.toInt()).use {
        val result = it.write(body, length)
        if (result > 0) it.toString() else null
    }
}

@Throws(RemoteFileNotFoundException::class, RemoteFileDownloadException::class, IOException::class)
internal suspend fun String.downloadFile(
    target: File,
    username: String? = null,
    password: String? = null,
) = downloadData(username, password) { body, length ->
    target.parentFile.takeIf { !it.exists() }?.mkdirs()
    target.outputStream().use { it.write(body, length) } > 0
}

@Throws(RemoteFileNotFoundException::class, RemoteFileDownloadException::class, IOException::class)
private suspend fun <T> String.downloadData(
    username: String? = null,
    password: String? = null,
    block: suspend (InputStream, Long) -> T,
): T = withContext(Dispatchers.IO) {
    val request = Request.Builder().get().url(this@downloadData).apply {
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            addHeader("Authorization", Credentials.basic(username, password))
        }
    }.build()
    httpClient.newCall(request).execute().use { response ->
        if (response.code != 200) {
            // Requesting release files from the sonatype snapshot repository will return 400.
            if (response.code == 404 || response.code == 400) {
                throw RemoteFileNotFoundException("Remote file not found: ${this@downloadData}")
            }
            throw RemoteFileDownloadException("Response failed with code ${response.code}: ${this@downloadData}")
        }
        response.body?.use { body ->
            val length: Long = response.header("CONTENT_LENGTH")?.toLong() ?: 1
            block(body.byteStream(), length)
        } ?: throw RemoteFileDownloadException("Response doesn't contain a file: ${this@downloadData}")
    }
}

@Throws(IOException::class)
private fun OutputStream.write(inputStream: InputStream, length: Long): Long {
    if (length <= 0L) {
        throw IllegalArgumentException("Length must be greater than or equal to 0")
    }
    BufferedInputStream(inputStream).use { input ->
        val dataBuffer = ByteArray(4096)
        var readBytes: Int
        var totalBytes: Long = 0
        while (input.read(dataBuffer).also { readBytes = it } != -1) {
            totalBytes += readBytes.toLong()
            write(dataBuffer, 0, readBytes)
        }
        return totalBytes
    }
}

/**
 * Suspend extension that allows suspend [Call] inside coroutine.
 *
 * [Reference](https://github.com/gildor/kotlin-coroutines-okhttp/blob/master/src/main/kotlin/ru/gildor/coroutines/okhttp/CallAwait.kt)
 *
 * @return Result of request or throw exception
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            // Don't bother with resuming the continuation if it is already cancelled.
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })

    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (ex: Throwable) {
            //Ignore cancel exception
        }
    }
}

internal class RemoteFileNotFoundException(message: String) : IOException(message)

internal class RemoteFileDownloadException(message: String) : IOException(message)

private val httpClient = OkHttpClient()