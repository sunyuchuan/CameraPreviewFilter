package com.xmly.media.video.view;

/**
 * Created by sunyc on 19-4-19.
 */

public class Subtitle {
    private static final String TAG = "Subtitle";
    public int start_time = 0; //ms
    public int end_time = 0; //ms
    public String str = null; //text
    public String headImagePath = null; //head portrait

    public Subtitle() {}

    public Subtitle(int start_time, int end_time, String str, String imagePath) {
        this.start_time = start_time;
        this.end_time = end_time;
        this.str = str;
        this.headImagePath = imagePath;
    }

    public static class TextCanvasParam {
        /** 画布宽度 默认1280 **/
        public int width = 1280;
        /** 画布高度 默认720 **/
        public int height = 720;
        /** 文本绘制距离画布下边的距离,以画布高度为基准**/
        public float textLabelBottom = 0.07f;
        /** 头像与字幕的水平距离，以字幕高度为基准**/
        public float mInterval = 0.3f;
        /** 头像高度与字幕高度的比值 **/
        public float mScaleRatio = 1.0f;
        /** 文本大小, 以画布高度为基准**/
        public float textSize = 0.08f;
        /** 文字的颜色 int rgb = (r << 16) | (g << 8) | b **/
        public int textColor = 0xFFFFFFFF;
        /** 文字的轮廓宽度, 以文本大小基准**/
        public float textStrokeWidth = 0.1f;
        /** 文字的轮廓颜色 **/
        public int textStrokeColor = 0xFF000000;
        /** 文本对齐方式 -1左对齐 0居中对齐 1右对齐 **/
        public int textAlignment = 0;

        public TextCanvasParam() {}

        public TextCanvasParam(float textLabelBottom, float interval, float scaleRatio,
                               float textSize, int textColor, float textStrokeWidth,
                               int textStrokeColor, int textAlignment) {
            this.textLabelBottom = textLabelBottom;
            this.mInterval = interval;
            this.mScaleRatio = scaleRatio;
            this.textSize = textSize;
            this.textColor = textColor;
            this.textStrokeWidth = textStrokeWidth;
            this.textStrokeColor = textStrokeColor;
            this.textAlignment = textAlignment;
        }

        public void setCanvasSize(int w, int h) {
            width = w;
            height = h;
        }
    }
}
