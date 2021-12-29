package com.tencent.liteav.demo.livepusher.camerapush.faceunity.render;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v7.app.AlertDialog;
import android.util.Log;


import com.tencent.liteav.demo.livepusher.R;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.CameraUtils;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles.ProgramTexture2d;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles.ProgramTextureOES;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles.core.GlUtil;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Camera 管理和 Surface 渲染
 *
 * @author Richie on 2019.12.20
 */
public class CameraRenderer implements GLSurfaceView.Renderer, Camera.PreviewCallback {
    private static final String TAG = "CameraRenderer";
    private static final int DEFAULT_CAMERA_WIDTH = 1280;
    private static final int DEFAULT_CAMERA_HEIGHT = 720;
    private static final int PREVIEW_BUFFER_COUNT = 3;
    private Activity mActivity;
    private GLSurfaceView mGlSurfaceView;
    private OnRendererStatusListener mOnRendererStatusListener;
    private int mViewWidth;
    private int mViewHeight;
    private Camera mCamera;
    private boolean mIsStoppedPreview;
    private byte[][] mPreviewCallbackBuffer;
    private int mCameraWidth = DEFAULT_CAMERA_WIDTH;
    private int mCameraHeight = DEFAULT_CAMERA_HEIGHT;
    private int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private int mCameraOrientation = 270;
    private int mCameraTextureId;
    private byte[] mCameraNv21Byte;
    private byte[] mNv21ByteCopy;
    private float[] mTexMatrix = {0.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f};
    private SurfaceTexture mSurfaceTexture;
    private int mFuTextureId;
    private boolean mIsPreviewing;
    private float[] mMvpMatrix;
    private ProgramTexture2d mProgramTexture2d;
    private ProgramTextureOES mProgramTextureOes;
    private Handler mBackgroundHandler;

    public CameraRenderer(Activity activity, GLSurfaceView glSurfaceView, OnRendererStatusListener onRendererStatusListener) {
        mActivity = activity;
        mGlSurfaceView = glSurfaceView;
        mOnRendererStatusListener = onRendererStatusListener;
    }

