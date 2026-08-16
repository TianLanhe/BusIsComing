package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.ui.main.EtaOperatorBadge
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaOperatorBadgeTest {
    @Test
    fun everySupportedOperatorUsesItsApprovedBrandTokens() {
        assertEquals(
            EtaOperatorBadge(R.string.operator_ctb, R.color.operator_ctb_background, R.color.operator_ctb_text),
            EtaOperatorBadge.forOperator(BusOperator.CTB)
        )
        assertEquals(
            EtaOperatorBadge(R.string.operator_kmb, R.color.operator_kmb_background, R.color.operator_kmb_text),
            EtaOperatorBadge.forOperator(BusOperator.KMB)
        )
        assertEquals(
            EtaOperatorBadge(R.string.operator_lwb, R.color.operator_lwb_background, R.color.operator_lwb_text),
            EtaOperatorBadge.forOperator(BusOperator.LWB)
        )
        assertNull(EtaOperatorBadge.forOperator(null))
    }

    @Test
    fun brandColorsAndOperatorLabelsExistWithoutChangingRouteCardLayout() {
        val colors = File("src/main/res/values/colors.xml").readText()
        val bottomSheet = File(
            "src/main/java/com/golink/busiscoming/ui/main/EtaArrivalsBottomSheet.kt"
        ).readText()
        val routeCard = File("src/main/res/layout/item_bus_route.xml").readText()

        assertTrue(colors.contains("name=\"operator_ctb_background\">#ECCF00"))
        assertTrue(colors.contains("name=\"operator_ctb_text\">#004891"))
        assertTrue(colors.contains("name=\"operator_kmb_background\">#E60012"))
        assertTrue(colors.contains("name=\"operator_kmb_text\">#FFFFFF"))
        assertTrue(colors.contains("name=\"operator_lwb_background\">#F15622"))
        assertTrue(colors.contains("name=\"operator_lwb_text\">#17211F"))
        assertTrue(bottomSheet.contains("EtaOperatorBadge.forOperator"))
        assertFalse(routeCard.contains("operator_"))
    }
}
