# CameraPreviewFilter
通过gles给相机和预览视频加特效并保存

android相机预览与录制

1、CameraPreview.java基于SurfaceView

相机预览 加特效并编码保存

2、com.xmly.media.video.view基于GLSurfaceView

XMCameraView相机预览 加特效并编码保存

XMDecoderView预览本地视频，加特效并编码保存

XMImageView图片预览，加转场幻灯片特效并编码成视频，转场特效如 百叶窗 淡入淡出等。

XMPIPView实现画中画功能，两路本地视频解码，一路画在另一路上形成画中画，并编码保存成一个视频文件

XMPlayerRenderer把相机输出画在视频上，形成画中画，并输出成一个视频

3、gles支持pbo模式，从gpu读取yuyv数据，比rgba数据减少一半数据量
