package com.example.docnumreader;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.util.List;

public class FirebaseTextRecognitionAnalyzer implements ImageAnalysis.Analyzer {

    private static final String TAG = "MY-FIRE";

    public interface ScannerAnalyzerListener {
        void call(String s);
    }

    private ScannerAnalyzerListener listener;
    private ImageView cropRectImageView;

    public FirebaseTextRecognitionAnalyzer(ScannerAnalyzerListener listener, ImageView cropRectImageView) {
        this.listener = listener;
        this.cropRectImageView = cropRectImageView;
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

            Rect rect = new Rect();
            cropRectImageView.getGlobalVisibleRect(rect);
//
//            int[] location = new int[2];
//            cropRectImageView.getLocationOnScreen(location);
//
//            int[] wind = new int[2];
//            cropRectImageView.getLocationInWindow(wind);

            Bitmap croped = Bitmap.createBitmap(
                    bmp,
                    rect.left, //x
                    rect.top,//y
                    rect.width(), //width
                    rect.height()  //height
            );

            firebaseVisionImage = FirebaseVisionImage.fromBitmap(croped);
            detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        } catch (Exception ex) {
            Log.e(TAG, "Ошибка распознование текста. Ошибка инициализации детектора", ex);
            return;
        }

        Task<FirebaseVisionText> result =
                detector.processImage(firebaseVisionImage)
                        .addOnSuccessListener(firebaseVisionText -> {
                            String text = firebaseVisionText.getText();
                            List<FirebaseVisionText.TextBlock> block = firebaseVisionText.getTextBlocks();

                            listener.call(firebaseVisionText.getText());

                        })
                        .addOnFailureListener(
                                ex -> {
                                    Log.e(TAG, "Ошибка распознование текста. Ошибка обработки изображения", ex);
                                });

        imageProxy.close();
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

  /*  private Bitmap getBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }*/
}