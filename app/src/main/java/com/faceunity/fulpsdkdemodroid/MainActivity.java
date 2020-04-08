package com.faceunity.fulpsdkdemodroid;

import android.app.AlertDialog;
import android.app.Service;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.faceunity.nama.FURenderer;
import com.faceunity.nama.ui.BeautyControlView;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveBase;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements ITXLivePushListener, FURenderer.OnTrackingStatusChangedListener {
    private static final String TAG = "MainActivity";
    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher mLivePusher;
    private TXCloudVideoView mCaptureView;
    private FURenderer mFURenderer;
    private static final int VIDEO_SRC_CAMERA = 0;
    private static final int VIDEO_SRC_SCREEN = 1;
    private PhoneStateListener mPhoneListener = null;
    private RotationObserver mRotationObserver;
    private boolean mFrontCamera = true;
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BeautyControlView beautyControlView = findViewById(R.id.faceunity_control);
        String sdkver = TXLiveBase.getSDKVersionStr();
        Log.d("liteavsdk", "liteav sdk version is : " + sdkver);

        mFURenderer = new FURenderer
                .Builder(this)
                .setInputTextureType(FURenderer.INPUT_2D_TEXTURE)
                .setOnTrackingStatusChangedListener(this)
                .build();
        beautyControlView.setOnFaceUnityControlListener(mFURenderer);

        mCaptureView = (TXCloudVideoView) findViewById(R.id.video_view);
        mLivePusher = new TXLivePusher(this);
        mLivePushConfig = new TXLivePushConfig();
        mLivePusher.setConfig(mLivePushConfig);
        // 设置自定义视频处理回调，在主播预览及编码前回调出来，用户可以用来做自定义美颜或者增加视频特效
        mLivePusher.setVideoProcessListener(new TXLivePusher.VideoCustomProcessListener() {
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
                    mFURenderer.onSurfaceCreated();
                    mIsFirstFrame = false;
                }
                return mFURenderer.onDrawFrameSingleInput(i, i1, i2);
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
                mFURenderer.onSurfaceDestroyed();
                mIsFirstFrame = true;
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mPhoneListener = new TXPhoneStateListener(mLivePusher);
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);

        mRotationObserver = new RotationObserver(new Handler());
        mRotationObserver.startObserver();

        String rtmpUrl = "https://room.qcloud.com/weapp/live_room/add_pusher?userID=user_4debca9c_515e&token=5bacc8b47ebca9651466c36a195f4c4f";
        mLivePusher.startPusher(rtmpUrl);
        mLivePusher.startCameraPreview(mCaptureView);

        findViewById(R.id.iv_change_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeCamera();
            }
        });
        mTextView = (TextView) findViewById(R.id.tv_track_text);
    }

    private void changeCamera() {
        mFrontCamera = !mFrontCamera;
//        if (mLivePusher.isPushing()) {
            mLivePusher.switchCamera();
//        }
        /*设置是否使用前置摄像头。默认使用前置摄像头*/
        mLivePushConfig.setFrontCamera(mFrontCamera);
        /*切换摄像头*/
        int cameraType = mFrontCamera ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        mFURenderer.onCameraChanged(cameraType, FURenderer.getCameraOrientation(cameraType));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCaptureView != null) {
            mCaptureView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCaptureView != null) {
            mCaptureView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCaptureView != null) {
            mCaptureView.onDestroy();
        }
        mRotationObserver.stopObserver();

        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
    }

    @Override
    public void onBackPressed() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定要退出吗？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        MainActivity.super.onBackPressed();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create();
        dialog.show();
    }

    @Override
    public void onPushEvent(int i, Bundle bundle) {

    }

    @Override
    public void onNetStatus(Bundle bundle) {

    }

    @Override
    public void onTrackingStatusChanged(final int status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setVisibility(status > 0 ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    //观察屏幕旋转设置变化，类似于注册动态广播监听变化机制
    private class RotationObserver extends ContentObserver {
        ContentResolver mResolver;

        public RotationObserver(Handler handler) {
            super(handler);
            mResolver = MainActivity.this.getContentResolver();
        }

        //屏幕旋转设置改变时调用
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            //更新按钮状态
            if (isActivityCanRotation()) {
                onActivityRotation();
            } else {
                mLivePushConfig.setHomeOrientation(TXLiveConstants.VIDEO_ANGLE_HOME_DOWN);
                mLivePusher.setRenderRotation(0);
                mLivePusher.setConfig(mLivePushConfig);
            }
        }

        public void startObserver() {
            mResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, this);
        }

        public void stopObserver() {
            mResolver.unregisterContentObserver(this);
        }
    }

    /**
     * 判断Activity是否可旋转。只有在满足以下条件的时候，Activity才是可根据重力感应自动旋转的。
     * 系统“自动旋转”设置项打开；
     *
     * @return false---Activity可根据重力感应自动旋转
     */
    protected boolean isActivityCanRotation() {
        // 判断自动旋转是否打开
        int flag = Settings.System.getInt(this.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        if (flag == 0) {
            return false;
        }
        return true;
    }

    protected void onActivityRotation() {
        // 自动旋转打开，Activity随手机方向旋转之后，需要改变推流方向
        int mobileRotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
        boolean screenCaptureLandscape = false;
        switch (mobileRotation) {
            case Surface.ROTATION_0:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
                break;
            case Surface.ROTATION_180:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_UP;
                break;
            case Surface.ROTATION_90:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_RIGHT;
                screenCaptureLandscape = true;
                break;
            case Surface.ROTATION_270:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_LEFT;
                screenCaptureLandscape = true;
                break;
            default:
                break;
        }
        mLivePusher.setRenderRotation(0); //因为activity也旋转了，本地渲染相对正方向的角度为0。
        mLivePushConfig.setHomeOrientation(pushRotation);
        if (mLivePusher.isPushing()) {
            int videoSrc = VIDEO_SRC_CAMERA;
            if (VIDEO_SRC_CAMERA == videoSrc) {
                mLivePusher.setConfig(mLivePushConfig);
                mLivePusher.stopCameraPreview(true);
                mLivePusher.startCameraPreview(mCaptureView);
            } else if (VIDEO_SRC_SCREEN == videoSrc) {
                //录屏横竖屏推流的判断条件是，视频分辨率取360*640还是640*360
                int currentVideoResolution = TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640;
                switch (currentVideoResolution) {
                    case TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640:
                        if (screenCaptureLandscape)
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_640_360);
                        else
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640);
                        break;
                    case TXLiveConstants.VIDEO_RESOLUTION_TYPE_540_960:
                        if (screenCaptureLandscape)
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_960_540);
                        else
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_540_960);
                        break;
                    case TXLiveConstants.VIDEO_RESOLUTION_TYPE_720_1280:
                        if (screenCaptureLandscape)
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_1280_720);
                        else
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_720_1280);
                        break;
                    default:
                }
                mLivePusher.setConfig(mLivePushConfig);
                mLivePusher.stopScreenCapture();
                mLivePusher.startScreenCapture();
            }
        }
    }


    static class TXPhoneStateListener extends PhoneStateListener {
        WeakReference<TXLivePusher> mPusher;

        public TXPhoneStateListener(TXLivePusher pusher) {
            mPusher = new WeakReference<TXLivePusher>(pusher);
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            TXLivePusher pusher = mPusher.get();
            switch (state) {
                //电话等待接听
                case TelephonyManager.CALL_STATE_RINGING:
                    if (pusher != null)
                        pusher.pausePusher();
                    break;
                //电话接听
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (pusher != null)
                        pusher.pausePusher();
                    break;
                //电话挂机
                case TelephonyManager.CALL_STATE_IDLE:
                    if (pusher != null)
                        pusher.resumePusher();
                    break;
                default:
            }
        }
    }

}