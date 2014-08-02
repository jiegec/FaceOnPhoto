package com.github.jiegec.faceonphoto;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import uk.co.senab.photoview.PhotoView;


public class MainActivity extends Activity {
    private CascadeClassifier mClassifier;
    private static final String sFileName = "lbpcascade_frontalface.xml";
    // haarcascade_frontalface_alt.xml

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            File f = new File(getCacheDir()+"/" + sFileName);
            if (!f.exists()) {
                InputStream is = getAssets().open(sFileName);
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();


                FileOutputStream fos = new FileOutputStream(f);
                fos.write(buffer);
                fos.close();
            }

            mClassifier = new CascadeClassifier();
            mClassifier.load(f.getAbsolutePath());

            if (mClassifier.isNull()) {
                System.err.println("Error loading classifier file \"" + f.getAbsolutePath() + "\".");
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        findViewById(R.id.imageButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                // Ensure that there's a camera activity to handle the intent
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    // Create the File where the photo should go
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                                Uri.fromFile(photoFile));
                        startActivityForResult(takePictureIntent, 1);
                    }
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            new AsyncTask<Void, Integer, Bitmap>() {
                private ProgressDialog mDialog;
                private int mFaces;
                @Override
                protected Bitmap doInBackground(Void... params) {
                    Bitmap bitmap = null;
                    try {
                        while(!new File(mCurrentPhotoPath).exists());

                        publishProgress(0);
                        Bitmap result = scaleImage(mCurrentPhotoPath, 480);

                        publishProgress(1);
                        FileOutputStream os = new FileOutputStream(mCurrentPhotoPath);

                        publishProgress(2);
                        result.compress(Bitmap.CompressFormat.JPEG, 100, os);

                        publishProgress(3);
                        os.close();

                        publishProgress(4);
                        Mat image = imread(mCurrentPhotoPath);

                        publishProgress(5);
                        Mat gray = imread(mCurrentPhotoPath, CV_LOAD_IMAGE_GRAYSCALE);

                        publishProgress(6);
                        Rect faces = new Rect();

                        publishProgress(7);
                        mClassifier.detectMultiScale(gray, faces);

                        publishProgress(8);
                        for (int i = 0;i < faces.capacity();i++) {
                            Rect rect = faces.position(i);
                            rectangle(image, rect, new Scalar());
                        }

                        publishProgress(9);
                        imwrite(getCacheDir() + "/output.jpg", image);

                        publishProgress(10);
                        bitmap = BitmapFactory.decodeFile(getCacheDir() + "/output.jpg");

                        mFaces = faces.capacity();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return bitmap;
                }

                @Override
                protected void onPreExecute() {
                    mDialog = new ProgressDialog(MainActivity.this);
                    mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mDialog.setMax(10);
                    mDialog.show();
                }

                @Override
                protected void onProgressUpdate(Integer... progress) {
                    mDialog.setProgress(progress[0]);
                }

                @Override
                protected void onPostExecute(Bitmap result) {
                    mDialog.dismiss();
                    if (result != null) {
                        PhotoView photoView = (PhotoView) findViewById(R.id.photo);
                        photoView.setImageBitmap(result);
                    }
                    Toast.makeText(MainActivity.this,
                            "检测到" + String.valueOf(mFaces) + "个人脸",
                            Toast.LENGTH_LONG).show();
                }
            }.execute();

        }
    }

    @SuppressWarnings("deprecation")
    public static Bitmap scaleImage(String filePath, int desiredWidth) {
        // Get the source image's dimensions
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        if ((options.outWidth <= 0) || (options.outHeight <= 0)) {
            return null;
        }

        int srcWidth = options.outWidth;
        int srcHeight = options.outHeight;

        // Only scale if the source is big enough. This code is just trying to fit a image into a certain width.
        if (desiredWidth > srcWidth) {
            desiredWidth = srcWidth;
        }

        // Calculate the correct inSampleSize/scale value. This helps reduce memory use. It should be a power of 2
        // from: http://stackoverflow.com/questions/477572/android-strange-out-of-memory-issue/823966#823966
        int inSampleSize = 1;
        /*while (srcWidth / 2 > desiredWidth) {
            srcWidth /= 2;
            srcHeight /= 2;
            inSampleSize *= 2;
        }*/
        int widthScale = Math.round((float) srcWidth / (float) desiredWidth);
        inSampleSize = widthScale;

        float desiredScale = (float) desiredWidth / (srcWidth / widthScale);

        // Decode with inSampleSize
        options.inJustDecodeBounds = false;
        options.inDither = false;
        options.inSampleSize = inSampleSize;
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap sampledSrcBitmap = BitmapFactory.decodeFile(filePath, options);

        // Resize
        Matrix matrix = new Matrix();
        matrix.postScale(desiredScale, desiredScale);
        return Bitmap.createBitmap(sampledSrcBitmap, 0, 0, sampledSrcBitmap.getWidth(),
                sampledSrcBitmap.getHeight(), matrix, true);
    }

    private String mCurrentPhotoPath;

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }
}
