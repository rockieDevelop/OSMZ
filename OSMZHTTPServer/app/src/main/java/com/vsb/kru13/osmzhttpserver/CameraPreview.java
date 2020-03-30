package com.vsb.kru13.osmzhttpserver;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;

    private static ArrayList<byte[]> picturesBytes = new ArrayList<>();

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    //region Preview Callback
    private Camera.PreviewCallback mPicturePrev = new Camera.PreviewCallback(){

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Bitmap bm = null;
            // Converting ByteArray to Bitmap - >Rotate and Convert back to Data
            if (data != null) {
                Log.d("preview_frame","new preview frame");
                Camera.Parameters parameters = camera.getParameters();
                int width = parameters.getPreviewSize().width;
                int height = parameters.getPreviewSize().height;

                YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), width, height, null);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);

                byte[] bytes = out.toByteArray();
                bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;

                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // Notice that width and height are reversed
                    Bitmap scaled = Bitmap.createScaledBitmap(bm, screenHeight, screenWidth, true);
                    int w = scaled.getWidth();
                    int h = scaled.getHeight();
                    // Setting post rotate to 90
                    Matrix mtx = new Matrix();

                    android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
                    int CameraEyeValue;
                    // do something for phones running an SDK before lollipop
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        CameraEyeValue = (info.orientation + 270) % 360;
                        CameraEyeValue = (360 - CameraEyeValue) % 360; // compensate the mirror
                    } else { // back-facing
                        CameraEyeValue = (info.orientation - 270 + 360) % 360;
                    }

                    mtx.postRotate(CameraEyeValue); // CameraEyeValue is default to Display Rotation

                    bm = Bitmap.createBitmap(scaled, 0, 0, w, h, mtx, true);
                }else{// LANDSCAPE MODE
                    //No need to reverse width and height
                    Bitmap scaled = Bitmap.createScaledBitmap(bm, screenWidth, screenHeight, true);
                    bm=scaled;
                }
            }
            // Converting the Die photo to Bitmap

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            picturesBytes.add(byteArray);
        }
    };
    //endregion

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.setPreviewCallback(mPicturePrev);
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d("cameraLog", "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(mPicturePrev);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d("cameraLog", "Error starting camera preview: " + e.getMessage());
        }
    }

    public void restartPreview(){
        mCamera.startPreview();
    }

    public static ArrayList<byte[]> getPicturesBytes() { return picturesBytes; }
    public static void clearPicturesBytes() { picturesBytes.clear(); }
}
