package com.baidu.paddle.lite.demo.segmentation.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtil {
    public static Bitmap getBitmapByPath(Context context , String path) {
        if (!path.substring(0, 1).equals("/")) {
            InputStream imageStream = null;
            try {
                imageStream = context.getAssets().open(path);
            } catch (IOException e) {
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

    /**
     * 保存图片到 Pictures 目录下，不刷新图库
     *
     * @param bmp           要变成图片的 bitmap
     * @param imageFileName 保存到本地的图片文件名，如：时间戳.jpg
     * @return 文件的真实地址
     */
    public static String onlySaveBitmap(Context context, Bitmap bmp, String imageFileName) {
        Context contextImpl = context.getApplicationContext() == null ? context : context.getApplicationContext();
        // 首先保存图片
        File appDir = contextImpl.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        // /storage/emulated/0/Android/data/com.longjiang.xinjianggong.enterprise/files/Pictures
        Log.i("NFL","要保存的路径：" + appDir.getAbsolutePath());
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        String fileName = imageFileName + ".jpg";
        File file = new File(appDir, fileName);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file.getAbsolutePath();
    }
}
