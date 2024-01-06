package com.baidu.paddle.lite.demo.segmentation

import android.app.Application
import android.content.Context
import com.baidu.paddle.lite.demo.segmentation.util.ToastUtil

class MyApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        ToastUtil.init(base)
    }
}