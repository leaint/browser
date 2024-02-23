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

    fun setIsTest(v: Boolean) {
        _isTest.value = v
    }

    fun toggleShow() {
        when (uiState.value) {
            ADPickUIState.CLOSED -> uiState.tryEmit(ADPickUIState.SHOWING)
            else -> uiState.tryEmit(ADPickUIState.CLOSED)
        }
    }
}

interface ADPickEvent {

    fun onShow()
    fun onClose()

    fun onShowPickTool()
    fun onHidePickTool()

    fun getDownElem()
    fun getUpElem()

    fun getCur()

    fun onShowPickMask()
    fun onHidePickMask()

    fun doPick()

    fun onTest(s: String)
    fun onTestReset(s: String)

    fun onAddRule(s: String)
}

@SuppressLint("ClickableViewAccessibility")
fun initAdPickModel(
    context: Context,
    setting: GlobalWebViewSetting,
    binding: AdPickBinding,
    adPickModel: ADPickViewModel,
    holderController: HolderController,
    uiModelListener: UIModelListener,
    owner: LifecycleOwner,
) {
    val pickEventListener = object : ADPickEvent {
        override fun onShow() {
            binding.adPickBox.visibility = View.VISIBLE
        }

        override fun onClose() {
            binding.adPickBox.visibility = View.GONE
        }

        override fun onShowPickTool() {
            binding.adPickToolBox.visibility = View.VISIBLE
        }

        override fun onHidePickTool() {
            binding.adPickToolBox.visibility = View.GONE
        }

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

        override fun onShowPickMask() {
            binding.adPickView.visibility = View.VISIBLE
        }

        override fun onHidePickMask() {
            binding.adPickView.visibility = View.GONE

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
                val u = it.url ?: return@let
                val h = hostMatch(u) ?: return@let
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
    }
    owner.lifecycle.coroutineScope.launch {

        adPickModel.uiState.collect {

            when (it) {
                ADPickUIState.CLOSED -> {
                    pickEventListener.onClose()
                    adPickModel.recentResult.value = ""
                }

                ADPickUIState.PICKING -> {
                    adPickModel.recentResult.value = ""
                    pickEventListener.onHidePickTool()
                    pickEventListener.onShowPickMask()
                    pickEventListener.doPick()

                }

                ADPickUIState.SHOWING -> {
                    pickEventListener.onShow()
                    pickEventListener.onShowPickTool()
                    pickEventListener.onHidePickMask()
                    adPickModel.setIsTest(false)
                }

            }
        }

    }

    owner.lifecycle.coroutineScope.launch {

        adPickModel.recentResult.transform {
            val s = it.trim('"')
            if (!s.contentEquals(binding.adPickTxt.text.toString())) {
                emit(s)
            }
        }.collect {
            binding.adPickTxt.setText(it)
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

        if (it != binding.adPickTestBtn.isChecked) {
            binding.adPickTestBtn.isChecked = it
        }
    }

    binding.adPickView.setOnTouchListener { _, event ->
        run {

            if (adPickModel.uiState.value == ADPickUIState.PICKING) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        event.action =
                            (event.action and MotionEvent.ACTION_MASK.inv()) or MotionEvent.ACTION_HOVER_EXIT
                        pickEventListener.getCur()
                        adPickModel.uiState.tryEmit(ADPickUIState.SHOWING)
                    }

                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_SCROLL -> {
                        event.action =
                            (event.action and MotionEvent.ACTION_MASK.inv()) or MotionEvent.ACTION_HOVER_MOVE
                    }

                }
                holderController.currentGroup?.getCurrent()?.webView?.get()
                    ?.dispatchGenericMotionEvent(event)
            }
            false
        }
    }

    binding.adPickTxt.addTextChangedListener {
        it ?: return@addTextChangedListener
        adPickModel.recentResult.tryEmit(it.toString())
    }

    binding.adPickBtn.setOnClickListener {
        adPickModel.uiState.tryEmit(ADPickUIState.PICKING)
    }

    binding.adPickDown.setOnClickListener {
        pickEventListener.getDownElem()
    }

    binding.adPickUp.setOnClickListener {
        pickEventListener.getUpElem()
    }

    binding.adPickTestBtn.setOnCheckedChangeListener { _, isChecked ->
        adPickModel.setIsTest(isChecked)
    }

    binding.adPickAdd.setOnClickListener {
        pickEventListener.onAddRule(adPickModel.recentResult.value.trim())
    }

    binding.adPickClose.setOnClickListener {
        adPickModel.uiState.tryEmit(ADPickUIState.CLOSED)
    }

}
