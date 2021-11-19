## 对接第三方 Demo 的 faceunity 模块

本工程是第三方 Demo 依赖的 faceunity 模块，每次升级 SDK 时会优先在这里改动，然后同步到各个第三方 Demo 中。
当前的 Nama SDK 版本是 **7.4.1.0**。

--------

## 集成方法

### 一、添加 SDK

将 faceunity 模块添加到工程中，下面是对库文件的说明。

- assets/sticker 文件夹下 \*.bundle 是特效贴纸文件。
- assets/makeup 文件夹下 \*.bundle 是美妆素材文件。
- com/faceunity/nama/authpack.java 是鉴权证书文件，必须提供有效的证书才能运行 Demo，请联系技术支持获取。

通过 Maven 依赖最新版 SDK，方便升级，推荐使用。
```java
allprojects {
    repositories {
        ...
        maven { url 'http://maven.faceunity.com/repository/maven-public/' }
        ...
  }
}
```
```java
dependencies {
...
implementation 'com.faceunity:core:7.4.1.0' // 实现代码
implementation 'com.faceunity:model:7.4.1.0' // 道具以及AI bundle
...
}
```

```java
dependencies {
...
implementation 'com.faceunity:nama:7.4.1.0' //底层库-标准版
implementation 'com.faceunity:nama-lite:7.4.1.0' //底层库-lite版
...
}
```
其中，AAR 包含以下内容：

```
    +libs
      -nama.jar                        // JNI 接口
    +assets
      +graphic                         // 图形效果道具
        -body_slim.bundle              // 美体道具
        -controller.bundle             // Avatar 道具
        -face_beautification.bundle    // 美颜道具
        -face_makeup.bundle            // 美妆道具
        -fuzzytoonfilter.bundle        // 动漫滤镜道具
        -fxaa.bundle                   // 3D 绘制抗锯齿
        -tongue.bundle                 // 舌头跟踪数据包
      +model                           // 算法能力模型
        -ai_face_processor.bundle      // 人脸识别AI能力模型，需要默认加载
        -ai_face_processor_lite.bundle // 人脸识别AI能力模型，轻量版
        -ai_hand_processor.bundle      // 手势识别AI能力模型
        -ai_human_processor.bundle     // 人体点位AI能力模型
    +jni                               // CNama fuai 库
      +armeabi-v7a
        -libCNamaSDK.so
        -libfuai.so
      +arm64-v8a
        -libCNamaSDK.so
        -libfuai.so
      +x86
        -libCNamaSDK.so
        -libfuai.so
      +x86_64
        -libCNamaSDK.so
        -libfuai.so
```

如需指定应用的 so 架构，请修改 app 模块 build.gradle：

```groovy
android {
    // ...
    defaultConfig {
        // ...
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a'
        }
    }
}
```

如需剔除不必要的 assets 文件，请修改 app 模块 build.gradle：

```groovy
android {
    // ...
    applicationVariants.all { variant ->
        variant.mergeAssetsProvider.configure {
            doLast {
                delete(fileTree(dir: outputDir, includes: ['model/ai_face_processor_lite.bundle',
                                                           'model/ai_hand_processor.bundle',
                                                           'graphics/controller.bundle',
                                                           'graphics/fuzzytoonfilter.bundle',
                                                           'graphics/fxaa.bundle',
                                                           'graphics/tongue.bundle']))
            }
        }
    }
}
```

### 二、使用 SDK

#### 1. 初始化

调用 `FURenderer` 类的  `setup` 方法初始化 SDK，可以在工作线程调用，应用启动后仅需调用一次。

在 NeedFaceUnityAcct 类 onCreate()方法中执行

#### 2.创建

在 `FaceUnityDataFactory` 类 的  `bindCurrentRenderer` 方法是对 FaceUnity SDK 每次使用前数据初始化的封装。

在 CameraPushMainActivity 类中 设置 VideoCustomProcessListener回调方法，且在onTextureCustomProcess方法中执行。

```
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
            mFURenderer.prepareRenderer(mFURendererListener);
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
        mFURenderer.release();
        mIsFirstFrame = true;
    }
});

private final FURendererListener mFURendererListener = new FURendererListener() {
    @Override
    public void onPrepare() {
        mFaceUnityDataFactory.bindCurrentRenderer();
    }

    @Override
    public void onRelease() {

    }
};
```

#### 3. 图像处理

调用 `FURenderer` 类的  `onDrawFrameXXX` 方法进行图像处理，有许多重载方法适用于不同数据类型的需求。

在CameraPushMainActivity类中，需要注册 VideoCustomProcessListener 接口，接口中的 onTextureCustomProcess 方法时可以执行美颜操作。（代码如上）

onDrawFrameDualInput 双输入，输入图像buffer数组与纹理Id，输出纹理Id。性能上，双输入优于单输入

#### 4. 销毁

调用 `FURenderer` 类的  `release` 方法在 SDK 结束前释放占用的资源。

在CameraPushMainActivity类中，需要注册 VideoCustomProcessListener 接口，接口中的 onTextureDestoryed方法时释放资源

#### 5. 切换相机

调用 `FURenderer` 类 的  `setCameraFacing` 方法，用于重新为 SDK 设置参数。

在CameraPushMainActivity 类下的 R.id.livepusher_btn_switch_camera 空间点击事件方法时执行 onCameraChanged方法。

#### 6. 旋转手机

调用 `FURenderer` 类 的  `setDeviceOrientation` 方法，用于重新为 SDK 设置参数。

使用方法：CameraPushMainActivity 中可见

```java
1.implements SensorEventListener
2. onCreate()    
mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

3.@Override
protected void onDestroy() {
    super.onDestroy();
    // 清理相关资源
    if (null != mSensorManager) {
        mSensorManager.unregisterListener(this);
    }
}
4. 
//实现接口
@Override
public void onSensorChanged(SensorEvent event) {
    //具体代码见 CameraPushMainActivity 类
}

```

**注意：** 上面一系列方法的使用，具体在 demo 中的 `MainActivity` 类，参考该代码示例接入即可。

### 三、接口介绍

- IFURenderer 是核心接口，提供了创建、销毁、渲染等接口。
- FaceUnityDataFactory 控制四个功能模块，用于功能模块的切换，初始化
- FaceBeautyDataFactory 是美颜业务工厂，用于调整美颜参数。
- PropDataFactory 是道具业务工厂，用于加载贴纸效果。
- MakeupDataFactory 是美妆业务工厂，用于加载美妆效果。
- BodyBeautyDataFactory 是美体业务工厂，用于调整美体参数。

关于 SDK 的更多详细说明，请参看 **[FULiveDemoDroid](https://github.com/Faceunity/FULiveDemoDroid/)**。如有对接问题，请联系技术支持。
