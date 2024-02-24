package com.example.clock.ui.model

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import com.example.clock.databinding.AdPickBinding
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.tab.manager.HolderController
import com.example.clock.utils.MyToast
import com.example.clock.utils.hostMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

interface ADPickUIState {
    data object CLOSED : ADPickUIState
    data object SHOWING : ADPickUIState
    data object PICKING : ADPickUIState
}

class ADPickViewModel : ViewModel() {

    private val _isTest = MutableLiveData(false)

    val isTest = _isTest.distinctUntilChanged()

    val uiState = MutableStateFlow<ADPickUIState>(ADPickUIState.CLOSED)

    var recentResult = MutableStateFlow("")

    fun clearRecentResult() {
        recentResult.value = ""
    }

    fun setIsTest(v: Boolean) {
        _isTest.value = v
    }

    fun toggleShow() {
        val state = when (uiState.value) {
            ADPickUIState.CLOSED -> ADPickUIState.SHOWING
            else -> ADPickUIState.CLOSED
        }
        viewModelScope.launch {
            uiState.emit(state)
        }
    }
}

interface ADPickEvent {

    fun getDownElem()
    fun getUpElem()

    fun getCur()

    fun doPick()

    fun onTest(s: String)
    fun onTestReset(s: String)

    fun onAddRule(s: String)
    fun onUpdatePickText(s: String)

    fun onPickBtnClick()
    fun onTestBtnClick(isChecked: Boolean)
    fun onAddRuleClick()
    fun onCloseBtnClick()
    fun onPickModelClick()
}

@SuppressLint("ClickableViewAccessibility")
class ADPick(
    private val adPickBinding: AdPickBinding,
    onInput: (s: String) -> Unit,
    onPickBtnClick: () -> Unit,
    onDownElemClick: () -> Unit,
    onUpElemClick: () -> Unit,
    onAddRuleClick: () -> Unit,
    onCloseBtnClick: () -> Unit,
    onTestBtnClick: (isChecked: Boolean) -> Unit,
    onPickModelClick: () -> Unit,
    onDispatchGenericMotionEvent: (event: MotionEvent) -> Unit
) {
    init {

        adPickBinding.adPickTxt.addTextChangedListener {
            it ?: return@addTextChangedListener
            onInput(it.toString())
        }

        adPickBinding.adPickBtn.setOnClickListener {
            onPickBtnClick()
        }

        adPickBinding.adPickDown.setOnClickListener {
            onDownElemClick()
        }

        adPickBinding.adPickUp.setOnClickListener {
            onUpElemClick()
        }

        adPickBinding.adPickTestBtn.setOnCheckedChangeListener { _, isChecked ->
            onTestBtnClick(isChecked)
        }

        adPickBinding.adPickAdd.setOnClickListener {
            onAddRuleClick()

        }

        adPickBinding.adPickClose.setOnClickListener {
            onCloseBtnClick()
        }

        adPickBinding.adPickView.setOnTouchListener { _, event ->
            run {

                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        event.action =
                            (event.action and MotionEvent.ACTION_MASK.inv()) or MotionEvent.ACTION_HOVER_EXIT
                        onPickModelClick()
                    }

                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_SCROLL -> {
                        event.action =
                            (event.action and MotionEvent.ACTION_MASK.inv()) or MotionEvent.ACTION_HOVER_MOVE
                    }
                }

                onDispatchGenericMotionEvent(event)
                false
            }
        }
    }

    fun onShow() {
        adPickBinding.adPickBox.visibility = View.VISIBLE
    }

    fun onClose() {
        adPickBinding.adPickBox.visibility = View.GONE
    }

    fun onShowPickTool() {
        adPickBinding.adPickToolBox.visibility = View.VISIBLE
    }

    fun onHidePickTool() {
        adPickBinding.adPickToolBox.visibility = View.GONE
    }

    fun onShowPickMask() {
        adPickBinding.adPickView.visibility = View.VISIBLE
    }

    fun onHidePickMask() {
        adPickBinding.adPickView.visibility = View.GONE
    }

    fun getTestChecked() = adPickBinding.adPickTestBtn.isChecked
    fun setTestChecked(isChecked: Boolean) {
        adPickBinding.adPickTestBtn.isChecked = isChecked
    }

    fun getPickText() = adPickBinding.adPickTxt.text.toString()
    fun setPickText(s: String) {
        adPickBinding.adPickTxt.setText(s)
    }
}

