package com.mobile.myapplication.bases

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.databinding.ViewDataBinding
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory


internal const val DISPLAY = 1080

fun View.goneView() {
    visibility = View.GONE
}

fun View.visibleView() {
    visibility = View.VISIBLE
}

fun View.invisibleView() {
    visibility = View.INVISIBLE
}

fun View.isVisible() = visibility == View.VISIBLE

fun View.isInvisible() = visibility == View.INVISIBLE

fun View.isGone() = visibility == View.GONE

fun ViewDataBinding.goneView() {
    this.root.goneView()
}

fun ViewDataBinding.visibleView() {
    this.root.visibleView()
}

fun ViewDataBinding.invisibleView() {
    this.root.invisibleView()
}

fun ViewDataBinding.isVisible() = this.root.visibility == View.VISIBLE

fun ViewDataBinding.isInvisible() = this.root.visibility == View.INVISIBLE

fun ViewDataBinding.isGone() = this.root.visibility == View.GONE

private var lastClickTime: Long = 0

fun View.click(action: (view: View?) -> Unit) {
    this.setOnClickListener(object : View.OnClickListener {
        override fun onClick(v: View) {
            if (SystemClock.elapsedRealtime() - lastClickTime < 300L) return
            else action(v)
            lastClickTime = SystemClock.elapsedRealtime()
        }
    })
}









