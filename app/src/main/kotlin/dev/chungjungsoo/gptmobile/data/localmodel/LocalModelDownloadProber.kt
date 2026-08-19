package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

fun interface LocalModelDownloadProber {
    fun probe(downloadUrl: String, accessToken: String?): Int
}

@Singleton
class HttpLocalModelDownloadProber @Inject constructor() : LocalModelDownloadProber {
    override fun probe(downloadUrl: String, accessToken: String?): Int = try {
        val connection = URL(downloadUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty(
                LocalModelDownloadPaths.RANGE_HEADER,
                PROBE_RANGE
            )
            connection.setRequestProperty(
                LocalModelDownloadPaths.ACCEPT_ENCODING_HEADER,
                LocalModelDownloadPaths.IDENTITY_ENCODING
            )
            if (!accessToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", HuggingFaceTokenStore.bearerHeader(accessToken))
            }
            connection.connect()
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        NETWORK_ERROR
    }

    companion object {
        const val NETWORK_ERROR = -1
        private const val PROBE_RANGE = "bytes=0-0"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
