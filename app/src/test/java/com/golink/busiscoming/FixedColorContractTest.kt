package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedColorContractTest {
    private val sourceRoot = File("src/main")

    @Test
    fun appUiDoesNotUseUnapprovedFixedWhiteOrLiteralColors() {
        val allowedFiles = setOf(
            "res/drawable/ic_launcher_background.xml",
            "res/drawable/ic_launcher_foreground.xml",
            "res/values/colors.xml",
            "res/values-night/colors.xml"
        )
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "xml" || it.extension == "kt") }
            .filterNot { it.relativeTo(sourceRoot).invariantSeparatorsPath in allowedFiles }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val fixedWhite = line.contains("@android:color/white") ||
                        line.contains("@color/white") ||
                        line.contains("Color.WHITE")
                    val isDocumentedRouteIdentityColor =
                        file.name == "RouteDetailBottomSheet.kt" && line.contains("Color.parseColor")
                    val literalHex = Regex("#[0-9A-Fa-f]{6,8}").containsMatchIn(line) &&
                        !isDocumentedRouteIdentityColor
                    if (fixedWhite || literalHex) {
                        "${file.relativeTo(sourceRoot).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue("Unapproved fixed UI colors:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
