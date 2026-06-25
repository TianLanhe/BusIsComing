package com.example.busiscoming.ui.main

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscoming.R
import com.example.busiscoming.data.location.CurrentPlaceSelectionResult
import com.example.busiscoming.data.location.PlaceAttribution
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfigValidator
import com.example.busiscoming.data.repository.CitybusPlaceSearchRepository
import com.example.busiscoming.data.repository.PlaceSearchRepository
import com.example.busiscoming.data.repository.RouteConfigRepository
import com.example.busiscoming.ui.common.PlaceInputController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.ExecutorService

class TemporaryRouteBottomSheet(
    private val context: Context,
    private val routeConfigRepository: RouteConfigRepository,
    private val mainHandler: android.os.Handler,
    private val searchExecutor: ExecutorService,
    private val placeSearchRepository: PlaceSearchRepository = CitybusPlaceSearchRepository(),
    private val onCurrentPlaceRequested: (
        isAuto: Boolean,
        callback: (CurrentPlaceSelectionResult) -> Unit
    ) -> Unit = { _, callback -> callback(CurrentPlaceSelectionResult.Failure) },
    private val onQuery: (Place, Place) -> Unit,
    private val onSaved: (Long) -> Unit
) {
    private var dialog: BottomSheetDialog? = null
    private var originController: PlaceInputController? = null
    private var destinationController: PlaceInputController? = null
    private var originAttributionText: TextView? = null
    private var swapButton: AppCompatImageButton? = null
    private var candidateBackCallback: OnBackInvokedCallback? = null
    private var currentPlaceGeneration: Int = 0
    private var originTouchedByUser: Boolean = false

    fun show() {
        dispose()
        originTouchedByUser = false
        currentPlaceGeneration += 1
        val bottomSheetDialog = BottomSheetDialog(context)
        dialog = bottomSheetDialog

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            isClickable = true
            setOnClickListener { hideCandidateLists() }
        }
        val scroll = NestedScrollView(context).apply {
            isFillViewport = true
            isNestedScrollingEnabled = true
            addView(content)
        }

        content.addView(TextView(context).apply {
            text = "臨時查詢"
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })

        val inputFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }
        val inputColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(56), 0)
        }

        val originInputLayout = placeInputLayout("輸入起點關鍵字，並從匹配清單中選擇")
        val originInput = placeInput(R.id.temporaryOriginInput)
        originInputLayout.addView(originInput)
        configureLocationEndIcon(originInputLayout)
        inputColumn.addView(originInputLayout)
        val originAttribution = attributionText()
        originAttributionText = originAttribution
        inputColumn.addView(originAttribution)
        val originLoading = loadingRow()
        inputColumn.addView(originLoading)
        val originCandidates = candidateList(R.id.temporaryOriginCandidateList)
        inputColumn.addView(originCandidates)

        val destinationInputLayout = placeInputLayout("輸入終點關鍵字，並從匹配清單中選擇").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val destinationInput = placeInput(R.id.temporaryDestinationInput)
        destinationInputLayout.addView(destinationInput)
        inputColumn.addView(destinationInputLayout)
        val destinationLoading = loadingRow()
        inputColumn.addView(destinationLoading)
        val destinationCandidates = candidateList(R.id.temporaryDestinationCandidateList)
        inputColumn.addView(destinationCandidates)

        inputFrame.addView(inputColumn)
        val swapControl = AppCompatImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(48),
                dp(48),
                android.view.Gravity.END or android.view.Gravity.TOP
            ).apply { topMargin = dp(52) }
            background = ContextCompat.getDrawable(context, R.drawable.sort_chip_background)
            contentDescription = "交換起點和終點"
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setImageResource(R.drawable.ic_swap_curved)
            scaleType = android.widget.ImageView.ScaleType.CENTER
        }
        swapButton = swapControl
        inputFrame.addView(swapControl)
        content.addView(inputFrame)

        originController = PlaceInputController(
            context = context,
            input = originInput,
            inputLayout = originInputLayout,
            loadingView = originLoading,
            candidateList = originCandidates,
            placeSearchRepository = placeSearchRepository,
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = { dialog?.isShowing == true },
            onCandidateVisibilityChanged = { visible ->
                if (visible) {
                    destinationController?.hideCandidates()
                }
                setCandidateMode(visible)
            },
            onUserTextEdited = {
                originTouchedByUser = true
                currentPlaceGeneration += 1
                hideOriginAttribution()
            },
            onPlaceSelected = {
                originTouchedByUser = true
                currentPlaceGeneration += 1
                hideOriginAttribution()
                focusUnselectedPeer(destinationController, destinationInput)
            }
        )
        destinationController = PlaceInputController(
            context = context,
            input = destinationInput,
            inputLayout = destinationInputLayout,
            loadingView = destinationLoading,
            candidateList = destinationCandidates,
            placeSearchRepository = placeSearchRepository,
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = { dialog?.isShowing == true },
            onCandidateVisibilityChanged = { visible ->
                if (visible) {
                    originController?.hideCandidates()
                }
                setCandidateMode(visible)
            },
            onPlaceSelected = {
                focusUnselectedPeer(originController, originInput)
            }
        )

        swapControl.setOnClickListener { view ->
            view.animate().rotationBy(180f).setDuration(220L).start()
            originController?.swapWith(destinationController ?: return@setOnClickListener)
        }

        content.addView(MaterialButton(context).apply {
            text = "使用此路線查詢"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
            setOnClickListener { queryTemporaryRoute() }
        })
        content.addView(MaterialButton(context).apply {
            text = "保存為常用"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener { promptSaveTemporaryRoute() }
        })

        bottomSheetDialog.setContentView(scroll)
        bottomSheetDialog.setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_BACK &&
                event.action == KeyEvent.ACTION_UP &&
                hideCandidateLists()
        }
        bottomSheetDialog.setOnDismissListener { disposeControllers() }
        bottomSheetDialog.show()
        requestCurrentOriginIfNeeded(isAuto = true)
    }

    fun dispose() {
        disposeControllers()
        dialog?.dismiss()
        dialog = null
    }

    private fun queryTemporaryRoute() {
        val places = validatePlaces() ?: return
        dialog?.dismiss()
        onQuery(places.first, places.second)
    }

    private fun promptSaveTemporaryRoute() {
        val places = validatePlaces() ?: return
        TemporaryRouteSaveDialog.show(
            context = context,
            routeConfigRepository = routeConfigRepository,
            origin = places.first,
            destination = places.second
        ) { id ->
            dialog?.dismiss()
            onSaved(id)
        }
    }

    private fun validatePlaces(): Pair<Place, Place>? {
        val origin = originController?.selectedPlace
        val destination = destinationController?.selectedPlace
        val validation = RouteConfigValidator.validate("臨時查詢", origin, destination)
        originController?.setError(validation.originError)
        destinationController?.setError(validation.destinationError)
        if (!validation.isValid || origin == null || destination == null) return null
        return origin to destination
    }

    private fun configureLocationEndIcon(inputLayout: TextInputLayout) {
        inputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        inputLayout.setEndIconDrawable(R.drawable.ic_location_outline)
        inputLayout.setEndIconContentDescription(context.getString(R.string.use_my_location))
        inputLayout.setEndIconOnClickListener {
            requestCurrentOriginIfNeeded(isAuto = false)
        }
    }

    private fun requestCurrentOriginIfNeeded(isAuto: Boolean) {
        if (isAuto && originTouchedByUser) return
        val generation = ++currentPlaceGeneration
        onCurrentPlaceRequested(isAuto) { result ->
            mainHandler.post {
                if (dialog?.isShowing != true || currentPlaceGeneration != generation) return@post
                when (result) {
                    is CurrentPlaceSelectionResult.Success -> {
                        originController?.setCurrentLocationSnapshot(result.snapshot)
                        destinationController?.setCurrentLocationSnapshot(result.snapshot)
                        originController?.setSelectedPlace(result.place)
                        showOriginAttribution(result.attribution)
                    }
                    CurrentPlaceSelectionResult.Failure -> {
                        hideOriginAttribution()
                        if (isAuto) {
                            originController?.setHelperText("暫時無法取得目前位置，請手動選擇起點")
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "暫時無法取得目前位置",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun hideCandidateLists(): Boolean {
        val originHidden = originController?.hideCandidates() == true
        val destinationHidden = destinationController?.hideCandidates() == true
        return originHidden || destinationHidden
    }

    private fun showOriginAttribution(attribution: PlaceAttribution?) {
        originAttributionText?.visibility = if (attribution == PlaceAttribution.GOOGLE_MAPS) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun hideOriginAttribution() {
        originAttributionText?.visibility = View.GONE
    }

    private fun focusUnselectedPeer(
        peerController: PlaceInputController?,
        peerInput: MaterialAutoCompleteTextView
    ) {
        if (peerController?.selectedPlace != null) return
        peerInput.requestFocus()
        peerInput.post {
            context.getSystemService(InputMethodManager::class.java)
                .showSoftInput(peerInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setCandidateMode(enabled: Boolean) {
        val hasVisibleCandidates = enabled ||
            originController?.isCandidateVisible() == true ||
            destinationController?.isCandidateVisible() == true
        swapButton?.visibility = if (hasVisibleCandidates) View.GONE else View.VISIBLE
        updateCandidateBackPriority(hasVisibleCandidates)
        val bottomSheet = dialog?.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = if (hasVisibleCandidates) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
        behavior.state = if (hasVisibleCandidates) {
            BottomSheetBehavior.STATE_EXPANDED
        } else {
            BottomSheetBehavior.STATE_COLLAPSED
        }
        bottomSheet.requestLayout()
    }

    private fun updateCandidateBackPriority(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val activeDialog = dialog ?: return
        if (enabled && candidateBackCallback == null) {
            val callback = OnBackInvokedCallback { hideCandidateLists() }
            activeDialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
            candidateBackCallback = callback
        } else if (!enabled) {
            candidateBackCallback?.let(
                activeDialog.onBackInvokedDispatcher::unregisterOnBackInvokedCallback
            )
            candidateBackCallback = null
        }
    }

    private fun placeInputLayout(hintText: String): TextInputLayout {
        return TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = hintText
            isEndIconVisible = false
        }
    }

    private fun placeInput(id: Int): MaterialAutoCompleteTextView {
        return MaterialAutoCompleteTextView(context).apply {
            this.id = id
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            threshold = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
            minHeight = dp(56)
            setSingleLine(true)
            setPadding(dp(16), paddingTop, dp(16), paddingBottom)
            textSize = 16f
        }
    }

    private fun candidateList(id: Int): RecyclerView {
        return RecyclerView(context).apply {
            this.id = id
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            isNestedScrollingEnabled = true
            visibility = View.GONE
        }
    }

    private fun loadingRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            gravity = android.view.Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            addView(ProgressBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                isIndeterminate = true
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
                text = "正在匹配地點..."
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                textSize = 13f
            })
        }
    }

    private fun attributionText(): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.google_maps_address_attribution)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 12f
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
    }

    private fun disposeControllers() {
        updateCandidateBackPriority(false)
        originController?.dispose()
        destinationController?.dispose()
        originController = null
        destinationController = null
        swapButton = null
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
