package com.baidu.paddle.lite.demo.segmentation.util

import android.content.Context
import android.widget.Toast

class ToastUtil {

    companion object {
        private lateinit var context: Context
        fun init(context: Context) {
            this.context = context
        }

        fun showToast(str : String) {
            Toast.makeText(context, str, Toast.LENGTH_SHORT).show()
        }
    }
}