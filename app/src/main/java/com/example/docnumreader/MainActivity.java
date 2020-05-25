package com.example.docnumreader;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MY-FIRE";

    private TextView textView;
    private ImageView cropRectImageView;
    private PreviewView previewView;

    private ImageAnalysis imageAnalysis;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    /**
     * Blocking camera operations are performed using this executor
     */
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    private void init() {
        textView = findViewById(R.id.text_view);
        cropRectImageView = findViewById(R.id.image_view_crop_rect);
        previewView = findViewById(R.id.preview_view);

        // Initialize background executor for ImageAnalysis
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (isCameraPermissionGranted()) {
            bindCameraAfterViewFinderInflated();
        } else {
            requestCameraPermission();
        }
    }

    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private boolean isCameraPermissionGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        final String[] cameraPermission = new String[]{Manifest.permission.CAMERA};
        ActivityCompat.requestPermissions(this, cameraPermission, CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != CAMERA_PERMISSION_REQUEST) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { //if (isCameraPermissionGranted()) {
            bindCameraAfterViewFinderInflated();
        } else {
            String msg = "Ошибка распознование текста. Отказано в доступе к камере";

        }
    }

    private void bindCameraAfterViewFinderInflated() {
        previewView.post(this::bindCameraUseCases);
    }


    private void bindCameraUseCases() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(this::cameraProviderFutureListener, ContextCompat.getMainExecutor(this));
    }

    private void cameraProviderFutureListener() {
        ProcessCameraProvider cameraProvider;
        try {
            cameraProvider = cameraProviderFuture.get();
        } catch (Exception ex) {
            showErrorMessage("Ошибка распознование текста. Ошибка инициализации камеры (Camera provider)", ex);
            return;
        }


        DisplayMetrics metrics = new DisplayMetrics();
        previewView.getDisplay().getRealMetrics(metrics);
        int rotation = previewView.getDisplay().getRotation();


        Preview preview = new Preview.Builder()
                .setTargetRotation(rotation)
                .setTargetResolution(new Size(metrics.widthPixels, metrics.heightPixels))
                .build();


        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetRotation(rotation)
                .setTargetResolution(new Size(metrics.widthPixels, metrics.heightPixels))
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, new FirebaseTextRecognitionAnalyzer(this::onSuccess, cropRectImageView));

        cameraProvider.unbindAll();
        try {
            Camera camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            preview.setSurfaceProvider(previewView.createSurfaceProvider(camera.getCameraInfo()));
        } catch (Exception ex) {
            showErrorMessage("Ошибка распознование текста. Ошибка инициализации камеры (Use cases)", ex);
        }
    }

    private void onSuccess(String content) {
        try {
            runOnUiThread(() -> {
                Log.d(TAG, content);
                textView.setText(content);
            });
        } catch (Exception ex) {
            showErrorMessage("Ошибка распознование текста. Не удалось установить распознанное значение", ex);
        }
    }

    private void showErrorMessage(String message, Throwable ex) {
        Log.e(TAG, message, ex);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
