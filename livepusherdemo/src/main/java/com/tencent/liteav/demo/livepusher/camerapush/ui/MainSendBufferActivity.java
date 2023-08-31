package com.tencent.liteav.demo.livepusher.camerapush.ui;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.faceunity.core.entity.FUCameraConfig;
import com.faceunity.core.entity.FURenderFrameData;
import com.faceunity.core.entity.FURenderInputData;
import com.faceunity.core.entity.FURenderOutputData;
import com.faceunity.core.enumeration.CameraFacingEnum;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.core.enumeration.FUInputTextureEnum;
import com.faceunity.core.enumeration.FUTransformMatrixEnum;
import com.faceunity.core.faceunity.FUAIKit;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.listener.OnGlRendererListener;
import com.faceunity.core.model.facebeauty.FaceBeautyBlurTypeEnum;
import com.faceunity.core.renderer.CameraRenderer;
import com.faceunity.core.utils.CameraUtils;
import com.faceunity.nama.FUConfig;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.data.FaceUnityDataFactory;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.ui.FaceUnityView;
import com.faceunity.nama.utils.FuDeviceUtils;
import com.tencent.liteav.demo.livepusher.R;
import com.tencent.liteav.demo.livepusher.camerapush.PreferenceUtil;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles.core.GlUtil;
import com.tencent.liteav.demo.livepusher.camerapush.model.Constants;
import com.tencent.liteav.demo.livepusher.camerapush.profile.CSVUtils;
import com.tencent.liteav.demo.livepusher.camerapush.profile.Constant;
import com.tencent.liteav.demo.livepusher.camerapush.ui.view.PusherPlayQRCodeFragment;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import static com.tencent.rtmp.TXLiveConstants.CUSTOM_MODE_VIDEO_CAPTURE;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MainSendBufferActivity extends AppCompatActivity implements OnGlRendererListener, LifeCycleSensorManager.OnAccelerometerChangedListener{
    private static final String TAG = "MainActivity";
    private TextView mTvFps;
    private TextView mTvTrackStatus;

    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher mLivePusher;

    private PusherPlayQRCodeFragment   mPusherPlayQRCodeFragment;   // 拉流地址面板


    private String mPusherURL       = "";   // 推流地址
    private String mRTMPPlayURL     = "";   // RTMP 拉流地址
    private String mFlvPlayURL      = "";   // flv 拉流地址
    private String mHlsPlayURL      = "";   // hls 拉流地址
    private String mRealtimePlayURL = "";   // 低延时拉流地址
    private CSVUtils mCSVUtils;

    private CameraRenderer mCameraRenderer;
    private FURenderer mFuRenderer;
    private FaceUnityDataFactory mFaceUnityDataFactory;
    private byte[] mReadBack, mYUV420P;
    private HandlerThread mPushThread;
    private Handler mPushHandler;
    private ReentrantReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private FUAIKit mFUAIKit = FUAIKit.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(null);
        setContentView(R.layout.activity_main_send_buffer);
        mTvTrackStatus = findViewById(R.id.tv_track_text);
        initData();
        mLivePusher = new TXLivePusher(this);
        mLivePushConfig = new TXLivePushConfig();
        mLivePushConfig.setCustomModeType(CUSTOM_MODE_VIDEO_CAPTURE);
        mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_720_1280);
        mLivePushConfig.setHardwareAcceleration(TXLiveConstants.ENCODE_VIDEO_HARDWARE);
        mLivePusher.setConfig(mLivePushConfig);

        GLSurfaceView glSurfaceView = findViewById(R.id.video_view);
        glSurfaceView.setEGLContextClientVersion(GlUtil.getSupportGLVersion(this));
        mCameraRenderer = new CameraRenderer(glSurfaceView, new FUCameraConfig(), this);
        glSurfaceView.setKeepScreenOn(true);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mTvFps = findViewById(R.id.tv_fps);
        ImageButton btnBack = findViewById(R.id.livepusher_ibtn_back);
        btnBack.setOnClickListener(v -> {
            finish();
        });

        FaceUnityView faceUnityView = findViewById(R.id.faceunity_control);
        String isOpen = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON);
        if (TextUtils.isEmpty(isOpen) || isOpen.equals("false")) {
            faceUnityView.setVisibility(View.GONE);
        } else {
            mFuRenderer = FURenderer.getInstance();
            mFuRenderer.setInputTextureType(FUInputTextureEnum.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE);
            mFuRenderer.setCameraFacing(CameraFacingEnum.CAMERA_FRONT);
            mFuRenderer.setMarkFPSEnable(true);
            mFuRenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0_FLIPHORIZONTAL);
            mFuRenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0_FLIPHORIZONTAL);
            mFuRenderer.setInputOrientation(CameraUtils.INSTANCE.getCameraOrientation(Camera.CameraInfo.CAMERA_FACING_FRONT));
            mFuRenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT180);

            mFaceUnityDataFactory = new FaceUnityDataFactory(-1);
            faceUnityView.bindDataFactory(mFaceUnityDataFactory);
            LifeCycleSensorManager lifeCycleSensorManager = new LifeCycleSensorManager(this, getLifecycle());
            lifeCycleSensorManager.setOnAccelerometerChangedListener(this);
        }

        mLivePusher.startPusher(mPusherURL);
        initFragment();

        mPushThread = new HandlerThread("TXLivePushe");
        mPushThread.start();
        mPushHandler = new Handler(mPushThread.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraRenderer.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraRenderer.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLivePusher.stopPusher();
        mPushHandler.removeMessages(0);
        mPushThread.quitSafely();
    }

    private void pushData(int cameraWidth, int cameraHeight) {
        if (null != mReadBack) {
            if (mYUV420P == null) {
                mYUV420P = new byte[mReadBack.length];
            }
            mReadWriteLock.readLock().lock();
            try {
                NV21toI420(mReadBack, mYUV420P, cameraWidth, cameraHeight);
            } finally {
                mReadWriteLock.readLock().unlock();
            }
            mLivePusher.sendCustomVideoData(mYUV420P, TXLivePusher.YUV_420P, cameraWidth, cameraHeight);
        }
    }

    private final FURendererListener mFURendererListener = new FURendererListener() {
        @Override
        public void onPrepare() {
            mFaceUnityDataFactory.bindCurrentRenderer();
        }

        @Override
        public void onTrackStatusChanged(FUAIProcessorEnum type, int status) {
            Log.i(TAG, "onTrackStatusChanged() called with: type = [" + type + "], status = [" + status + "]");
            if (mTvTrackStatus == null) {
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvTrackStatus.setText(type == FUAIProcessorEnum.FACE_PROCESSOR ? R.string.toast_not_detect_face : R.string.toast_not_detect_face_or_body);
                    mTvTrackStatus.setVisibility(status > 0 ? View.INVISIBLE : View.VISIBLE);
                }
            });
        }

        @Override
        public void onFpsChanged(double fps, double callTime) {
            final String FPS = String.format(Locale.getDefault(), "%.2f", fps);
            Log.e(TAG, "onFpsChanged: FPS " + FPS + " callTime " + String.format(Locale.getDefault(), "%.2f", callTime));
            if (mTvFps == null) {
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvFps.setText(String.format("FPS: %d", (int) fps));
                }
            });
        }

        @Override
        public void onRelease() {

        }
    };

    @Override
    public void onSurfaceCreated() {
        initCsvUtil(this);
        if (mFuRenderer != null) {
            mFuRenderer.prepareRenderer(mFURendererListener);
        }
    }

    @Override
    public void onSurfaceChanged(int viewWidth, int viewHeight) {
    }

