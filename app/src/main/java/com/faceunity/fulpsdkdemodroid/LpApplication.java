package com.faceunity.fulpsdkdemodroid;

import android.app.Application;

import com.faceunity.nama.FURenderer;
import com.tencent.rtmp.TXLiveBase;
import com.tencent.ugc.TXUGCBase;

/**
 * @author Richie on 2018.08.27
 */
public class LpApplication extends Application {
    String licenceUrl = "";
    String licenseKey = "";

    @Override
    public void onCreate() {
        super.onCreate();
        TXLiveBase.setConsoleEnabled(true);
        TXLiveBase.getInstance().setLicence(this, licenceUrl, licenseKey);
    }
}
