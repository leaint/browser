package com.example.clock.ui.model

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.View
import com.example.clock.R
import com.example.clock.databinding.ActivityChromeBinding

interface VideoToolBarListener {

    fun onShowToolBar()
    fun onHideToolBar()
    fun onLockScreenOrientation(orientation: Int)
    fun onLockScreen(isLocked: Boolean)
}


class VideoToolBarModel {

    private var isShown = false

    var isLocked = false
        get() = field

    var isLockOrientation = false
        get() = field

    var videoToolBarListener: VideoToolBarListener? = null
    fun showToolBar() {
        if (!isShown) {
            isShown = true
            videoToolBarListener?.onShowToolBar()
        }
    }

    fun hideToolBar() {
        if (isShown) {
            isShown = false
            videoToolBarListener?.onHideToolBar()
        }
    }

    fun lockScreenOrientation() {

        isLockOrientation = !isLockOrientation
        videoToolBarListener?.onLockScreenOrientation(
            if (isLockOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
        )
    }

    fun lockScreen() {
        isLocked = !isLocked
        videoToolBarListener?.onLockScreen(isLocked)
    }

    fun reset() {
        if (isLocked) {
            lockScreen()
        }
        isLockOrientation = false
    }
}

@SuppressLint("ClickableViewAccessibility")
fun initVideoToolBar(
    binding: ActivityChromeBinding,
    videoToolBarModel: VideoToolBarModel,
    setRequestedOrientation: (Int) -> Unit,
) {

    val onInterceptTouchEventListener = { _: MotionEvent? -> videoToolBarModel.isLocked }
    videoToolBarModel.videoToolBarListener = object : VideoToolBarListener {
        override fun onShowToolBar() {
            with(binding.videoToolbarBox) {

                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f)
                postDelayed(
                    {
                        videoToolBarModel.hideToolBar()
                    }, 3000
                )
            }
        }

        override fun onHideToolBar() {

            binding.videoToolbarBox.animate().alpha(0f).withEndAction {
                binding.videoToolbarBox.visibility = View.GONE

            }
        }

        override fun onLockScreenOrientation(orientation: Int) {
            setRequestedOrientation(orientation)
            binding.rotateScreenBtn.setImageResource(
                if (videoToolBarModel.isLockOrientation) {
                    R.drawable.outline_screen_lock_rotation_24
                } else {
                    R.drawable.outline_screen_rotation_24
                }
            )
        }


        @SuppressLint("ClickableViewAccessibility")
        override fun onLockScreen(isLocked: Boolean) {
            binding.lockScreenBtn.setImageResource(
                if (isLocked) {
                    R.drawable.outline_lock_24
                } else {
                    R.drawable.outline_lock_open_24
                }
            )
        }

    }

    val fullScreenView = binding.fullscreenViewBox
    fullScreenView.onInterceptTouchEventListener = onInterceptTouchEventListener

    binding.fullscreenBox.onInterceptTouchEventListener = { event ->
        run {

            if (event?.actionMasked == MotionEvent.ACTION_DOWN) {
                videoToolBarModel.showToolBar()
            }
            false
        }
    }

    binding.rotateScreenBtn.setOnClickListener {
        videoToolBarModel.lockScreenOrientation()
    }

    binding.lockScreenBtn.setOnClickListener {
        videoToolBarModel.lockScreen()
    }

}