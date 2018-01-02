/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.hellojnicallback;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.Keep;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    String TAG = "MainActivity";

    int hour = 0;
    int minute = 0;
    int second = 0;
    TextView tickView;
    ActivityManager activityMgr;
    ActivityManager.MemoryInfo memoryInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        tickView = (TextView) findViewById(R.id.tickView);
        activityMgr = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        memoryInfo = new ActivityManager.MemoryInfo();
    }
    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        hour = minute = second = 0;
        ((TextView)findViewById(R.id.hellojniMsg)).setText(stringFromJNI());
        startTicks();
    }

    @Override
    public void onPause () {
        super.onPause();
        Log.i(TAG, "onPause");
        StopTicks();
    }

    /*
     * A function calling from JNI to update current timer
     */
    @Keep
    private void updateTimer() {
        Log.i(TAG, "updateTimer");
        //XXX just for test
        oom();

        ++second;
        if(second >= 60) {
            ++minute;
            second -= 60;
            if(minute >= 60) {
                ++hour;
                minute -= 60;
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String ticks = "" + MainActivity.this.hour + ":" +
                        MainActivity.this.minute + ":" +
                        MainActivity.this.second;
                MainActivity.this.tickView.setText(ticks);
            }
        });
    }
    static {
        System.loadLibrary("hello-jnicallback");
    }
    public native  String stringFromJNI();
    public native void startTicks();
    public native void StopTicks();

    // XXX just for test
    private void divZero(){
        int i = 5/0;
        if (i > 0) Log.i(TAG, "i=5/0, i>0");
    }


    private void derefNull(){
        Button btn = null;
        Log.i(TAG, "try deref null...");
        btn.findFocus();
    }

    private void oom(){
        ArrayList<Bitmap> bitmapList = new ArrayList<>();
        Bitmap bm;
        int cnt=1024*1024;
        for (int i=0; i<cnt; ++i){
            bm = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
            bitmapList.add(bm);
            activityMgr.getMemoryInfo(memoryInfo);
            Log.i(TAG, "bitmap count:"+i+" totalMem="+memoryInfo.totalMem+" used="+(memoryInfo.totalMem-memoryInfo.availMem));

        }
    }
}
