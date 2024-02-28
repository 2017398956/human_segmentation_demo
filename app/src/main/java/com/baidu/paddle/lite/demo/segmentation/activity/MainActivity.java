package com.baidu.paddle.lite.demo.segmentation.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.baidu.paddle.lite.demo.segmentation.HumanSegPredictor;
import com.baidu.paddle.lite.demo.segmentation.R;
import com.baidu.paddle.lite.demo.segmentation.Utils;
import com.baidu.paddle.lite.demo.segmentation.config.HumanSegConfig;
import com.baidu.paddle.lite.demo.segmentation.preprocess.HumanSegPreprocess;
import com.baidu.paddle.lite.demo.segmentation.util.ImageUtil;
import com.baidu.paddle.lite.demo.segmentation.visual.ReplaceBackgroundVisualize;

import java.io.IOException;

/**
 * 百度原 Activity
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int OPEN_GALLERY_REQUEST_CODE = 0;
    public static final int TAKE_PHOTO_REQUEST_CODE = 1;

    public static final int REQUEST_LOAD_MODEL = 0;
    public static final int REQUEST_RUN_MODEL = 1;
    public static final int RESPONSE_LOAD_MODEL_SUCCESSED = 0;
    public static final int RESPONSE_LOAD_MODEL_FAILED = 1;
    public static final int RESPONSE_RUN_MODEL_SUCCESSED = 2;
    public static final int RESPONSE_RUN_MODEL_FAILED = 3;

    protected ProgressDialog pbLoadModel = null;
    protected ProgressDialog pbRunModel = null;

    protected Handler receiver = null; // receive messages from worker thread
    protected Handler sender = null; // send command to worker thread
    protected HandlerThread worker = null; // worker thread to load&run model


    protected TextView tvInputSetting;
    protected ImageView ivInputImage;
    protected TextView tvOutputResult;
    protected TextView tvInferenceTime;

    // model config
    HumanSegConfig humanSegConfig = new HumanSegConfig();
    protected HumanSegPredictor humanSegPredictor = new HumanSegPredictor();
    HumanSegPreprocess humanSegPreprocess = new HumanSegPreprocess();

    ReplaceBackgroundVisualize replaceBackgroundVisualize = new ReplaceBackgroundVisualize();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        receiver = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RESPONSE_LOAD_MODEL_SUCCESSED:
                        pbLoadModel.dismiss();
                        onLoadModelSucceed();
                        break;
                    case RESPONSE_LOAD_MODEL_FAILED:
                        pbLoadModel.dismiss();
                        Toast.makeText(MainActivity.this, "Load model failed!", Toast.LENGTH_SHORT).show();
                        onLoadModelFailed();
                        break;
                    case RESPONSE_RUN_MODEL_SUCCESSED:
                        pbRunModel.dismiss();
                        onRunModelSucceed();
                        break;
                    case RESPONSE_RUN_MODEL_FAILED:
                        pbRunModel.dismiss();
                        Toast.makeText(MainActivity.this, "Run model failed!", Toast.LENGTH_SHORT).show();
                        onRunModelFailed();
                        break;
                    default:
                        break;
                }
            }
        };
        worker = new HandlerThread("Predictor Worker");
        worker.start();
        sender = new Handler(worker.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REQUEST_LOAD_MODEL:
                        // load model and reload test image
                        boolean loadResult = humanSegPredictor.init(getApplicationContext(), humanSegConfig);
                        receiver.sendEmptyMessage(loadResult? RESPONSE_LOAD_MODEL_SUCCESSED : RESPONSE_LOAD_MODEL_FAILED);
                        break;
                    case REQUEST_RUN_MODEL:
                        // run model if model is loaded
                        if (onRunModel()) {
                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_SUCCESSED);
                        } else {
                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_FAILED);
                        }
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void initView(){
        tvInputSetting = findViewById(R.id.tv_input_setting);
        ivInputImage = findViewById(R.id.iv_input_image);
        tvInferenceTime = findViewById(R.id.tv_inference_time);
        tvOutputResult = findViewById(R.id.tv_output_result);
        tvInputSetting.setMovementMethod(ScrollingMovementMethod.getInstance());
        tvOutputResult.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    private boolean onRunModel() {
        replaceBackgroundVisualize.setBackgroundImage(ImageUtil.getBitmapByPath(this, humanSegConfig.backgroundPath));
        replaceBackgroundVisualize.setScaledImage(humanSegPredictor.scaledImage);
        return humanSegPredictor.isLoaded() && humanSegPredictor.runModel(humanSegPreprocess, replaceBackgroundVisualize);
    }

    public void onLoadModelFailed() {

    }

    public void onRunModelFailed() {
    }

    private void loadModel() {
        pbLoadModel = ProgressDialog.show(this, "", "Loading model...", false, false);
        sender.sendEmptyMessage(REQUEST_LOAD_MODEL);
    }

    public void runModel() {
        pbRunModel = ProgressDialog.show(this, "", "Running model...", false, false);
        sender.sendEmptyMessage(REQUEST_RUN_MODEL);
    }

    public void onLoadModelSucceed() {
        // load test image from file_paths and run model
        if (humanSegConfig.imagePath.isEmpty()) {
            return;
        }

        Bitmap image = ImageUtil.getBitmapByPath(this, humanSegConfig.imagePath);
        if (image != null && humanSegPredictor.isLoaded()) {
            humanSegPredictor.setInputImage(image);
            humanSegPreprocess.init(humanSegConfig);
            // to_array 后会把数据放入到 humanSegPreprocess.inputData 中，这里为了效率要使用 scaledImage
            humanSegPreprocess.to_array(humanSegPredictor.scaledImage) ;
            humanSegPreprocess.normalize(humanSegPreprocess.inputData);
            runModel();
        }
    }

    private void onRunModelSucceed() {
        // obtain results and update UI
        tvInferenceTime.setText("Inference time: " + humanSegPredictor.inferenceTime() + " ms");
        Bitmap outputImage = humanSegPredictor.outputImage();
        if (outputImage != null) {
            ivInputImage.setImageBitmap(outputImage);
        }
        tvOutputResult.setText(humanSegPredictor.outputResult());
        tvOutputResult.scrollTo(0, 0);
    }

    public void onImageChanged(Bitmap image) {
        // rerun model if users pick test image from gallery or camera
        if (image != null && humanSegPredictor.isLoaded()) {
            humanSegPredictor.setInputImage(image);
            runModel();
        }
    }

    public void onImageChanged(String path) {
        Bitmap image = BitmapFactory.decodeFile(path);
        humanSegPredictor.setInputImage(image);
        runModel();
    }

    public void onSettingsClicked() {
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_action_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.open_gallery:
                if (requestAllPermissions()) {
                    openGallery();
                }
                break;
            case R.id.take_photo:
                if (requestAllPermissions()) {
                    takePhoto();
                }
                break;
            case R.id.settings:
                if (requestAllPermissions()) {
                    // make sure we have SDCard r&w permissions to load model from SDCard
                    onSettingsClicked();
                }
                break;
            case R.id.human_seg:
                if (requestAllPermissions()){
                    startActivity(new Intent(this , HumanSegActivity.class));
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            switch (requestCode) {
                case OPEN_GALLERY_REQUEST_CODE:
                    try {
                        ContentResolver resolver = getContentResolver();
                        Uri uri = data.getData();
                        Bitmap image = MediaStore.Images.Media.getBitmap(resolver, uri);
                        String[] proj = {MediaStore.Images.Media.DATA};
                        Cursor cursor = managedQuery(uri, proj, null, null, null);
                        cursor.moveToFirst();
                        onImageChanged(image);
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }
                    break;

                case TAKE_PHOTO_REQUEST_CODE:
                    Bitmap image = (Bitmap) data.getParcelableExtra("data");
                    onImageChanged(image);

                    break;
                default:
                    break;
            }
        }
    }

    private boolean requestAllPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.CAMERA},
                    0);
            return false;
        }
        return true;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, OPEN_GALLERY_REQUEST_CODE);
    }

    private void takePhoto() {
        Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePhotoIntent, TAKE_PHOTO_REQUEST_CODE);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isLoaded = humanSegPredictor.isLoaded();
        menu.findItem(R.id.open_gallery).setEnabled(isLoaded);
        menu.findItem(R.id.take_photo).setEnabled(isLoaded);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean settingsChanged = false;
        String model_path = sharedPreferences.getString(getString(R.string.MODEL_PATH_KEY),
                getString(R.string.MODEL_PATH_DEFAULT));
        String label_path = sharedPreferences.getString(getString(R.string.LABEL_PATH_KEY),
                getString(R.string.LABEL_PATH_DEFAULT));
        String image_path = sharedPreferences.getString(getString(R.string.IMAGE_PATH_KEY),
                getString(R.string.IMAGE_PATH_DEFAULT));
        String background_path = sharedPreferences.getString(getString(R.string.BACKGROUND_PATH_KEY), getString(R.string.BACKGROUND_PATH_DEFAULT));
        settingsChanged |= !model_path.equalsIgnoreCase(humanSegConfig.modelPath);
        settingsChanged |= !label_path.equalsIgnoreCase(humanSegConfig.labelPath);
        settingsChanged |= !image_path.equalsIgnoreCase(humanSegConfig.imagePath);
        settingsChanged |= !background_path.equalsIgnoreCase(humanSegConfig.backgroundPath);
        int cpu_thread_num = Integer.parseInt(sharedPreferences.getString(getString(R.string.CPU_THREAD_NUM_KEY),
                getString(R.string.CPU_THREAD_NUM_DEFAULT)));
        settingsChanged |= cpu_thread_num != humanSegConfig.cpuThreadNum;
        String cpu_power_mode =
                sharedPreferences.getString(getString(R.string.CPU_POWER_MODE_KEY),
                        getString(R.string.CPU_POWER_MODE_DEFAULT));
        settingsChanged |= !cpu_power_mode.equalsIgnoreCase(humanSegConfig.cpuPowerMode);
        String input_color_format =
                sharedPreferences.getString(getString(R.string.INPUT_COLOR_FORMAT_KEY),
                        getString(R.string.INPUT_COLOR_FORMAT_DEFAULT));
        settingsChanged |= !input_color_format.equalsIgnoreCase(humanSegConfig.inputColorFormat);
        long[] input_shape =
                Utils.parseLongsFromString(sharedPreferences.getString(getString(R.string.INPUT_SHAPE_KEY),
                        getString(R.string.INPUT_SHAPE_DEFAULT)), ",");

        settingsChanged |= input_shape.length != humanSegConfig.inputShape.length;

        if (!settingsChanged) {
            for (int i = 0; i < input_shape.length; i++) {
                settingsChanged |= input_shape[i] != humanSegConfig.inputShape[i];
            }
        }

        if (settingsChanged) {
            humanSegConfig.init(model_path, label_path, image_path, background_path, cpu_thread_num, cpu_power_mode,
                    input_color_format, input_shape);
            humanSegPreprocess.init(humanSegConfig);
            // update UI
            tvInputSetting.setText("Model: " + humanSegConfig.modelPath.substring(humanSegConfig.modelPath.lastIndexOf("/") + 1) + "\n" + "CPU" +
                    " Thread Num: " + Integer.toString(humanSegConfig.cpuThreadNum) + "\n" + "CPU Power Mode: " + humanSegConfig.cpuPowerMode);
            tvInputSetting.scrollTo(0, 0);
            // reload model if configure has been changed
            loadModel();
        }
    }

    @Override
    protected void onDestroy() {
        if (humanSegPredictor != null) {
            humanSegPredictor.releaseModel();
        }
        worker.quit();
        super.onDestroy();
    }
}
