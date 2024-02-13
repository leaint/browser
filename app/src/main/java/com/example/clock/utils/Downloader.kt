package com.example.clock.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.util.UUID

object Downloader {
    fun downloadFile(
        context: Context,
        uri: String, filename: String, mimetype: String?, headers: Map<String, String>?
    ) {
        try {

            var fname = filename
            val u = Uri.parse(uri)
            if (filename.isBlank()) {
                u.pathSegments.lastOrNull()?.let {
                    fname = it
                }
            }
            if (fname.isBlank()) {
                fname = UUID.randomUUID().toString()
            }

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(u)

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fname)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            headers?.forEach { (t, u) -> request.addRequestHeader(t, u) }
            mimetype?.let { request.setMimeType(mimetype) }
            downloadManager.enqueue(request)

            Toast.makeText(context, "Downloading file:\n$uri", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            e.message?.let { Log.e("error", it) }
        }
    }

}