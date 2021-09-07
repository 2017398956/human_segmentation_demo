package com.baidu.paddle.lite.demo.segmentation;

import android.content.Context;
import android.content.SyncContext;
import android.graphics.Bitmap;
import android.util.Log;

import com.baidu.paddle.lite.MobileConfig;
import com.baidu.paddle.lite.PaddlePredictor;
import com.baidu.paddle.lite.PowerMode;
import com.baidu.paddle.lite.Tensor;
import com.baidu.paddle.lite.demo.segmentation.config.Config;
import com.baidu.paddle.lite.demo.segmentation.preprocess.Preprocess;
import com.baidu.paddle.lite.demo.segmentation.util.Visualize;

import java.io.File;
import java.io.InputStream;
import java.util.Vector;

public class Predictor {
    private static final String TAG = Predictor.class.getSimpleName();
    protected Vector<String> wordLabels = new Vector<String>();
    private Config config = null;
    protected Bitmap inputImage = null;
    public Bitmap scaledImage = null;
    protected Bitmap outputImage = null;
    protected String outputResult = "";
    protected float preprocessTime = 0;
    protected float postprocessTime = 0;

    public boolean isLoaded = false;
    public int warmupIterNum = 0;
    public int inferIterNum = 1;
    protected Context appCtx = null;
    public int cpuThreadNum = 1;
    public String cpuPowerMode = "LITE_POWER_HIGH";
    public String modelPath = "";
    public String modelName = "";
    protected PaddlePredictor paddlePredictor = null;
    protected float inferenceTime = 0;

    public Predictor() {
        super();
    }

    public boolean init(Context appCtx, Config config) {
        this.config = config;
        this.appCtx = appCtx;
        if (config.inputShape.length != 4) {
            Log.i(TAG, "size of input shape should be: 4");
            return false;
        }
        if (config.inputShape[0] != 1) {
            Log.i(TAG, "only one batch is supported in the image classification demo, you can use any batch size in " +
                    "your Apps!");
            return false;
        }
        if (config.inputShape[1] != 1 && config.inputShape[1] != 3) {
            Log.i(TAG, "only one/three channels are supported in the image classification demo, you can use any " +
                    "channel size in your Apps!");
            return false;
        }
        if (!config.inputColorFormat.equalsIgnoreCase("RGB") && !config.inputColorFormat.equalsIgnoreCase("BGR")) {
            Log.i(TAG, "only RGB and BGR color format is supported.");
            return false;
        }
        isLoaded = loadModel(config.modelPath, config.cpuThreadNum, config.cpuPowerMode);
        return isLoaded;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    protected boolean loadLabel(String labelPath) {
        wordLabels.clear();
        // load word labels from file
        try {
            InputStream assetsInputStream = appCtx.getAssets().open(labelPath);
            int available = assetsInputStream.available();
            byte[] lines = new byte[available];
            assetsInputStream.read(lines);
            assetsInputStream.close();
            String words = new String(lines);
            String[] contents = words.split("\n");
            for (String content : contents) {
                wordLabels.add(content);
            }
            Log.i(TAG, "word label size: " + wordLabels.size());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    public Tensor getInput(int idx) {
        if (!isLoaded()) {
            return null;
        }
        return paddlePredictor.getInput(idx);
    }

    public Tensor getOutput(int idx) {
        if (!isLoaded()) {
            return null;
        }
        return paddlePredictor.getOutput(idx);
    }

    protected boolean loadModel(String modelPath, int cpuThreadNum, String cpuPowerMode) {
        // release model if exists
        releaseModel();

        // load model
        if (modelPath.isEmpty()) {
            return false;
        }
        String realPath = modelPath;
        if (!modelPath.substring(0, 1).equals("/")) {
            // read model files from custom file_paths if the first character of mode file_paths is '/'
            // otherwise copy model to cache from assets
            realPath = appCtx.getCacheDir() + "/" + modelPath;
            Utils.copyDirectoryFromAssets(appCtx, modelPath, realPath);
        }
        if (realPath.isEmpty()) {
            return false;
        }
        MobileConfig config = new MobileConfig();
        config.setModelFromFile(realPath + File.separator + "hrnet_w18_small.nb");
        config.setThreads(cpuThreadNum);
        if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_HIGH")) {
            config.setPowerMode(PowerMode.LITE_POWER_HIGH);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_LOW")) {
            config.setPowerMode(PowerMode.LITE_POWER_LOW);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_FULL")) {
            config.setPowerMode(PowerMode.LITE_POWER_FULL);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_NO_BIND")) {
            config.setPowerMode(PowerMode.LITE_POWER_NO_BIND);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_RAND_HIGH")) {
            config.setPowerMode(PowerMode.LITE_POWER_RAND_HIGH);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_RAND_LOW")) {
            config.setPowerMode(PowerMode.LITE_POWER_RAND_LOW);
        } else {
            Log.e(TAG, "unknown cpu power mode!");
            return false;
        }
        paddlePredictor = PaddlePredictor.createPaddlePredictor(config);
        this.cpuThreadNum = cpuThreadNum;
        this.cpuPowerMode = cpuPowerMode;
        this.modelPath = realPath;
        this.modelName = realPath.substring(realPath.lastIndexOf("/") + 1);
        return true;
    }

    public boolean runModel() {
        if (!isLoaded()) {
            return false;
        }
        long startTime = System.currentTimeMillis() ;
        // warm up (warmupIterNum = 0)
        for (int i = 0; i < warmupIterNum; i++) {
            paddlePredictor.run();
        }
        // inference (inferIterNum = 1)
        for (int i = 0; i < inferIterNum; i++) {
            paddlePredictor.run();
        }
        Log.d("NFL" , "转换消耗的时间：" + (System.currentTimeMillis() - startTime)) ;
        return true;
    }

    public boolean runModel(Bitmap image) {
        setInputImage(image);
        return runModel();
    }

    public boolean runModel(Preprocess preprocess, Visualize visualize) {
        if (inputImage == null) {
            return false;
        }

        // set input shape
        Tensor inputTensor = getInput(0);
        inputTensor.resize(config.inputShape);

        inputTensor.setData(preprocess.inputData);
        // inference
        runModel();
        Tensor outputTensor = getOutput(0);
        this.outputImage = visualize.getVisualize(outputTensor);
        return true;
    }

    public void releaseModel() {
        paddlePredictor = null;
        isLoaded = false;
        cpuThreadNum = 1;
        cpuPowerMode = "LITE_POWER_HIGH";
        modelPath = "";
        modelName = "";
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Bitmap inputImage() {
        return inputImage;
    }

    public Bitmap outputImage() {
        return outputImage;
    }

    public String outputResult() {
        return outputResult;
    }

    public float preprocessTime() {
        return preprocessTime;
    }

    public float postprocessTime() {
        return postprocessTime;
    }

    public String modelPath() {
        return modelPath;
    }

    public String modelName() {
        return modelName;
    }

    public int cpuThreadNum() {
        return cpuThreadNum;
    }

    public String cpuPowerMode() {
        return cpuPowerMode;
    }

    public float inferenceTime() {
        return inferenceTime;
    }

    public void setInputImage(Bitmap image) {
        if (image == null) {
            return;
        }
        // scale image to the size of input tensor
        Bitmap rgbaImage = image.copy(Bitmap.Config.ARGB_8888, true);
        // 1,3,192,192
        Bitmap scaleImage = Bitmap.createScaledBitmap(rgbaImage, (int) this.config.inputShape[3], (int) this.config.inputShape[2], true);
        this.inputImage = rgbaImage;
        this.scaledImage = scaleImage;
    }

}
