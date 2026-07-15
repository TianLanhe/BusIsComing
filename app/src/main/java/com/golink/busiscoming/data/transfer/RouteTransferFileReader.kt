package com.golink.busiscoming.data.transfer

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

object RouteTransferFileReader {
    const val MAX_FILE_BYTES = 2 * 1024 * 1024
    private const val EXTENSION = ".bicroutes"

    fun read(input: InputStream, displayName: String?): ByteArray {
        if (displayName != null && !displayName.lowercase(Locale.ROOT).endsWith(EXTENSION)) {
            throw RouteTransferException(RouteTransferError.INVALID_FILE_EXTENSION)
        }
        val output = ByteArrayOutputStream(minOf(MAX_FILE_BYTES, input.available().coerceAtLeast(32)))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FILE_BYTES) throw RouteTransferException(RouteTransferError.FILE_TOO_LARGE)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
