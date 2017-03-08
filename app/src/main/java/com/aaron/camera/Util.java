package com.aaron.camera;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Build;
import android.view.Surface;

import java.util.Comparator;
import java.util.List;

/**
 * Created by 创宇 on 2017/2/28.
 */

public class Util {

    public static Camera.Size getLargePictureSize(Camera camera){
        if(camera != null){
            List<Camera.Size> sizes = camera.getParameters().getSupportedPictureSizes();
            Camera.Size temp = sizes.get(0);
            for(int i = 1;i < sizes.size();i ++){
                float scale = (float)(sizes.get(i).height) / sizes.get(i).width;
                if(temp.width < sizes.get(i).width && scale < 0.6f && scale > 0.5f)
                    temp = sizes.get(i);
            }
            return temp;
        }
        return null;
    }

    public static int determineDisplayOrientation(Activity activity, int defaultCameraId) {
        int displayOrientation = 0;
        if(Build.VERSION.SDK_INT >  Build.VERSION_CODES.FROYO)
        {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(defaultCameraId, cameraInfo);

            int degrees  = getRotationAngle(activity);


            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                displayOrientation = (cameraInfo.orientation + degrees) % 360;
                displayOrientation = (360 - displayOrientation) % 360;
            } else {
                displayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
            }
        }
        return displayOrientation;
    }

    public static int getRotationAngle(Activity activity)
    {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees  = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;

            case Surface.ROTATION_90:
                degrees = 90;
                break;

            case Surface.ROTATION_180:
                degrees = 180;
                break;

            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                break;
        }
        return degrees;
    }

    public static class ResolutionComparator implements Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size size1, Camera.Size size2) {
            if(size1.height != size2.height)
                return size1.height -size2.height;
            else
                return size1.width - size2.width;
        }
    }
}
