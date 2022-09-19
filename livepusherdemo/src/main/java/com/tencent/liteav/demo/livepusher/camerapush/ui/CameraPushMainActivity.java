package com.tencent.liteav.demo.livepusher.camerapush.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.FileProvider;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.faceunity.core.enumeration.CameraFacingEnum;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.core.enumeration.FUInputTextureEnum;
import com.faceunity.core.enumeration.FUTransformMatrixEnum;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.data.FaceUnityDataFactory;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.ui.FaceUnityView;
import com.tencent.liteav.audiosettingkit.AudioEffectPanel;
import com.tencent.liteav.demo.livepusher.R;
import com.tencent.liteav.demo.livepusher.camerapush.PreferenceUtil;
import com.tencent.liteav.demo.livepusher.camerapush.model.CameraPush;
import com.tencent.liteav.demo.livepusher.camerapush.model.CameraPushImpl;
import com.tencent.liteav.demo.livepusher.camerapush.model.Constants;
import com.tencent.liteav.demo.livepusher.camerapush.ui.view.LogInfoWindow;
import com.tencent.liteav.demo.livepusher.camerapush.ui.view.PusherPlayQRCodeFragment;
import com.tencent.liteav.demo.livepusher.camerapush.ui.view.PusherSettingFragment;
import com.tencent.liteav.demo.livepusher.camerapush.ui.view.PusherVideoQualityFragment;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 腾讯云 {@link TXLivePusher} 推流器使用参考 Demo
 *
 * 有以下功能参考 ：
 *
 * - 基本功能参考： 启动推流 {@link CameraPush#startPush()} 与 结束推流 {@link CameraPush#stopPush()} ()}
 *
 * - 性能数据查看参考： {@link #onNetStatus(Bundle)}
 *
 * - 处理 SDK 回调事件参考： {@link #onPushEvent(int, Bundle)}
 *
 * - BGM 面板：{@link AudioEffectPanel}
 *
 * - 画质选择：{@link PusherVideoQualityFragment}
 *
 * - 混响、变声、码率自适应、硬件加速等使用参考： {@link PusherSettingFragment} 与 {@link PusherSettingFragment.OnSettingChangeListener}
 *
 */
public class CameraPushMainActivity extends FragmentActivity implements
        PusherVideoQualityFragment.OnVideoQualityChangeListener, PusherSettingFragment.OnSettingChangeListener, CameraPush.OnLivePusherCallback , SensorEventListener {

    private static final String TAG = "CameraPushMainActivity";

    private static final String PUSHER_SETTING_FRAGMENT       = "push_setting_fragment";
    private static final String PUSHER_PLAY_QR_CODE_FRAGMENT  = "push_play_qr_code_fragment";
    private static final String PUSHER_VIDEO_QUALITY_FRAGMENT = "push_video_quality_fragment";

    private static final int REQUEST_CODE = 100;

    private CameraPush mLivePusher;

    private TextView         mTextNetBusyTips;              // 网络繁忙Tips
    private Button           mBtnStartPush;                 // 开启推流的按钮
    private LinearLayout     mLinearBottomBar;              // 底部工具栏布局
    private AudioEffectPanel mAudioEffectPanel;             // 音效面板

    private PusherPlayQRCodeFragment   mPusherPlayQRCodeFragment;   // 拉流地址面板
    private PusherSettingFragment      mPusherSettingFragment;      // 设置面板
    private PusherVideoQualityFragment mPusherVideoQualityFragment; // 画质面板
    private LogInfoWindow              mLogInfoWindow;              // Log 信息面板

    private String mPusherURL       = "";   // 推流地址
    private String mRTMPPlayURL     = "";   // RTMP 拉流地址
    private String mFlvPlayURL      = "";   // flv 拉流地址
    private String mHlsPlayURL      = "";   // hls 拉流地址
    private String mRealtimePlayURL = "";   // 低延时拉流地址

    private int mLogClickCount = 0;
    private TextView mTvFps;
    private boolean isFUOn = false;
    private FURenderer mFURenderer;
    private FaceUnityDataFactory mFaceUnityDataFactory;
    private CameraFacingEnum mCameraFacing = CameraFacingEnum.CAMERA_FRONT;
    private SensorManager mSensorManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.livepusher_activity_live_pusher_main);
        checkPublishPermission();  // 检查权限

        mTvFps = findViewById(R.id.tv_fps);
        FaceUnityView faceUnityView = findViewById(R.id.fu_view);
        String isOpen = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON);
        if (TextUtils.isEmpty(isOpen) || isOpen.equals("false")) {
            isFUOn = false;
            faceUnityView.setVisibility(View.GONE);
        } else {
            isFUOn = true;
            mFURenderer = FURenderer.getInstance();
            mFURenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
            mFURenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
            mFURenderer.setInputOrientation(0);
            mFURenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT0);
            mFURenderer.setCameraFacing(CameraFacingEnum.CAMERA_FRONT);
            mFURenderer.setInputTextureType(FUInputTextureEnum.FU_ADM_FLAG_COMMON_TEXTURE);
            mFURenderer.setMarkFPSEnable(true);
            mFaceUnityDataFactory = new FaceUnityDataFactory(0);
            faceUnityView.bindDataFactory(mFaceUnityDataFactory);
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        initData();                // 初始化数据
        initFragment();            // 初始化Fragment
        initPusher();              // 初始化 SDK 推流器
        initMainView();            // 初始化一些核心的 View

        // 进入页面，自动开始推流，并且弹出推流对应的拉流地址
        mLivePusher.startPush();
        mPusherPlayQRCodeFragment.toggle(getFragmentManager(), PUSHER_PLAY_QR_CODE_FRAGMENT);
    }

    @Override
    public void onResume() {
        super.onResume();
        mLivePusher.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLivePusher.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLivePusher.destroy();
        if (mAudioEffectPanel != null) {
            mAudioEffectPanel.unInit();
            mAudioEffectPanel = null;
        }
        if (null != mSensorManager) {
            mSensorManager.unregisterListener(this);
        }
    }

    private final FURendererListener mFURendererListener = new FURendererListener() {
        @Override
        public void onPrepare() {
            mFaceUnityDataFactory.bindCurrentRenderer();
        }

        @Override
        public void onTrackStatusChanged(FUAIProcessorEnum type, int status) {

        }

        @Override
        public void onFpsChanged(double fps, double callTime) {
            final String FPS = String.format(Locale.getDefault(), "%.2f", fps);
            Log.e(TAG, "onFpsChanged: FPS " + FPS + " callTime " + String.format(Locale.getDefault(), "%.2f", callTime));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mTvFps != null) {
                        mTvFps.setText("FPS: " + FPS);
                    }
                }
            });
        }

        @Override
        public void onRelease() {

        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mLivePusher.setRotationForActivity(); // Activity 旋转
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (null != mAudioEffectPanel && mAudioEffectPanel.getVisibility() != View.GONE && ev.getRawY() < mAudioEffectPanel.getTop()) {
            mAudioEffectPanel.setVisibility(View.GONE);
            mAudioEffectPanel.hideAudioPanel();
            mLinearBottomBar.setVisibility(View.VISIBLE);
        }
        return super.dispatchTouchEvent(ev);
    }

    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.livepusher_ibtn_back) {
            mLivePusher.stopPush();
            finish();
        } else if (id == R.id.livepusher_ibtn_show_log) {
            if (mLogInfoWindow.isShowing()) {
                mLogInfoWindow.dismiss();
            }
            int count = mLogClickCount % 3;
            if (count == 0) {
                mLogInfoWindow.show(view);
                mLivePusher.showLog(false);
            } else if (count == 1) {
                mLivePusher.showLog(true);
            } else if (count == 2) {
                mLivePusher.showLog(false);
            }
            mLogClickCount++;
        } else if (id == R.id.livepusher_ibtn_qrcode) {
            if (mLogInfoWindow.isShowing()) {
                mLogInfoWindow.dismiss();
            }
            mPusherPlayQRCodeFragment.toggle(getFragmentManager(), PUSHER_PLAY_QR_CODE_FRAGMENT);
        } else if (id == R.id.livepusher_btn_start) {
            mLivePusher.togglePush();
        } else if (id == R.id.livepusher_btn_switch_camera) {
            // 表明当前是前摄像头
            if (view.getTag() == null || (Boolean) view.getTag()) {
                view.setTag(false);
                view.setBackgroundResource(R.drawable.livepusher_camera_back_btn);
            } else {
                view.setTag(true);
                view.setBackgroundResource(R.drawable.livepusher_camera_front);
            }
            mLivePusher.switchCamera();
            mCameraFacing = mCameraFacing == CameraFacingEnum.CAMERA_BACK ? CameraFacingEnum.CAMERA_FRONT : CameraFacingEnum.CAMERA_BACK;
            if (mFURenderer != null) {
                mFURenderer.setCameraFacing(mCameraFacing);
                if (mCameraFacing == CameraFacingEnum.CAMERA_FRONT) {
                    mFURenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
                    mFURenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
                    mFURenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT0);
                    mFURenderer.setInputOrientation(0);
                }else {
                    mFURenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0);
                    mFURenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0);
                    mFURenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
                    mFURenderer.setInputOrientation(90);
                }

            }
        } else if (id == R.id.livepusher_btn_beauty) {
            if (mLogInfoWindow.isShowing()) {
                mLogInfoWindow.dismiss();
            }
        } else if (id == R.id.livepusher_btn_bgm) {
            if (mLogInfoWindow.isShowing()) {
                mLogInfoWindow.dismiss();
            }
            if (mAudioEffectPanel.isShown()) {
                mAudioEffectPanel.setVisibility(View.GONE);
                mAudioEffectPanel.hideAudioPanel();
                mLinearBottomBar.setVisibility(View.VISIBLE);
            } else {
                mAudioEffectPanel.setVisibility(View.VISIBLE);
                mAudioEffectPanel.showAudioPanel();
                mLinearBottomBar.setVisibility(View.GONE);
            }
        } else if (id == R.id.livepusher_btn_video_quality) {
            if (mLogInfoWindow.isShowing()) {
                mLogInfoWindow.dismiss();
            }
            mPusherVideoQualityFragment.toggle(getSupportFragmentManager(), PUSHER_VIDEO_QUALITY_FRAGMENT);
        } else if (id == R.id.livepusher_btn_setting) {
            if (mLogInfoWindow.isShowing()) {
                mLogInfoWindow.dismiss();
            }
            mPusherSettingFragment.toggle(getSupportFragmentManager(), PUSHER_SETTING_FRAGMENT);
        }
    }

    /**
     * 推流器状态回调
     *
     * @param event 事件id.id类型请参考 {@linkplain TXLiveConstants#PLAY_EVT_CONNECT_SUCC 推流事件列表}.
     * @param param
     */
    @Override
    public void onPushEvent(int event, Bundle param) {
        String msg = param.getString(TXLiveConstants.EVT_DESCRIPTION);
        String pushEventLog = getString(R.string.livepusher_receive_event) + event + ", " + msg;
        Log.d(TAG, pushEventLog);
        mLogInfoWindow.setLogText(null, param, event);

        // Toast错误内容
        if (event < 0) {
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
        }
        if (event == TXLiveConstants.PUSH_WARNING_HW_ACCELERATION_FAIL) {
            // 开启硬件加速失败
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
        } else if (event == TXLiveConstants.PUSH_EVT_CHANGE_RESOLUTION) {
            Log.d(TAG, "change resolution to " + param.getInt(TXLiveConstants.EVT_PARAM2) + ", bitrate to" + param.getInt(TXLiveConstants.EVT_PARAM1));
        } else if (event == TXLiveConstants.PUSH_EVT_CHANGE_BITRATE) {
            Log.d(TAG, "change bitrate to" + param.getInt(TXLiveConstants.EVT_PARAM1));
        } else if (event == TXLiveConstants.PUSH_WARNING_NET_BUSY) {
            showNetBusyTips();
        }
    }

    @Override
    public void onActivityRotationObserverChange(boolean selfChange) {
        if (isActivityCanRotation(this)) {
            mPusherSettingFragment.hideOrientationItem();
        } else {
            mPusherSettingFragment.showOrientationItem();
        }
    }

    @Override
    public void onNetStatus(Bundle status) {
        Log.d(TAG, "Current status, CPU:" + status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE) +
                ", RES:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH) + "*" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT) +
                ", SPD:" + status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED) + "Kbps" +
                ", FPS:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS) +
                ", ARA:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE) + "Kbps" +
                ", VRA:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE) + "Kbps");
        mLogInfoWindow.setLogText(status, null, 0);
    }

    @Override
    public void onPrivateModeChange(boolean enable) {
        mLivePusher.setPrivateMode(enable);
        if (mLivePusher.isPushing()) {
        }
    }

    @Override
    public void onMuteChange(boolean enable) {
        mLivePusher.setMute(enable);
    }

    @Override
    public void onHomeOrientationChange(boolean isPortrait) {
        mLivePusher.setHomeOrientation(isPortrait);
    }

    @Override
    public void onMirrorChange(boolean enable) {
        mLivePusher.setMirror(enable);
    }

    @Override
    public void onFlashLightChange(boolean enable) {
        mLivePusher.turnOnFlashLight(enable);
    }

    @Override
    public void onWatermarkChange(boolean enable) {
        mLivePusher.setWatermark(enable);
    }

    @Override
    public void onPureAudioPushChange(boolean enable) {
        mLivePusher.enablePureAudioPush(enable);
    }

    @Override
    public void onHardwareAcceleration(boolean enable) {
        mLivePusher.setHardwareAcceleration(enable);
    }

    @Override
    public void onTouchFocusChange(boolean enable) {
        mLivePusher.setTouchFocus(enable);
        if (mLivePusher.isPushing()) {
            Toast.makeText(this, getString(R.string.livepusher_pushing_start_stop_retry_push), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEnableZoomChange(boolean enable) {
        mLivePusher.setEnableZoom(enable);
        if (mLivePusher.isPushing()) {
            Toast.makeText(this, getString(R.string.livepusher_pushing_start_stop_retry_push), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClickSnapshot() {
        if (mLivePusher != null) {
            mLivePusher.snapshot();
        }
    }

    @Override
    public void onSendMessage(String msg) {
        if (mLivePusher != null) {
            mLivePusher.sendMessage(msg);
        }
    }

    @Override
    public void onAdjustBitrateChange(boolean enable) {
        mLivePusher.setAdjustBitrate(enable, mPusherVideoQualityFragment.getQualityType());
    }

    @Override
    public void onQualityChange(int type) {
        mLivePusher.setQuality(mPusherSettingFragment.isAdjustBitrate(), type);
    }

    @Override
    public void onEnableAudioEarMonitoringChange(boolean enable) {
        mLivePusher.enableAudioEarMonitoring(enable);
    }

    @Override
    public void onAudioQualityChange(int channel, int sampleRate) {
        mLivePusher.setAudioQuality(channel, sampleRate);
    }

    @Override
    public void onPushStart(int code) {
        Log.d(TAG, "onPusherStart: code -> " + code);
        switch (code) {
            case Constants.PLAY_STATUS_SUCCESS:
                mBtnStartPush.setBackgroundResource(R.drawable.livepusher_pause);
                break;
            case Constants.PLAY_STATUS_INVALID_URL:
                Toast.makeText(getApplicationContext(), getString(R.string.livepusher_url_illegal), Toast.LENGTH_SHORT).show();
                // 输出状态log
                Bundle params = new Bundle();
                params.putString(TXLiveConstants.EVT_DESCRIPTION, getString(R.string.livepusher_check_url));
                mLogInfoWindow.setLogText(null, params, LogInfoWindow.CHECK_RTMP_URL_FAIL);
                break;
            case Constants.PLAY_STATUS_LICENSE_ERROR:
                String errInfo = getString(R.string.livepusher_license_check_fail);
                int start = (errInfo + getString(R.string.livepusher_license_click_info)).length();
                int end = (errInfo + getString(R.string.livepusher_license_click_use_info)).length();
                SpannableStringBuilder spannableStrBuidler = new SpannableStringBuilder(errInfo + getString(R.string.livepusher_license_click_use_info));
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent();
                        intent.setAction("android.intent.action.VIEW");
                        Uri content_url = Uri.parse("https://cloud.tencent.com/document/product/454/34750");
                        intent.setData(content_url);
                        startActivity(intent);
                    }
                };
                spannableStrBuidler.setSpan(new ForegroundColorSpan(Color.BLUE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannableStrBuidler.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                TextView tv = new TextView(this);
                tv.setMovementMethod(LinkMovementMethod.getInstance());
                tv.setText(spannableStrBuidler);
                tv.setPadding(20, 0, 20, 0);
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                dialogBuilder.setTitle(getString(R.string.livepusher_push_fail)).setView(tv).setPositiveButton(getString(R.string.livepusher_comfirm), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLivePusher.stopPush();
                    }
                });
                dialogBuilder.show();
            default:
                break;
        }
        if (code != Constants.PLAY_STATUS_INVALID_URL) {
            // 输出状态log
            Bundle bundle = new Bundle();
            bundle.putString(TXLiveConstants.EVT_DESCRIPTION, getString(R.string.livepusher_check_url));
            mLogInfoWindow.setLogText(null, bundle, LogInfoWindow.CHECK_RTMP_URL_OK);
        }
    }

    @Override
    public void onPushResume() {
        mAudioEffectPanel.resumeBGM();
    }

    @Override
    public void onPushPause() {
        mAudioEffectPanel.pauseBGM();
    }

    @Override
    public void onPushStop() {
        if (mPusherSettingFragment != null) {
            mPusherSettingFragment.closePrivateModel();
        }
        mBtnStartPush.setBackgroundResource(R.drawable.livepusher_start);
        mLogInfoWindow.clear();
        mAudioEffectPanel.reset();
    }

    @Override
    public void onSnapshot(File file) {
        if (mLivePusher.isPushing()) {
            if (file != null && file.exists() && file.length() > 0) {
                Toast.makeText(this, getString(R.string.livepusher_screenshot_success), Toast.LENGTH_SHORT).show();
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);//设置分享行为
                Uri uri = getUri(this, "com.tencent.liteav.demo", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(Intent.createChooser(intent, getString(R.string.livepusher_share_pic)));
            } else {
                Toast.makeText(CameraPushMainActivity.this, getString(R.string.livepusher_screenshot_fail), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(CameraPushMainActivity.this, getString(R.string.livepusher_screenshot_fail_push), Toast.LENGTH_SHORT).show();
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
     * 初始化 SDK 推流器
     */
    private void initPusher() {
        TXCloudVideoView pusherView = (TXCloudVideoView) findViewById(R.id.livepusher_tx_cloud_view);
        mLivePusher = new CameraPushImpl(this, pusherView);
        setVideoProcessListener(mLivePusher.getTXLivePusher());
        mLivePusher.setMute(mPusherSettingFragment.isMute());
        mLivePusher.setMirror(mPusherSettingFragment.isMirror());
        mLivePusher.setWatermark(mPusherSettingFragment.isWatermark());
        mLivePusher.setTouchFocus(mPusherSettingFragment.isTouchFocus());
        mLivePusher.setEnableZoom(mPusherSettingFragment.isEnableZoom());
        mLivePusher.enablePureAudioPush(mPusherSettingFragment.enablePureAudioPush());
        mLivePusher.enableAudioEarMonitoring(mPusherSettingFragment.enableAudioEarMonitoring());
        mLivePusher.setQuality(mPusherSettingFragment.isAdjustBitrate(), mPusherVideoQualityFragment.getQualityType());
        mLivePusher.setAudioQuality(mPusherSettingFragment.getAudioChannels(), mPusherSettingFragment.getAudioSampleRate());
        mLivePusher.setHomeOrientation(mPusherSettingFragment.isLandscape());
        mLivePusher.turnOnFlashLight(mPusherSettingFragment.isFlashEnable());
        mLivePusher.setHardwareAcceleration(mPusherSettingFragment.isHardwareAcceleration());
        mLivePusher.setOnLivePusherCallback(this);
        mLivePusher.setURL(mPusherURL);
    }

    private long currentTime;
    private void setVideoProcessListener(TXLivePusher livePusher){
        if (mFURenderer == null) {
            return;
        }
        livePusher.setVideoProcessListener(new TXLivePusher.VideoCustomProcessListener() {
            private boolean mIsFirstFrame = true;

            /**
             * 在OpenGL线程中回调，在这里可以进行采集图像的二次处理
             * @param i  纹理ID
             * @param i1      纹理的宽度
             * @param i2     纹理的高度
             * @return 返回给SDK的纹理
             * 说明：SDK回调出来的纹理类型是GLES20.GL_TEXTURE_2D，接口返回给SDK的纹理类型也必须是GLES20.GL_TEXTURE_2D
             */
            @Override
            public int onTextureCustomProcess(int i, int i1, int i2) {
                if (mIsFirstFrame) {
                    Log.d(TAG, "onTextureCustomProcess: texture:" + i + ", width:" + i1 + ", height:" + i2);
                    mFURenderer.prepareRenderer(mFURendererListener);
                    mIsFirstFrame = false;
                    return 0;
                }
                //三星s6总是会出现花屏
                if (System.currentTimeMillis() - currentTime < 200){
                    mFURenderer.onDrawFrameDualInput(null, i, i1, i2);
                }
                int texId = mFURenderer.onDrawFrameDualInput(null, i, i1, i2);
                return texId;
            }

            /**
             * 增值版回调人脸坐标
             * @param floats   归一化人脸坐标，每两个值表示某点P的X,Y值。值域[0.f, 1.f]
             */
            @Override
            public void onDetectFacePoints(float[] floats) {

            }

            /**
             * 在OpenGL线程中回调，可以在这里释放创建的OpenGL资源
             */
            @Override
            public void onTextureDestoryed() {
                Log.d(TAG, "onTextureDestroyed tid:" + Thread.currentThread().getId());
                mFURenderer.release();
                mIsFirstFrame = true;
            }
        });
    }

    /**
     * 初始化两个配置的 Fragment
     */
    private void initFragment() {
        if (mPusherSettingFragment == null) {
            mPusherSettingFragment = new PusherSettingFragment();
            mPusherSettingFragment.loadConfig(this);
            mPusherSettingFragment.setOnSettingChangeListener(this);
        }
        if (mPusherPlayQRCodeFragment == null) {
            mPusherPlayQRCodeFragment = new PusherPlayQRCodeFragment();
            mPusherPlayQRCodeFragment.setQRCodeURL(mFlvPlayURL, mRTMPPlayURL, mHlsPlayURL, mRealtimePlayURL);
        }
        if (mPusherVideoQualityFragment == null) {
            mPusherVideoQualityFragment = new PusherVideoQualityFragment();
            mPusherVideoQualityFragment.loadConfig(this);
            mPusherVideoQualityFragment.setOnVideoQualityChangeListener(this);
        }
        if (mLogInfoWindow == null) {
            mLogInfoWindow = new LogInfoWindow(this);
        }
    }

    /**
     * 初始化 美颜、log、二维码 等 view
     */
    private void initMainView() {
        mBtnStartPush = (Button) findViewById(R.id.livepusher_btn_start);
        mTextNetBusyTips = (TextView) findViewById(R.id.livepusher_tv_net_error_warning);
        mLinearBottomBar = (LinearLayout) findViewById(R.id.livepusher_ll_bottom_bar);

        mAudioEffectPanel = (AudioEffectPanel) findViewById(R.id.livepusher_audio_panel);
        mAudioEffectPanel.setAudioEffectManager(mLivePusher.getTXLivePusher().getAudioEffectManager());
        mAudioEffectPanel.setBackgroundColor(0xff13233F);
        mAudioEffectPanel.setOnAudioEffectPanelHideListener(new AudioEffectPanel.OnAudioEffectPanelHideListener() {
            @Override
            public void onClosePanel() {
                mAudioEffectPanel.setVisibility(View.GONE);
                mLinearBottomBar.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * 显示网络繁忙的提示
     */
    private void showNetBusyTips() {
        if (mTextNetBusyTips.isShown()) {
            return;
        }
        mTextNetBusyTips.setVisibility(View.VISIBLE);
        mTextNetBusyTips.postDelayed(new Runnable() {
            @Override
            public void run() {
                mTextNetBusyTips.setVisibility(View.GONE);
            }
        }, 5000);
    }

    private boolean checkPublishPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)) {
                permissions.add(Manifest.permission.READ_PHONE_STATE);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    private Uri getUri(Context context, String authority, File file) {
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //设置7.0以上共享文件，分享路径定义在xml/file_paths.xml
            uri = FileProvider.getUriForFile(context, authority, file);
        } else {
            // 7.0以下,共享文件
            uri = Uri.fromFile(file);
        }
        return uri;
    }


    /**
     * 判断系统 "自动旋转" 设置功能是否打开
     *
     * @return false---Activity可根据重力感应自动旋转
     */
    private boolean isActivityCanRotation(Context context) {
        int flag = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        return flag != 0;
    }

    /**
     * 获取当前推流状态
     *
     * @param status
     * @return
     */
    private String getStatus(Bundle status) {
        String str = String.format("%-14s %-14s %-12s\n%-8s %-8s %-8s %-8s\n%-14s %-14s %-12s\n%-14s %-14s",
                "CPU:" + status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE),
                "RES:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH) + "*" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT),
                "SPD:" + status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED) + "Kbps",
                "JIT:" + status.getInt(TXLiveConstants.NET_STATUS_NET_JITTER),
                "FPS:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS),
                "GOP:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_GOP) + "s",
                "ARA:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE) + "Kbps",
                "QUE:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_CACHE) + "|" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_CACHE),
                "DRP:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_DROP) + "|" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_DROP),
                "VRA:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE) + "Kbps",
                "SVR:" + status.getString(TXLiveConstants.NET_STATUS_SERVER_IP),
                "AUDIO:" + status.getString(TXLiveConstants.NET_STATUS_AUDIO_INFO));
        return str;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        int orientation = 0;
        if (Math.abs(x) > 3 || Math.abs(y) > 3) {
            if (Math.abs(x) > Math.abs(y)) {
                orientation = x > 0 ? 270 : 90;
            } else {
                orientation = y > 0 ? 0 : 180;
            }
        }
        if (mCameraFacing == CameraFacingEnum.CAMERA_FRONT) {
            mFURenderer.setDeviceOrientation((orientation + 270) % 360);
        }else {
            mFURenderer.setDeviceOrientation(orientation);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
