package com.faceunity.fulpsdkdemodroid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.tencent.liteav.demo.liveplayer.ui.LivePlayerEntranceActivity;
import com.tencent.liteav.demo.livepusher.camerapush.model.Constants;
import com.tencent.liteav.demo.livepusher.camerapush.ui.CameraPushEntranceActivity;

public class LiveEnterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_enter);
    }


    public void onTxPushAction(View view) {
        startActivity(new Intent(this, CameraPushEntranceActivity.class));
    }

    public void onCustomPushAction(View view) {
        Intent intent = new Intent(this, CameraPushEntranceActivity.class);
        intent.putExtra(Constants.IS_CAMERA_COLLECT, true);
        startActivity(intent);
    }

    public void onPlayerAction(View view) {
        startActivity(new Intent(this, LivePlayerEntranceActivity.class));
    }
}