//    @Override
    public int onDrawFrame(byte[] nv21Byte, int texId, int cameraWidth, int cameraHeight, float[] mvpMatrix, float[] texMatrix, long timeStamp) {
        int tid = 0;
        if (nv21Byte == null) {
            return tid;
        }
        if (mFuRenderer != null) {
            long start = System.nanoTime();

            mReadWriteLock.writeLock().lock();
            int tempW = 0;
            int tempH = 0;
            try {
                if (FUConfig.DEVICE_LEVEL > FuDeviceUtils.DEVICE_LEVEL_MID) {
                    //高性能设备
                    cheekFaceNum();
                }
                FURenderOutputData outputData = mFuRenderer.onDrawFrameDualInput(nv21Byte, texId, cameraWidth, cameraHeight, true);
                if (outputData.getTexture() != null && outputData.getTexture().getTexId() > 0) {
                    tid = outputData.getTexture().getTexId();
                }
                if (outputData.getImage() != null && outputData.getImage().getBuffer() != null) {
                    mReadBack = outputData.getImage().getBuffer();
                    tempW = outputData.getImage().getWidth();
                    tempH = outputData.getImage().getHeight();
                }
            } finally {
                mReadWriteLock.writeLock().unlock();
            }
            final int w = tempW;
            final int h = tempH;
            mPushHandler.post(() -> pushData(w, h));

            long renderTime = System.nanoTime() - start;
            mCSVUtils.writeCsv(null, renderTime);

            return tid;
        } else {
            mReadBack = nv21Byte;
            mPushHandler.post(() -> pushData(cameraWidth, cameraHeight));
        }
        return tid;
    }

    private static void NV21toI420(byte[] nv12bytes, byte[] i420bytes, int width,int height) {
        int nLenY = width * height;
        int nLenU = nLenY / 4;

        System.arraycopy(nv12bytes, 0, i420bytes, 0, width * height);

        for (int i = 0; i < nLenU; i++) {
            i420bytes[nLenY + i] = nv12bytes[nLenY + 2 * i + 1];

            i420bytes[nLenY + nLenU + i] = nv12bytes[nLenY + 2 * i];
        }
    }

    @Override
    public void onSurfaceDestroy() {
        mCSVUtils.close();
        if (mFuRenderer != null) {
            mFuRenderer.release();
        }
    }

