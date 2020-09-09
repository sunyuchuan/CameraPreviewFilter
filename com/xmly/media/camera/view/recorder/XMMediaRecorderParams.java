package com.xmly.media.camera.view.recorder;

/**
 * Created by sunyc on 19-4-3.
 */

public class XMMediaRecorderParams {
    public static final int FALSE = 0;
    public static final int TRUE = 1;
    public int width;
    public int height;
    public int bitrate = 700000;
    public int fps = 25;
    public int CFR = FALSE; //constant frame rate
    public float gop_size = 0.5f;// In seconds
    public int crf = 23;
    public int multiple = 1000; //time_base of stream is multiple * fps
    public int max_b_frames = 0;
    public String output_path;
    public String preset = "veryfast";
    public String tune = "zerolatency";

    public XMMediaRecorderParams() {
    }

    public XMMediaRecorderParams setSize(int w, int h) {
        width = w;
        height = h;
        return this;
    }

    public XMMediaRecorderParams setBitrate(int bitrate) {
        this.bitrate = bitrate;
        return this;
    }

    public XMMediaRecorderParams setFps(int fps) {
        this.fps = fps;
        return this;
    }

    public XMMediaRecorderParams setCFR(int CFR) {
        this.CFR = CFR;
        return this;
    }

    public XMMediaRecorderParams setGopsize(float time) {
        this.gop_size = time;
        return this;
    }

    public XMMediaRecorderParams setCrf(int crf) {
        this.crf = crf;
        return this;
    }

    public XMMediaRecorderParams setMultiple(int multiple) {
        this.multiple = multiple;
        return this;
    }

    public XMMediaRecorderParams setMaxBFrames(int max_b_frames) {
        this.max_b_frames = max_b_frames;
        return this;
    }

    public XMMediaRecorderParams setOutputPath(String output_path) {
        this.output_path = output_path;
        return this;
    }

    public XMMediaRecorderParams setPreset(String preset) {
        this.preset = preset;
        return this;
    }

    public XMMediaRecorderParams setTune(String tune) {
        this.tune = tune;
        return this;
    }
}