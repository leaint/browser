package com.example.clock.utils

import android.animation.Animator
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.TextView
import com.example.clock.R

class MyToast(val box: View, val msg: String, val length: Int) {

    private var actionName: String? = null
    private var action: (() -> Unit)? = null
    private var endAction: (() -> Unit)? = null

    private var valid = true
    fun show() {
        if (!valid) return
        val m = handler.obtainMessage(SEND_MESSAGE, this)
        m.sendToTarget()
        valid = false
    }

    private fun visible() {
        box.alpha = 1f

        box.findViewById<TextView>(R.id.toast_title)?.text = msg
        box.findViewById<TextView>(R.id.toast_action)?.let {
            it.text = actionName
            it.setOnClickListener {
                hide()
                action?.let { it1 -> it1() }
            }
        }
        box.visibility = View.VISIBLE
    }

    private fun showAnim(now: Boolean = false) {

        if (now) {
            visible()
            return
        }

        box.animate().alpha(1f).setListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                visible()
                box.alpha = 0f
            }

            override fun onAnimationEnd(animation: Animator) {

            }

            override fun onAnimationCancel(animation: Animator) {
                onAnimationEnd(animation)
            }

            override fun onAnimationRepeat(animation: Animator) {
            }

        }

        )

    }

    private fun gone() {
        box.visibility = View.GONE
        box.findViewById<TextView>(R.id.toast_title)?.text = ""
        box.findViewById<TextView>(R.id.toast_action)?.let {
            it.text = ""
            it.setOnClickListener(null)
        }
        box.alpha = 0f
        endAction?.let { it() }
    }

    private fun hide(now: Boolean = false) {
        if (now) {
            gone()
            handler.sendEmptyMessage(END_MEESSAGE)
            return
        }
        box.animate().alpha(0f).setListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                box.alpha = 1f
                box.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animator) {
                gone()
                handler.sendEmptyMessage(END_MEESSAGE)
            }

            override fun onAnimationCancel(animation: Animator) {
                onAnimationEnd(animation)
            }

            override fun onAnimationRepeat(animation: Animator) {
            }

        }

        )

    }

    fun setAction(name: String, action: (() -> Unit)?): MyToast {
        actionName = name
        this.action = action
        return this
    }

    fun setEndAction(action: (() -> Unit)?): MyToast {
        this.endAction = action
        return this
    }

    companion object {
        const val SEND_MESSAGE = 1
        const val CLOSE_MESSAGE = 2
        const val END_MEESSAGE = 3
        const val LENGTH_SHORT = 1
        const val LENGTH_LONG = 2
        private var lastMsg: Message? = null
        private val handler = object : Handler(Looper.myLooper()!!) {
            override fun handleMessage(msg: Message) {

                when (msg.what) {
                    SEND_MESSAGE -> {
                        val l = lastMsg
                        if (l != null) {
                            val t = l.obj as? MyToast
                            t?.gone()
                            lastMsg = null
                        }
                        val t = msg.obj as? MyToast ?: return
                        lastMsg = msg
                        t.showAnim()
                        val d = when (t.length) {
                            2 -> 5000L
                            else -> 3000L
                        }
                        removeMessages(CLOSE_MESSAGE)
                        sendMessageDelayed(this.obtainMessage(CLOSE_MESSAGE, t), d)

                    }

                    CLOSE_MESSAGE -> {
                        val t = msg.obj as MyToast
                        t.hide()
                    }

                    END_MEESSAGE -> lastMsg = null
                }


            }
        }

        fun make(view: View, msg: String, length: Int): MyToast {
            return MyToast(view, msg, length)
        }
    }


}