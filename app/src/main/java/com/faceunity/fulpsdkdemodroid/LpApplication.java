package com.faceunity.fulpsdkdemodroid;

import android.app.Application;

import com.faceunity.FURenderer;
import com.tencent.rtmp.TXLiveBase;
import com.tencent.ugc.TXUGCBase;

/**
 * @author LiuQiang on 2018.08.27
 */
public class LpApplication extends Application {
    String ugcLicenceUrl = "http://download-1252463788.cossh.myqcloud.com/xiaoshipin/licence_android/TXUgcSDK.licence";
    String ugcKey = "731ebcab46ecc59ab1571a6a837ddfb6";

    @Override
    public void onCreate() {
        super.onCreate();
        TXLiveBase.setConsoleEnabled(true);
        TXLiveBase.setAppID("1252463788");
        TXUGCBase.getInstance().setLicence(this, ugcLicenceUrl, ugcKey);

        FURenderer.initFURenderer(this);
    }
}