//    @Override
//    public void onCameraChanged(int cameraFacing, int cameraOrientation) {
//        if (mFuRenderer != null) {
//            mFuRenderer.setCameraFacing(cameraFacing == 1 ? CameraFacingEnum.CAMERA_FRONT : CameraFacingEnum.CAMERA_BACK);
//            if (cameraFacing == 1) {
//                mFuRenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0_FLIPHORIZONTAL);
//                mFuRenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0_FLIPHORIZONTAL);
//                mFuRenderer.setInputOrientation(CameraUtils.INSTANCE.getCameraOrientation(Camera.CameraInfo.CAMERA_FACING_FRONT));
//                mFuRenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT90_FLIPHORIZONTAL);
//            }else {
//                mFuRenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0);
//                mFuRenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0);
//                mFuRenderer.setInputOrientation(CameraUtils.INSTANCE.getCameraOrientation(cameraFacing));
//                mFuRenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT90_FLIPHORIZONTAL);
//            }
//        }
//    }

    private void cheekFaceNum() {
        //根据有无人脸 + 设备性能 判断开启的磨皮类型
        float faceProcessorGetConfidenceScore = FUAIKit.getInstance().getFaceProcessorGetConfidenceScore(0);
        if (faceProcessorGetConfidenceScore >= 0.95) {
            //高端手机并且检测到人脸开启均匀磨皮，人脸点位质
            if (FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.EquallySkin) {
                FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.EquallySkin);
                FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(true);
            }
        } else {
            if (FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.FineSkin) {
                FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.FineSkin);
                FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(false);
            }
        }
    }


    public void onClick(View v) {
         if (v.getId() == R.id.livepusher_ibtn_qrcode) {
             mPusherPlayQRCodeFragment.toggle(getFragmentManager(), "push_play_qr_code_fragment");
        }else if (v.getId() == R.id.iv_change_camera) {
            mCameraRenderer.switchCamera();
        }
    }

    private void initData() {
        Intent intent = getIntent();
        mPusherURL = intent.getStringExtra(Constants.INTENT_URL_PUSH);
        mRTMPPlayURL = intent.getStringExtra(Constants.INTENT_URL_PLAY_RTMP);
        mFlvPlayURL = intent.getStringExtra(Constants.INTENT_URL_PLAY_FLV);
        mHlsPlayURL = intent.getStringExtra(Constants.INTENT_URL_PLAY_HLS);
        mRealtimePlayURL = intent.getStringExtra(Constants.INTENT_URL_PLAY_ACC);
    }

    /**
     * 初始化两个配置的 Fragment
     */
    private void initFragment() {

        if (mPusherPlayQRCodeFragment == null) {
            mPusherPlayQRCodeFragment = new PusherPlayQRCodeFragment();
            mPusherPlayQRCodeFragment.setQRCodeURL(mFlvPlayURL, mRTMPPlayURL, mHlsPlayURL, mRealtimePlayURL);
            mPusherPlayQRCodeFragment.toggle(getFragmentManager(), "push_play_qr_code_fragment");
        }

    }

    @Override
    public void onAccelerometerChanged(float x, float y, float z) {
        if (Math.abs(x) > 3 || Math.abs(y) > 3) {
            if (Math.abs(x) > Math.abs(y)) {
                mFuRenderer.setDeviceOrientation(x > 0 ? 0 : 180);
            } else {
                mFuRenderer.setDeviceOrientation(y > 0 ? 90 : 270);
            }
        }
    }

    private void initCsvUtil(Context context) {
        mCSVUtils = new CSVUtils(context);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String dateStrDir = format.format(new Date(System.currentTimeMillis()));
        dateStrDir = dateStrDir.replaceAll("-", "").replaceAll("_", "");
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
        String dateStrFile = df.format(new Date());
        String filePath = Constant.filePath + dateStrDir + File.separator + "excel-" + dateStrFile + ".csv";
        Log.d(TAG, "initLog: CSV file path:" + filePath);
        StringBuilder headerInfo = new StringBuilder();
        headerInfo.append("version：").append(FURenderer.getInstance().getVersion()).append(CSVUtils.COMMA)
                .append("机型：").append(android.os.Build.MANUFACTURER).append(android.os.Build.MODEL)
                .append("处理方式：Texture").append(CSVUtils.COMMA);
        mCSVUtils.initHeader(filePath, headerInfo);
    }

    @Override
    public void onDrawFrameAfter() {
        if (mFuRenderer != null) {
            benchmarkFPS();
            trackStatus();
        }
    }

    private long csvStartTime = 0;
    @Override
    public void onRenderBefore(@Nullable FURenderInputData fuRenderInputData) {
        mFuCallStartTime = System.nanoTime();
        fuRenderInputData.getRenderConfig().setNeedBufferReturn(true);
        fuRenderInputData.getRenderConfig().setOutputMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
        if (FUConfig.DEVICE_LEVEL > FuDeviceUtils.DEVICE_LEVEL_MID) {
            //高性能设备
            cheekFaceNum();
        }
        csvStartTime = System.nanoTime();
    }

    @Override
    public void onRenderAfter(@NotNull FURenderOutputData fuRenderOutputData, @NotNull FURenderFrameData fuRenderFrameData) {
        long renderTime = System.nanoTime() - csvStartTime;
        mCSVUtils.writeCsv(null, renderTime);

        float[] mvp = fuRenderFrameData.getMvpMatrix();
        Matrix.scaleM(mvp, 0, 1, -1, 1);
        fuRenderFrameData.setMvpMatrix(mvp);
        mReadWriteLock.writeLock().lock();
        int width = 0;
        int height = 0;
        try {
            if (fuRenderOutputData.getImage() != null) {
                mReadBack = fuRenderOutputData.getImage().getBuffer();
                width = fuRenderOutputData.getImage().getWidth();
                height = fuRenderOutputData.getImage().getHeight();
            }
        }finally {
            mReadWriteLock.writeLock().unlock();
        }
        final int w = width;
        final int h = height;
        mPushHandler.post(() -> pushData(w, h));
    }


    private int mCurrentFrameCnt = 0;
    private int mMaxFrameCnt = 10;
    private long mLastOneHundredFrameTimeStamp = 0;
    private long mFuCallStartTime = 0; //渲染前时间锚点（用于计算渲染市场）
    private long mOneHundredFrameFUTime = 0;
    protected int aIProcessTrackStatus = 1;

    private void onTrackStatusChanged(FUAIProcessorEnum type, int status) {
        Log.i(TAG, "onTrackStatusChanged() called with: type = [" + type + "], status = [" + status + "]");
        if (mTvTrackStatus == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvTrackStatus.setText(type == FUAIProcessorEnum.FACE_PROCESSOR ? R.string.toast_not_detect_face : R.string.toast_not_detect_body);
                mTvTrackStatus.setVisibility(status > 0 ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    private void onFpsChanged(double fps, double callTime) {
        final String FPS = String.format(Locale.getDefault(), "%.2f", fps);
        Log.e(TAG, "onFpsChanged: FPS " + FPS + " callTime " + String.format(Locale.getDefault(), "%.2f", callTime));
        if (mTvFps == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvFps.setText(String.format("FPS: %d", (int) fps));
            }
        });
    }

    /*AI识别数目检测*/
    private void trackStatus() {
        FUAIProcessorEnum fuaiProcessorEnum = mFuRenderer.getAIProcessTrackType();
        int trackCount;
        if (fuaiProcessorEnum == FUAIProcessorEnum.HAND_GESTURE_PROCESSOR) {
            trackCount = mFUAIKit.handProcessorGetNumResults();
        } else if (fuaiProcessorEnum == FUAIProcessorEnum.HUMAN_PROCESSOR) {
            trackCount = mFUAIKit.humanProcessorGetNumResults();
        } else {
            trackCount = mFUAIKit.isTracking();
        }
        if (aIProcessTrackStatus != trackCount) {
            aIProcessTrackStatus = trackCount;
            runOnUiThread(() -> onTrackStatusChanged(fuaiProcessorEnum, trackCount));
        }
    }

    private boolean mEnableFaceRender = false; //是否使用sdk渲染，该变量只在一个线程使用不需要volatile

    /*渲染FPS日志*/
    private void benchmarkFPS() {

        if (mEnableFaceRender)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        else
            mOneHundredFrameFUTime = 0;

        if (++mCurrentFrameCnt == mMaxFrameCnt) {
            mCurrentFrameCnt = 0;
            double fps = ((double) mMaxFrameCnt) * 1000000000L / (System.nanoTime() - mLastOneHundredFrameTimeStamp);
            double renderTime = ((double) mOneHundredFrameFUTime) / mMaxFrameCnt / 1000000L;
            mLastOneHundredFrameTimeStamp = System.nanoTime();
            mOneHundredFrameFUTime = 0;
            runOnUiThread(() -> onFpsChanged(fps, renderTime));
        }
        mEnableFaceRender = false;
    }
}