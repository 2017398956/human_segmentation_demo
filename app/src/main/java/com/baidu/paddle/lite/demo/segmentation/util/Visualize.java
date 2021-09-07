package com.baidu.paddle.lite.demo.segmentation.util;

import android.graphics.Bitmap;

import com.baidu.paddle.lite.Tensor;

public interface Visualize {

    Bitmap getVisualize(Tensor outputTensor);
}
