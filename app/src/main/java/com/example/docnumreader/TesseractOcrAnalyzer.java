package com.example.docnumreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class TesseractOcrAnalyzer implements ImageAnalysis.Analyzer {

    private static final String TAG = "MY-TESS";

    private ScannerAnalyzerListener listener;
    private ImageView cropRectImageView;
    private Context context;
    private TessBaseAPI tessBaseAPI;

    public interface ScannerAnalyzerListener {
        void call(String s);
    }

    public TesseractOcrAnalyzer(ScannerAnalyzerListener listener, ImageView cropRectImageView, Context context) {
        this.listener = listener;
        this.cropRectImageView = cropRectImageView;
        this.context = context;

        this.tessBaseAPI = createAndInitTessBaseAPI();
    }

    private TessBaseAPI createAndInitTessBaseAPI() {
        TessBaseAPI tessBaseAPI = new TessBaseAPI();
        copyTessData();

        String dataPath = context.getExternalFilesDir("/").getPath() + "/";

        tessBaseAPI.init(dataPath, "eng");//"rus");
        return tessBaseAPI;
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        int rotation = degreesToFirebaseRotation(imageProxy.getImageInfo().getRotationDegrees());

        Image image = imageProxy.getImage();

        FirebaseVisionImage firebaseVisionImage;
        FirebaseVisionTextRecognizer detector;

        try {
            firebaseVisionImage = FirebaseVisionImage.fromMediaImage(image, rotation);
            Bitmap bmp = firebaseVisionImage.getBitmap();

            Rect cropRect = getCropRectFromImageView(cropRectImageView);
            Bitmap cropped = cropBitmap(bmp, cropRect);

            String result = getText(cropped);

            listener.call(result);
        } catch (Exception ex) {
            Log.e(TAG, "Ошибка распознование текста. Ошибка инициализации детектора", ex);
            return;
        }

        imageProxy.close();
    }

    private Rect getCropRectFromImageView(ImageView cropRectImageView) {
        Rect rect = new Rect();
        cropRectImageView.getGlobalVisibleRect(rect);
        return rect;
    }

    private int degreesToFirebaseRotation(int degrees) {
        switch (degrees) {
            case 0:
                return FirebaseVisionImageMetadata.ROTATION_0;
            case 90:
                return FirebaseVisionImageMetadata.ROTATION_90;
            case 180:
                return FirebaseVisionImageMetadata.ROTATION_180;
            case 270:
                return FirebaseVisionImageMetadata.ROTATION_270;
            default:
                throw new IllegalArgumentException(
                        "Rotation must be 0, 90, 180, or 270.");
        }
    }

    private Bitmap cropBitmap(Bitmap src, Rect cropRect) {
        return Bitmap.createBitmap(
                src,
                cropRect.left, //x
                cropRect.top,//y
                cropRect.width(), //width
                cropRect.height()  //height
        );
    }

    private void copyTessData() {
        try {
            File dir = context.getExternalFilesDir("/tessdata");
            if (!dir.exists()) {
                if (!dir.mkdir()) {
                    throw new RuntimeException("The folder " + dir.getPath() + "was not created");
                }
            }
            String[] fileList = context.getAssets().list("tesseract");
            for (String fileName : fileList) {
                String pathToDataFile = dir + "/" + fileName;
                if (!(new File(pathToDataFile)).exists()) {
                    InputStream in = context.getAssets().open("tesseract/" + fileName);
                    OutputStream out = new FileOutputStream(pathToDataFile);
                    byte[] buff = new byte[1024];
                    int len;
                    while ((len = in.read(buff)) > 0) {
                        out.write(buff, 0, len);
                    }
                    in.close();
                    out.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка копирования tessdata", e);
        }
    }

    private String getText(Bitmap bitmap) {
        tessBaseAPI.setImage(bitmap);
        String retStr = "No result";
        try {
            retStr = tessBaseAPI.getUTF8Text();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка TessBaseApi", e);
        }
        return retStr;
    }

    public void onDestroy() {
        if (tessBaseAPI != null)
            tessBaseAPI.end();
        ;
    }
}
