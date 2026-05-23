package com.qaplatform.android.agent.install

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Streams an APK from a URL to disk while computing SHA-256 in one pass, then verifies
 * against the expected hash. Returns the temp file on success, {@link Result.failure}
 * on any HTTP / I/O / verification error.
 *
 * <p>Caller (the installer) is responsible for deleting the file after committing the
 * install session — we intentionally leave cleanup there so a mid-install crash doesn't
 * strand a half-staged session waiting for bytes we already discarded.</p>
 */
object ApkDownloader {

    private const val TAG = "ApkDownloader"

    /** OkHttp tuned for big APK transfers — read-timeout is generous (30 min on slow Wi-Fi). */
    private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun download(context: Context, url: String, expectedSha256: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("download HTTP ${resp.code}")
                    val body = resp.body ?: error("empty response body")

                    val temp = File.createTempFile("apk-", ".apk", context.cacheDir)
                    val md = MessageDigest.getInstance("SHA-256")
                    var bytes = 0L
                    body.byteStream().use { src ->
                        temp.outputStream().use { dst ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = src.read(buf)
                                if (n == -1) break
                                dst.write(buf, 0, n)
                                md.update(buf, 0, n)
                                bytes += n
                            }
                        }
                    }

                    val actual = md.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expectedSha256, ignoreCase = true)) {
                        temp.delete()
                        error("sha256 mismatch: expected=$expectedSha256 actual=$actual")
                    }
                    Log.i(TAG, "downloaded ${temp.name} ($bytes bytes), sha256 ok")
                    temp
                }
            }
        }
}
