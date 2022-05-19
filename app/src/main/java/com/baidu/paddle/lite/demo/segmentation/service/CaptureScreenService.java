package com.baidu.paddle.lite.demo.segmentation.service;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import com.baidu.paddle.lite.demo.segmentation.activity.GetScreenActivity;
import com.baidu.paddle.lite.demo.segmentation.util.ScreenCapture;
import com.baidu.paddle.lite.demo.segmentation.util.ScreenCaptureHelper;

public class CaptureScreenService extends Service {

    public final static String CAPTURE_SCREEN_DATA = "captureScreenData";

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Intent captureData = intent.getParcelableExtra(CAPTURE_SCREEN_DATA);
        if (captureData != null) {
            ScreenCaptureHelper.getInstance().startCapture(Activity.RESULT_OK , captureData);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        Notification.Builder builder = new Notification.Builder(this); //获取一个Notification构造器
        Intent nfIntent = new Intent(this, GetScreenActivity.class); //点击后跳转的界面，可以设置跳转数据
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0)) // 设置PendingIntent
                //.setLargeIcon(BitmapFactory.decodeResource(getResources(), 0)) // 设置下拉列表中的图标(大图标)
                //.setContentTitle("SMI InstantView") // 设置下拉列表里的标题
                //.setSmallIcon(0) // 设置状态栏内的小图标
                .setContentText("录屏中......") // 设置上下文内容
                .setWhen(System.currentTimeMillis());// 设置该通知发生的时间

        /*以下是对Android 8.0的适配*/
        //普通notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(this.getClass().getSimpleName());
        }
        //前台服务notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    this.getClass().getSimpleName(),
                    this.getClass().getSimpleName(),
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build();// 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND;//设置为默认的声音
        startForeground(110, notification);
    }
}