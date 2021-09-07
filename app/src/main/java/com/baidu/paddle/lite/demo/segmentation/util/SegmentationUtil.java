package com.baidu.paddle.lite.demo.segmentation.util;

import android.content.Context;
import android.graphics.Bitmap;

import com.baidu.paddle.lite.demo.segmentation.Predictor;
import com.baidu.paddle.lite.demo.segmentation.config.Config;
import com.baidu.paddle.lite.demo.segmentation.preprocess.Preprocess;

import org.jetbrains.annotations.Nullable;

public class SegmentationUtil {

    public static final SegmentationUtil instance = new SegmentationUtil();
    private Context context;
    private Config config;
    private Predictor predictor = new Predictor();
    private Preprocess preprocess = new Preprocess();

    private SegmentationUtil() {
    }

    public static SegmentationUtil getInstance() {
        return instance;
    }

    public void init(Context context) {
        if (null != context.getApplicationContext()) {
            this.context = context.getApplicationContext();
        } else {
            this.context = context;
        }
        this.config = Config.defaultConfig(this.context);
        predictor.init(this.context, config);
        predictor.setInputImage(ImageUtil.getBitmapByPath(context, config.imagePath));
        preprocess.init(config);
        preprocess.to_array(predictor.scaledImage);
        preprocess.normalize(preprocess.inputData);
    }

    public boolean loadModel() {
        return predictor.init(context, config);
    }

    public boolean runModel(Visualize visualize) {
        return predictor.isLoaded() && predictor.runModel(preprocess, visualize);
    }

    public Bitmap getSegmentationBitmap() {
        return predictor.outputImage();
    }

    public Bitmap getScaledImage() {
        return predictor.scaledImage;
    }

    public Bitmap getBackgroundImage() {
        return ImageUtil.getBitmapByPath(context, config.backgroundPath);
    }

    public void refreshInputBitmap(Bitmap image) {
        predictor.init(this.context, config);
        predictor.setInputImage(image);
        preprocess.init(config);
        preprocess.to_array(predictor.scaledImage);
        preprocess.normalize(preprocess.inputData);
    }
}
