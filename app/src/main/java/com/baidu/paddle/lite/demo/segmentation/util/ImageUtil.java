package com.baidu.paddle.lite.demo.segmentation.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtil {
    public static Bitmap getBitmapByPath(Context context , String path) {
        if (!path.substring(0, 1).equals("/")) {
            InputStream imageStream = null;
            try {
                imageStream = context.getAssets().open(path);
            } catch (IOException e) {
                Toast.makeText(context, "Load image failed!", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            Bitmap temp = BitmapFactory.decodeStream(imageStream);
            try {
                imageStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return temp ;
        } else {
            if (!new File(path).exists()) {
                return null;
            }
            return BitmapFactory.decodeFile(path);
        }
    }
}