fun initAdPickModel(
    context: Context,
    setting: GlobalWebViewSetting,
    adPickBinding: AdPickBinding,
    adPickModel: ADPickViewModel,
    holderController: HolderController,
    uiModelListener: UIModelListener,
    owner: LifecycleOwner,
) {

    val pickEventListener = object : ADPickEvent {

        override fun getDownElem() {
            holderController.currentGroup?.getCurrent()?.webView?.get()
                ?.evaluateJavascript("goBack()") {
                    adPickModel.recentResult.tryEmit(it)
                }

        }

        override fun getUpElem() {
            holderController.currentGroup?.getCurrent()?.webView?.get()
                ?.evaluateJavascript("goUp()") {
                    adPickModel.recentResult.tryEmit(it)
                }
        }

        override fun getCur() {
            holderController.currentGroup?.getCurrent()?.webView?.get()
                ?.evaluateJavascript("getCur()") {
                    adPickModel.recentResult.tryEmit(it)
                }
        }

        override fun onTest(s: String) {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.evaluateJavascript(
                "document.querySelectorAll('${s}').forEach(i=>{let oriDisplay = i.computedStyleMap().get('display');i.style.display = 'none';i.oriDisplay = oriDisplay;})",
                null
            )
        }

        override fun onTestReset(s: String) {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.evaluateJavascript(
                "document.querySelectorAll('${s}').forEach(i=>{if(i.oriDisplay!==undefined){i.style.display = i.oriDisplay;}})",
                null
            )
        }

        override fun onAddRule(s: String) {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                val h = hostMatch(it.url ?: return@let) ?: return@let
                uiModelListener.makeToast("Add Rule?\n$h##$s", MyToast.LENGTH_LONG)
                    .setAction("YES") {
                        val cur = setting.ad_rule[h]
                        setting.ad_rule[h] = if (cur != null) {
                            "$cur,$s"
                        } else {
                            s
                        }
                        setting.ruleChanged = true
                        Toast.makeText(
                            context, "Rule Added", Toast.LENGTH_SHORT
                        ).show()
                    }.show()
            }
        }

        override fun doPick() {
            holderController.currentGroup?.getCurrent()?.webView?.get()
                ?.evaluateJavascript(setting.do_pick_js, null)
        }

        override fun onUpdatePickText(s: String) {
            adPickModel.recentResult.tryEmit(s)
        }

        override fun onPickBtnClick() {
            adPickModel.uiState.tryEmit(ADPickUIState.PICKING)
        }

        override fun onTestBtnClick(isChecked: Boolean) {
            adPickModel.setIsTest(isChecked)
        }

        override fun onAddRuleClick() {
            onAddRule(adPickModel.recentResult.value.trim())
        }

        override fun onCloseBtnClick() {
            adPickModel.uiState.tryEmit(ADPickUIState.CLOSED)
        }

        override fun onPickModelClick() {
            if (adPickModel.uiState.value == ADPickUIState.PICKING) {
                getCur()
                adPickModel.uiState.tryEmit(ADPickUIState.SHOWING)
            }
        }
    }

    val adPick = ADPick(
        adPickBinding,
        pickEventListener::onUpdatePickText,
        pickEventListener::onPickBtnClick,
        pickEventListener::getDownElem,
        pickEventListener::getUpElem,
        pickEventListener::onAddRuleClick,
        pickEventListener::onCloseBtnClick,
        pickEventListener::onTestBtnClick,
        pickEventListener::onPickModelClick

    ) { event ->
        holderController.currentGroup?.getCurrent()?.webView?.get()
            ?.dispatchGenericMotionEvent(event)
    }

    owner.lifecycle.coroutineScope.launch {

        adPickModel.uiState.collect {

            when (it) {
                ADPickUIState.CLOSED -> {
                    adPick.onClose()
                    adPickModel.clearRecentResult()
                }

                ADPickUIState.PICKING -> {
                    adPickModel.clearRecentResult()
                    adPick.onHidePickTool()
                    adPick.onShowPickMask()
                    pickEventListener.doPick()
                }

                ADPickUIState.SHOWING -> {
                    adPick.onShow()
                    adPick.onShowPickTool()
                    adPick.onHidePickMask()
                    adPickModel.setIsTest(false)
                }
            }
        }

    }

    owner.lifecycle.coroutineScope.launch {

        adPickModel.recentResult.transform {
            val s = it.trim('"')
            if (!s.contentEquals(adPick.getPickText())) {
                emit(s)
            }
        }.collect {
            adPick.setPickText(it)
        }
    }

    adPickModel.isTest.observe(owner) {
        val s = adPickModel.recentResult.value
        if (it) {
            if (s.isNotEmpty()) {
                pickEventListener.onTest(s)
            }
        } else {
            pickEventListener.onTestReset(s)
        }

        if (it != adPick.getTestChecked()) {
            adPick.setTestChecked(it)
        }
    }
}
