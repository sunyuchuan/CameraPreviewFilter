package com.xmly.media.video.view;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;

/**
 * Created by sunyc on 19-4-17.
 */

public class SubtitleParser {
    private static final String TAG = "SubtitleParser";
    private Subtitle.TextCanvasParam mParam = null;
    Bitmap mTextBitmap = null;
    Canvas mLocalCanvas = null;
    Paint mLocalPaint = null;
    Paint mStrokePaint = null;

    public SubtitleParser() {
    }

    public void initSubtitleParser(Subtitle.TextCanvasParam param) {
        release();
        mParam = param;
        if(param == null)
            return;

        mParam.textLabelBottom *= param.height;
        mParam.textSize *= param.height;
        mParam.textStrokeWidth *= mParam.textSize;
        mTextBitmap = Bitmap.createBitmap(param.width, param.height, Bitmap.Config.ARGB_8888);
        mLocalCanvas = new Canvas(mTextBitmap);

        mStrokePaint = new Paint();
        mStrokePaint.setColor(Color.argb(255, Color.red(param.textStrokeColor),
                Color.green(param.textStrokeColor), Color.blue(param.textStrokeColor)));
        mStrokePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mStrokePaint.setStrokeWidth(param.textStrokeWidth);
        mStrokePaint.setTextSize(param.textSize);
        mStrokePaint.setAntiAlias(true);
        mStrokePaint.setTextAlign(Paint.Align.CENTER);

        mLocalPaint = new Paint();
        mLocalPaint.setColor(Color.argb(255, Color.red(param.textColor),
                Color.green(param.textColor), Color.blue(param.textColor)));
        mLocalPaint.setStyle(Paint.Style.FILL);
        mLocalPaint.setTextSize(param.textSize);
        mLocalPaint.setAntiAlias(true);
        mLocalPaint.setTextAlign(Paint.Align.CENTER);
    }

    private Bitmap decodeBitmap(String path) {
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

    public Bitmap drawTextToBitmap(String text, String headImagePath) {
        if (text == null || mParam == null || mLocalCanvas == null || mLocalPaint == null)
            return null;

        mLocalCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        Rect textRect = new Rect();
        mLocalPaint.getTextBounds(text, 0, text.length(), textRect);
        Bitmap bmp = decodeBitmap(headImagePath);
        if (bmp != null) {
            int headHeight = (int) (mParam.mScaleRatio * textRect.height());
            int headWidth = (headHeight * bmp.getWidth()) / bmp.getHeight();
            int headLeft = (int) ((mParam.width - (headWidth + textRect.height() * mParam.mInterval + textRect.width())) / 2);
            int headTop = (int) (mParam.height - (mParam.textLabelBottom + headHeight - (headHeight - textRect.height()) / 2));
            Rect headRect = new Rect(headLeft, headTop, headLeft + headWidth, headTop + headHeight);
            mLocalCanvas.drawBitmap(bmp, null, headRect, null);

            int textLeft = (int) (headLeft + headWidth + (textRect.height() * mParam.mInterval));
            int textTop = headTop + (headHeight - textRect.height()) / 2;
            textRect = new Rect(textLeft, textTop, textLeft + textRect.width(), textTop + textRect.height());
            Paint.FontMetricsInt fontMetrics = mLocalPaint.getFontMetricsInt();
            int baseline = (textRect.bottom + textRect.top - fontMetrics.bottom - fontMetrics.top) / 2;
            mLocalCanvas.drawText(text, textRect.centerX(), baseline, mStrokePaint);
            mLocalCanvas.drawText(text, textRect.centerX(), baseline, mLocalPaint);
            bmp.recycle();
        } else {
            int textLeft = (mParam.width - textRect.width()) / 2;
            int textTop = (int) (mParam.height - mParam.textLabelBottom - textRect.height());
            textRect = new Rect(textLeft, textTop, textLeft + textRect.width(), textTop + textRect.height());
            Paint.FontMetricsInt fontMetrics = mLocalPaint.getFontMetricsInt();
            int baseline = (textRect.bottom + textRect.top - fontMetrics.bottom - fontMetrics.top) / 2;
            mLocalCanvas.drawText(text, textRect.centerX(), baseline, mStrokePaint);
            mLocalCanvas.drawText(text, textRect.centerX(), baseline, mLocalPaint);
        }

        return mTextBitmap;
    }

    public void release() {
        if (mTextBitmap != null && !mTextBitmap.isRecycled()) {
            mLocalPaint = null;
            mStrokePaint = null;
            mLocalCanvas = null;
            mTextBitmap.recycle();
            mTextBitmap = null;
        }
    }
}
