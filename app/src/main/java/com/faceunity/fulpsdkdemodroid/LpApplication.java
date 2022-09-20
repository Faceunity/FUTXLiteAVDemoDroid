package com.faceunity.fulpsdkdemodroid;

import android.content.Context;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;

import com.faceunity.FUConfig;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.utils.FuDeviceUtils;
import com.tencent.rtmp.TXLiveBase;

/**
 * @author Richie on 2018.08.27
 */
public class LpApplication extends MultiDexApplication {
    String licenceUrl = "";
    String licenseKey = "";

    @Override
    public void onCreate() {
        super.onCreate();
        FURenderer.getInstance().setup(this);
        FUConfig.DEVICE_LEVEL = FuDeviceUtils.judgeDeviceLevel(this);
        TXLiveBase.setConsoleEnabled(true);
        TXLiveBase.getInstance().setLicence(this, licenceUrl, licenseKey);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
