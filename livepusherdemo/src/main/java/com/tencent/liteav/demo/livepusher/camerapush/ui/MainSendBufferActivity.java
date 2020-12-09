package com.tencent.liteav.demo.livepusher.camerapush.ui;

import android.content.Context;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.faceunity.nama.FURenderer;
import com.faceunity.nama.ui.FaceUnityView;
import com.tencent.liteav.demo.livepusher.R;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.CameraUtils;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles.core.GlUtil;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.render.CameraRenderer;
import com.tencent.liteav.demo.livepusher.camerapush.model.Constants;
import com.tencent.liteav.demo.livepusher.camerapush.profile.CSVUtils;
import com.tencent.liteav.demo.livepusher.camerapush.profile.Constant;
import com.tencent.liteav.demo.livepusher.camerapush.ui.view.PusherPlayQRCodeFragment;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.tencent.rtmp.TXLiveConstants.CUSTOM_MODE_VIDEO_CAPTURE;

public class MainSendBufferActivity extends AppCompatActivity implements CameraRenderer.OnRendererStatusListener, FURenderer.OnDebugListener,
        FURenderer.OnTrackStatusChangedListener , LifeCycleSensorManager.OnAccelerometerChangedListener{
    private static final String TAG = "MainActivity";
    private CameraRenderer mCameraRenderer;
    private FURenderer mFuRenderer;
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
        mLivePushConfig.setHardwareAcceleration(TXLiveConstants.ENCODE_VIDEO_SOFTWARE);
        mLivePusher.setConfig(mLivePushConfig);

        GLSurfaceView glSurfaceView = findViewById(R.id.video_view);
        glSurfaceView.setEGLContextClientVersion(GlUtil.getSupportGLVersion(this));
        mCameraRenderer = new CameraRenderer(this, glSurfaceView, this);
        glSurfaceView.setRenderer(mCameraRenderer);
        glSurfaceView.setKeepScreenOn(true);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mTvFps = findViewById(R.id.tv_fps);
        FaceUnityView faceUnityView = findViewById(R.id.faceunity_control);
        FURenderer.setup(this);
        mFuRenderer = new FURenderer.Builder(this)
                .setInputTextureType(FURenderer.INPUT_TEXTURE_EXTERNAL_OES)
                .setCameraFacing(FURenderer.CAMERA_FACING_FRONT)
                .setInputImageOrientation(CameraUtils.getCameraOrientation(FURenderer.CAMERA_FACING_FRONT))
                .setRunBenchmark(true)
                .setOnDebugListener(this)
                .setOnTrackStatusChangedListener(this)
                .build();
        faceUnityView.setModuleManager(mFuRenderer);
        LifeCycleSensorManager lifeCycleSensorManager = new LifeCycleSensorManager(this, getLifecycle());
        lifeCycleSensorManager.setOnAccelerometerChangedListener(this);

        mLivePusher.startPusher(mPusherURL);
        initFragment();

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
    public void onSurfaceCreated() {
        if (mFuRenderer != null) {
            mFuRenderer.onSurfaceCreated();
        }
        initCsvUtil(this);
    }

    @Override
    public void onSurfaceChanged(int viewWidth, int viewHeight) {

    }

    private byte[] mReadBack;

    @Override
    public int onDrawFrame(byte[] nv21Byte, int texId, int cameraWidth, int cameraHeight, float[] mvpMatrix, float[] texMatrix, long timeStamp) {
        if (nv21Byte == null) {
            return 0;
        }
        if (mReadBack == null) {
            mReadBack = new byte[nv21Byte.length];
        }
        if (mFuRenderer != null) {
            long start = System.nanoTime();

            int tId = mFuRenderer.onDrawFrameDualInput(nv21Byte, texId, cameraWidth, cameraHeight, mReadBack, cameraHeight, cameraWidth);
            byte[] buffer = nv21ToI420(mReadBack, cameraHeight, cameraWidth);
            mLivePusher.sendCustomVideoData(buffer, TXLivePusher.YUV_420P, cameraHeight, cameraWidth);

            long renderTime = System.nanoTime() - start;
            mCSVUtils.writeCsv(null, renderTime);

            return tId;
        }
        return 0;
    }

    private static byte[] nv21ToI420(byte[] data, int width, int height) {
        byte[] ret = new byte[data.length];
        int total = width * height;

        ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
        ByteBuffer bufferU = ByteBuffer.wrap(ret, total, total / 4);
        ByteBuffer bufferV = ByteBuffer.wrap(ret, total + total / 4, total / 4);

        bufferY.put(data, 0, total);
        for (int i = total; i < data.length; i += 2) {
            bufferV.put(data[i]);
            bufferU.put(data[i + 1]);
        }

        return ret;
    }

    @Override
    public void onSurfaceDestroy() {
        if (mFuRenderer != null) {
            mFuRenderer.onSurfaceDestroyed();
        }
        mCSVUtils.close();
    }

    @Override
    public void onCameraChanged(int cameraFacing, int cameraOrientation) {
        if (mFuRenderer != null) {
            mFuRenderer.onCameraChanged(cameraFacing, cameraOrientation);
            if (mFuRenderer.getMakeupModule() != null) {
                mFuRenderer.getMakeupModule().setIsMakeupFlipPoints(cameraFacing == FURenderer.CAMERA_FACING_BACK ? 1 : 0);
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

    @Override
    public void onTrackStatusChanged(final int type, final int status) {
        Log.i(TAG, "onTrackStatusChanged() called with: type = [" + type + "], status = [" + status + "]");
        if (mTvTrackStatus == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvTrackStatus.setText(type == FURenderer.TRACK_TYPE_FACE ? R.string.toast_not_detect_face : R.string.toast_not_detect_face_or_body);
                mTvTrackStatus.setVisibility(status > 0 ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    @Override
    public void onFpsChanged(final double fps, final double callTime) {
        Log.d(TAG, "onFpsChanged() called with: fps = [" + (int) fps + "], callTime = [" + String.format("%.2f", (float) callTime) + "]");
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
                mFuRenderer.onDeviceOrientationChanged(x > 0 ? 0 : 180);
            } else {
                mFuRenderer.onDeviceOrientationChanged(y > 0 ? 90 : 270);
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
        headerInfo.append("version：").append(FURenderer.getVersion()).append(CSVUtils.COMMA)
                .append("机型：").append(android.os.Build.MANUFACTURER).append(android.os.Build.MODEL)
                .append("处理方式：Texture").append(CSVUtils.COMMA);
        mCSVUtils.initHeader(filePath, headerInfo);
    }
}