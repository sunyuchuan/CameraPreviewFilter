package com.xmly.media.gles.filter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;

import com.xmly.media.gles.utils.XMFilterType;

/**
 * Created by sunyc on 18-9-29.
 */

public class GPUImageFilterFactory {

    public static Bitmap decodeBitmap(String path) {
        if(path == null)
            return null;

        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inJustDecodeBounds = true;
        Bitmap bmp = BitmapFactory.decodeFile(path, op);

        int scale = 1;
        int w = op.outWidth;
        int h = op.outHeight;
        while (w*h > 1280*720) {
            scale ++;
            w = w/scale;
            h = h/scale;
        }
        op.inSampleSize = scale;
        op.inJustDecodeBounds = false;
        bmp = BitmapFactory.decodeFile(path, op);
        return bmp;
    }

    public static GPUImageFilter CreateFilter(XMFilterType filterType) {
        GPUImageFilter filter = null;
        switch(filterType) {
            case FILTER_BEAUTY:
                filter = new GPUImageFilterGroup();
                ((GPUImageFilterGroup) filter).addFilter(new GPUImageBeautyFilter(5));
                ((GPUImageFilterGroup) filter).addFilter(new GPUImageBrightnessFilter(0.05f));
                break;
            case FILTER_BEAUTY_OPTIMIZATION:
                filter = new GPUImageFilterGroup();
                ((GPUImageFilterGroup) filter).addFilter(new GPUImageBeautyFilterOptimization(0.7f));
                ((GPUImageFilterGroup) filter).addFilter(new GPUImageBrightnessFilter(0.05f));
                break;
            case FILTER_SKETCH:
                filter = new GPUImageSketchFilter();
                break;
            case FILTER_SEPIA:
                filter = new GPUImageSepiaFilter();
                break;
            case FILTER_INVERT:
                filter = new GPUImageColorInvertFilter();
                break;
            case FILTER_VIGNETTE:
                PointF centerPoint = new PointF();
                centerPoint.x = 0.5f;
                centerPoint.y = 0.5f;
                filter =  new GPUImageVignetteFilter(centerPoint, new float[] {0.0f, 0.0f, 0.0f}, 0.3f, 0.75f);
                break;
            case FILTER_LAPLACIAN:
                filter = new GPUImageLaplacianFilter();
                break;
            case FILTER_GLASS_SPHERE:
                filter = new GPUImageGlassSphereFilter();
                break;
            case FILTER_CRAYON:
                filter = new GPUImageCrayonFilter();
                break;
            case FILTER_MIRROR:
                filter = new GPUImageMirrorFilter();
                break;
            case FILTER_CROSSHATCH:
                filter = new GPUImageCrosshatchFilter();
                break;
            case FILTER_FISSION:
                filter = new GPUImageFissionFilter();
                break;
            case FILTER_BLINDS:
                filter = new GPUImageBlindsFilter();
                break;
            case FILTER_FADE_IN_OUT:
                filter = new GPUImageFadeInOutFilter();
                break;
            default:
                filter = new GPUImageFilter();
                break;
        }
        return filter;
    }
}
