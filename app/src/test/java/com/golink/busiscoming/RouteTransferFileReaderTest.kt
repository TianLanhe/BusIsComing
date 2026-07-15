package com.golink.busiscoming

import com.golink.busiscoming.data.transfer.RouteTransferError
import com.golink.busiscoming.data.transfer.RouteTransferException
import com.golink.busiscoming.data.transfer.RouteTransferFileReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RouteTransferFileReaderTest {
    @Test
    fun readsAtMostTwoMiBAndRejectsOneAdditionalByte() {
        val maximum = ByteArray(RouteTransferFileReader.MAX_FILE_BYTES) { 7 }

        assertArrayEquals(maximum, RouteTransferFileReader.read(ByteArrayInputStream(maximum), "routes.bicroutes"))

        val oversized = ByteArray(RouteTransferFileReader.MAX_FILE_BYTES + 1)
        assertReadError(oversized, "routes.bicroutes", RouteTransferError.FILE_TOO_LARGE)
    }

    @Test
    fun checksExtensionOnlyWhenDisplayNameIsAvailable() {
        val content = "{}".toByteArray()

        assertArrayEquals(content, RouteTransferFileReader.read(ByteArrayInputStream(content), null))
        assertArrayEquals(content, RouteTransferFileReader.read(ByteArrayInputStream(content), "ROUTES.BICROUTES"))
        assertReadError(content, "routes.json", RouteTransferError.INVALID_FILE_EXTENSION)
    }

    private fun assertReadError(bytes: ByteArray, name: String?, expected: RouteTransferError) {
        try {
            RouteTransferFileReader.read(ByteArrayInputStream(bytes), name)
            throw AssertionError("Expected $expected")
        } catch (error: RouteTransferException) {
            assertEquals(expected, error.error)
        }
    }
}
