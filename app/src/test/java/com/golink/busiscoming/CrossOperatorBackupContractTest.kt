package com.golink.busiscoming

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossOperatorBackupContractTest {
    @Test
    fun excludesOnlyRebuildableCrossOperatorDatabaseFromBothBackupContracts() {
        val legacyExcludes = excludes("src/main/res/xml/backup_rules.xml")
        val modernExcludes = excludes("src/main/res/xml/data_extraction_rules.xml")

        assertEquals(
            setOf("database:cross_operator_routes.db"),
            legacyExcludes
        )
        assertTrue(modernExcludes.contains("cloud-backup:database:cross_operator_routes.db"))
        assertTrue(modernExcludes.contains("device-transfer:database:cross_operator_routes.db"))
        assertEquals(2, modernExcludes.size)
    }

    private fun excludes(path: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val excludes = document.getElementsByTagName("exclude")
        return buildSet {
            for (index in 0 until excludes.length) {
                val element = excludes.item(index)
                val parent = element.parentNode.nodeName.takeIf { it != "full-backup-content" }
                val value = "${element.attributes.getNamedItem("domain").nodeValue}:" +
                    element.attributes.getNamedItem("path").nodeValue
                add(if (parent == null) value else "$parent:$value")
            }
        }
    }
}

