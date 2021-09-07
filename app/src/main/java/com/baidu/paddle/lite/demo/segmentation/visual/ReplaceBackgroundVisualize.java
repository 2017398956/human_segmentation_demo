package com.baidu.paddle.lite.demo.segmentation.visual;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.baidu.paddle.lite.Tensor;
import com.baidu.paddle.lite.demo.segmentation.util.Visualize;

public class ReplaceBackgroundVisualize implements Visualize {
    private static final String TAG = ReplaceBackgroundVisualize.class.getSimpleName();
    private Bitmap backgroundImage;
    private Bitmap scaledImage;

    private Bitmap draw(Bitmap backgroundImage, Bitmap scaledImage, Tensor outputTensor) {

        final int[] colors_map = {0x00000000, 0xFFFFFF00};
        long[] output = outputTensor.getLongData();
        // {1 , 192 , 192}
        long outputShape[] = outputTensor.shape();
        long outputSize = 1;

        for (long s : outputShape) {
            outputSize *= s;
        }

        int[] objectColor = new int[(int) outputSize];

        for (int i = 0; i < output.length; i++) {
            if ((int) output[i] == 0) {
                // 人物之外的像素点
                objectColor[i] = colors_map[(int) output[i]];
            } else {
                // 人物内的像素点
                objectColor[i] = scaledImage.getPixel(i % scaledImage.getWidth(), i / scaledImage.getWidth());
            }
        }

        Bitmap.Config config = backgroundImage.getConfig();
        Bitmap outputImage = null;
        if (outputShape.length == 3) {
            outputImage = Bitmap.createBitmap(objectColor, (int) outputShape[2], (int) outputShape[1], config);
            outputImage = Bitmap.createScaledBitmap(outputImage, backgroundImage.getWidth(), backgroundImage.getHeight(), true);
        } else if (outputShape.length == 4) {
            outputImage = Bitmap.createBitmap(objectColor, (int) outputShape[3], (int) outputShape[2], config);
        }
        Bitmap bmOverlay = Bitmap.createBitmap(backgroundImage.getWidth(), backgroundImage.getHeight(), backgroundImage.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(backgroundImage, new Matrix(), null);
        Paint paint = new Paint();
        // paint.setAlpha(0x80);
        canvas.drawBitmap(outputImage, 0, 0, paint);
        return bmOverlay;
    }

    public void setBackgroundImage(Bitmap backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public void setScaledImage(Bitmap scaledImage) {
        this.scaledImage = scaledImage;
    }

    @Override
    public Bitmap getVisualize(Tensor outputTensor) {
        return draw(backgroundImage, scaledImage, outputTensor);
    }
}
