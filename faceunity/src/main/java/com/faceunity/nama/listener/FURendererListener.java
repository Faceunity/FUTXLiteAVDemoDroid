package com.faceunity.nama.listener;

/**
 * DESC：FURenderer状态回调监听
 * Created on 2021/4/29
 */
public interface FURendererListener {

    /**
     * prepare完成回调
     */
    void onPrepare();

    /**
     * release完成回调
     */
    void onRelease();


}
