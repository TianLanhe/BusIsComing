package com.example.busiscoming.ui.main

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.example.busiscoming.R
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfigValidator
import com.example.busiscoming.data.repository.CitybusPlaceSearchRepository
import com.example.busiscoming.data.repository.PlaceSearchRepository
import com.example.busiscoming.data.repository.RouteConfigRepository
import com.example.busiscoming.ui.common.PlaceInputController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.ExecutorService

class TemporaryRouteBottomSheet(
    private val context: Context,
    private val routeConfigRepository: RouteConfigRepository,
    private val mainHandler: android.os.Handler,
    private val searchExecutor: ExecutorService,
    private val placeSearchRepository: PlaceSearchRepository = CitybusPlaceSearchRepository(),
    private val onQuery: (Place, Place) -> Unit,
    private val onSaved: (Long) -> Unit
) {
    private var dialog: BottomSheetDialog? = null
    private var originController: PlaceInputController? = null
    private var destinationController: PlaceInputController? = null

    fun show() {
        dispose()
        val bottomSheetDialog = BottomSheetDialog(context)
        dialog = bottomSheetDialog

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
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
        val originInput = placeInput()
        originInputLayout.addView(originInput)
        inputColumn.addView(originInputLayout)
        val originLoading = loadingRow()
        inputColumn.addView(originLoading)

        val destinationInputLayout = placeInputLayout("輸入終點關鍵字，並從匹配清單中選擇").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val destinationInput = placeInput()
        destinationInputLayout.addView(destinationInput)
        inputColumn.addView(destinationInputLayout)
        val destinationLoading = loadingRow()
        inputColumn.addView(destinationLoading)

        inputFrame.addView(inputColumn)
        val swapButton = AppCompatImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48), android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL)
            background = ContextCompat.getDrawable(context, R.drawable.sort_chip_background)
            contentDescription = "交換起點和終點"
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setImageResource(R.drawable.ic_swap_curved)
            scaleType = android.widget.ImageView.ScaleType.CENTER
        }
        inputFrame.addView(swapButton)
        content.addView(inputFrame)

        originController = PlaceInputController(
            context = context,
            input = originInput,
            inputLayout = originInputLayout,
            loadingView = originLoading,
            placeSearchRepository = placeSearchRepository,
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = { dialog?.isShowing == true }
        )
        destinationController = PlaceInputController(
            context = context,
            input = destinationInput,
            inputLayout = destinationInputLayout,
            loadingView = destinationLoading,
            placeSearchRepository = placeSearchRepository,
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = { dialog?.isShowing == true }
        )

        swapButton.setOnClickListener { view ->
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

        bottomSheetDialog.setContentView(content)
        bottomSheetDialog.setOnDismissListener { disposeControllers() }
        bottomSheetDialog.show()
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
        val nameInput = TextInputEditText(context).apply {
            setText("${places.first.name} -> ${places.second.name}")
            setSelectAllOnFocus(true)
            maxLines = 1
        }
        val nameLayout = TextInputLayout(context).apply {
            hint = "常用路線名稱"
            addView(nameInput)
            setPadding(dp(4), 0, dp(4), 0)
        }

        AlertDialog.Builder(context)
            .setTitle("保存為常用")
            .setView(nameLayout)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameInput.text?.toString()?.trim().orEmpty()
                        val validation = RouteConfigValidator.validate(name, places.first, places.second)
                        nameLayout.error = validation.nameError
                        if (!validation.isValid) return@setOnClickListener
                        if (routeConfigRepository.hasDuplicate(name, places.first, places.second)) {
                            nameLayout.error = "路線已存在，請修改名稱或起終點"
                            return@setOnClickListener
                        }
                        val id = routeConfigRepository.insert(name, places.first, places.second)
                        Toast.makeText(context, "已保存為常用", Toast.LENGTH_SHORT).show()
                        dismiss()
                        dialog?.dismiss()
                        onSaved(id)
                    }
                }
                show()
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

    private fun placeInputLayout(hintText: String): TextInputLayout {
        return TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = hintText
        }
    }

    private fun placeInput(): MaterialAutoCompleteTextView {
        return MaterialAutoCompleteTextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            threshold = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 2
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

    private fun disposeControllers() {
        originController?.dispose()
        destinationController?.dispose()
        originController = null
        destinationController = null
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
