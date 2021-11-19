package com.faceunity.nama.listener;

import com.faceunity.core.enumeration.FUAIProcessorEnum;

/**
 * DESC：
 * Created on 2021/4/26
 */
public interface OnTrackStatusChangedListener {
    /**
     * 识别到的人脸或人体数量发生变化
     *
     * @param type   类型
     * @param status 数量
     */
    void onTrackStatusChanged(FUAIProcessorEnum type, int status);
}
