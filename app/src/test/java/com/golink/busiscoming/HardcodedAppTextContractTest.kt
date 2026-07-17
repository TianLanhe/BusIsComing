package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HardcodedAppTextContractTest {
    @Test
    fun appFacingKotlinDoesNotContainHardcodedChineseText() {
        val roots = listOf(
            File("src/main/java/com/golink/busiscoming/ui"),
            File("src/main/java/com/golink/busiscoming/service"),
            File("src/main/java/com/golink/busiscoming/data/model"),
            File("src/main/java/com/golink/busiscoming/data/location/PlaceDistanceFormatter.kt")
        )
        val sourceRoot = File("src/main/java/com/golink/busiscoming")
        val violations = roots.asSequence()
            .flatMap { root -> if (root.isDirectory) root.walkTopDown().asSequence() else sequenceOf(root) }
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val relative = file.relativeTo(sourceRoot).invariantSeparatorsPath
                    val location = "$relative:${index + 1}"
                    if (line.contains(CJK) && line.contains('"')) {
                        "$location: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue("Hardcoded app-facing Chinese text:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    private companion object {
        val CJK = Regex("[\\u3400-\\u9FFF]")
    }
}
