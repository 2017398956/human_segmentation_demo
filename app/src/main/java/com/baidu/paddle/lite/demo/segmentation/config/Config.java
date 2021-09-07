package com.baidu.paddle.lite.demo.segmentation.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import com.baidu.paddle.lite.demo.segmentation.R;
import com.baidu.paddle.lite.demo.segmentation.Utils;

public class Config {

    public String modelPath = "";
    public String labelPath = "";
    public String imagePath = "";
    public String backgroundPath = "";
    public int cpuThreadNum = 1;
    public String cpuPowerMode = "";
    public String inputColorFormat = "";
    public long[] inputShape = new long[]{};


    public void init(String modelPath, String labelPath, String imagePath, String backgroundPath, int cpuThreadNum,
                     String cpuPowerMode, String inputColorFormat, long[] inputShape) {

        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.imagePath = imagePath;
        this.backgroundPath = backgroundPath;
        this.cpuThreadNum = cpuThreadNum;
        this.cpuPowerMode = cpuPowerMode;
        this.inputColorFormat = inputColorFormat;
        this.inputShape = inputShape;
    }

    public void setInputShape(Bitmap inputImage) {
        this.inputShape[0] = 1;
        this.inputShape[1] = 3;
        this.inputShape[2] = inputImage.getHeight();
        this.inputShape[3] = inputImage.getWidth();

    }

    public static Config defaultConfig(Context context) {
        Config config = new Config();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String model_path = sharedPreferences.getString(context.getString(R.string.MODEL_PATH_KEY),
                context.getString(R.string.MODEL_PATH_DEFAULT));
        String label_path = sharedPreferences.getString(context.getString(R.string.LABEL_PATH_KEY),
                context.getString(R.string.LABEL_PATH_DEFAULT));
        String image_path = sharedPreferences.getString(context.getString(R.string.IMAGE_PATH_KEY),
                context.getString(R.string.IMAGE_PATH_DEFAULT));
        String background_path = sharedPreferences.getString(context.getString(R.string.BACKGROUND_PATH_KEY), context.getString(R.string.BACKGROUND_PATH_DEFAULT));
        int cpu_thread_num = Integer.parseInt(sharedPreferences.getString(context.getString(R.string.CPU_THREAD_NUM_KEY),
                context.getString(R.string.CPU_THREAD_NUM_DEFAULT)));
        String cpu_power_mode =
                sharedPreferences.getString(context.getString(R.string.CPU_POWER_MODE_KEY),
                        context.getString(R.string.CPU_POWER_MODE_DEFAULT));
        cpu_power_mode =
                sharedPreferences.getString(context.getString(R.string.CPU_POWER_MODE_KEY),
                        "LITE_POWER_FULL");
        String input_color_format =
                sharedPreferences.getString(context.getString(R.string.INPUT_COLOR_FORMAT_KEY),
                        context.getString(R.string.INPUT_COLOR_FORMAT_DEFAULT));
        long[] input_shape =
                Utils.parseLongsFromString(sharedPreferences.getString(context.getString(R.string.INPUT_SHAPE_KEY),
                        context.getString(R.string.INPUT_SHAPE_DEFAULT)), ",");
        config.init(model_path, label_path, image_path, background_path, cpu_thread_num, cpu_power_mode,
                input_color_format, input_shape);
        return config;
    }

}