    public void onResume() {
        startBackgroundThread();
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                openCamera(mCameraFacing);
                startPreview();
            }
        });
        mGlSurfaceView.onResume();
    }

    public void onPause() {
        final CountDownLatch count = new CountDownLatch(1);
        mGlSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                destroyGlSurface();
                count.countDown();
            }
        });
        try {
            count.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // ignored
        }
        mGlSurfaceView.onPause();
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                releaseCamera();
            }
        });
        stopBackgroundThread();
    }

    public void switchCamera() {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean isFront = mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
                int cameraFacing = isFront ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
                mIsStoppedPreview = true;
                releaseCamera();
                openCamera(cameraFacing);
                startPreview();
                mIsStoppedPreview = false;
            }
        });
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated. Thread:" + Thread.currentThread().getName());
        Log.i(TAG, "GLES INFO vendor: " + GLES20.glGetString(GLES20.GL_VENDOR) + ", renderer: " + GLES20.glGetString(GLES20.GL_RENDERER)
                + ", version: " + GLES20.glGetString(GLES20.GL_VERSION));
        mProgramTexture2d = new ProgramTexture2d();
        mProgramTextureOes = new ProgramTextureOES();
        mCameraTextureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                openCamera(mCameraFacing);
                startPreview();
            }
        });
        mOnRendererStatusListener.onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        createMVPMatrix(width, height);
        Log.d(TAG, "onSurfaceChanged. viewWidth:" + width + ", viewHeight:" + height
                + ". cameraOrientation:" + mCameraOrientation + ", cameraWidth:" + mCameraWidth
                + ", cameraHeight:" + mCameraHeight + ", textureId:" + mCameraTextureId);
        mViewWidth = width;
        mViewHeight = height;
        mOnRendererStatusListener.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (mProgramTexture2d == null || mSurfaceTexture == null || mIsStoppedPreview) {
            return;
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        try {
            mSurfaceTexture.updateTexImage();
            mSurfaceTexture.getTransformMatrix(mTexMatrix);
        } catch (Exception e) {
            Log.e(TAG, "onDrawFrame: ", e);
        }

        if (!mIsStoppedPreview) {
            if (mCameraNv21Byte != null) {
                if (mNv21ByteCopy == null) {
                    mNv21ByteCopy = new byte[mCameraNv21Byte.length];
                }
                System.arraycopy(mCameraNv21Byte, 0, mNv21ByteCopy, 0, mCameraNv21Byte.length);
            }
            mFuTextureId = mOnRendererStatusListener.onDrawFrame(mNv21ByteCopy, mCameraTextureId,
                    mCameraWidth, mCameraHeight, mMvpMatrix, mTexMatrix, mSurfaceTexture.getTimestamp());
        }
        if (!mIsStoppedPreview) {
            if (mFuTextureId > 0) {
                mProgramTexture2d.drawFrame(mFuTextureId, mTexMatrix, mMvpMatrix);
            } else if (mCameraTextureId > 0) {
                mProgramTextureOes.drawFrame(mCameraTextureId, mTexMatrix, mMvpMatrix);
            }
            mGlSurfaceView.requestRender();
        }

        LimitFpsUtil.limitFrameRate();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mCameraNv21Byte = data;
        mCamera.addCallbackBuffer(data);
        if (!mIsStoppedPreview) {
            mGlSurfaceView.requestRender();
        }
    }

    private void openCamera(final int cameraFacing) {
        try {
            if (mCamera != null) {
                return;
            }
            Camera.CameraInfo info = new Camera.CameraInfo();
            int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
            int numCameras = Camera.getNumberOfCameras();
            if (numCameras <= 0) {
                throw new RuntimeException("No camera");
            }
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == cameraFacing) {
                    cameraId = i;
                    mCamera = Camera.open(i);
                    mCameraFacing = cameraFacing;
                    break;
                }
            }
            if (mCamera == null) {
                cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
                Camera.getCameraInfo(cameraId, info);
                mCamera = Camera.open(cameraId);
                mCameraFacing = cameraId;
            }
            mCameraOrientation = info.orientation;
            CameraUtils.setCameraDisplayOrientation(mActivity, cameraId, mCamera);
            Camera.Parameters parameters = mCamera.getParameters();
            CameraUtils.setFocusModes(parameters);
            int[] size = CameraUtils.choosePreviewSize(parameters, mCameraWidth, mCameraHeight);
            mCameraWidth = size[0];
            mCameraHeight = size[1];
            parameters.setPreviewFormat(ImageFormat.NV21);
            mCamera.setParameters(parameters);
            if (mViewWidth > 0 && mViewHeight > 0) {
                createMVPMatrix(mViewWidth, mViewHeight);
            }
            Log.d(TAG, "openCamera. facing: " + (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK
                    ? "back" : "front") + ", orientation:" + mCameraOrientation + ", cameraWidth:" + mCameraWidth
                    + ", cameraHeight:" + mCameraHeight);
        } catch (Exception e) {
            Log.e(TAG, "openCamera: ", e);
            releaseCamera();
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(mActivity)
                            .setTitle(R.string.camera_dialog_title)
                            .setMessage(R.string.camera_dialog_message)
                            .setNegativeButton(R.string.camera_dialog_open, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    if (!mIsPreviewing) {
                                        openCamera(cameraFacing);
                                        startPreview();
                                    }
                                }
                            })
                            .setNeutralButton(R.string.camera_dialog_back, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    mActivity.onBackPressed();
                                }
                            })
                            .show();
                }
            });
        }
    }

    private void startPreview() {
        if (mCameraTextureId <= 0) {
            return;
        }
        try {
            if (mCamera == null || mIsPreviewing) {
                return;
            }
            mCamera.stopPreview();
            if (mPreviewCallbackBuffer == null) {
                mPreviewCallbackBuffer = new byte[PREVIEW_BUFFER_COUNT][mCameraWidth * mCameraHeight
                        * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8];
            }
            mCamera.setPreviewCallbackWithBuffer(this);
            for (byte[] bytes : mPreviewCallbackBuffer) {
                mCamera.addCallbackBuffer(bytes);
            }
            if (mSurfaceTexture == null) {
                mSurfaceTexture = new SurfaceTexture(mCameraTextureId);
            }
            mCamera.setPreviewTexture(mSurfaceTexture);
            mCamera.startPreview();
            mIsPreviewing = true;
            Log.d(TAG, "startPreview: cameraTexId:" + mCameraTextureId);
            mOnRendererStatusListener.onCameraChanged(mCameraFacing, mCameraOrientation);
        } catch (Exception e) {
            Log.e(TAG, "startPreview: ", e);
        }
    }

    private void createMVPMatrix(int width, int height) {
        mMvpMatrix = GlUtil.changeMvpMatrixCrop(Arrays.copyOf(GlUtil.IDENTITY_MATRIX,
                GlUtil.IDENTITY_MATRIX.length), width, height, mCameraHeight, mCameraWidth);
        Matrix.rotateM(mMvpMatrix, 0, 90, 0f, 0f, 1f);
        if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            Matrix.scaleM(mMvpMatrix, 0, 1f, -1f, 1f);
        }
    }

    private void releaseCamera() {
        Log.d(TAG, "releaseCamera()");
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewTexture(null);
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.release();
                mCamera = null;
            }
            mIsPreviewing = false;
        } catch (Exception e) {
            Log.e(TAG, "releaseCamera: ", e);
        }
    }

    private void destroyGlSurface() {
        Log.d(TAG, "destroyGlSurface: ");
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mCameraTextureId > 0) {
            GLES20.glDeleteTextures(1, new int[]{mCameraTextureId}, 0);
            mCameraTextureId = 0;
        }
        if (mProgramTexture2d != null) {
            mProgramTexture2d.release();
            mProgramTexture2d = null;
        }
        if (mProgramTextureOes != null) {
            mProgramTextureOes.release();
            mProgramTextureOes = null;
        }

        mOnRendererStatusListener.onSurfaceDestroy();
    }

    private void startBackgroundThread() {
        HandlerThread backgroundThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        mBackgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.getLooper().quitSafely();
            mBackgroundHandler = null;
        }
    }

    public interface OnRendererStatusListener {
        /**
         * Called when surface is created or recreated.
         */
        void onSurfaceCreated();

        /**
         * Called when surface'size changed.
         *
         * @param viewWidth
         * @param viewHeight
         */
        void onSurfaceChanged(int viewWidth, int viewHeight);

        /**
         * Called when drawing current frame
         *
         * @param nv21Byte
         * @param texId
         * @param cameraWidth
         * @param cameraHeight
         * @param mvpMatrix
         * @param texMatrix
         * @param timeStamp
         * @return
         */
        int onDrawFrame(byte[] nv21Byte, int texId, int cameraWidth, int cameraHeight,
                        float[] mvpMatrix, float[] texMatrix, long timeStamp);

        /**
         * Called when surface is destroyed
         */
        void onSurfaceDestroy();

        /**
         * Called when camera changed
         *
         * @param cameraFacing
         * @param cameraOrientation
         */
        void onCameraChanged(int cameraFacing, int cameraOrientation);
    }
}
