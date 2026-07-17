package com.golink.busiscoming.ui.settings

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.golink.busiscoming.R
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.data.repository.RouteImportMode
import com.golink.busiscoming.data.transfer.DecodedRouteTransfer
import com.golink.busiscoming.data.transfer.RouteImportPlan
import com.golink.busiscoming.data.transfer.RouteImportPlanner
import com.golink.busiscoming.data.transfer.RouteTransferCodec
import com.golink.busiscoming.data.transfer.RouteTransferError
import com.golink.busiscoming.data.transfer.RouteTransferException
import com.golink.busiscoming.data.transfer.RouteTransferFileReader
import com.golink.busiscoming.ui.common.applyStatusBarPadding
import com.golink.busiscoming.ui.common.localizedText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RouteTransferActivity : AppCompatActivity() {
    private lateinit var repository: RouteConfigRepository
    private lateinit var executor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var homeGroup: View
    private lateinit var previewGroup: View
    private lateinit var processingGroup: View
    private lateinit var currentCountText: TextView
    private lateinit var summaryText: TextView
    private lateinit var previewFileText: TextView
    private lateinit var previewImpactText: TextView
    private lateinit var previewNames: LinearLayout
    private lateinit var importButton: View
    private lateinit var exportButton: View
    private lateinit var mergeButton: View
    private lateinit var replaceButton: View
    private lateinit var cancelPreviewButton: View

    private var currentRouteCount = 0
    private var isBusy = false
    private var operationGeneration = 0
    private var destroyed = false
    private var stage = Stage.HOME
    private var candidateUri: Uri? = null
    private var candidateDisplayName: String? = null
    private var preview: PreviewState? = null

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument(RouteTransferUiPolicy.EXPORT_MIME)
    ) { uri ->
        if (uri != null) exportTo(uri)
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            candidateUri = uri
            candidateDisplayName = null
            stage = Stage.PREVIEW
            parseCandidate(uri, null, recovering = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_transfer)
        title = getString(R.string.route_transfer_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        repository = RouteConfigRepository(this)
        executor = Executors.newSingleThreadExecutor()
        bindViews()
        bindActions()

        savedInstanceState?.getString(STATE_SUMMARY)?.takeIf { it.isNotBlank() }?.let(::setSummary)
        stage = savedInstanceState?.getString(STATE_STAGE)?.let {
            runCatching { Stage.valueOf(it) }.getOrDefault(Stage.HOME)
        } ?: Stage.HOME
        candidateUri = savedInstanceState?.getString(STATE_URI)?.let(Uri::parse)
        candidateDisplayName = savedInstanceState?.getString(STATE_DISPLAY_NAME)
        render()
        refreshRouteCount()
        if (stage == Stage.PREVIEW && candidateUri != null) {
            parseCandidate(requireNotNull(candidateUri), candidateDisplayName, recovering = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_STAGE, stage.name)
        outState.putString(STATE_URI, candidateUri?.toString())
        outState.putString(STATE_DISPLAY_NAME, candidateDisplayName)
        outState.putString(STATE_SUMMARY, summaryText.text?.toString())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        destroyed = true
        operationGeneration += 1
        executor.shutdownNow()
        repository.close()
        super.onDestroy()
    }

    private fun bindViews() {
        findViewById<View>(R.id.routeTransferRoot).applyStatusBarPadding()
        homeGroup = findViewById(R.id.routeTransferHomeGroup)
        previewGroup = findViewById(R.id.routeTransferPreviewGroup)
        processingGroup = findViewById(R.id.routeTransferProcessingGroup)
        currentCountText = findViewById(R.id.routeTransferCurrentCountText)
        summaryText = findViewById(R.id.routeTransferSummaryText)
        previewFileText = findViewById(R.id.routeTransferPreviewFileText)
        previewImpactText = findViewById(R.id.routeTransferPreviewImpactText)
        previewNames = findViewById(R.id.routeTransferPreviewNames)
        importButton = findViewById(R.id.routeTransferImportButton)
        exportButton = findViewById(R.id.routeTransferExportButton)
        mergeButton = findViewById(R.id.routeTransferMergeButton)
        replaceButton = findViewById(R.id.routeTransferReplaceButton)
        cancelPreviewButton = findViewById(R.id.routeTransferCancelPreviewButton)
    }

    private fun bindActions() {
        findViewById<View>(R.id.routeTransferBackButton).setOnClickListener { handleBack() }
        importButton.setOnClickListener {
            if (!isBusy) openDocument.launch(arrayOf(RouteTransferUiPolicy.IMPORT_MIME))
        }
        exportButton.setOnClickListener { showExportPrivacyWarning() }
        mergeButton.setOnClickListener { submitImport(RouteImportMode.MERGE) }
        replaceButton.setOnClickListener { showReplaceConfirmation() }
        cancelPreviewButton.setOnClickListener { returnToHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
    }

    private fun showExportPrivacyWarning() {
        if (isBusy || currentRouteCount == 0) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.route_transfer_privacy_title)
            .setMessage(R.string.route_transfer_privacy_warning)
            .setNegativeButton(R.string.route_transfer_cancel, null)
            .setPositiveButton(R.string.route_transfer_continue_export) { _, _ ->
                createDocument.launch(RouteTransferUiPolicy.suggestedFileName(System.currentTimeMillis()))
            }
            .show()
    }

    private fun exportTo(uri: Uri) {
        val token = beginOperation() ?: return
        executor.execute {
            try {
                val routes = repository.getAll()
                val bytes = RouteTransferCodec.encode(
                    routes,
                    RouteTransferUiPolicy.exportedAtUtc(System.currentTimeMillis())
                )
                contentResolver.openOutputStream(uri, "w")?.use { stream ->
                    stream.write(bytes)
                    stream.flush()
                } ?: throw IOException("Unable to open export destination")
                postIfCurrent(token) {
                    finishOperation()
                    setSummary(RouteTransferUiPolicy.exportSummary(routes.size, localizedText()))
                }
            } catch (_: Exception) {
                runCatching { contentResolver.delete(uri, null, null) }
                postIfCurrent(token) {
                    finishOperation()
                    setSummary(getString(R.string.route_transfer_error_write))
                }
            }
        }
    }

    internal fun exportToForTesting(uri: Uri) {
        exportTo(uri)
    }

    private fun parseCandidate(uri: Uri, knownDisplayName: String?, recovering: Boolean) {
        val token = beginOperation() ?: return
        executor.execute {
            try {
                val displayName = knownDisplayName ?: resolveDisplayName(uri)
                val decoded = contentResolver.openInputStream(uri)?.use { stream ->
                    RouteTransferCodec.decode(RouteTransferFileReader.read(stream, displayName))
                } ?: throw FileNotFoundException("Unable to open import source")
                val plan = RouteImportPlanner.plan(
                    decoded.routes,
                    decoded.duplicateCount,
                    repository.getAll()
                )
                postIfCurrent(token) {
                    candidateDisplayName = displayName
                    preview = PreviewState(decoded, plan)
                    stage = Stage.PREVIEW
                    finishOperation()
                    renderPreview()
                }
            } catch (error: Exception) {
                postIfCurrent(token) {
                    finishOperation()
                    val message = if (recovering && (error is SecurityException || error is FileNotFoundException)) {
                        getString(R.string.route_transfer_uri_expired)
                    } else {
                        transferErrorMessage(error)
                    }
                    returnToHome(clearSummary = false)
                    setSummary(message)
                }
            }
        }
    }

    internal fun previewImportForTesting(uri: Uri, displayName: String?) {
        if (isBusy) return
        candidateUri = uri
        candidateDisplayName = displayName
        stage = Stage.PREVIEW
        parseCandidate(uri, displayName, recovering = false)
    }

    private fun submitImport(mode: RouteImportMode) {
        val state = preview ?: return
        val token = beginOperation() ?: return
        executor.execute {
            try {
                val result = repository.importRoutes(state.decoded.routes, mode)
                postIfCurrent(token) {
                    currentRouteCount = repositoryCountAfter(result.addedCount, mode)
                    finishOperation()
                    returnToHome(clearSummary = false)
                    setSummary(RouteTransferUiPolicy.importSummary(mode, result, localizedText()))
                    refreshRouteCount()
                }
            } catch (_: Exception) {
                postIfCurrent(token) {
                    finishOperation()
                    setSummary(getString(R.string.route_transfer_error_import))
                }
            }
        }
    }

    private fun showReplaceConfirmation() {
        val state = preview ?: return
        if (isBusy) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.route_transfer_replace_title)
            .setMessage(
                getString(
                    R.string.route_transfer_replace_warning,
                    state.plan.replaceDeleteCount,
                    state.plan.replaceImportCount
                )
            )
            .setNegativeButton(R.string.route_transfer_cancel, null)
            .setPositiveButton(R.string.route_transfer_confirm_replace) { _, _ ->
                submitImport(RouteImportMode.REPLACE)
            }
            .show()
    }

    private fun renderPreview() {
        val state = preview ?: return
        previewFileText.text = getString(
            R.string.route_transfer_preview_file,
            candidateDisplayName ?: candidateUri?.lastPathSegment.orEmpty()
        )
        previewImpactText.text = listOf(
            getString(R.string.route_transfer_preview_unique, state.plan.uniqueRouteCount),
            getString(R.string.route_transfer_preview_duplicates, state.plan.inFileDuplicateCount),
            getString(R.string.route_transfer_preview_merge, state.plan.mergeAddCount, state.plan.mergeSkipCount),
            getString(
                R.string.route_transfer_preview_replace,
                state.plan.replaceDeleteCount,
                state.plan.replaceImportCount
            )
        ).joinToString("\n")
        previewNames.removeAllViews()
        state.decoded.routes.forEachIndexed { index, route ->
            previewNames.addView(TextView(this).apply {
                text = "${index + 1}. ${route.name}"
                setTextColor(getColor(R.color.bus_text_primary))
                textSize = 14f
                setPadding(0, 8, 0, 8)
            })
        }
        render()
    }

    private fun refreshRouteCount() {
        val token = operationGeneration
        executor.execute {
            val count = runCatching { repository.getAll().size }.getOrNull() ?: return@execute
            postIfCurrent(token) {
                currentRouteCount = count
                render()
            }
        }
    }

    private fun render() {
        currentCountText.text = getString(R.string.route_transfer_current_count, currentRouteCount)
        val hasPreview = stage == Stage.PREVIEW && preview != null
        homeGroup.visibility = if (hasPreview) View.GONE else View.VISIBLE
        previewGroup.visibility = if (hasPreview) View.VISIBLE else View.GONE
        processingGroup.visibility = if (isBusy) View.VISIBLE else View.GONE
        val actions = RouteTransferUiPolicy.actionState(currentRouteCount, isBusy, hasPreview)
        importButton.isEnabled = actions.importEnabled
        exportButton.isEnabled = actions.exportEnabled
        mergeButton.isEnabled = actions.mergeEnabled
        replaceButton.isEnabled = actions.replaceEnabled
        cancelPreviewButton.isEnabled = !isBusy && hasPreview
    }

    private fun beginOperation(): Int? {
        if (isBusy || destroyed) return null
        isBusy = true
        operationGeneration += 1
        render()
        return operationGeneration
    }

    private fun finishOperation() {
        isBusy = false
        render()
    }

    private fun postIfCurrent(token: Int, action: () -> Unit) {
        mainHandler.post {
            if (!destroyed && token == operationGeneration) action()
        }
    }

    private fun returnToHome(clearSummary: Boolean = false) {
        stage = Stage.HOME
        candidateUri = null
        candidateDisplayName = null
        preview = null
        previewNames.removeAllViews()
        if (clearSummary) {
            summaryText.text = ""
            summaryText.visibility = View.GONE
        }
        render()
    }

    private fun setSummary(message: String) {
        summaryText.text = message
        summaryText.contentDescription = message
        summaryText.visibility = View.VISIBLE
    }

    private fun resolveDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    }.getOrNull()

    private fun transferErrorMessage(error: Exception): String {
        val transferError = (error as? RouteTransferException)?.error
        return getString(
            when (transferError) {
                RouteTransferError.FILE_TOO_LARGE -> R.string.route_transfer_error_file_too_large
                RouteTransferError.INVALID_FILE_EXTENSION -> R.string.route_transfer_error_extension
                RouteTransferError.INVALID_FORMAT,
                RouteTransferError.UNSUPPORTED_VERSION -> R.string.route_transfer_error_format
                RouteTransferError.MALFORMED_JSON,
                RouteTransferError.INVALID_SCHEMA,
                RouteTransferError.EMPTY_ROUTES,
                RouteTransferError.TOO_MANY_ROUTES,
                RouteTransferError.INVALID_ROUTE -> R.string.route_transfer_error_schema
                null -> R.string.route_transfer_error_read
            }
        )
    }

    private fun repositoryCountAfter(added: Int, mode: RouteImportMode): Int = when (mode) {
        RouteImportMode.MERGE -> currentRouteCount + added
        RouteImportMode.REPLACE -> added
    }

    private fun handleBack() {
        if (stage == Stage.PREVIEW) returnToHome(clearSummary = false) else finish()
    }

    private enum class Stage { HOME, PREVIEW }

    private data class PreviewState(
        val decoded: DecodedRouteTransfer,
        val plan: RouteImportPlan
    )

    companion object {
        private const val STATE_STAGE = "route_transfer_stage"
        private const val STATE_URI = "route_transfer_uri"
        private const val STATE_DISPLAY_NAME = "route_transfer_display_name"
        private const val STATE_SUMMARY = "route_transfer_summary"
    }
}
