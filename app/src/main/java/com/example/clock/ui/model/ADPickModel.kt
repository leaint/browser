package com.example.clock.ui.model

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import com.example.clock.ChromeActivity
import com.example.clock.databinding.AdPickBinding
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.tab.manager.HolderController
import com.example.clock.utils.MyToast
import com.example.clock.utils.hostMatch

interface ADPickEvent {

    fun onShow()
    fun onHide()

    fun onShowPickTool()

    fun onHidePickTool()

    fun getNextElem()
    fun getUpElem()

    fun getCur()

    fun onShowPickMask()
    fun onHidePickMask()

    fun onUpdateUI()

    fun doPick()

    fun onTest(s: String)
    fun onTestReset(s: String)

    fun onAddRule(s: String)
}

class ADPickModel {

    var pickText = ""

    private var showTool = true

    var showPick = false

    var pickEventListener: ADPickEvent? = null

    var isTest = false

    fun toogleShow() {
        if (showPick) {
            hideADPick()
        } else {
            showADPick()
        }
    }

    fun showADPick() {
        showPick = true
        pickEventListener?.onUpdateUI()
        pickEventListener?.onShow()
        pickEventListener?.onShowPickTool()
        showTool = true
        isTest = false
        pickEventListener?.onHidePickMask()
    }

    fun hideADPick() {

        showPick = false
        pickText = ""
        updateTest(false)
        pickEventListener?.onHide()
    }

    fun startPick() {
        pickEventListener?.onHidePickTool()
        showTool = false
        pickEventListener?.onShowPickMask()
        pickEventListener?.doPick()
    }

    fun stopPick() {
        pickEventListener?.onHidePickMask()
        pickEventListener?.onShowPickTool()
        showTool = true
        pickEventListener?.getCur()
    }

    fun updatePickText(s: String, refreshUI: Boolean = true) {
        val ss = s.trim('"')
        if (pickText != ss) {

            pickText = ss
            updateTest(false)
            if (refreshUI)
                pickEventListener?.onUpdateUI()
        }
    }

    fun downElem() {
        pickEventListener?.getNextElem()

    }

    fun upElem() {
        pickEventListener?.getUpElem()
    }

    fun stopTest() {
        isTest = false
        pickEventListener?.onTestReset(pickText)

    }

    fun doTest() {
        if (pickText.isNotEmpty()) {

            isTest = true
            pickEventListener?.onTest(pickText)
        }
    }

    fun updateTest(t: Boolean) {
        if (t != isTest) {
            if (t) {
                doTest()
            } else {
                stopTest()
            }
        }
    }

    fun addRule() {
        val ss = pickText.trim()
        pickEventListener?.onAddRule(ss)
    }
}

@SuppressLint("ClickableViewAccessibility")
fun initAdPickModel(
    context: Context,
    setting: GlobalWebViewSetting,
    binding: AdPickBinding,
    adPickModel: ADPickModel,
    holderController: HolderController,
    uiModelListener: UIModelListener
) {

    binding.adPickView.setOnTouchListener { v, event ->
        run {

            if (adPickModel.showPick) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        event.action =
                            (event.action and MotionEvent.ACTION_MASK.inv()) or MotionEvent.ACTION_HOVER_EXIT
                        adPickModel.stopPick()
                    }

                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_SCROLL -> {
                        event.action =
                            (event.action and MotionEvent.ACTION_MASK.inv()) or MotionEvent.ACTION_HOVER_MOVE
                    }

                }
                holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                    it.dispatchGenericMotionEvent(event)
                }
            }
            false
        }
    }

    binding.adPickTxt.addTextChangedListener {
        adPickModel.updatePickText(it.toString(), false)
    }

    binding.adPickBtn.setOnClickListener {
        adPickModel.startPick()
    }

    binding.adPickDown.setOnClickListener {
        adPickModel.downElem()
    }

    binding.adPickUp.setOnClickListener {
        adPickModel.upElem()
    }


    binding.adPickTestBtn.setOnCheckedChangeListener { _, isChecked ->
        run {

            adPickModel.updateTest(isChecked)
        }
    }

    binding.adPickAdd.setOnClickListener {
        adPickModel.addRule()
    }

    binding.adPickClose.setOnClickListener {
        adPickModel.hideADPick()
    }

    adPickModel.pickEventListener = object : ADPickEvent {
        override fun onShow() {
            binding.adPickBox.visibility = View.VISIBLE
        }

        override fun onHide() {
            binding.adPickBox.visibility = View.GONE
        }

        override fun onShowPickTool() {
            binding.adPickToolBox.visibility = View.VISIBLE
        }

        override fun onHidePickTool() {
            binding.adPickToolBox.visibility = View.GONE
        }

        override fun getNextElem() {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                it.evaluateJavascript("goBack()") {

                    adPickModel.updatePickText(it)
                }
            }
        }

        override fun getUpElem() {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                it.evaluateJavascript("goUp()") {

                    adPickModel.updatePickText(it)
                }
            }
        }

        override fun onShowPickMask() {
            binding.adPickView.visibility = View.VISIBLE
        }

        override fun onHidePickMask() {
            binding.adPickView.visibility = View.GONE

        }

        override fun onUpdateUI() {
            if (!adPickModel.pickText.contentEquals(binding.adPickTxt.text)) {
                binding.adPickTxt.setText(adPickModel.pickText)
            }
            if (adPickModel.isTest != binding.adPickTestBtn.isChecked) {
                binding.adPickTestBtn.isChecked = adPickModel.isTest
            }
        }

        override fun getCur() {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                it.evaluateJavascript("getCur()") {
                    adPickModel.updatePickText(it)
                }
            }
        }

        override fun onTest(s: String) {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                it.evaluateJavascript(
                    "document.querySelectorAll('${s}').forEach(i=>{let oriDisplay = i.computedStyleMap().get('display');i.style.display = 'none';i.oriDisplay = oriDisplay;})",
                    null
                )
            }
        }

        override fun onTestReset(s: String) {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                it.evaluateJavascript(
                    "document.querySelectorAll('${s}').forEach(i=>{if(i.oriDisplay!==undefined){i.style.display = i.oriDisplay;}})",
                    null
                )
            }
        }

        override fun onAddRule(s: String) {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                val u = it.url
                if (u != null) {

                    val h = hostMatch(u)
                    if (h != null) {
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
            }
        }

        override fun doPick() {
            holderController.currentGroup?.getCurrent()?.webView?.get()?.let {
                it.evaluateJavascript(setting.do_pick_js, null)
            }

        }
    }

}
