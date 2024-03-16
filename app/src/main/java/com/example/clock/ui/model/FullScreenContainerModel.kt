package com.example.clock.ui.model

import android.view.View
import com.example.clock.databinding.ActivityChromeBinding

interface FullScreenContainerListener {
    fun onEnterFullScreen(view: View)
    fun onExitFullScreen()
}

class FullScreenContainerModel {
    var fullScreenContainerListener: FullScreenContainerListener? = null
    fun enterFullScreen(view: View) {
        fullScreenContainerListener?.onEnterFullScreen(view)
    }

    fun exitFullScreen() {
        fullScreenContainerListener?.onExitFullScreen()
    }
}

fun initFullScreenContainer(
    binding: ActivityChromeBinding,
    videoToolBarModel: VideoToolBarModel,
    fullScreenContainerModel: FullScreenContainerModel,
) {

    fullScreenContainerModel.fullScreenContainerListener = object : FullScreenContainerListener {
        override fun onEnterFullScreen(view: View) {
            if (binding.fullscreenViewBox.childCount > 0) {
                binding.fullscreenViewBox.removeAllViews()
            }

            binding.fullscreenViewBox.addView(view)
            videoToolBarModel.reset()
            binding.fullscreenBox.visibility = View.VISIBLE
        }

        override fun onExitFullScreen() {
            binding.fullscreenViewBox.removeAllViews()
            binding.fullscreenBox.visibility = View.GONE
        }

    }

}