package com.baidu.paddle.lite.demo.segmentation.util;

import android.content.Context;
import android.graphics.Bitmap;

import com.baidu.paddle.lite.demo.segmentation.HumanSegPredictor;
import com.baidu.paddle.lite.demo.segmentation.config.HumanSegConfig;
import com.baidu.paddle.lite.demo.segmentation.preprocess.HumanSegPreprocess;

public class SegmentationUtil {

    public static final SegmentationUtil instance = new SegmentationUtil();
    private Context context;
    private HumanSegConfig humanSegConfig;
    private HumanSegPredictor humanSegPredictor = new HumanSegPredictor();
    private HumanSegPreprocess humanSegPreprocess = new HumanSegPreprocess();

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
        this.humanSegConfig = HumanSegConfig.defaultConfig(this.context);
        humanSegPredictor.init(this.context, humanSegConfig);
        humanSegPredictor.setInputImage(ImageUtil.getBitmapByPath(context, humanSegConfig.imagePath));
        humanSegPreprocess.init(humanSegConfig);
        humanSegPreprocess.to_array(humanSegPredictor.scaledImage);
        humanSegPreprocess.normalize(humanSegPreprocess.inputData);
    }

    public boolean loadModel() {
        return humanSegPredictor.init(context, humanSegConfig);
    }

    public boolean runModel(Visualize visualize) {
        return humanSegPredictor.isLoaded() && humanSegPredictor.runModel(humanSegPreprocess, visualize);
    }

    public Bitmap getSegmentationBitmap() {
        return humanSegPredictor.outputImage();
    }

    public Bitmap getScaledImage() {
        return humanSegPredictor.scaledImage;
    }

    public Bitmap getBackgroundImage() {
        return ImageUtil.getBitmapByPath(context, humanSegConfig.backgroundPath);
    }

    public void refreshInputBitmap(Bitmap image) {
        humanSegPredictor.init(this.context, humanSegConfig);
        humanSegPredictor.setInputImage(image);
        humanSegPreprocess.init(humanSegConfig);
        humanSegPreprocess.to_array(humanSegPredictor.scaledImage);
        humanSegPreprocess.normalize(humanSegPreprocess.inputData);
    }
}
