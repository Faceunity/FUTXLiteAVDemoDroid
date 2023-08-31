package com.faceunity.nama;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;

import com.faceunity.core.callback.OperateCallback;
import com.faceunity.core.entity.FURenderInputData;
import com.faceunity.core.entity.FURenderOutputData;
import com.faceunity.core.enumeration.CameraFacingEnum;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.core.enumeration.FUAITypeEnum;
import com.faceunity.core.faceunity.FUAIKit;
import com.faceunity.core.faceunity.FURenderConfig;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.faceunity.FURenderManager;
import com.faceunity.core.utils.CameraUtils;
import com.faceunity.core.utils.FULogger;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.utils.FuDeviceUtils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * DESC：
 * Created on 2021/4/26
 */
public class FURenderer extends IFURenderer {


    public volatile static FURenderer INSTANCE;

    public static FURenderer getInstance() {
        if (INSTANCE == null) {
            synchronized (FURenderer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FURenderer();
                    INSTANCE.mFURenderKit = FURenderKit.getInstance();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 状态回调监听
     */
    private FURendererListener mFURendererListener;


    /* 特效FURenderKit*/
    private FURenderKit mFURenderKit = FURenderKit.getInstance();
    /* 特效FURenderKit*/
    private FUAIKit mFUAIKit = FUAIKit.getInstance();

    /* AI道具*/
    private String BUNDLE_AI_FACE = "model" + File.separator + "ai_face_processor.bundle";
    private String BUNDLE_AI_HUMAN = "model" + File.separator + "ai_human_processor.bundle";

    /* 相机角度-朝向映射 */
    private HashMap<CameraFacingEnum, Integer> cameraOrientationMap = new HashMap<>();

    /* GL 线程 ID */
    private Long mGlThreadId = 0L;
    /* 任务队列 */
    private ArrayList<Runnable> mEventQueue = new ArrayList<>(16);
    private final Object queueLock = new Object();
    /* 渲染开关标识 */
    private volatile boolean mRendererSwitch = false;
    /* 清除队列标识 */
    private volatile boolean mClearQueue = false;

    /*检测类型*/
    private FUAIProcessorEnum aIProcess = FUAIProcessorEnum.FACE_PROCESSOR;
    /*检测标识*/
    private int aIProcessTrackStatus = -1;

    public String getVersion() {
        return mFURenderKit.getVersion();
    }

    /**
     * 初始化鉴权
     *
     * @param context
     */
    @Override
    public void setup(Context context) {
        FURenderManager.setKitDebug(FULogger.LogLevel.DEBUG);
        FURenderManager.setCoreDebug(FULogger.LogLevel.ERROR);
        FURenderManager.registerFURender(context, authpack.A(), new OperateCallback() {
            @Override
            public void onSuccess(int i, @NotNull String s) {
                if (i == FURenderConfig.OPERATE_SUCCESS_AUTH) {
                    mFUAIKit.loadAIProcessor(BUNDLE_AI_FACE, FUAITypeEnum.FUAITYPE_FACEPROCESSOR);
                    mFUAIKit.loadAIProcessor(BUNDLE_AI_HUMAN, FUAITypeEnum.FUAITYPE_HUMAN_PROCESSOR);
                    int cameraFrontOrientation = CameraUtils.INSTANCE.getCameraOrientation(Camera.CameraInfo.CAMERA_FACING_FRONT);
                    int cameraBackOrientation = CameraUtils.INSTANCE.getCameraOrientation(Camera.CameraInfo.CAMERA_FACING_BACK);
                    cameraOrientationMap.put(CameraFacingEnum.CAMERA_FRONT, cameraFrontOrientation);
                    cameraOrientationMap.put(CameraFacingEnum.CAMERA_BACK, cameraBackOrientation);
                }
            }

            @Override
            public void onFail(int i, @NotNull String s) {
            }
        });
    }

    /**
     * 开启合成状态
     */
    @Override
    public void prepareRenderer(FURendererListener mFURendererListener) {
        this.mFURendererListener = mFURendererListener;
        mRendererSwitch = true;
        mClearQueue = false;
        queueEvent(() -> mGlThreadId = Thread.currentThread().getId());
        mFURendererListener.onPrepare();
    }


    /**
     * 双输入接口，输入 buffer 和 texture，必须在具有 GL 环境的线程调用
     * 由于省去数据拷贝，性能相对最优，优先推荐使用。
     * 缺点是无法保证 buffer 和纹理对齐，可能出现点位和效果对不上的情况。
     *
     * @param img    NV21 buffer
     * @param texId  纹理 ID
     * @param width  宽
     * @param height 高
     * @return
     */
    @Override
    public int onDrawFrameDualInput(byte[] img, int texId, int width, int height) {
        prepareDrawFrame();
        if (!mRendererSwitch) {
            return texId;
        }
        FURenderInputData inputData = new FURenderInputData(width, height);
        //buffer为空，单纹理输入
        if (null != img && img.length > 0) {
            inputData.setImageBuffer(new FURenderInputData.FUImageBuffer(inputBufferType, img));
        } else {
            inputData.setImageBuffer(null);
        }
        inputData.setTexture(new FURenderInputData.FUTexture(inputTextureType, texId));
        FURenderInputData.FURenderConfig config = inputData.getRenderConfig();
        config.setExternalInputType(externalInputType);
        config.setInputOrientation(inputOrientation);
        config.setDeviceOrientation(deviceOrientation);
        config.setInputBufferMatrix(inputBufferMatrix);
        config.setInputTextureMatrix(inputTextureMatrix);
        config.setOutputMatrix(outputMatrix);
        config.setCameraFacing(cameraFacing);
        mCallStartTime = System.nanoTime();
        FURenderOutputData outputData = mFURenderKit.renderWithInput(inputData);
        mSumCallTime += System.nanoTime() - mCallStartTime;
        if (outputData.getTexture() != null && outputData.getTexture().getTexId() > 0) {
            return outputData.getTexture().getTexId();
        }
        return texId;
    }

    @Override
    public FURenderOutputData onDrawFrameDualInput(byte[] img, int texId, int width, int height, boolean readback) {
        prepareDrawFrame();
        if (!mRendererSwitch) {
            return null;
        }
        FURenderInputData inputData = new FURenderInputData(width, height);
        //buffer为空，单纹理输入
        if (null != img && img.length > 0) {
            inputData.setImageBuffer(new FURenderInputData.FUImageBuffer(inputBufferType, img));
        } else {
            inputData.setImageBuffer(null);
        }
        inputData.setTexture(new FURenderInputData.FUTexture(inputTextureType, texId));
        FURenderInputData.FURenderConfig config = inputData.getRenderConfig();
        config.setExternalInputType(externalInputType);
        config.setInputOrientation(inputOrientation);
        config.setDeviceOrientation(deviceOrientation);
        config.setInputBufferMatrix(inputBufferMatrix);
        config.setInputTextureMatrix(inputTextureMatrix);
        config.setCameraFacing(cameraFacing);
        config.setOutputMatrix(outputMatrix);
        config.setNeedBufferReturn(readback);
        mCallStartTime = System.nanoTime();
        FURenderOutputData outputData = mFURenderKit.renderWithInput(inputData);
        mSumCallTime += System.nanoTime() - mCallStartTime;
        return outputData;
    }

    /**
     * 类似 GLSurfaceView 的 queueEvent 机制，把任务抛到 GL 线程执行。
     *
     * @param runnable
     */
    @Override
    public void queueEvent(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (mGlThreadId == Thread.currentThread().getId()) {
            runnable.run();
        } else {
            synchronized (queueLock) {
                mEventQueue.add(runnable);
            }
        }
    }


    /**
     * 释放资源
     * 需要gl线程
     */
    @Override
    public void release() {
        mRendererSwitch = false;
        mClearQueue = true;
        mGlThreadId = 0L;
        synchronized (queueLock) {
            mEventQueue.clear();
            mClearQueue = false;
            mFURenderKit.release();
            aIProcessTrackStatus = -1;
            if (mFURendererListener != null) {
                mFURendererListener.onRelease();
                mFURendererListener = null;
            }
        }
    }


    /**
     * 渲染前置执行
     *
     * @return
     */
    public void prepareDrawFrame() {
        benchmarkFPS();

        // 执行任务队列中的任务
        synchronized (queueLock) {
            while (!mEventQueue.isEmpty() && !mClearQueue) {
                mEventQueue.remove(0).run();
            }
        }
        // AI检测
        trackStatus();
    }

    //region AI识别

    @Override
    public void setCameraFacing(CameraFacingEnum cameraFacing) {
        super.setCameraFacing(cameraFacing);
    }

    @Override
    public void setDeviceOrientation(int deviceOrientation) {
        super.setDeviceOrientation(deviceOrientation);
    }

    /**
     * 设置检测类型
     *
     * @param type
     */
    @Override
    public void setAIProcessTrackType(FUAIProcessorEnum type) {
        aIProcess = type;
        aIProcessTrackStatus = -1;
    }

    @Override
    public FUAIProcessorEnum getAIProcessTrackType() {
        return aIProcess;
    }

    /**
     * 设置FPS检测
     *
     * @param enable
     */
    @Override
    public void setMarkFPSEnable(boolean enable) {
        mIsRunBenchmark = enable;
    }


    /**
     * AI识别数目检测
     */
    private void trackStatus() {
        int trackCount;
        if (aIProcess == FUAIProcessorEnum.HAND_GESTURE_PROCESSOR) {
            trackCount = mFURenderKit.getFUAIController().handProcessorGetNumResults();
        } else if (aIProcess == FUAIProcessorEnum.HUMAN_PROCESSOR) {
            trackCount = mFURenderKit.getFUAIController().humanProcessorGetNumResults();
        } else {
            trackCount = mFURenderKit.getFUAIController().isTracking();
        }
        if (trackCount != aIProcessTrackStatus) {
            aIProcessTrackStatus = trackCount;
        }
        if (mFURendererListener != null) {
            mFURendererListener.onTrackStatusChanged(aIProcess, trackCount);
        }
    }
    //endregion AI识别

    //------------------------------FPS 渲染时长回调相关定义------------------------------------

    private static final int NANO_IN_ONE_MILLI_SECOND = 1_000_000;
    private static final int NANO_IN_ONE_SECOND = 1_000_000_000;
    private static final int FRAME_COUNT = 20;
    private boolean mIsRunBenchmark = false;
    private int mCurrentFrameCount;
    private long mLastFrameTimestamp;
    private long mSumCallTime;
    private long mCallStartTime;

    private void benchmarkFPS() {
        if (!mIsRunBenchmark) {
            return;
        }
        if (++mCurrentFrameCount == FRAME_COUNT) {
            long tmp = System.nanoTime();
            double fps = (double) NANO_IN_ONE_SECOND / ((double) (tmp - mLastFrameTimestamp) / FRAME_COUNT);
            double renderTime = (double) mSumCallTime / FRAME_COUNT / NANO_IN_ONE_MILLI_SECOND;
            mLastFrameTimestamp = tmp;
            mSumCallTime = 0;
            mCurrentFrameCount = 0;

            if (mFURendererListener != null) {
                mFURendererListener.onFpsChanged(fps, renderTime);
            }
        }
    }

}
