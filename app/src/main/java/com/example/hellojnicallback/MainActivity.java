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
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
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
        dMemInfo = new Debug.MemoryInfo();
    }
    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        hour = minute = second = 0;
        ((TextView)findViewById(R.id.hellojniMsg)).setText(stringFromJNI());
        startTicks();
        oom();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                oom();
            }
        }, 5000);
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
//        Log.i(TAG, "updateTimer");
        //XXX just for test
        oom();  // 尽管此方法由native层回调，但是其中分配的内存仍然算在java heap上，受制于java heap limit的上限，超过触发OOM。（native heap不受此限制 ）

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

    ArrayList<Bitmap> bitmapList = new ArrayList<>();
    boolean oomPrevented = false;
    long threshold = 4000000/1024;
    private void oom(){
        if (oomPrevented){
            printMemInfo();
            return;
        }
        Bitmap bm;
        int cnt=3;

        for (int i=0; i<cnt; ++i){
//            Log.i(TAG, "javaHeapLimit="+javaHeapLimit+ " javaHeapSize="+javaHeapSize+ " javaUsed="+javaHeapAlloc);
            if (javaHeapLimit - javaHeapAlloc < threshold){ // 防止触发OOM（此刻使用命令观察，对比代码输出）
                Log.i(TAG, "javaHeapLimit - javaHeapAlloc < threshold");
                oomPrevented = true;
//                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        printMemInfo();
//
//                    }
//                }, 5000);
//                printMemInfo();
                break;
            }
            /*上段代码无法防止发生OOM,因为触发OOM的原因是java heap size增长超过了java heap limit，而java heap size 会在java heap alloc
            * 大小快接近它时发生增长行为（具体接近多少，getprop中有定义），所以很可能即使满足了上述条件(javaHeapLimit - javaHeapAlloc < 4000000),
             * 仍然触发了java heap size的增长，而此增长超过了java heap limit进而导致OOM。故改用如下代码以防止OOM*/
//            if (javaHeapLimit - javaHeapSize < threshold){ // 防止触发OOM（此刻使用命令观察，对比代码输出）
//                oomPrevented = true;
//                break;
//            }
            bm = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
            bitmapList.add(bm);

            printMemInfo();
        }
    }

    long javaHeapLimit = Runtime.getRuntime().maxMemory()/1024; // java堆大小上限（同“getprop |grep heap”中获取到的值）超过该值将触发OOM
    // 如下项在dumpsys meminfo <package-name>中均有，且意义相同，已对比验证过。
    long javaHeapSize = 0; // 当前已分配java堆大小
    long javaHeapAlloc = 0;  // 当前已使用java堆大小
    long nativeHeapSize; // 当前已分配native堆大小(native没有limit，实测中分配1G也没有触发OOM)
    long nativeHeapAlloc;   // 当前已使用native堆大小
    Debug.MemoryInfo dMemInfo;
    private void printMemInfo(){
        javaHeapSize = Runtime.getRuntime().totalMemory()/1024; // 通过Runtime途径获取java heap信息
        javaHeapAlloc = javaHeapSize - Runtime.getRuntime().freeMemory()/1024;
        nativeHeapSize = Debug.getNativeHeapSize()/1024;
        nativeHeapAlloc = Debug.getNativeHeapAllocatedSize()/1024;
        Debug.getMemoryInfo(dMemInfo);
        String memMessage = String.format(
                "Memory: Pss=%.2f MB, Private=%.2f MB, Shared=%.2f MB",
                dMemInfo.getTotalPss() / 1024.0,
                dMemInfo.getTotalPrivateDirty() / 1024.0,
                dMemInfo.getTotalSharedDirty() / 1024.0);
        activityMgr.getMemoryInfo(memoryInfo); // 通过ActivityManager途径获取java heap信息
        Log.i(TAG, "javaHeapLimit="+ javaHeapLimit
                +" javaHeapSize="+ javaHeapSize +" javaHeapAlloc="+ javaHeapAlloc
                +" nativeHeapSize="+ nativeHeapSize + " nativeHeapAlloc="+ nativeHeapAlloc
                +" am.totalMem="+memoryInfo.totalMem/1024+" am.used="+(memoryInfo.totalMem-memoryInfo.availMem)/1024
                +" lowMem threshold="+memoryInfo.threshold+" isLowMem="+memoryInfo.lowMemory
                +"\n"+memMessage
        );
    }
}
