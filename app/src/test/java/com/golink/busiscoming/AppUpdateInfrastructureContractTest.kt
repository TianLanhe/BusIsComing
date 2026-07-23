package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateInfrastructureContractTest {
    private val catalog = File("../gradle/libs.versions.toml").readText()
    private val appBuild = File("build.gradle.kts").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val runtime = File(
        "src/main/java/com/golink/busiscoming/data/update/AppUpdateRuntime.kt"
    ).readText()

    @Test
    fun playInAppUpdateDependenciesAreDeclaredThroughTheVersionCatalog() {
        assertTrue(catalog.contains("playAppUpdate = \"2.1.0\""))
        assertTrue(catalog.contains("play-app-update ="))
        assertTrue(catalog.contains("play-app-update-ktx ="))
        assertTrue(appBuild.contains("implementation(libs.play.app.update)"))
        assertTrue(appBuild.contains("implementation(libs.play.app.update.ktx)"))
        assertFalse(appBuild.contains("org.jetbrains.kotlin.android"))
    }

    @Test
    fun manifestQueriesOnlyThePlayPackageWithoutInstallPermissions() {
        assertTrue(manifest.contains("<package android:name=\"com.android.vending\" />"))
        assertFalse(manifest.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertFalse(manifest.contains("application/vnd.android.package-archive"))
    }

    @Test
    fun websiteOnlySwitchIsDefinedAndWiresTheCoordinator() {
        assertTrue(
            Regex(
                """buildConfigField\(\s*"boolean",\s*"FORCE_WEBSITE_UPDATE_CHECK",""" +
                    """\s*"(?:true|false)"\s*\)"""
            ).containsMatchIn(appBuild)
        )
        assertTrue(
            runtime.contains(
                "forceWebsiteOnly = BuildConfig.FORCE_WEBSITE_UPDATE_CHECK"
            )
        )
        assertTrue(
            runtime.contains(
                "if (BuildConfig.FORCE_WEBSITE_UPDATE_CHECK) DisabledPlayUpdateSource"
            )
        )
    }
}
