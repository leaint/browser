package com.example.clock.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.EditText

class PowerEditText : EditText {
    private val sTempRect1 = Rect()

    override fun onDraw(canvas: Canvas) {
        val curLine = layout.getLineForOffset(selectionStart)
        layout.getLineBounds(layout.getLineForOffset(selectionStart), sTempRect1)
        val backupStyle = paint.style
        val backupColor = paint.color
        val textAlign = paint.textAlign

        paint.style = Paint.Style.FILL
        paint.color = Color.YELLOW
        sTempRect1.set(
            paddingLeft,
            paddingTop + layout.getLineTop(curLine),
            paddingLeft + sTempRect1.right,
            paddingTop + layout.getLineTop(curLine + 1)
        )
        canvas.drawRect(sTempRect1, paint)
        paint.style = backupStyle

        super.onDraw(canvas)

        if (lineMapToParagraph.size != layout.lineCount + 1) {
            updateD(text)
            return
        }

        canvas.save()

        if (!canvas.getClipBounds(sTempRect1)) {
            // Negative range end used as a special flag
            return
        }
        val dtop = sTempRect1.top
        val dbottom = sTempRect1.bottom

        val top = dtop.coerceAtLeast(0)
        val bottom: Int = layout.getLineTop(lineCount).coerceAtMost(dbottom)
        val firstLine = layout.getLineForVertical(top)
        val lastLine = layout.getLineForVertical(bottom)

        if (lastLine < 0) return
        val paint = layout.paint
        /*  Would be faster if we didn't have to do this. Can we chop the
            (displayable) text so that we don't need to do this ever?
        */
        val extendedPaddingTop = extendedPaddingTop
        val extendedPaddingBottom = extendedPaddingBottom

        val vspace: Int = bottom - top - compoundPaddingBottom - compoundPaddingTop
        val maxScrollY: Int = layout.height - vspace

        var clipLeft = (compoundPaddingLeft + scrollX).toFloat()
        var clipTop = (if (scrollY == 0) 0 else extendedPaddingTop + scrollY).toFloat()
        var clipRight = (right - left - compoundPaddingRight + scrollX).toFloat()
        var clipBottom = (bottom - top + scrollY
                - if (scrollY == maxScrollY) 0 else extendedPaddingBottom).toFloat()

        if (shadowRadius != 0f) {
            clipLeft += Math.min(0f, shadowDx - shadowRadius)
            clipRight += Math.max(0f, shadowDx + shadowRadius)
            clipTop += Math.min(0f, shadowDy - shadowRadius)
            clipBottom += Math.max(0f, shadowDy + shadowRadius)
        }


        canvas.clipRect(0f, clipTop, clipRight, clipBottom + totalPaddingTop + 30f)
//
        canvas.translate(
            0f,
            extendedPaddingTop.toFloat() + 30f
        )


        paint.color = Color.GRAY
        paint.textAlign = Paint.Align.RIGHT
        val left = (paddingLeft.toFloat() - 15).coerceAtLeast(0f)
        if (lineMapToParagraph.size == layout.lineCount + 1) {
            for (i in firstLine..lastLine) {

                val top = layout.getLineTop(i) + layout.getLineBottom(i) - layout.getLineBaseline(i)
                val lineNumber = lineMapToParagraph[i]
                if (lineNumber > 0)
                    canvas.drawText("$lineNumber", left, top.toFloat(), paint)
            }
        }

        canvas.drawLine(
            (paddingLeft.toFloat() - 15).coerceAtLeast(0f),
            clipTop,
            paddingLeft.toFloat(),
            clipBottom,
            paint
        )
        paint.color = backupColor
        paint.textAlign = textAlign

        canvas.restore()
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        var id = id
        if (id == android.R.id.paste) {
            id = android.R.id.pasteAsPlainText
        }
        return super.onTextContextMenuItem(id)
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

    private var lineMapToParagraph = IntArray(0)

    private fun updateD(text: CharSequence) {
        val newLineOffsets = ArrayList<Int>()
        var idx = text.indexOf('\n')
        if (idx == -1) {
            lineMapToParagraph = IntArray(0)
            return
        }
        val maxIdx = text.length - 1 - 1
        while (idx in 0..maxIdx) {
            newLineOffsets.add(idx)
            idx = text.indexOf('\n', idx + 1)
        }
        newLineOffsets.add(text.length - 1)
        val lineCount = layout?.lineCount ?: 0
        lineMapToParagraph = IntArray(lineCount + 1)
        lineMapToParagraph[0] = 1
        if (lineCount == 0) return
        for ((i, offset) in newLineOffsets.withIndex()) {
            val l = layout.getLineForOffset(offset) + 1
            lineMapToParagraph[l] = i + 2
        }
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        text ?: return
        layout ?: return

        updateD(text)
    }
}