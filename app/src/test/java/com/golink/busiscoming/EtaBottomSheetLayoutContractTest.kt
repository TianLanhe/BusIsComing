package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaBottomSheetLayoutContractTest {
    private val source = File(
        "src/main/java/com/golink/busiscoming/ui/main/EtaArrivalsBottomSheet.kt"
    ).readText()

    @Test
    fun headerIsFixedAndEveryArrivalIsPlacedInScrollableList() {
        assertTrue(source.contains("ScrollView(context)"))
        assertTrue(source.contains("arrivals.forEach"))
        assertFalse(source.contains("arrivals.take(3)"))
        assertTrue(source.contains("eta_arrival_row_content_description"))
        assertTrue(source.indexOf("EtaArrivalsSheetFormatter.title") < source.indexOf("ScrollView(context)"))
    }
}
