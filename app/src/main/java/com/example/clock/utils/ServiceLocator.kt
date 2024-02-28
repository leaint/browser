package com.example.clock.utils

import android.content.ClipboardManager
import android.content.Context


fun getClipText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val p = clipboard.primaryClip

    if (p != null && p.itemCount > 0) {
        return p.getItemAt(0).text.toString()
    }

    return null
}