package com.example.clock.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText

class NoWarpEditText : EditText {

    init {
        val newLineChars = CharArray(2)
        newLineChars[0] = '\r'
        newLineChars[1] = '\n'

        val newLinePattern = "[\r\n]".toRegex()

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {

                if (s != null && s.indexOfAny(newLineChars) != -1) {
                    val t = s.replace(newLinePattern, "")
                    if (t.length != s.length) {
                        text.replace(0, length(), t)
//                        setText(t)
                    }
                }
            }

        })

    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs)
        outAttrs!!.imeOptions = outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()
        return ic
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        var id = id
        if(id == android.R.id.paste) {
            id = android.R.id.pasteAsPlainText
        }
        return super.onTextContextMenuItem(id)
    }
}